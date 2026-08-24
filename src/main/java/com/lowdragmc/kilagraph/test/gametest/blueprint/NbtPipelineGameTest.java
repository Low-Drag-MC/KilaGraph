package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListAppendNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSizeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtHasNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtRemoveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtValueType;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * NBT flowing through a graph rather than one node at a time.
 *
 * <p>{@code NbtNodeGameTest} covers each NBT node on its own. What it cannot cover, and what real
 * graphs actually do, is NBT travelling: nested compounds several levels deep, tags stored in lists,
 * and tags crossing a subgraph boundary — where the value is carried through a variable store, a
 * child executor and back.</p>
 *
 * <p>{@link #chainedSetsShareOneTag} is the load-bearing one for the executor work: {@code NbtSet}
 * mutates the compound it is given and returns the same object, so how many times the executor
 * evaluates a node is directly observable in the resulting tag. Any change to evaluation count
 * shows up here as a wrong tag rather than as a performance difference.</p>
 */
public final class NbtPipelineGameTest {
    private static final String NESTED_COMPOUND_ROUND_TRIP = "nbt_pipeline_nested_compound_round_trip";
    private static final String CHAINED_SETS_SHARE_ONE_TAG = "nbt_pipeline_chained_sets_share_one_tag";
    private static final String NBT_THROUGH_A_LIST = "nbt_pipeline_nbt_through_a_list";
    private static final String REMOVE_DROPS_ONLY_THE_NAMED_KEY = "nbt_pipeline_remove_drops_only_the_named_key";
    private static final String NBT_ACROSS_A_SUBGRAPH_BOUNDARY = "nbt_pipeline_nbt_across_a_subgraph_boundary";

    private NbtPipelineGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(NESTED_COMPOUND_ROUND_TRIP, NbtPipelineGameTest::nestedCompoundRoundTrip);
        KGGameTests.registerFunction(CHAINED_SETS_SHARE_ONE_TAG, NbtPipelineGameTest::chainedSetsShareOneTag);
        KGGameTests.registerFunction(NBT_THROUGH_A_LIST, NbtPipelineGameTest::nbtThroughAList);
        KGGameTests.registerFunction(REMOVE_DROPS_ONLY_THE_NAMED_KEY, NbtPipelineGameTest::removeDropsOnlyTheNamedKey);
        KGGameTests.registerFunction(NBT_ACROSS_A_SUBGRAPH_BOUNDARY, NbtPipelineGameTest::nbtAcrossASubgraphBoundary);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                NESTED_COMPOUND_ROUND_TRIP, CHAINED_SETS_SHARE_ONE_TAG, NBT_THROUGH_A_LIST,
                REMOVE_DROPS_ONLY_THE_NAMED_KEY, NBT_ACROSS_A_SUBGRAPH_BOUNDARY
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** {@code {stats: {inner: {hp: 20}}}} built and read back three levels down. */
    public static void nestedCompoundRoundTrip(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();

        // innermost: {hp: 20}
        b.add("deepTag", NbtCreateNode.class);
        b.add("setHp", NbtSetNode.class).option("setHp", "valueType", NbtValueType.INT)
                .wire("setHp.tag", "deepTag").constant("setHp.key", "hp").constant("setHp.value", 20);

        // middle: {inner: {hp: 20}}
        b.add("midTag", NbtCreateNode.class);
        b.add("setInner", NbtSetNode.class).option("setInner", "valueType", NbtValueType.COMPOUND)
                .wire("setInner.tag", "midTag").constant("setInner.key", "inner")
                .wire("setInner.value", "setHp");

        // outer: {stats: {inner: {hp: 20}}}
        b.add("rootTag", NbtCreateNode.class);
        b.add("setStats", NbtSetNode.class).option("setStats", "valueType", NbtValueType.COMPOUND)
                .wire("setStats.tag", "rootTag").constant("setStats.key", "stats")
                .wire("setStats.value", "setInner");

        // read back down: root -> stats -> inner -> hp
        b.add("getStats", NbtGetNode.class).option("getStats", "valueType", NbtValueType.COMPOUND)
                .wire("getStats.tag", "setStats").constant("getStats.key", "stats");
        b.add("getInner", NbtGetNode.class).option("getInner", "valueType", NbtValueType.COMPOUND)
                .wire("getInner.tag", "getStats").constant("getInner.key", "inner");
        b.add("getHp", NbtGetNode.class).option("getHp", "valueType", NbtValueType.INT)
                .wire("getHp.tag", "getInner").constant("getHp.key", "hp");

        var exec = new GraphExecutor(b.graph());
        Integer hp = exec.evaluate(b.outputOf("getHp.out"), Integer.class);
        assertEq(helper, "hp three levels down", 20, hp == null ? -1 : hp);
        helper.succeed();
    }

    /**
     * {@code NbtSet} writes into the tag it is given and hands back that same object, so two setters
     * fed by one {@code NbtCreate} both operate on a single compound and the result holds both keys.
     *
     * <p>This is a property of the node <em>and</em> of the memo: the shared {@code NbtCreate} is
     * evaluated once, so there is one tag to share. An executor that re-evaluated it would produce
     * two tags and the second setter's key would go missing — which is why the evaluation count is
     * asserted here alongside the contents.</p>
     */
    public static void chainedSetsShareOneTag(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("tag", NbtCreateNode.class);
        b.add("setA", NbtSetNode.class).option("setA", "valueType", NbtValueType.INT)
                .wire("setA.tag", "tag").constant("setA.key", "a").constant("setA.value", 1);
        b.add("setB", NbtSetNode.class).option("setB", "valueType", NbtValueType.INT)
                .wire("setB.tag", "tag").constant("setB.key", "b").constant("setB.value", 2);
        // Pull through setB, which forces setA only if something demands it — so demand both.
        b.add("hasA", NbtHasNode.class).wire("hasA.tag", "setA").constant("hasA.key", "a");
        b.add("hasB", NbtHasNode.class).wire("hasB.tag", "setB").constant("hasB.key", "b");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);

        assertTrue(helper, "setA wrote 'a'", Boolean.TRUE.equals(exec.evaluate(b.outputOf("hasA.out"), Boolean.class)));
        assertTrue(helper, "setB wrote 'b'", Boolean.TRUE.equals(exec.evaluate(b.outputOf("hasB.out"), Boolean.class)));
        assertEq(helper, "the shared NbtCreate ran once", 1, trace.evalCount(b.node("tag").getUid()));

        // Both keys landed in the one tag both setters were handed.
        CompoundTag shared = exec.evaluate(b.outputOf("setA.out"), CompoundTag.class);
        assertTrue(helper, "one tag holds both keys",
                shared != null && shared.contains("a") && shared.contains("b"));
        helper.succeed();
    }

    /** Compounds stored in a list, then read back out by index. */
    public static void nbtThroughAList(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("empty", NbtCreateNode.class);

        // three distinct tags, each {v: i}
        for (int i = 0; i < 3; i++) {
            b.add("t" + i, NbtCreateNode.class);
            b.add("set" + i, NbtSetNode.class).option("set" + i, "valueType", NbtValueType.INT)
                    .wire("set" + i + ".tag", "t" + i).constant("set" + i + ".key", "v")
                    .constant("set" + i + ".value", i * 10);
        }

        b.add("l0", ListAppendNode.class).wire("l0.value", "set0");
        b.add("l1", ListAppendNode.class).wire("l1.list", "l0").wire("l1.value", "set1");
        b.add("l2", ListAppendNode.class).wire("l2.list", "l1").wire("l2.value", "set2");

        b.add("size", ListSizeNode.class).wire("size.list", "l2");
        b.add("pick", ListGetNode.class).wire("pick.list", "l2").constant("pick.index", 1);
        b.add("readV", NbtGetNode.class).option("readV", "valueType", NbtValueType.INT)
                .wire("readV.tag", "pick.value").constant("readV.key", "v");

        var exec = new GraphExecutor(b.graph());
        Integer size = exec.evaluate(b.outputOf("size"), Integer.class);
        Integer v = exec.evaluate(b.outputOf("readV.out"), Integer.class);
        assertEq(helper, "three tags in the list", 3, size == null ? -1 : size);
        assertEq(helper, "element 1 carries its own value", 10, v == null ? -1 : v);
        helper.succeed();
    }

    /** Remove drops exactly the key it names and leaves the rest. */
    public static void removeDropsOnlyTheNamedKey(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("tag", NbtCreateNode.class);
        b.add("setA", NbtSetNode.class).option("setA", "valueType", NbtValueType.INT)
                .wire("setA.tag", "tag").constant("setA.key", "a").constant("setA.value", 1);
        b.add("setB", NbtSetNode.class).option("setB", "valueType", NbtValueType.INT)
                .wire("setB.tag", "setA").constant("setB.key", "b").constant("setB.value", 2);
        b.add("drop", NbtRemoveNode.class).wire("drop.tag", "setB").constant("drop.key", "a");
        b.add("hasA", NbtHasNode.class).wire("hasA.tag", "drop").constant("hasA.key", "a");
        b.add("hasB", NbtHasNode.class).wire("hasB.tag", "drop").constant("hasB.key", "b");

        var exec = new GraphExecutor(b.graph());
        assertEq(helper, "'a' removed", Boolean.FALSE, exec.evaluate(b.outputOf("hasA.out"), Boolean.class));
        assertEq(helper, "'b' kept", Boolean.TRUE, exec.evaluate(b.outputOf("hasB.out"), Boolean.class));
        helper.succeed();
    }

    /**
     * A compound passed into a function, modified there, and read back out — the value crosses into
     * a child variable store and back through the call site's mirror pins.
     */
    public static void nbtAcrossASubgraphBoundary(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();

        // fn(tagIn) { tagOut = set(tagIn, "stamped", 7) }
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("tagIn", CompoundTag.class, null, VariableKind.INPUT);
        fn.declare("tagOut", CompoundTag.class, null, VariableKind.OUTPUT);
        fn.add("stamp", NbtSetNode.class).option("stamp", "valueType", NbtValueType.INT)
                .wire("stamp.tag", "tagIn").constant("stamp.key", "stamped").constant("stamp.value", 7);
        fn.add("setOut", SetVarNode.class).option("setOut", "varName", "tagOut")
                .wire("setOut.value", "stamp");
        fn.then("call", "setOut", "ret");

        outer.add("entry", EntryNode.class);
        outer.add("tag", NbtCreateNode.class);
        outer.add("seed", NbtSetNode.class).option("seed", "valueType", NbtValueType.INT)
                .wire("seed.tag", "tag").constant("seed.key", "seeded").constant("seed.value", 3);
        outer.call("f", fn).wire("f.tagIn", "seed");
        outer.wire("f.call", "entry");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        CompoundTag out = exec.evaluate(outer.outputOf("f.tagOut"), CompoundTag.class);
        assertTrue(helper, "function returned a compound", out != null);
        if (out == null) return;
        assertEq(helper, "the caller's key survived the call", 3, out.getIntOr("seeded", 0));
        assertEq(helper, "the function's key came back", 7, out.getIntOr("stamped", 0));
        helper.succeed();
    }
}
