package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitAndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitNotNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitOrNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitXorNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.ShiftLeftNode;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.ShiftRightNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

@GameTestHolder(Kilagraph.MODID)
public final class BitwiseNodeGameTest {
    private BitwiseNodeGameTest() {}

    private static int binary(Class<? extends Node> nodeClass, String pa, int a, String pb, int b) {
        var g = newGraph();
        var n = addNode(g, nodeClass);
        setInputConstant(n, pa, a);
        setInputConstant(n, pb, b);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void and(GameTestHelper helper) {
        assertEq(helper, "0b1100 & 0b1010", 0b1000, binary(BitAndNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void or(GameTestHelper helper) {
        assertEq(helper, "0b1100 | 0b1010", 0b1110, binary(BitOrNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void xor(GameTestHelper helper) {
        assertEq(helper, "0b1100 ^ 0b1010", 0b0110, binary(BitXorNode.class, "a", 0b1100, "b", 0b1010));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void not(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, BitNotNode.class);
        setInputConstant(n, "in", 0);
        assertEq(helper, "~0", -1, (int) (Integer) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void shiftLeft(GameTestHelper helper) {
        assertEq(helper, "1 << 4", 16, binary(ShiftLeftNode.class, "value", 1, "bits", 4));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void shiftRight(GameTestHelper helper) {
        assertEq(helper, "-16 >> 2", -4, binary(ShiftRightNode.class, "value", -16, "bits", 2));
        assertEq(helper, "256 >> 4", 16, binary(ShiftRightNode.class, "value", 256, "bits", 4));
        helper.succeed();
    }
}
