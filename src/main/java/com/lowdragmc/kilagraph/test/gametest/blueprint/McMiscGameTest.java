package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.entity.EntityDataNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.item.ItemStackNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.FindStructureNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.world.WorldQueryNodes;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
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
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The odds and ends: durability writes, entity tags, villager trades, chunk loading, structure search.
 *
 * <p>Each of these closes a gap where the game could write something a graph could not read back, or the
 * other way round. They have nothing else in common, which is why they are here rather than spread across
 * five files nobody would find them in.
 */
public final class McMiscGameTest {
    private static final String DAMAGE_IS_WRITTEN_AND_CLAMPED = "mc_misc_damage_is_written_and_clamped";
    private static final String ENTITY_TAGS_ARE_READ_BACK_SORTED = "mc_misc_entity_tags_are_read_back_sorted";
    private static final String VILLAGER_TRADES_ARE_LISTED = "mc_misc_villager_trades_are_listed";
    private static final String CHUNK_LOADING_IS_DISTINGUISHED_FROM_OUT_OF_BOUNDS = "mc_misc_chunk_loading_is_distinguished_from_out_of_bounds";
    private static final String STRUCTURE_SEARCH_IS_CONSISTENT_AND_FAILS_SOFTLY = "mc_misc_structure_search_is_consistent_and_fails_softly";

    public static void registerFunctions() {
        KGGameTests.registerFunction(DAMAGE_IS_WRITTEN_AND_CLAMPED, McMiscGameTest::damageIsWrittenAndClamped);
        KGGameTests.registerFunction(ENTITY_TAGS_ARE_READ_BACK_SORTED, McMiscGameTest::entityTagsAreReadBackSorted);
        KGGameTests.registerFunction(VILLAGER_TRADES_ARE_LISTED, McMiscGameTest::villagerTradesAreListed);
        KGGameTests.registerFunction(CHUNK_LOADING_IS_DISTINGUISHED_FROM_OUT_OF_BOUNDS, McMiscGameTest::chunkLoadingIsDistinguishedFromOutOfBounds);
        KGGameTests.registerFunction(STRUCTURE_SEARCH_IS_CONSISTENT_AND_FAILS_SOFTLY, McMiscGameTest::structureSearchIsConsistentAndFailsSoftly);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                DAMAGE_IS_WRITTEN_AND_CLAMPED, ENTITY_TAGS_ARE_READ_BACK_SORTED, VILLAGER_TRADES_ARE_LISTED,
                CHUNK_LOADING_IS_DISTINGUISHED_FROM_OUT_OF_BOUNDS, STRUCTURE_SEARCH_IS_CONSISTENT_AND_FAILS_SOFTLY
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private McMiscGameTest() {
    }

    /**
     * Durability writes, read back through {@code mc_item_stack_damage}.
     *
     * <p>The read node is the check rather than {@code getDamageValue} directly, because the pair is what a
     * graph actually uses and a disagreement between them is the failure worth catching.</p>
     */
    public static void damageIsWrittenAndClamped(GameTestHelper helper) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        int maxDamage = pick.getMaxDamage();

        var worn = probe(ItemStackNodes.SetDamage.class, "stack", pick, "damage", 100);
        assertTrue(helper, "writing damage worked", worn.eval("ok", Boolean.class));
        assertFalse(helper, "and it is not broken yet", worn.eval("broken", Boolean.class));
        ItemStack out = worn.eval("out", ItemStack.class);
        assertEq(helper, "the read node agrees", 100,
                probe(ItemStackNodes.Damage.class, "stack", out).eval("damage", Integer.class).intValue());
        assertEq(helper, "and the input was not modified", 0, pick.getDamageValue());

        // Out of range clamps, which is the game's own rule and keeps a subtraction gone negative usable.
        var over = probe(ItemStackNodes.SetDamage.class, "stack", pick, "damage", maxDamage + 500);
        assertEq(helper, "past the maximum clamps", maxDamage,
                over.eval("out", ItemStack.class).getDamageValue());
        assertTrue(helper, "and reads as broken", over.eval("broken", Boolean.class));

        var under = probe(ItemStackNodes.SetDamage.class, "stack", pick, "damage", -5);
        assertEq(helper, "below zero clamps too", 0, under.eval("out", ItemStack.class).getDamageValue());

        // An item with no durability is refused rather than silently given a damage value.
        var diamond = probe(ItemStackNodes.SetDamage.class, "stack", new ItemStack(Items.DIAMOND), "damage", 5);
        assertFalse(helper, "a diamond has no durability", diamond.eval("ok", Boolean.class));
        assertEq(helper, "and passes through unchanged", Items.DIAMOND,
                diamond.eval("out", ItemStack.class).getItem());
        assertFalse(helper, "an empty stack is refused",
                probe(ItemStackNodes.SetDamage.class, "stack", ItemStack.EMPTY, "damage", 5).eval("ok", Boolean.class));
        helper.succeed();
    }

    /**
     * Scoreboard tags, written with the game's own API and read back through the node.
     *
     * <p>Added out of alphabetical order on purpose: the node sorts, because the game keeps these in a hash
     * set and a graph reading {@code out[0]} would otherwise get a different tag on a different launch.</p>
     */
    public static void entityTagsAreReadBackSorted(GameTestHelper helper) {
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        assertEq(helper, "a fresh pig has no tags", 0,
                probe(EntityDataNodes.Tags.class, "entity", pig).eval("count", Integer.class).intValue());

        pig.addTag("zebra");
        pig.addTag("aardvark");
        var tags = probe(EntityDataNodes.Tags.class, "entity", pig);
        assertEq(helper, "both tags are there", 2, tags.eval("count", Integer.class).intValue());
        assertEq(helper, "in sorted order", List.of("aardvark", "zebra"), tags.eval("out", List.class));

        assertEq(helper, "no entity means no tags", 0,
                probe(EntityDataNodes.Tags.class).eval("count", Integer.class).intValue());
        helper.succeed();
    }

    /**
     * Villager trades, read off a wandering trader.
     *
     * <p>A wandering trader rather than a villager because an unemployed villager has no trades at all, and
     * giving one a profession is a lot of setup for the same assertion. The trader generates its offers the
     * first time they are read, which is the game's own behaviour and is why this works on a freshly spawned
     * one.
     *
     * <p>The counts are asserted as a relationship rather than a number, since which trades a trader gets is
     * random: the four lists must be the same length, and every trade must actually take and give something.
     * A node that mismatched the lists, or read the wrong side of a trade, breaks one of those.</p>
     */
    public static void villagerTradesAreListed(GameTestHelper helper) {
        Entity trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));

        var trades = probe(EntityDataNodes.Trades.class, "entity", trader);
        assertTrue(helper, "a trader is a merchant", trades.eval("found", Boolean.class));
        List<?> costA = trades.eval("costA", List.class);
        List<?> costB = trades.eval("costB", List.class);
        List<?> result = trades.eval("result", List.class);
        List<?> outOfStock = trades.eval("outOfStock", List.class);

        int count = trades.eval("count", Integer.class);
        assertTrue(helper, "with some trades, got " + count, count > 0);
        assertEq(helper, "costA is the same length", count, costA.size());
        assertEq(helper, "costB is the same length", count, costB.size());
        assertEq(helper, "result is the same length", count, result.size());
        assertEq(helper, "outOfStock is the same length", count, outOfStock.size());

        for (int i = 0; i < count; i++) {
            assertFalse(helper, "trade " + i + " asks for something",
                    ((ItemStack) costA.get(i)).isEmpty());
            assertFalse(helper, "trade " + i + " gives something",
                    ((ItemStack) result.get(i)).isEmpty());
            assertFalse(helper, "trade " + i + " is in stock on a fresh trader",
                    (Boolean) outOfStock.get(i));
        }

        // A pig is not a merchant, which is reported rather than thrown.
        Entity pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 2));
        var notATrader = probe(EntityDataNodes.Trades.class, "entity", pig);
        assertFalse(helper, "a pig does not trade", notATrader.eval("found", Boolean.class));
        assertEq(helper, "and offers nothing", 0, notATrader.eval("count", Integer.class).intValue());
        helper.succeed();
    }

    public static void chunkLoadingIsDistinguishedFromOutOfBounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(new BlockPos(1, 2, 1));

        var loaded = probe(level, WorldQueryNodes.IsChunkLoaded.class, "pos", here);
        assertTrue(helper, "the test's own chunk is loaded", loaded.eval("out", Boolean.class));
        assertTrue(helper, "and in bounds", loaded.eval("inBounds", Boolean.class));

        // Far away horizontally: in bounds, but no chunk — and asking must not load one.
        BlockPos far = new BlockPos(4_000_000, 64, 4_000_000);
        var distant = probe(level, WorldQueryNodes.IsChunkLoaded.class, "pos", far);
        assertFalse(helper, "a distant chunk is not loaded", distant.eval("out", Boolean.class));
        assertTrue(helper, "though the position exists", distant.eval("inBounds", Boolean.class));
        assertFalse(helper, "and asking did not load it", level.isLoaded(far));

        // Above the build height there is nothing to load and never will be: both outputs false.
        var tooHigh = probe(level, WorldQueryNodes.IsChunkLoaded.class,
                "pos", new BlockPos(here.getX(), 5000, here.getZ()));
        assertFalse(helper, "5000 blocks up is out of bounds", tooHigh.eval("inBounds", Boolean.class));
        assertFalse(helper, "and so not readable either", tooHigh.eval("out", Boolean.class));

        var noLevel = probe(WorldQueryNodes.IsChunkLoaded.class, "pos", here);
        assertFalse(helper, "no world means not loaded", noLevel.eval("out", Boolean.class));
        assertFalse(helper, "and not in bounds", noLevel.eval("inBounds", Boolean.class));
        helper.succeed();
    }

    /**
     * Structure search, in a world that may well contain no structures.
     *
     * <p>Honest about its limits: the test level's generator decides whether anything is there, so this
     * cannot assert a hit. What it does assert is that the outputs agree with each other in both cases — a
     * hit has a position and a non-negative distance, a miss has neither — and that the failure branches
     * (unknown structure id, no world) come back false rather than throwing.
     *
     * <p>Radius is kept at 1 chunk throughout. This is the most expensive node in the mod and a wide search
     * in a test would cost more than the test is worth.</p>
     */
    public static void structureSearchIsConsistentAndFailsSoftly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));

        var stronghold = probe(level, FindStructureNode.class,
                "pos", from, "structure", Identifier.parse("minecraft:stronghold"), "radius", 1);
        boolean found = stronghold.eval("found", Boolean.class);
        BlockPos at = stronghold.eval("out", BlockPos.class);
        double distance = stronghold.eval("distance", Double.class);
        if (found) {
            assertTrue(helper, "a hit has a position", at != null);
            assertTrue(helper, "at a non-negative distance, got " + distance, distance >= 0);
        } else {
            assertEq(helper, "a miss has no position", null, at);
            assertEq(helper, "and no distance", 0f, (float) distance, 1e-6f);
        }

        var unknown = probe(level, FindStructureNode.class,
                "pos", from, "structure", Identifier.parse("kilagraph:nope"), "radius", 1);
        assertFalse(helper, "an unknown structure id is not found", unknown.eval("found", Boolean.class));
        assertEq(helper, "and gives no position", null, unknown.eval("out", BlockPos.class));

        var noLevel = probe(FindStructureNode.class,
                "pos", from, "structure", Identifier.parse("minecraft:stronghold"), "radius", 1);
        assertFalse(helper, "no world means no search", noLevel.eval("found", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** One node in its own graph, carried with the executor so its outputs can be read. */
    private record Probe(GraphExecutor exec, NodeModel node) {
        <T> T eval(String output, Class<T> type) {
            return exec.evaluate(node.getOutputsById().get(output), type);
        }
    }

    /**
     * A node in its own graph with the given input constants applied.
     *
     * <p>No world, which is a case worth having rather than an omission: several nodes here have to answer
     * sensibly when the {@code level} port is left unwired, and this is how those branches are reached.</p>
     */
    private static Probe probe(Class<? extends Node> cls, Object... inputs) {
        return probe(null, cls, inputs);
    }

    /** The same, with the level wired from a graph variable seeded on the environment. */
    private static Probe probe(@Nullable ServerLevel level, Class<? extends Node> cls, Object... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        PortModel levelPort = n.getInputsById().get("level");
        if (level == null || levelPort == null) {
            return new Probe(new GraphExecutor(g), n);
        }
        var v = (VariableDeclarationModelBase)
                g.graphModel.createVariable("level", KGTypeHandles.LEVEL, null, VariableKind.INPUT);
        wire(g, levelPort,
                g.graphModel.createVariableNode(v, new Vector2f(0, 0), null, null).getOutputPort());
        return new Probe(new GraphExecutor(g, EvaluationEnvironment.with(Map.of("level", level))), n);
    }
}
