package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The content properties of a UI element — {@code ProgressBar.value}, {@code Toggle.isOn},
 * {@code TextField.rawText} — discovered from LDLib2's {@link Configurable} annotations.
 *
 * <h2>Why an annotation scan is acceptable here</h2>
 * KilaGraph deleted a reflective member picker once already (see {@code InfoContextNode}'s class
 * javadoc), and the objections were real: raw reflection offered {@code describeConstable} beside
 * the handful of properties anyone wanted, and it serialised a member name that a Minecraft rename
 * would silently turn into a dead read.
 *
 * <p>This is a narrower thing. {@code @Configurable} is not "every public member" — it is an
 * explicit, hand-placed annotation, and the set it produces is exactly the set LDLib2's own UI
 * editor shows in its Inspector. An element author decides what appears; nothing appears by
 * accident. The rename hazard does survive in reduced form (the stored value is still a field
 * name), which is why {@link #propertiesOf} is also the source for the node's search picker: a
 * property that no longer exists shows as unresolvable in the editor rather than reading as
 * {@code null} at runtime, and {@link #find} logs rather than silently returning nothing.
 *
 * <h2>What is deliberately excluded</h2>
 * <ul>
 *   <li><b>{@code subConfigurable = true}</b> fields. Those are the nested {@code Style} groups
 *       ({@code ButtonStyle}, {@code TextStyle}, {@code SlotStyle}) — the visual layer, which this
 *       graph addresses through LSS and class names instead. Reaching them here would be a second,
 *       competing way to set a background texture.</li>
 *   <li><b>Static fields</b>, which are not per-element state.</li>
 * </ul>
 *
 * <h2>Writes go through {@code @ConfigSetter} when there is one</h2>
 * The annotated setter is where the behaviour lives: {@code ProgressBar.setValue} clamps to the
 * min/max and {@code Toggle.setOn} notifies its listeners, while writing the field directly does
 * neither. So a setter wins whenever one is declared, and the raw field is only the fallback.
 */
public final class UIPropertyRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * One writable property of a UI element.
     *
     * @param key      the field name, as stored in a node option and shown in the picker
     * @param display  the {@code @Configurable(name=…)} label, or the key when there is none
     * @param type     the field's generic type, used to coerce an incoming graph value
     * @param getter   reads the current value off an element instance
     * @param setter   writes it, through the {@code @ConfigSetter} method when one exists
     */
    public record Property(String key, String display, Type type,
                           Function<Object, Object> getter,
                           BiConsumer<Object, Object> setter) {}

    private static final Map<Class<?>, Map<String, Property>> CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Property>> EXTRA = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> EXCLUDED = new ConcurrentHashMap<>();

    private UIPropertyRegistry() {}

    /**
     * Every property of {@code elementClass}, in declaration order from the base class down, keyed
     * by field name. Cached per class.
     */
    public static Map<String, Property> propertiesOf(Class<?> elementClass) {
        if (elementClass == null) return Map.of();
        return CACHE.computeIfAbsent(elementClass, UIPropertyRegistry::scan);
    }

    /** The properties of the class registered under {@code registryName}, or empty if unknown. */
    public static Map<String, Property> propertiesOfRegistered(String registryName) {
        Class<? extends UIElement> clazz = UIElements.classOf(registryName);
        return clazz == null ? Map.of() : propertiesOf(clazz);
    }

    /**
     * Looks a property up on the element's own class. Returns {@code null} — with a log line — when
     * the key does not resolve, which is what a graph saved before an LDLib2 rename will hit.
     */
    @Nullable
    public static Property find(Object element, String key) {
        if (element == null || key == null || key.isEmpty()) return null;
        Property property = propertiesOf(element.getClass()).get(key);
        if (property == null) {
            LOGGER.warn("No @Configurable property '{}' on {}", key, element.getClass().getName());
        }
        return property;
    }

    /**
     * Adds a property the annotations do not expose. {@code ItemSlot}'s real stack is the motivating
     * case: only {@code editorItemDisplay} carries {@code @Configurable}, because the live contents
     * are meant to arrive through a data binding rather than be set directly.
     *
     * <p>Inherited by subclasses, and the whole cache is dropped, for the same reasons as
     * {@link #exclude}. The getter and setter are handed the element instance, so they must accept
     * anything assignable to {@code elementClass}.</p>
     */
    public static void registerExtra(Class<?> elementClass, String key, String display, Type type,
                                     Function<Object, Object> getter, BiConsumer<Object, Object> setter) {
        EXTRA.computeIfAbsent(elementClass, c -> new LinkedHashMap<>())
                .put(key, new Property(key, display, type, getter, setter));
        CACHE.clear();
    }

    /**
     * Hides a property that is annotated but meaningless outside the editor.
     *
     * <p>Applies to {@code elementClass} and everything below it. The whole cache is dropped rather
     * than one entry, because a subclass scanned earlier would otherwise keep serving the property
     * this call was meant to hide.</p>
     */
    public static void exclude(Class<?> elementClass, String key) {
        EXCLUDED.computeIfAbsent(elementClass, c -> ConcurrentHashMap.newKeySet()).add(key);
        CACHE.clear();
    }

    private static Map<String, Property> scan(Class<?> elementClass) {
        // Superclass first, so a UIElement's shared properties sort above the ones its own class
        // adds — the order the Inspector shows, and the order the picker will list.
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = elementClass; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(0, c);
        }

        Map<String, Method> setters = new LinkedHashMap<>();
        for (Class<?> c : hierarchy) {
            for (Method m : c.getDeclaredMethods()) {
                ConfigSetter annotation = m.getAnnotation(ConfigSetter.class);
                if (annotation == null || m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                // A subclass overriding the setter should win, and the subclass is visited last.
                setters.put(annotation.field(), m);
            }
        }

        Map<String, Property> result = new LinkedHashMap<>();
        // Exclusions apply down the hierarchy: hiding UIElement's "id" should hide it on Button too,
        // which is the only reading of exclude() that is useful for a property every element inherits.
        Set<String> excluded = new java.util.HashSet<>();
        for (Class<?> c : hierarchy) {
            excluded.addAll(EXCLUDED.getOrDefault(c, Set.of()));
        }
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Configurable annotation = f.getAnnotation(Configurable.class);
                if (annotation == null || annotation.subConfigurable()) continue;
                if (excluded.contains(f.getName())) continue;
                f.setAccessible(true);
                result.put(f.getName(), toProperty(f, annotation, setters.get(f.getName())));
            }
        }
        // Superclass-first, so an extra registered on a subclass overrides one on its parent — the
        // same precedence the annotated fields above already follow.
        for (Class<?> c : hierarchy) {
            result.putAll(EXTRA.getOrDefault(c, Map.of()));
        }
        return Map.copyOf(result);
    }

    private static Property toProperty(Field field, Configurable annotation, @Nullable Method setter) {
        String display = annotation.name().isEmpty() ? field.getName() : annotation.name();
        Function<Object, Object> getter = owner -> {
            try {
                return field.get(owner);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Cannot read UI property {}", field, e);
                return null;
            }
        };
        BiConsumer<Object, Object> write = (owner, value) -> {
            try {
                if (setter != null) {
                    setter.invoke(owner, value);
                } else {
                    field.set(owner, value);
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Cannot write UI property {}", field, e);
            }
        };
        return new Property(field.getName(), display, field.getGenericType(), getter, write);
    }
}
