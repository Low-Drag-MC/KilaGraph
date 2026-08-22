package com.lowdragmc.kilagraph.graph.type;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

    /**
     * Vectors, two to four components.
     *
     * <p>Bound to JOML because LDLib2 already has accessors for {@code Vector2f/3f/4f} — that is
     * what gives these pins an inline value editor and a serialisable embedded constant, which a
     * bare custom type would not have.
     *
     * <p>They live here rather than in a consumer because vector arithmetic is not specific to any
     * one host: EntityStudio needed them first, but "add two vectors" belongs beside "add two
     * floats", not in an animation mod. A consumer that defined its own would also be defining a
     * second, incompatible handle for the same three floats.
     */
    public static final TypeHandle VEC2;
    public static final TypeHandle VEC3;
    public static final TypeHandle VEC4;

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
    /**
     * An item inventory, as NeoForge's {@code IItemHandler}.
     *
     * <p>The capability interface rather than vanilla's {@code Container} on purpose: it is the one
     * abstraction that covers a chest, a furnace's three slots, a player's inventory, an entity's
     * inventory and any modded machine alike, and it is what hoppers and pipes already speak. A node
     * set built on {@code Container} would work on vanilla blocks and silently fail on every modded
     * one, which is the opposite of what a scripting layer wants.
     *
     * <p>Wire-only, like the other live-object handles — an inventory is resolved from the world by
     * {@code mc_block_container} / {@code mc_entity_container}, never authored as a literal.</p>
     */
    public static final TypeHandle CONTAINER;
    /**
     * A fluid tank, as NeoForge's {@code IFluidHandler} — the same idea as {@link #CONTAINER} for the
     * other thing blocks store. Vanilla has no fluid-storage abstraction at all, so this one is entirely
     * NeoForge's, which is also why every tank in the game that a graph might care about speaks it.
     */
    public static final TypeHandle FLUID_CONTAINER;
    /**
     * NBT compound tag. LDLib2 has a {@code Tag} accessor and a {@code TagAccessor} widget, so unlike
     * LEVEL/ENTITY this one is a real editable value — it just needed a default before the editor
     * could build a constant for it.
     */
    public static final TypeHandle NBT_COMPOUND;

    // ---- data types whose codec and widget LDLib2 already provides ----------------------------
    //
    // Each of these is two lines of work: fromType mints the handle (identification = class name, so
    // handleFor resolves it with no override) and setCustomDefaultValue keeps an unconnected port's
    // constant from being null. AccessorRegistries already has an entry for every one of them
    // (ResourceLocation, AABB, ChunkPos, Component, and every enum via EnumAccessor), and so does
    // ConfiguratorAccessors — except ChunkPos, which falls back to an empty inspector row rather
    // than crashing.

    /** A namespaced id. Also how this graph models registry keys, tags, biomes and dimensions. */
    public static final TypeHandle RESOURCE_LOCATION;
    /** An axis-aligned bounding box. */
    public static final TypeHandle AABB;
    /** A chunk coordinate pair. */
    public static final TypeHandle CHUNK_POS;
    /** A chat component. Named TEXT rather than COMPONENT to avoid reading as a UI component. */
    public static final TypeHandle TEXT;
    /** Block rotation, for {@code BlockState.rotate}. */
    public static final TypeHandle ROTATION;
    /** Block mirroring, for {@code BlockState.mirror}. */
    public static final TypeHandle MIRROR;
    /** {@code Direction.Axis} — X, Y or Z. */
    public static final TypeHandle AXIS;
    /** Which slot an entity holds or wears something in. */
    public static final TypeHandle EQUIPMENT_SLOT;

    /** Optional overrides: a Java type that should resolve to a specific custom TypeHandle. */
    private static final Map<Type, TypeHandle> OVERRIDES = new ConcurrentHashMap<>();

    static {
        VEC2 = vector(org.joml.Vector2f.class, "VEC2", "Vector2", 0xFF7ED3F0, org.joml.Vector2f::new);
        VEC3 = vector(org.joml.Vector3f.class, "VEC3", "Vector3", 0xFFF3C13A, org.joml.Vector3f::new);
        VEC4 = vector(org.joml.Vector4f.class, "VEC4", "Vector4", 0xFFE08A3C, org.joml.Vector4f::new);

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
        // LDLib2 2.2.26's TypeHandle.getDefaultValue() returns null for a fromType handle with no registered
        // default (26.1 fell back to the accessor's own default). BLOCK_POS/BLOCK_STATE are accessor-backed,
        // so an unconnected input port builds a constant editor that reads getDefaultValue(); a null value
        // then NPEs inside BlockPosAccessor/BlockStateAccessor (supplier.get().getX()). Seed non-null defaults
        // explicitly (matching BlockPosAccessor.defaultValue()'s BlockPos(0,0,0)).
        TypeHandleHelpers.setCustomDefaultValue(BLOCK_POS, () -> new BlockPos(0, 0, 0));
        TypeHandleHelpers.setCustomDefaultValue(BLOCK_STATE, () -> Blocks.AIR.defaultBlockState());
        LEVEL = TypeHandleHelpers.fromType(Level.class, "Level");
        ENTITY = TypeHandleHelpers.fromType(Entity.class, "Entity");
        PLAYER = TypeHandleHelpers.fromType(Player.class, "Player");
        BLOCK_ENTITY = TypeHandleHelpers.fromType(BlockEntity.class, "BlockEntity");
        CONTAINER = TypeHandleHelpers.fromType(
                net.neoforged.neoforge.items.IItemHandler.class, "Container");
        FLUID_CONTAINER = TypeHandleHelpers.fromType(
                net.neoforged.neoforge.fluids.capability.IFluidHandler.class, "FluidContainer");
        NBT_COMPOUND = TypeHandleHelpers.fromType(CompoundTag.class, "CompoundTag");
        // Without this the NBT editor cannot open: Constant.init seeds the value from
        // getDefaultValue(), and TagAccessor has nothing to edit when that is null.
        TypeHandleHelpers.setCustomDefaultValue(NBT_COMPOUND, CompoundTag::new);

        // DIRECTION is LDLib2's handle, not ours, and it is the one Minecraft handle LDLib2 minted
        // without a default value — every other one (BLOCK, ITEM, FLUID, ENTITY_TYPE, ITEM_STACK,
        // FLUID_STACK) got a setCustomDefaultValue and Direction did not. The consequence is worse
        // than a null: an enum constant renders through SelectorConfigurator, which displays the
        // first candidate when the value is null WITHOUT writing it back, so a Direction constant
        // node reads "down" in the editor while emitting null. Seeding the default here is what
        // makes a plain Direction constant node behave, and it is the whole difference between one
        // and DirectionConstNode.
        //
        // Safe to do from here because nothing has read the handle's default yet: LDLib2's
        // TypeHandles.init() only mints handles, and defaults are first read when a constant is
        // built — which happens no earlier than the node registry scan, and Kilagraph's constructor
        // calls KGTypeHandles.init() before touching the registry.
        TypeHandleHelpers.setCustomDefaultValue(TypeHandles.DIRECTION, () -> Direction.NORTH);

        RESOURCE_LOCATION = mc(ResourceLocation.class, "ResourceLocation", 0xFFB48EAD,
                () -> ResourceLocation.fromNamespaceAndPath("minecraft", "air"));
        AABB = mc(net.minecraft.world.phys.AABB.class, "AABB", 0xFF88C0D0,
                () -> new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1));
        CHUNK_POS = mc(ChunkPos.class, "ChunkPos", 0xFF81A1C1, () -> new ChunkPos(0, 0));
        TEXT = mc(Component.class, "Text", 0xFFEBCB8B, Component::empty);
        ROTATION = mc(Rotation.class, "Rotation", 0xFFA3BE8C, () -> Rotation.NONE);
        MIRROR = mc(Mirror.class, "Mirror", 0xFF8FBCBB, () -> Mirror.NONE);
        AXIS = mc(Direction.Axis.class, "Axis", 0xFFD08770, () -> Direction.Axis.Y);
        EQUIPMENT_SLOT = mc(EquipmentSlot.class, "EquipmentSlot", 0xFFBF616A,
                () -> EquipmentSlot.MAINHAND);
    }

    private KGTypeHandles() {}

    /**
     * A vector handle, fully described in the one call that mints it.
     *
     * <p>LDLib2 caches colour, default value and configurator lazily <b>per handle instance</b>, so
     * a property attached after something has already asked for it is silently ignored. The default
     * is not cosmetic either: an unconnected port builds its constant from it and hands it straight
     * to the accessor, which reads {@code .x} off it — a vector type without a default is a null
     * dereference the first time anyone drops the node.
     */
    private static TypeHandle vector(Class<?> javaType, String id, String display, int colour,
                                     java.util.function.Supplier<Object> defaultValue) {
        TypeHandle handle = TypeHandleHelpers.customType(javaType, id, display);
        TypeHandleHelpers.setCustomColor(handle, colour);
        TypeHandleHelpers.setCustomDefaultValue(handle, defaultValue);
        registerOverride(javaType, handle);
        return handle;
    }

    /**
     * A Minecraft value handle, fully described in the one call that mints it.
     *
     * <p>Same caching hazard as {@link #vector}: colour and default are cached lazily per handle
     * instance, so both have to be attached here rather than later. No {@code registerOverride} —
     * {@code fromType} makes the identification the class name, which is exactly what
     * {@link #handleFor} falls through to.
     */
    private static TypeHandle mc(Class<?> javaType, String display, int colour,
                                java.util.function.Supplier<Object> defaultValue) {
        TypeHandle handle = TypeHandleHelpers.fromType(javaType, display);
        TypeHandleHelpers.setCustomColor(handle, colour);
        TypeHandleHelpers.setCustomDefaultValue(handle, defaultValue);
        return handle;
    }

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
