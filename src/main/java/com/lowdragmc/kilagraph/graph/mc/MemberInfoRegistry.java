package com.lowdragmc.kilagraph.graph.mc;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Reflection-derived catalog of the readable members on a class, used by {@link InfoNode} to expose
 * "pick a property to read off this {@code T}" options.
 *
 * <p>Discovery (reflection-primary, per the locked design): public, non-static
 * <ul>
 *   <li><b>fields</b> → key = field name;</li>
 *   <li><b>no-arg methods</b> named {@code getX}/{@code isX}/{@code hasX} (and record accessors) with
 *       a non-void return → key = de-prefixed camelCase ({@code getRainLevel}→{@code rainLevel};
 *       {@code isEmpty}→{@code isEmpty}, kept readable for booleans).</li>
 * </ul>
 * {@link Object} noise ({@code getClass}/{@code hashCode}/…) is excluded. Results are cached per
 * class. On top of the reflective set, callers may {@link #registerExtra} custom members or
 * {@link #exclude} unwanted keys — these overrides are applied every time the cache is (re)built.</p>
 *
 * <p>Getters swallow reflective failures and return {@code null} (a node reading a property should
 * degrade to "no value", never throw mid-evaluation).</p>
 */
public final class MemberInfoRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One readable property: a stable key, its declared return type, and a value extractor. */
    public record Member(String key, Type returnType, Function<Object, Object> getter) {}

    private static final Map<Class<?>, Map<String, Member>> CACHE = new ConcurrentHashMap<>();

    /** Per-class extra members layered on top of reflection (key → member factory input). */
    private static final Map<Class<?>, Map<String, Member>> EXTRA = new ConcurrentHashMap<>();
    /** Per-class excluded keys. */
    private static final Map<Class<?>, Set<String>> EXCLUDED = new ConcurrentHashMap<>();

    /** Method-name prefixes recognised as getters. */
    private static final String[] GETTER_PREFIXES = {"get", "is", "has"};
    /** Object-level / noise methods never exposed. */
    private static final Set<String> METHOD_DENYLIST = Set.of(
            "getClass", "hashCode", "toString", "clone", "wait", "notify", "notifyAll");

    private MemberInfoRegistry() {}

    /** The readable members of {@code clazz} (cached). Iteration order: fields then methods, sorted. */
    public static Map<String, Member> membersFor(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, MemberInfoRegistry::build);
    }

    /** Look up a single member by key, or {@code null} if the class has no such readable property. */
    @Nullable
    public static Member member(Class<?> clazz, String key) {
        return membersFor(clazz).get(key);
    }

    /**
     * Register an extra readable member on {@code clazz} (e.g. a computed value reflection can't see,
     * or a friendlier alias). Overwrites any reflective member of the same key. Clears the cache so
     * the next {@link #membersFor} rebuild picks it up.
     */
    public static void registerExtra(Class<?> clazz, String key, Type returnType,
                                     Function<Object, Object> getter) {
        EXTRA.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
                .put(key, new Member(key, returnType, getter));
        CACHE.remove(clazz);
    }

    /** Hide a reflective member by key. Clears the cache so the change takes effect. */
    public static void exclude(Class<?> clazz, String key) {
        EXCLUDED.computeIfAbsent(clazz, k -> ConcurrentHashMap.newKeySet()).add(key);
        CACHE.remove(clazz);
    }

    // ---- build -----------------------------------------------------------------------------

    private static Map<String, Member> build(Class<?> clazz) {
        var members = new LinkedHashMap<String, Member>();

        // public non-static fields
        for (Field f : clazz.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            String key = f.getName();
            members.putIfAbsent(key, new Member(key, f.getGenericType(), owner -> {
                try {
                    return f.get(owner);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }));
        }

        // public non-static no-arg getters
        for (Method m : clazz.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (METHOD_DENYLIST.contains(m.getName())) continue;
            String key = keyFor(m.getName());
            if (key == null || key.isEmpty()) continue;
            m.setAccessible(true);
            // Fields win over getters on key collision (fields are simpler/cheaper); putIfAbsent.
            members.putIfAbsent(key, new Member(key, m.getGenericReturnType(), owner -> {
                try {
                    return m.invoke(owner);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }));
        }

        // apply overrides
        Set<String> excluded = EXCLUDED.get(clazz);
        if (excluded != null) excluded.forEach(members::remove);
        Map<String, Member> extra = EXTRA.get(clazz);
        if (extra != null) members.putAll(extra);

        if (members.isEmpty()) {
            LOGGER.warn("[KGInfo] no readable members discovered for {}", clazz.getName());
        }
        return Collections.unmodifiableMap(members);
    }

    /**
     * Derives an option key from a getter name: strips {@code get}, lower-cases the first letter
     * ({@code getRainLevel}→{@code rainLevel}). {@code is*}/{@code has*} are kept verbatim so boolean
     * properties stay readable ({@code isEmpty}, {@code hasGlint}). Non-getter names (record-style
     * accessors like {@code count()}) are kept as-is.
     */
    @Nullable
    private static String keyFor(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) {
            return methodName; // keep "isEmpty"
        }
        if (methodName.startsWith("has") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) {
            return methodName; // keep "hasGlint"
        }
        // record-style accessor or plain no-arg method: use the name directly.
        return methodName;
    }
}
