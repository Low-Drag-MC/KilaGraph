package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay.EnchantmentNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.redstone.RedstoneNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.RaycastEntityNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Vector2f;
import org.joml.Vector3f;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.minecraft.world.phys.Vec3;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Redstone reading, enchantments and the entity raycast.
 *
 * <p>The redstone cases place a real signal source and assert the number changes with it. A test that only
 * checked "an unpowered block reads zero" would pass on a node that always returned zero, so every case
 * here has a powered and an unpowered state.
 */
public final class McRedstoneGameTest {
    private static final String READS_POWER_FROM_A_REDSTONE_BLOCK = "mc_redstone_reads_power_from_a_redstone_block";
    private static final String DIRECT_SIGNAL_IGNORES_WEAK_POWER = "mc_redstone_direct_signal_ignores_weak_power";
    private static final String COMPARATOR_READS_CONTAINER_FULLNESS = "mc_redstone_comparator_reads_container_fullness";
    private static final String READS_AND_ADDS_ENCHANTMENTS = "mc_redstone_reads_and_adds_enchantments";
    private static final String RAYCAST_FINDS_AN_ENTITY = "mc_redstone_raycast_finds_an_entity";

    public static void registerFunctions() {
        KGGameTests.registerFunction(READS_POWER_FROM_A_REDSTONE_BLOCK, McRedstoneGameTest::readsPowerFromARedstoneBlock);
        KGGameTests.registerFunction(DIRECT_SIGNAL_IGNORES_WEAK_POWER, McRedstoneGameTest::directSignalIgnoresWeakPower);
        KGGameTests.registerFunction(COMPARATOR_READS_CONTAINER_FULLNESS, McRedstoneGameTest::comparatorReadsContainerFullness);
        KGGameTests.registerFunction(READS_AND_ADDS_ENCHANTMENTS, McRedstoneGameTest::readsAndAddsEnchantments);
        KGGameTests.registerFunction(RAYCAST_FINDS_AN_ENTITY, McRedstoneGameTest::raycastFindsAnEntity);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                READS_POWER_FROM_A_REDSTONE_BLOCK, DIRECT_SIGNAL_IGNORES_WEAK_POWER, COMPARATOR_READS_CONTAINER_FULLNESS,
                READS_AND_ADDS_ENCHANTMENTS, RAYCAST_FINDS_AN_ENTITY
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private McRedstoneGameTest() {
    }

    // ---- redstone ----------------------------------------------------------------------------

    public static void readsPowerFromARedstoneBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos beside = source.east();
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(beside, Blocks.AIR.defaultBlockState(), 3);

        // Unpowered first — this is the half that makes the powered assertion mean something.
        assertEq(helper, "nothing there yet", 0, power(level, beside, "power"));
        assertFalse(helper, "and not powered", flag(level, beside, "powered"));

        level.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        assertEq(helper, "a redstone block gives full power", 15, power(level, beside, "power"));
        assertTrue(helper, "and reads as powered", flag(level, beside, "powered"));

        // Signal from the specific side facing the source.
        var fromWest = probe(level, RedstoneNodes.Signal.class, "pos", beside, "side", Direction.WEST);
        assertEq(helper, "the west face sees the source", 15,
                fromWest.eval("power", Integer.class).intValue());
        var fromEast = probe(level, RedstoneNodes.Signal.class, "pos", beside, "side", Direction.EAST);
        assertEq(helper, "the opposite face sees nothing", 0,
                fromEast.eval("power", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * Strong power and weak power are different, and the nodes report the difference.
     *
     * <p>A redstone block emits <b>weak</b> power only — visible to Signal, invisible to Direct Signal.
     * A lever emits <b>strong</b> power into the block it is attached to, which both see. That asymmetry
     * is the whole reason the two nodes exist, and it is the pair of cases here: without the lever, Direct
     * Signal would only ever be asserted as zero and a node that always returned zero would pass.</p>
     */
    public static void directSignalIgnoresWeakPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // --- weak: a redstone block ---
        BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos target = source.east();
        level.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);

        assertEq(helper, "a redstone block signals its neighbour", 15,
                probe(level, RedstoneNodes.Signal.class, "pos", target, "side", Direction.WEST)
                        .eval("power", Integer.class).intValue());
        assertEq(helper, "but only weakly — no direct signal", 0,
                probe(level, RedstoneNodes.DirectSignal.class, "pos", target, "side", Direction.WEST)
                        .eval("power", Integer.class).intValue());

        // --- strong: a thrown lever powers the block it stands on ---
        BlockPos base = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockPos lever = base.above();
        level.setBlock(base, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE,
                        AttachFace.FLOOR)
                .setValue(LeverBlock.POWERED, true), 3);

        assertEq(helper, "a thrown lever strongly powers what it stands on", 15,
                probe(level, RedstoneNodes.DirectSignal.class, "pos", base, "side", Direction.UP)
                        .eval("power", Integer.class).intValue());
        assertEq(helper, "and weakly too, since strong implies weak", 15,
                probe(level, RedstoneNodes.Signal.class, "pos", base, "side", Direction.UP)
                        .eval("power", Integer.class).intValue());

        // --- neither: plain stone ---
        BlockPos stoneAt = helper.absolutePos(new BlockPos(7, 2, 1));
        level.setBlock(stoneAt, Blocks.STONE.defaultBlockState(), 3);
        assertEq(helper, "plain stone signals nothing", 0,
                probe(level, RedstoneNodes.Signal.class, "pos", stoneAt.west(), "side", Direction.EAST)
                        .eval("power", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * Comparator output, and the distinction between "empty" and "not a container".
     *
     * <p>Both read zero on the number alone, which is exactly why the node has a separate
     * {@code hasOutput}.</p>
     */
    public static void comparatorReadsContainerFullness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);

        var empty = probe(level, RedstoneNodes.ComparatorOutput.class, "pos", chest);
        assertTrue(helper, "a chest has comparator output", empty.eval("hasOutput", Boolean.class));
        assertEq(helper, "but an empty one reads zero", 0, empty.eval("signal", Integer.class).intValue());

        // Fill it and the signal must rise — the assertion a stub returning zero would fail.
        Container inv = (Container) level.getBlockEntity(chest);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            inv.setItem(i, new ItemStack(Items.STONE, 64));
        }
        inv.setChanged();
        var full = probe(level, RedstoneNodes.ComparatorOutput.class, "pos", chest);
        assertEq(helper, "a full chest reads 15", 15, full.eval("signal", Integer.class).intValue());

        // Stone is not a container, and says so rather than looking like an empty one.
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        var none = probe(level, RedstoneNodes.ComparatorOutput.class, "pos", stone);
        assertFalse(helper, "stone has no comparator output", none.eval("hasOutput", Boolean.class));
        assertEq(helper, "and reads zero", 0, none.eval("signal", Integer.class).intValue());
        helper.succeed();
    }

    // ---- enchantments ------------------------------------------------------------------------

    public static void readsAndAddsEnchantments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Identifier sharpness = Identifier.parse("minecraft:sharpness");

        // Nothing on it yet.
        var before = probe(level, EnchantmentNodes.LevelOf.class,
                "stack", sword, "enchantment", sharpness);
        assertTrue(helper, "sharpness resolves from the datapack registry",
                before.eval("found", Boolean.class));
        assertFalse(helper, "but the sword does not have it", before.eval("has", Boolean.class));
        assertEq(helper, "at level zero", 0,
                before.eval("enchantmentLevel", Integer.class).intValue());

        // Add it, and the original must be untouched — these are value operations.
        var add = probe(level, EnchantmentNodes.Add.class,
                "stack", sword, "enchantment", sharpness, "enchantmentLevel", 3);
        assertTrue(helper, "adding reported success", add.eval("ok", Boolean.class));
        ItemStack enchanted = add.eval("out", ItemStack.class);
        assertTrue(helper, "the result is enchanted", enchanted.isEnchanted());
        assertFalse(helper, "and the input was not modified", sword.isEnchanted());

        var after = probe(level, EnchantmentNodes.LevelOf.class,
                "stack", enchanted, "enchantment", sharpness);
        assertTrue(helper, "the copy has sharpness", after.eval("has", Boolean.class));
        assertEq(helper, "at the level given", 3,
                after.eval("enchantmentLevel", Integer.class).intValue());

        var all = probe(level, EnchantmentNodes.All.class, "stack", enchanted);
        assertEq(helper, "one enchantment listed", 1, all.eval("count", Integer.class).intValue());
        assertEq(helper, "which is sharpness", List.of(sharpness), all.eval("ids", List.class));
        assertEq(helper, "at level three", List.of(3), all.eval("levels", List.class));

        // An unknown id is reported, not thrown.
        var unknown = probe(level, EnchantmentNodes.LevelOf.class,
                "stack", enchanted, "enchantment", Identifier.parse("kilagraph:nope"));
        assertFalse(helper, "an unknown enchantment id is not found",
                unknown.eval("found", Boolean.class));
        assertFalse(helper, "adding an unknown one is refused",
                probe(level, EnchantmentNodes.Add.class, "stack", sword,
                        "enchantment", Identifier.parse("kilagraph:nope"))
                        .eval("ok", Boolean.class));

        // ---- and back off again ----
        var remove = probe(level, EnchantmentNodes.Remove.class,
                "stack", enchanted, "enchantment", sharpness);
        assertTrue(helper, "removing reported success", remove.eval("ok", Boolean.class));
        ItemStack plain = remove.eval("out", ItemStack.class);
        assertFalse(helper, "the copy is no longer enchanted", plain.isEnchanted());
        assertTrue(helper, "and the input still is", enchanted.isEnchanted());
        assertEq(helper, "with nothing left to list", 0,
                probe(level, EnchantmentNodes.All.class, "stack", plain)
                        .eval("count", Integer.class).intValue());

        assertFalse(helper, "removing what was not there changes nothing",
                probe(level, EnchantmentNodes.Remove.class, "stack", plain, "enchantment", sharpness)
                        .eval("ok", Boolean.class));
        assertFalse(helper, "and an unknown id is refused",
                probe(level, EnchantmentNodes.Remove.class, "stack", enchanted,
                        "enchantment", Identifier.parse("kilagraph:nope"))
                        .eval("ok", Boolean.class));
        helper.succeed();
    }

    // ---- entity raycast ----------------------------------------------------------------------

    public static void raycastFindsAnEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        // A tick to let the pig land and settle, so the position snapped below is the one it keeps.
        helper.runAfterDelay(1, () -> raycastAssertions(helper, level, pig));
    }

    private static void raycastAssertions(GameTestHelper helper, ServerLevel level, Entity pig) {
        // Put the pig on the float grid before aiming at it.
        //
        // The ray's ports are VEC3, i.e. float, and the game test server places this level millions of
        // blocks from the origin, where a float's spacing is 0.5 to 1 block — as wide as the pig's
        // 0.9-block hitbox. So `(float) pig.getX()` is not the pig's centre but somewhere up to half a
        // block away, and whether the ray still clips the hitbox is luck: this test failed about half
        // the time, on whichever axis the harness happened to make the coarse one.
        //
        // Snapping the pig to coordinates that survive the round trip makes the aim exact and the test
        // deterministic. It does not paper over anything: the precision limit is real and belongs to
        // any float vector far from the origin, so a graph raycasting near the world border has it too
        // — it is just not what this test is about.
        pig.snapTo(new Vec3((float) pig.getX(), (float) pig.getY(), (float) pig.getZ()));

        // A ray along x through the pig's middle.
        Vector3f from = new Vector3f((float) pig.getX() - 4f, (float) pig.getY() + 0.5f, (float) pig.getZ());
        Vector3f to = new Vector3f((float) pig.getX() + 4f, (float) pig.getY() + 0.5f, (float) pig.getZ());

        var hit = probe(level, RaycastEntityNode.class, "from", from, "to", to);
        assertTrue(helper, "the ray found something", hit.eval("hit", Boolean.class));
        assertEq(helper, "and it was the pig", pig, hit.eval("entity", Entity.class));

        // A ray well above it misses — the half that makes the hit meaningful.
        var miss = probe(level, RaycastEntityNode.class,
                "from", new Vector3f(from.x, from.y + 12f, from.z),
                "to", new Vector3f(to.x, to.y + 12f, to.z));
        assertFalse(helper, "a ray above it misses", miss.eval("hit", Boolean.class));
        assertEq(helper, "and reports no entity", null, miss.eval("entity", Object.class));

        // Ignoring the pig makes the same ray miss, which is what the ignore input is for.
        var ignored = probe(level, RaycastEntityNode.class, "from", from, "to", to, "ignore", pig);
        assertFalse(helper, "ignoring the only entity leaves nothing to hit",
                ignored.eval("hit", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private record Probe(GraphExecutor exec, NodeModel node) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    private static Probe probe(ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = n.getInputsById().get("level");
        if (levelPort != null) {
            var v = (VariableDeclarationModelBase)
                    g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
            wire(g, levelPort,
                    g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        }
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }

    private static int power(ServerLevel level, BlockPos pos, String output) {
        return probe(level, RedstoneNodes.Power.class, "pos", pos).eval(output, Integer.class);
    }

    private static boolean flag(ServerLevel level, BlockPos pos, String output) {
        return probe(level, RedstoneNodes.Power.class, "pos", pos).eval(output, Boolean.class);
    }
}
