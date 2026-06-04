package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KilaGraph-defined {@link TypeHandle}s and a {@code Type → TypeHandle} override registry shared
 * across all KilaGraph graphs.
 *
 * <p>Element-typed list ports use {@link #LIST} as the wire type; the actual element type lives
 * on the producing/consuming node as an {@code @Option TypeHandle}.</p>
 */
public final class KGTypeHandles {

    public static final TypeHandle LIST;
    public static final TypeHandle MAP;

    /** Optional overrides: a Java type that should resolve to a specific custom TypeHandle. */
    private static final Map<Type, TypeHandle> OVERRIDES = new ConcurrentHashMap<>();

    static {
        LIST = TypeHandleHelpers.customType(List.class, "LIST", "List");
        // No custom default value: LDLib2 would otherwise initialise the embedded constant with
        // an ArrayList, and serialising it fails because List<raw> has no AccessorRegistries entry.
        // List input ports rely on the upstream wire; if unconnected, evaluate() falls back to
        // List.of() at the call site.
        registerOverride(List.class, LIST);

        // MAP mirrors LIST: customType + override, no default constant so the no-configurator path
        // takes over. Backed by Map<Object, Object> at runtime; keyType/valueType options on map
        // nodes drive the actual element typing.
        MAP = TypeHandleHelpers.customType(Map.class, "MAP", "Map");
        registerOverride(Map.class, MAP);
        registerOverride(HashMap.class, MAP);
    }

    private KGTypeHandles() {}

    public static void registerOverride(Type javaType, TypeHandle handle) {
        OVERRIDES.put(javaType, handle);
    }

    /** Resolves a Java {@link Type} to its KilaGraph-canonical {@link TypeHandle}. */
    public static TypeHandle handleFor(Type t) {
        TypeHandle override = OVERRIDES.get(t);
        if (override != null) return override;
        // ParameterizedType (e.g. List<Float>) collapses to the raw type's handle for v1.
        if (t instanceof ParameterizedType pt) {
            override = OVERRIDES.get(pt.getRawType());
            if (override != null) return override;
        }
        return TypeHandleHelpers.fromType(t);
    }

    @Nullable
    public static TypeHandle lookupOverride(Type t) {
        return OVERRIDES.get(t);
    }

    public static void init() {
        // Force static init from elsewhere.
    }
}
