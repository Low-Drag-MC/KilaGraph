package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.NotEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

public final class ComparisonNodeGameTest {
    private static final String GT = "cmp_greater_than";
    private static final String GE = "cmp_greater_equal";
    private static final String LT = "cmp_less_than";
    private static final String LE = "cmp_less_equal";
    private static final String NEQ = "cmp_not_equals";

    private ComparisonNodeGameTest() {}

    static void registerFunctions() {
        KGGameTests.registerFunction(GT, ComparisonNodeGameTest::greaterThan);
        KGGameTests.registerFunction(GE, ComparisonNodeGameTest::greaterEqual);
        KGGameTests.registerFunction(LT, ComparisonNodeGameTest::lessThan);
        KGGameTests.registerFunction(LE, ComparisonNodeGameTest::lessEqual);
        KGGameTests.registerFunction(NEQ, ComparisonNodeGameTest::notEquals);
    }

    static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, GT, KGGameTests.functionKey(GT), data);
        KGGameTests.registerFunctionTest(event, GE, KGGameTests.functionKey(GE), data);
        KGGameTests.registerFunctionTest(event, LT, KGGameTests.functionKey(LT), data);
        KGGameTests.registerFunctionTest(event, LE, KGGameTests.functionKey(LE), data);
        KGGameTests.registerFunctionTest(event, NEQ, KGGameTests.functionKey(NEQ), data);
    }

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

    public static void greaterThan(GameTestHelper helper) {
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 3f, true);
        cmpCase(helper, GreaterThanNode.class, ">", 3f, 5f, false);
        cmpCase(helper, GreaterThanNode.class, ">", 5f, 5f, false);
        helper.succeed();
    }

    public static void greaterEqual(GameTestHelper helper) {
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 3f, true);
        cmpCase(helper, GreaterEqualNode.class, ">=", 3f, 5f, false);
        cmpCase(helper, GreaterEqualNode.class, ">=", 5f, 5f, true);
        helper.succeed();
    }

    public static void lessThan(GameTestHelper helper) {
        cmpCase(helper, LessThanNode.class, "<", 3f, 5f, true);
        cmpCase(helper, LessThanNode.class, "<", 5f, 3f, false);
        cmpCase(helper, LessThanNode.class, "<", 5f, 5f, false);
        helper.succeed();
    }

    public static void lessEqual(GameTestHelper helper) {
        cmpCase(helper, LessEqualNode.class, "<=", 3f, 5f, true);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 3f, false);
        cmpCase(helper, LessEqualNode.class, "<=", 5f, 5f, true);
        helper.succeed();
    }

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
}
