package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    public static final TypeHandle NODE_REF;

    // Minecraft context/value handles not exposed as constants by LDLib2's TypeHandles.
    // (LDLib2 already registers DIRECTION/BLOCK/ITEM/FLUID/ENTITY_TYPE/ITEM_STACK/FLUID_STACK —
    //  import those from TypeHandles directly; don't re-register.)
    // Accessor-backed (picker + serialization): BLOCK_POS, BLOCK_STATE.
    // Wire-only context (no AccessorRegistries entry → withoutConfigurator path): LEVEL, ENTITY,
    //  PLAYER, BLOCK_ENTITY. All registered via fromType so the identification is the class name
    //  and handleFor() resolves them without an override.
    public static final TypeHandle BLOCK_POS;
    public static final TypeHandle BLOCK_STATE;
    public static final TypeHandle LEVEL;
    public static final TypeHandle ENTITY;
    public static final TypeHandle PLAYER;
    public static final TypeHandle BLOCK_ENTITY;

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

        // NODE_REF: a reference to another node (by UID), carried by the Cache / CacheClear pair.
        // Same no-configurator path as LIST/MAP — NodeRef has no AccessorRegistries entry, so its
        // ports get no embedded constant and the value flows purely over the wire.
        NODE_REF = TypeHandleHelpers.customType(NodeRef.class, "NODE_REF", "Node Reference");
        registerOverride(NodeRef.class, NODE_REF);

        // Minecraft handles. fromType → identification is the class name, so handleFor(BlockPos.class)
        // etc. resolves to the same handle with no override needed. BlockPos/BlockState have
        // accessors (pickers + serialization); Level/Entity/Player/BlockEntity don't (wire-only).
        BLOCK_POS = TypeHandleHelpers.fromType(BlockPos.class, "BlockPos");
        BLOCK_STATE = TypeHandleHelpers.fromType(BlockState.class, "BlockState");
        LEVEL = TypeHandleHelpers.fromType(Level.class, "Level");
        ENTITY = TypeHandleHelpers.fromType(Entity.class, "Entity");
        PLAYER = TypeHandleHelpers.fromType(Player.class, "Player");
        BLOCK_ENTITY = TypeHandleHelpers.fromType(BlockEntity.class, "BlockEntity");
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
