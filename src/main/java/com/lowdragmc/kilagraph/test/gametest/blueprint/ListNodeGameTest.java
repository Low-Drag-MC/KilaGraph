package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListAppendNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListConcatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListContainsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListDistinctNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListIndexOfNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListInsertNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListIsEmptyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListPrependNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListRangeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListRemoveAtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListRemoveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListRepeatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListReverseNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSliceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSortNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

public final class ListNodeGameTest {
    private static final String IS_EMPTY = "list_is_empty";
    private static final String APPEND = "list_append";
    private static final String PREPEND = "list_prepend";
    private static final String INSERT = "list_insert";
    private static final String REMOVE_AT = "list_remove_at";
    private static final String REMOVE = "list_remove";
    private static final String CONTAINS = "list_contains";
    private static final String INDEX_OF = "list_index_of";
    private static final String SLICE = "list_slice";
    private static final String CONCAT = "list_concat";
    private static final String REVERSE = "list_reverse";
    private static final String DISTINCT = "list_distinct";
    private static final String SORT = "list_sort";
    private static final String RANGE = "list_range";
    private static final String REPEAT = "list_repeat";
    private static final String IMMUTABILITY = "list_immutability";

    private ListNodeGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(IS_EMPTY, ListNodeGameTest::isEmpty);
        KGGameTests.registerFunction(APPEND, ListNodeGameTest::append);
        KGGameTests.registerFunction(PREPEND, ListNodeGameTest::prepend);
        KGGameTests.registerFunction(INSERT, ListNodeGameTest::insert);
        KGGameTests.registerFunction(REMOVE_AT, ListNodeGameTest::removeAt);
        KGGameTests.registerFunction(REMOVE, ListNodeGameTest::remove);
        KGGameTests.registerFunction(CONTAINS, ListNodeGameTest::contains);
        KGGameTests.registerFunction(INDEX_OF, ListNodeGameTest::indexOf);
        KGGameTests.registerFunction(SLICE, ListNodeGameTest::slice);
        KGGameTests.registerFunction(CONCAT, ListNodeGameTest::concat);
        KGGameTests.registerFunction(REVERSE, ListNodeGameTest::reverse);
        KGGameTests.registerFunction(DISTINCT, ListNodeGameTest::distinct);
        KGGameTests.registerFunction(SORT, ListNodeGameTest::sort);
        KGGameTests.registerFunction(RANGE, ListNodeGameTest::range);
        KGGameTests.registerFunction(REPEAT, ListNodeGameTest::repeat);
        KGGameTests.registerFunction(IMMUTABILITY, ListNodeGameTest::immutability);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{IS_EMPTY, APPEND, PREPEND, INSERT, REMOVE_AT, REMOVE, CONTAINS,
                INDEX_OF, SLICE, CONCAT, REVERSE, DISTINCT, SORT, RANGE, REPEAT, IMMUTABILITY}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** Build a String-typed ListCombine with the given values; return its output port. */
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel
            stringList(BlueprintGraph g, String... values) {
        NodeModel combine = addNode(g, ListCombineNode.class);
        setOption(combine, "type", TypeHandles.STRING.getIdentification());
        setOption(combine, "inputs", Math.max(1, values.length));
        for (int i = 0; i < values.length; i++) setInputConstant(combine, "in" + (i + 1), values[i]);
        return combine.getOutputsById().get("out");
    }

    /** A single String value via a 1-element list + ListGet (UNKNOWN typed). */
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel
            stringScalar(BlueprintGraph g, String value) {
        var combine = stringList(g, value);
        var get = addNode(g, com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode.class);
        setOption(get, "type", TypeHandles.STRING.getIdentification());
        setInputConstant(get, "index", 0);
        wire(g, get.getInputsById().get("list"), combine);
        return get.getOutputsById().get("value");
    }

    public static void isEmpty(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListIsEmptyNode.class);
        // Unwired list → defaults to List.of()
        assertEq(helper, "empty default", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ListIsEmptyNode.class);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a"));
        assertEq(helper, "non-empty", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    public static void append(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListAppendNode.class);
        setOption(n, "type", TypeHandles.STRING.getIdentification());
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b"));
        setInputConstant(n, "value", "c");
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "append size", 3, out.size());
        assertEq(helper, "tail", "c", out.get(2));
        helper.succeed();
    }

    public static void prepend(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListPrependNode.class);
        setOption(n, "type", TypeHandles.STRING.getIdentification());
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b"));
        setInputConstant(n, "value", "z");
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "prepend size", 3, out.size());
        assertEq(helper, "head", "z", out.get(0));
        helper.succeed();
    }

    public static void insert(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListInsertNode.class);
        setOption(n, "type", TypeHandles.STRING.getIdentification());
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "c"));
        setInputConstant(n, "index", 1);
        setInputConstant(n, "value", "b");
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 3, out.size());
        assertEq(helper, "[1]", "b", out.get(1));
        helper.succeed();
    }

    public static void removeAt(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListRemoveAtNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "c"));
        setInputConstant(n, "index", 1);
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 2, out.size());
        assertEq(helper, "[0]", "a", out.get(0));
        assertEq(helper, "[1]", "c", out.get(1));

        // Out-of-bounds index → unchanged
        var g2 = newGraph();
        var n2 = addNode(g2, ListRemoveAtNode.class);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a"));
        setInputConstant(n2, "index", 99);
        @SuppressWarnings("unchecked")
        List<Object> out2 = new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), List.class);
        assertEq(helper, "unchanged size", 1, out2.size());
        helper.succeed();
    }

    public static void remove(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListRemoveNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "a"));
        wire(g, n.getInputsById().get("value"), stringScalar(g, "a"));
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "first match removed", 2, out.size());
        assertEq(helper, "[0]", "b", out.get(0));
        assertEq(helper, "[1]", "a", out.get(1));
        helper.succeed();
    }

    public static void contains(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListContainsNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b"));
        wire(g, n.getInputsById().get("value"), stringScalar(g, "b"));
        assertEq(helper, "contains b", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ListContainsNode.class);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a", "b"));
        wire(g2, n2.getInputsById().get("value"), stringScalar(g2, "z"));
        assertEq(helper, "no contains z", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    public static void indexOf(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListIndexOfNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "c"));
        wire(g, n.getInputsById().get("value"), stringScalar(g, "b"));
        assertEq(helper, "indexOf b", 1,
                (int) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ListIndexOfNode.class);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a"));
        wire(g2, n2.getInputsById().get("value"), stringScalar(g2, "z"));
        assertEq(helper, "indexOf missing", -1,
                (int) new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    public static void slice(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListSliceNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "c", "d"));
        setInputConstant(n, "from", 1);
        setInputConstant(n, "to", 3);
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 2, out.size());
        assertEq(helper, "[0]", "b", out.get(0));
        assertEq(helper, "[1]", "c", out.get(1));

        // from > to → empty
        var g2 = newGraph();
        var n2 = addNode(g2, ListSliceNode.class);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a", "b"));
        setInputConstant(n2, "from", 5);
        setInputConstant(n2, "to", 0);
        @SuppressWarnings("unchecked")
        List<Object> out2 = new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), List.class);
        assertEq(helper, "empty slice", 0, out2.size());
        helper.succeed();
    }

    public static void concat(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListConcatNode.class);
        setOption(n, "inputs", 3);
        wire(g, n.getInputsById().get("in1"), stringList(g, "a"));
        wire(g, n.getInputsById().get("in2"), stringList(g, "b", "c"));
        wire(g, n.getInputsById().get("in3"), stringList(g, "d"));
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "concat size", 4, out.size());
        assertEq(helper, "[3]", "d", out.get(3));
        helper.succeed();
    }

    public static void reverse(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListReverseNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "c"));
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 3, out.size());
        assertEq(helper, "[0]", "c", out.get(0));
        assertEq(helper, "[2]", "a", out.get(2));
        helper.succeed();
    }

    public static void distinct(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListDistinctNode.class);
        wire(g, n.getInputsById().get("list"), stringList(g, "a", "b", "a", "c", "b"));
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 3, out.size());
        assertEq(helper, "[0]", "a", out.get(0));
        assertEq(helper, "[1]", "b", out.get(1));
        assertEq(helper, "[2]", "c", out.get(2));
        helper.succeed();
    }

    public static void sort(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListSortNode.class);
        setOption(n, "ascending", true);
        wire(g, n.getInputsById().get("list"), stringList(g, "c", "a", "b"));
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "[0]", "a", out.get(0));
        assertEq(helper, "[2]", "c", out.get(2));

        // Descending
        var g2 = newGraph();
        var n2 = addNode(g2, ListSortNode.class);
        setOption(n2, "ascending", false);
        wire(g2, n2.getInputsById().get("list"), stringList(g2, "a", "c", "b"));
        @SuppressWarnings("unchecked")
        List<Object> out2 = new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), List.class);
        assertEq(helper, "desc[0]", "c", out2.get(0));
        assertEq(helper, "desc[2]", "a", out2.get(2));
        helper.succeed();
    }

    public static void range(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListRangeNode.class);
        setInputConstant(n, "from", 0);
        setInputConstant(n, "to", 5);
        setInputConstant(n, "step", 1);
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 5, out.size());
        assertEq(helper, "[0]", 0, out.get(0));
        assertEq(helper, "[4]", 4, out.get(4));

        // step=2 → 0,2,4,6,8
        var g2 = newGraph();
        var n2 = addNode(g2, ListRangeNode.class);
        setInputConstant(n2, "from", 0);
        setInputConstant(n2, "to", 10);
        setInputConstant(n2, "step", 2);
        @SuppressWarnings("unchecked")
        List<Object> out2 = new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), List.class);
        assertEq(helper, "step=2 size", 5, out2.size());
        assertEq(helper, "step=2 [2]", 4, out2.get(2));

        // step <= 0 → empty
        var g3 = newGraph();
        var n3 = addNode(g3, ListRangeNode.class);
        setInputConstant(n3, "from", 0);
        setInputConstant(n3, "to", 10);
        setInputConstant(n3, "step", 0);
        @SuppressWarnings("unchecked")
        List<Object> out3 = new GraphExecutor(g3).evaluate(n3.getOutputsById().get("out"), List.class);
        assertEq(helper, "step=0 empty", 0, out3.size());
        helper.succeed();
    }

    public static void repeat(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ListRepeatNode.class);
        setOption(n, "type", TypeHandles.STRING.getIdentification());
        setInputConstant(n, "value", "x");
        setInputConstant(n, "count", 4);
        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 4, out.size());
        assertEq(helper, "[0]", "x", out.get(0));
        assertEq(helper, "[3]", "x", out.get(3));
        helper.succeed();
    }

    public static void immutability(GameTestHelper helper) {
        // Append must not mutate the source list — evaluator can re-evaluate same source port.
        var g = newGraph();
        var src = stringList(g, "a", "b");
        var append = addNode(g, ListAppendNode.class);
        setOption(append, "type", TypeHandles.STRING.getIdentification());
        wire(g, append.getInputsById().get("list"), src);
        setInputConstant(append, "value", "c");

        var exec = new GraphExecutor(g);
        // Two pulls of the source should give the same un-mutated list (size 2)
        @SuppressWarnings("unchecked")
        List<Object> s1 = exec.evaluate(src, List.class);
        @SuppressWarnings("unchecked")
        List<Object> appended = exec.evaluate(append.getOutputsById().get("out"), List.class);
        @SuppressWarnings("unchecked")
        List<Object> s2 = exec.evaluate(src, List.class);

        assertEq(helper, "source size pre", 2, s1.size());
        assertEq(helper, "appended size", 3, appended.size());
        assertEq(helper, "source size post", 2, s2.size());
        helper.succeed();
    }
}
