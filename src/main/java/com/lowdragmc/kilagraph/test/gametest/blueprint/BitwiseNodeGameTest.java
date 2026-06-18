package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitAndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitNotNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitOrNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitXorNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.ShiftLeftNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.ShiftRightNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

public final class BitwiseNodeGameTest {
    private static final String AND = "bitwise_and";
    private static final String OR = "bitwise_or";
    private static final String XOR = "bitwise_xor";
    private static final String NOT = "bitwise_not";
    private static final String SHL = "bitwise_shift_left";
    private static final String SHR = "bitwise_shift_right";

    private BitwiseNodeGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(AND, BitwiseNodeGameTest::and);
        KGGameTests.registerFunction(OR, BitwiseNodeGameTest::or);
        KGGameTests.registerFunction(XOR, BitwiseNodeGameTest::xor);
        KGGameTests.registerFunction(NOT, BitwiseNodeGameTest::not);
        KGGameTests.registerFunction(SHL, BitwiseNodeGameTest::shiftLeft);
        KGGameTests.registerFunction(SHR, BitwiseNodeGameTest::shiftRight);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{AND, OR, XOR, NOT, SHL, SHR}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private static int binary(Class<? extends Node> nodeClass, String pa, int a, String pb, int b) {
        var g = newGraph();
        var n = addNode(g, nodeClass);
        setInputConstant(n, pa, a);
        setInputConstant(n, pb, b);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class);
    }

    public static void and(GameTestHelper helper) {
        assertEq(helper, "0b1100 & 0b1010", 0b1000, binary(BitAndNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    public static void or(GameTestHelper helper) {
        assertEq(helper, "0b1100 | 0b1010", 0b1110, binary(BitOrNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    public static void xor(GameTestHelper helper) {
        assertEq(helper, "0b1100 ^ 0b1010", 0b0110, binary(BitXorNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    public static void not(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, BitNotNode.class);
        setInputConstant(n, "in", 0);
        assertEq(helper, "~0", -1, (int) (Integer) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    public static void shiftLeft(GameTestHelper helper) {
        assertEq(helper, "1 << 4", 16, binary(ShiftLeftNode.class, "value", 1, "bits", 4));
        helper.succeed();
    }

    public static void shiftRight(GameTestHelper helper) {
        assertEq(helper, "-16 >> 2", -4, binary(ShiftRightNode.class, "value", -16, "bits", 2));
        assertEq(helper, "256 >> 4", 16, binary(ShiftRightNode.class, "value", 256, "bits", 4));
        helper.succeed();
    }
}
