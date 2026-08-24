package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.block.BlockStateNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityTypeNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.fluid.FluidNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.AabbNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.ChunkPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.DirectionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.id.McIdNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtKeysNode;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import net.minecraft.core.component.DataComponents;

/**
 * The dedicated read nodes for the simple MC value types.
 *
 * <p>These replaced the reflective {@code info_*} contexts for every type that is a value rather than a
 * live game object — a {@code BlockPos} is three ints, an {@code AABB} is six doubles, and neither has a
 * long tail of members worth a context node plus a Field block plus a property searcher.
 *
 * <p><b>Expectations are computed from the same MC call the node makes, not written as literals</b>,
 * wherever the value is a game constant rather than something this test chose. Asserting that a stone
 * block's friction is {@code 0.6} would be testing Minecraft; asserting that the node reports whatever
 * {@code Blocks.STONE.getFriction()} returns is testing the node. The literals that remain — the
 * coordinates, the item counts — are values the test supplies itself.
 */
public final class McDecomposeGameTest {
    private static final String BLOCK_POS_UNPACK = "mc_decompose_block_pos_unpack";
    private static final String AABB_UNPACK_AND_CENTRE = "mc_decompose_aabb_unpack_and_centre";
    private static final String CHUNK_POS_UNPACK = "mc_decompose_chunk_pos_unpack";
    private static final String DIRECTION_DATA = "mc_decompose_direction_data";
    private static final String ID_UNPACK = "mc_decompose_id_unpack";
    private static final String NBT_KEYS = "mc_decompose_nbt_keys";
    private static final String BLOCK_STATE_BLOCK_AND_FLAGS = "mc_decompose_block_state_block_and_flags";
    private static final String BLOCK_PROPS = "mc_decompose_block_props";
    private static final String ITEM_STACK_UNPACK_DAMAGE_LIMITS = "mc_decompose_item_stack_unpack_damage_limits";
    private static final String ITEM_PROPS = "mc_decompose_item_props";
    private static final String FLUID_STACK_UNPACK_AND_BUCKET = "mc_decompose_fluid_stack_unpack_and_bucket";
    private static final String ENTITY_TYPE_PROPS = "mc_decompose_entity_type_props";

    public static void registerFunctions() {
        KGGameTests.registerFunction(BLOCK_POS_UNPACK, McDecomposeGameTest::blockPosUnpack);
        KGGameTests.registerFunction(AABB_UNPACK_AND_CENTRE, McDecomposeGameTest::aabbUnpackAndCentre);
        KGGameTests.registerFunction(CHUNK_POS_UNPACK, McDecomposeGameTest::chunkPosUnpack);
        KGGameTests.registerFunction(DIRECTION_DATA, McDecomposeGameTest::directionData);
        KGGameTests.registerFunction(ID_UNPACK, McDecomposeGameTest::idUnpack);
        KGGameTests.registerFunction(NBT_KEYS, McDecomposeGameTest::nbtKeys);
        KGGameTests.registerFunction(BLOCK_STATE_BLOCK_AND_FLAGS, McDecomposeGameTest::blockStateBlockAndFlags);
        KGGameTests.registerFunction(BLOCK_PROPS, McDecomposeGameTest::blockProps);
        KGGameTests.registerFunction(ITEM_STACK_UNPACK_DAMAGE_LIMITS, McDecomposeGameTest::itemStackUnpackDamageLimits);
        KGGameTests.registerFunction(ITEM_PROPS, McDecomposeGameTest::itemProps);
        KGGameTests.registerFunction(FLUID_STACK_UNPACK_AND_BUCKET, McDecomposeGameTest::fluidStackUnpackAndBucket);
        KGGameTests.registerFunction(ENTITY_TYPE_PROPS, McDecomposeGameTest::entityTypeProps);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                BLOCK_POS_UNPACK, AABB_UNPACK_AND_CENTRE, CHUNK_POS_UNPACK,
                DIRECTION_DATA, ID_UNPACK, NBT_KEYS,
                BLOCK_STATE_BLOCK_AND_FLAGS, BLOCK_PROPS, ITEM_STACK_UNPACK_DAMAGE_LIMITS,
                ITEM_PROPS, FLUID_STACK_UNPACK_AND_BUCKET, ENTITY_TYPE_PROPS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private static final float EPS = 1.0e-4f;

    private McDecomposeGameTest() {
    }

    // ---- geometry ----------------------------------------------------------------------------

    public static void blockPosUnpack(GameTestHelper helper) {
        var n = probe(BlockPosNodes.Unpack.class, "in", new BlockPos(3, -4, 5));
        assertEq(helper, "x", 3, n.eval("x", Integer.class).intValue());
        assertEq(helper, "y", -4, n.eval("y", Integer.class).intValue());
        assertEq(helper, "z", 5, n.eval("z", Integer.class).intValue());

        // A null position reads as the origin rather than throwing, like the rest of the group.
        var empty = probe(BlockPosNodes.Unpack.class);
        assertEq(helper, "null in → 0", 0, empty.eval("y", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * The box readers, and the reason their outputs are {@code double}.
     *
     * <p>The coordinates are deliberately ones a {@code float} cannot hold: {@code 1e7 + 0.5} rounds to
     * a whole number in single precision. Asserting <b>exact</b> equality on the {@code Double} therefore
     * fails if these outputs are ever narrowed — which is not hypothetical, it is the precision cliff
     * {@code McConvert} documents and that the raycast node was already caught on once, at gametest
     * coordinates of about 1.3e7.
     */
    public static void aabbUnpackAndCentre(GameTestHelper helper) {
        AABB box = new AABB(1e7 + 0.5, 2.25, 3, 1e7 + 4.5, 8, 13);
        var u = probe(AabbNodes.Unpack.class, "in", box);
        assertEq(helper, "minX", (Object) box.minX, u.eval("minX", Double.class));
        assertEq(helper, "minY", (Object) box.minY, u.eval("minY", Double.class));
        assertEq(helper, "minZ", (Object) box.minZ, u.eval("minZ", Double.class));
        assertEq(helper, "maxX", (Object) box.maxX, u.eval("maxX", Double.class));
        assertEq(helper, "maxY", (Object) box.maxY, u.eval("maxY", Double.class));
        assertEq(helper, "maxZ", (Object) box.maxZ, u.eval("maxZ", Double.class));
        // The half-block that a float would have swallowed really did survive.
        assertTrue(helper, "minX kept its half block", u.eval("minX", Double.class) % 1.0 == 0.5);

        var c = probe(AabbNodes.Center.class, "in", box);
        assertEq(helper, "xSize", (Object) box.getXsize(), c.eval("xSize", Double.class));
        assertEq(helper, "ySize", (Object) box.getYsize(), c.eval("ySize", Double.class));
        assertEq(helper, "zSize", (Object) box.getZsize(), c.eval("zSize", Double.class));

        // The centre is the graph's vector type, not a Vec3 — the whole reason this is a node.
        float[] mid = VectorNodes.components(c.eval("center", Object.class));
        assertEq(helper, "centre x", (float) box.getCenter().x, mid[0], EPS);
        assertEq(helper, "centre y", (float) box.getCenter().y, mid[1], EPS);
        assertEq(helper, "centre z", (float) box.getCenter().z, mid[2], EPS);
        helper.succeed();
    }

    public static void chunkPosUnpack(GameTestHelper helper) {
        ChunkPos chunk = new ChunkPos(2, -3);
        var n = probe(ChunkPosNodes.Unpack.class, "in", chunk);
        assertEq(helper, "x", 2, n.eval("x", Integer.class).intValue());
        assertEq(helper, "z", -3, n.eval("z", Integer.class).intValue());
        assertEq(helper, "minBlockX", chunk.getMinBlockX(), n.eval("minBlockX", Integer.class).intValue());
        assertEq(helper, "minBlockZ", chunk.getMinBlockZ(), n.eval("minBlockZ", Integer.class).intValue());
        assertEq(helper, "maxBlockX", chunk.getMaxBlockX(), n.eval("maxBlockX", Integer.class).intValue());
        assertEq(helper, "maxBlockZ", chunk.getMaxBlockZ(), n.eval("maxBlockZ", Integer.class).intValue());
        // The bounds really do span 16 blocks — the shift arithmetic is the point of the node.
        assertEq(helper, "the chunk is 16 wide", 15,
                n.eval("maxBlockX", Integer.class) - n.eval("minBlockX", Integer.class));
        helper.succeed();
    }

    public static void directionData(GameTestHelper helper) {
        for (Direction d : Direction.values()) {
            var n = probe(DirectionNodes.Data.class, "in", d);
            assertEq(helper, d + " yRot", d.toYRot(), n.eval("yRot", Float.class), EPS);
            assertEq(helper, d + " data2D", d.get2DDataValue(), n.eval("data2D", Integer.class).intValue());
            assertEq(helper, d + " data3D", d.get3DDataValue(), n.eval("data3D", Integer.class).intValue());
        }
        // The documented -1 for the vertical faces, which is the part a graph has to handle.
        assertEq(helper, "up has no horizontal index", -1,
                probe(DirectionNodes.Data.class, "in", Direction.UP).eval("data2D", Integer.class).intValue());
        helper.succeed();
    }

    // ---- identifiers and NBT -----------------------------------------------------------------

    public static void idUnpack(GameTestHelper helper) {
        var n = probe(McIdNodes.Unpack.class, "in", Identifier.fromNamespaceAndPath("kilagraph", "some/path"));
        assertEq(helper, "namespace", "kilagraph", n.eval("namespace", String.class));
        assertEq(helper, "path", "some/path", n.eval("path", String.class));

        // An unconnected identifier input is not null: the handle carries a default of minecraft:air,
        // which is what stops a generic Identifier constant from emitting null into a registry lookup.
        var unset = probe(McIdNodes.Unpack.class);
        assertEq(helper, "unset id uses the handle default", "minecraft", unset.eval("namespace", String.class));
        assertEq(helper, "and its path", "air", unset.eval("path", String.class));
        helper.succeed();
    }

    public static void nbtKeys(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("zeta", 1);
        tag.putString("alpha", "x");
        tag.putBoolean("mid", true);

        var n = probe(NbtKeysNode.class, "in", tag);
        assertEq(helper, "size", 3, n.eval("size", Integer.class).intValue());
        assertFalse(helper, "not empty", n.eval("empty", Boolean.class));
        // Sorted, not hash order — the node promises this so two equal tags compare equal.
        assertEq(helper, "keys are sorted", List.of("alpha", "mid", "zeta"), n.eval("keys", List.class));

        var blank = probe(NbtKeysNode.class, "in", new CompoundTag());
        assertTrue(helper, "empty tag", blank.eval("empty", Boolean.class));
        assertEq(helper, "empty size", 0, blank.eval("size", Integer.class).intValue());
        helper.succeed();
    }

    // ---- blocks ------------------------------------------------------------------------------

    public static void blockStateBlockAndFlags(GameTestHelper helper) {
        assertEq(helper, "state → block", Blocks.STONE,
                probe(BlockStateNodes.StateBlock.class, "in", Blocks.STONE.defaultBlockState())
                        .eval("out", Block.class));

        var stone = probe(BlockStateNodes.Flags.class, "in", Blocks.STONE.defaultBlockState());
        assertFalse(helper, "stone is not air", stone.eval("air", Boolean.class));
        assertTrue(helper, "stone blocks motion", stone.eval("blocksMotion", Boolean.class));
        assertFalse(helper, "stone is not liquid", stone.eval("liquid", Boolean.class));
        assertTrue(helper, "stone is solid", stone.eval("solid", Boolean.class));
        assertTrue(helper, "stone occludes", stone.eval("canOcclude", Boolean.class));
        assertFalse(helper, "stone does not tick randomly", stone.eval("randomlyTicking", Boolean.class));
        assertEq(helper, "stone emits no light", 0, stone.eval("lightEmission", Integer.class).intValue());

        var air = probe(BlockStateNodes.Flags.class, "in", Blocks.AIR.defaultBlockState());
        assertTrue(helper, "air is air", air.eval("air", Boolean.class));
        assertFalse(helper, "air does not block motion", air.eval("blocksMotion", Boolean.class));
        assertFalse(helper, "air is not solid", air.eval("solid", Boolean.class));

        // Glass is the block that separates "solid" from "occludes": it stops you but not light.
        var glass = probe(BlockStateNodes.Flags.class, "in", Blocks.GLASS.defaultBlockState());
        assertTrue(helper, "glass blocks motion", glass.eval("blocksMotion", Boolean.class));
        assertFalse(helper, "but does not occlude", glass.eval("canOcclude", Boolean.class));

        assertTrue(helper, "leaves tick randomly",
                probe(BlockStateNodes.Flags.class, "in", Blocks.OAK_LEAVES.defaultBlockState())
                        .eval("randomlyTicking", Boolean.class));

        var water = probe(BlockStateNodes.Flags.class, "in", Blocks.WATER.defaultBlockState());
        assertTrue(helper, "water is liquid", water.eval("liquid", Boolean.class));

        var lamp = probe(BlockStateNodes.Flags.class, "in", Blocks.GLOWSTONE.defaultBlockState());
        assertEq(helper, "glowstone emits light", Blocks.GLOWSTONE.defaultBlockState().getLightEmission(),
                lamp.eval("lightEmission", Integer.class).intValue());
        assertTrue(helper, "glowstone emits light at all", lamp.eval("lightEmission", Integer.class) > 0);

        var chest = probe(BlockStateNodes.Flags.class, "in", Blocks.CHEST.defaultBlockState());
        assertTrue(helper, "a chest has a block entity", chest.eval("hasBlockEntity", Boolean.class));
        helper.succeed();
    }

    public static void blockProps(GameTestHelper helper) {
        var stone = probe(BlockStateNodes.BlockProps.class, "in", Blocks.STONE);
        assertEq(helper, "friction", Blocks.STONE.getFriction(), stone.eval("friction", Float.class), EPS);
        assertEq(helper, "speedFactor", Blocks.STONE.getSpeedFactor(), stone.eval("speedFactor", Float.class), EPS);
        assertEq(helper, "jumpFactor", Blocks.STONE.getJumpFactor(), stone.eval("jumpFactor", Float.class), EPS);
        assertEq(helper, "destroyTime", Blocks.STONE.defaultDestroyTime(), stone.eval("destroyTime", Float.class), EPS);
        assertEq(helper, "explosionResistance", Blocks.STONE.getExplosionResistance(),
                stone.eval("explosionResistance", Float.class), EPS);
        assertEq(helper, "name", Blocks.STONE.getName().getString(),
                stone.eval("name", Component.class).getString());

        // Ice is the block these numbers exist for: it is measurably more slippery than stone.
        assertTrue(helper, "ice is slipperier than stone",
                probe(BlockStateNodes.BlockProps.class, "in", Blocks.ICE).eval("friction", Float.class)
                        > stone.eval("friction", Float.class));
        helper.succeed();
    }

    // ---- items -------------------------------------------------------------------------------

    public static void itemStackUnpackDamageLimits(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND, 7);
        var u = probe(ItemStackNodes.Unpack.class, "stack", stack);
        assertEq(helper, "item", Items.DIAMOND, u.eval("item", Item.class));
        assertEq(helper, "count", 7, u.eval("count", Integer.class).intValue());
        assertFalse(helper, "not empty", u.eval("empty", Boolean.class));

        assertTrue(helper, "EMPTY is empty",
                probe(ItemStackNodes.Unpack.class, "stack", ItemStack.EMPTY).eval("empty", Boolean.class));

        // A diamond cannot be damaged; a pickaxe can. That pair is what `damageable` is for — dividing
        // damage by maxDamage on the first would produce a NaN.
        var gem = probe(ItemStackNodes.Damage.class, "stack", stack);
        assertFalse(helper, "a diamond is not damageable", gem.eval("damageable", Boolean.class));
        assertEq(helper, "and has no max damage", 0, gem.eval("maxDamage", Integer.class).intValue());

        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.setDamageValue(5);
        var tool = probe(ItemStackNodes.Damage.class, "stack", pick);
        assertTrue(helper, "a pickaxe is damageable", tool.eval("damageable", Boolean.class));
        assertTrue(helper, "and is damaged", tool.eval("damaged", Boolean.class));
        assertEq(helper, "by the amount set", 5, tool.eval("damage", Integer.class).intValue());
        assertEq(helper, "out of its own maximum", pick.getMaxDamage(),
                tool.eval("maxDamage", Integer.class).intValue());

        var limits = probe(ItemStackNodes.Limits.class, "stack", stack);
        assertEq(helper, "maxStackSize", stack.getMaxStackSize(),
                limits.eval("maxStackSize", Integer.class).intValue());
        assertTrue(helper, "diamonds stack", limits.eval("stackable", Boolean.class));
        assertFalse(helper, "and are not enchanted", limits.eval("enchanted", Boolean.class));
        assertFalse(helper, "a pickaxe does not stack",
                probe(ItemStackNodes.Limits.class, "stack", pick).eval("stackable", Boolean.class));
        helper.succeed();
    }

    public static void itemProps(GameTestHelper helper) {
        var n = probe(ItemStackNodes.ItemProps.class, "in", Items.DIAMOND);
        assertEq(helper, "maxStackSize", Items.DIAMOND.getDefaultMaxStackSize(),
                n.eval("maxStackSize", Integer.class).intValue());
        var enchantable = Items.DIAMOND.components().get(DataComponents.ENCHANTABLE);
        assertEq(helper, "enchantmentValue", enchantable == null ? 0 : enchantable.value(),
                n.eval("enchantmentValue", Integer.class).intValue());
        assertEq(helper, "name", Component.translatable(Items.DIAMOND.getDescriptionId()).getString(),
                n.eval("name", Component.class).getString());
        helper.succeed();
    }

    // ---- fluids ------------------------------------------------------------------------------

    public static void fluidStackUnpackAndBucket(GameTestHelper helper) {
        var n = probe(FluidNodes.Unpack.class, "stack", new FluidStack(Fluids.WATER, 250));
        assertEq(helper, "fluid", Fluids.WATER, n.eval("fluid", Fluid.class));
        assertEq(helper, "amount", 250, n.eval("amount", Integer.class).intValue());
        assertFalse(helper, "not empty", n.eval("empty", Boolean.class));
        assertTrue(helper, "has a display name", !n.eval("name", Component.class).getString().isEmpty());

        assertTrue(helper, "EMPTY is empty",
                probe(FluidNodes.Unpack.class, "stack", FluidStack.EMPTY).eval("empty", Boolean.class));

        assertEq(helper, "water's bucket", Items.WATER_BUCKET,
                probe(FluidNodes.Bucket.class, "in", Fluids.WATER).eval("out", Item.class));
        assertEq(helper, "EMPTY has no bucket", Items.AIR,
                probe(FluidNodes.Bucket.class, "in", Fluids.EMPTY).eval("out", Item.class));
        helper.succeed();
    }

    // ---- entity types ------------------------------------------------------------------------

    public static void entityTypeProps(GameTestHelper helper) {
        var pig = probe(EntityTypeNodes.Props.class, "in", EntityType.PIG);
        assertEq(helper, "width", EntityType.PIG.getWidth(), pig.eval("width", Float.class), EPS);
        assertEq(helper, "height", EntityType.PIG.getHeight(), pig.eval("height", Float.class), EPS);
        assertFalse(helper, "a pig burns", pig.eval("fireImmune", Boolean.class));
        assertEq(helper, "category", EntityType.PIG.getCategory().getName(), pig.eval("category", String.class));
        assertEq(helper, "name", EntityType.PIG.getDescription().getString(),
                pig.eval("name", Component.class).getString());

        // The serialized category name, not the enum constant — lower case is what commands use.
        assertEq(helper, "category is serialized form", "creature", pig.eval("category", String.class));
        assertTrue(helper, "a blaze is fire immune",
                probe(EntityTypeNodes.Props.class, "in", EntityType.BLAZE).eval("fireImmune", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** A node alone in a graph with its inputs set as constants. */
    private record Probe(GraphExecutor exec, NodeModel model) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(model.getOutputsById().get(output), type);
        }
    }

    private static Probe probe(Class<? extends Node> cls, Object... inputs) {
        var g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        return new Probe(new GraphExecutor(g), n);
    }
}
