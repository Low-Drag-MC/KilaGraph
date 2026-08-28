package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.NotEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class ComparisonNodeGameTest {
    private static final String GT = "cmp_greater_than";
    private static final String GE = "cmp_greater_equal";
    private static final String LT = "cmp_less_than";
    private static final String LE = "cmp_less_equal";
    private static final String NEQ = "cmp_not_equals";

    private ComparisonNodeGameTest() {}

    /** Reusable: runs a 2-arg comparison node with the given a/b and asserts the out. */
    private static void cmpCase(GameTestHelper helper, Class<? extends Node> nodeClass,
                                String label, float a, float b, boolean expected) {
        var g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        Boolean actual = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class);
        assertEq(helper, label + " " + a + "?" + b, expected, actual);
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void greaterThan(GameTestHelper helper) {
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 3f, true);
        cmpCase(helper, GreaterThanNode.class, ">", 3f, 5f, false);
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 5f, false);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void greaterEqual(GameTestHelper helper) {
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 3f, true);
        cmpCase(helper, GreaterEqualNode.class, ">=", 3f, 5f, false);
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 5f, true);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void lessThan(GameTestHelper helper) {
        cmpCase(helper, LessThanNode.class, "<", 3f, 5f, true);
        cmpCase(helper, LessThanNode.class, "<", 5f, 3f, false);
        cmpCase(helper, LessThanNode.class, "<", 5f, 5f, false);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void lessEqual(GameTestHelper helper) {
        cmpCase(helper, LessEqualNode.class, "<=", 3f, 5f, true);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 3f, false);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 5f, true);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void notEquals(GameTestHelper helper) {
        // Two AddNodes producing different Float values: 1.0 vs 2.0 → not equal
        var g = newGraph();
        var a = addNode(g, AddNode.class);
        var b = addNode(g, AddNode.class);
        setInputConstant(a, "in1", 1.0f); setInputConstant(a, "in2", 0.0f);
        setInputConstant(b, "in1", 2.0f); setInputConstant(b, "in2", 0.0f);
        var n = addNode(g, NotEqualsNode.class);
        wire(g, n.getInputsById().get("a"), a.getOutputsById().get("out"));
        wire(g, n.getInputsById().get("b"), b.getOutputsById().get("out"));
        assertEq(helper, "1f != 2f", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        // Same value → equal
        var g2 = newGraph();
        var a2 = addNode(g2, AddNode.class);
        var b2 = addNode(g2, AddNode.class);
        setInputConstant(a2, "in1", 1.0f); setInputConstant(a2, "in2", 0.0f);
        setInputConstant(b2, "in1", 1.0f); setInputConstant(b2, "in2", 0.0f);
        var n2 = addNode(g2, NotEqualsNode.class);
        wire(g2, n2.getInputsById().get("a"), a2.getOutputsById().get("out"));
        wire(g2, n2.getInputsById().get("b"), b2.getOutputsById().get("out"));
        assertEq(helper, "1f != 1f", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));

        helper.succeed();
    }

    /**
     * A number equals a number of the same value, whatever wrapper it arrived in.
     *
     * <p>This used to be {@code Objects.equals}, which asks the wrapper: {@code Long.equals} demands a
     * {@code Long} on the other side, so a 5 produced by one node and a 5 produced by another compared
     * <em>unequal</em> whenever the two nodes happened to publish different numeric types. Nothing in
     * the editor shows which type a wire carries, so the only way to find out was for a graph to
     * misbehave.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void notEqualsComparesNumbersByValue(GameTestHelper helper) {
        record Case(String label, Class<?> ta, Object a, Class<?> tb, Object b, boolean differ) {}
        var cases = new Case[]{
                new Case("long 5 vs int 5", long.class, 5L, int.class, 5, false),
                new Case("int 5 vs float 5.0", int.class, 5, float.class, 5f, false),
                new Case("long 5 vs double 5.0", long.class, 5L, double.class, 5d, false),
                new Case("float 5.0 vs double 5.0", float.class, 5f, double.class, 5d, false),
                new Case("long 5 vs int 6", long.class, 5L, int.class, 6, true),
                new Case("int 5 vs float 5.5", int.class, 5, float.class, 5.5f, true),
                // A long past 2^53 and the double nearest it are different numbers, and comparing
                // through double would have called them equal.
                new Case("2^53+1 vs the double below it", long.class, 9007199254740993L,
                        double.class, 9007199254740992d, true),
        };
        for (Case c : cases) {
            var g = newGraph();
            var n = addNode(g, NotEqualsNode.class);
            wire(g, n.getInputsById().get("a"), valueSource(g.graphModel, "a", c.ta(), c.a()));
            wire(g, n.getInputsById().get("b"), valueSource(g.graphModel, "b", c.tb(), c.b()));
            assertEq(helper, c.label(), c.differ(),
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        }
        helper.succeed();
    }

    /** Non-numbers keep {@code Objects.equals}, nulls included. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void notEqualsStillComparesNonNumbersByEquals(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, NotEqualsNode.class);
        wire(g, n.getInputsById().get("a"), valueSource(g.graphModel, "a", String.class, "x"));
        wire(g, n.getInputsById().get("b"), valueSource(g.graphModel, "b", String.class, "x"));
        assertEq(helper, "\"x\" != \"x\"", Boolean.FALSE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, NotEqualsNode.class);
        wire(g2, n2.getInputsById().get("a"), valueSource(g2.graphModel, "a", String.class, "x"));
        wire(g2, n2.getInputsById().get("b"), valueSource(g2.graphModel, "b", String.class, "y"));
        assertEq(helper, "\"x\" != \"y\"", Boolean.TRUE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));

        // Both unwired: two nulls are equal, as they always were.
        var g3 = newGraph();
        var n3 = addNode(g3, NotEqualsNode.class);
        assertEq(helper, "null != null", Boolean.FALSE,
                new GraphExecutor(g3).evaluate(n3.getOutputsById().get("out"), Boolean.class));

        // One side a number, the other not — not equal, and not an exception either.
        var g4 = newGraph();
        var n4 = addNode(g4, NotEqualsNode.class);
        wire(g4, n4.getInputsById().get("a"), valueSource(g4.graphModel, "a", int.class, 5));
        wire(g4, n4.getInputsById().get("b"), valueSource(g4.graphModel, "b", String.class, "5"));
        assertEq(helper, "5 != \"5\"", Boolean.TRUE,
                new GraphExecutor(g4).evaluate(n4.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }
}
