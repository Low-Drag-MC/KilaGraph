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
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

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

    // ---- the 64-bit lane ----------------------------------------------------------------------
    //
    // A long on a wire used to be truncated to its low 32 bits on the way in, so a packed BlockPos or
    // an id came out of a mask as the wrong half of itself. See BitwiseLane for why only an actual
    // long widens and an Integer keeps the 32-bit answer it always had.

    /** {@code long} operands keep all 64 bits. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void longOperandsKeepAllSixtyFourBits(GameTestHelper helper) {
        // A value whose meaning lives entirely above bit 32 — truncating to an int gives 0.
        long high = 0x1234_5678_0000_0000L;

        var gAnd = newGraph();
        var and = addNode(gAnd, BitAndNode.class);
        wire(gAnd, and.getInputsById().get("a"), valueSource(gAnd.graphModel, "a", long.class, high));
        wire(gAnd, and.getInputsById().get("b"),
                valueSource(gAnd.graphModel, "b", long.class, -1L));
        assertEq(helper, "high & ~0 keeps the high half", high,
                new GraphExecutor(gAnd).evaluate(and.getOutputsById().get("out"), Object.class));

        var gOr = newGraph();
        var or = addNode(gOr, BitOrNode.class);
        wire(gOr, or.getInputsById().get("a"), valueSource(gOr.graphModel, "a", long.class, high));
        setInputConstant(or, "b", 1);
        assertEq(helper, "high | 1", high | 1L,
                new GraphExecutor(gOr).evaluate(or.getOutputsById().get("out"), Object.class));

        var gXor = newGraph();
        var xor = addNode(gXor, BitXorNode.class);
        wire(gXor, xor.getInputsById().get("a"), valueSource(gXor.graphModel, "a", long.class, high));
        wire(gXor, xor.getInputsById().get("b"), valueSource(gXor.graphModel, "b", long.class, high));
        assertEq(helper, "x ^ x is 0 even in the high half", 0L,
                new GraphExecutor(gXor).evaluate(xor.getOutputsById().get("out"), Object.class));

        var gNot = newGraph();
        var not = addNode(gNot, BitNotNode.class);
        wire(gNot, not.getInputsById().get("in"), valueSource(gNot.graphModel, "in", long.class, high));
        assertEq(helper, "~high flips all 64 bits", ~high,
                new GraphExecutor(gNot).evaluate(not.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** Shifting a long moves within 64 bits; shifting an int still wraps the distance at 32. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void shiftWidthFollowsTheValueNotTheDistance(GameTestHelper helper) {
        var gWide = newGraph();
        var wide = addNode(gWide, ShiftLeftNode.class);
        wire(gWide, wide.getInputsById().get("value"),
                valueSource(gWide.graphModel, "v", long.class, 1L));
        setInputConstant(wide, "bits", 35);
        assertEq(helper, "1L << 35", 1L << 35,
                new GraphExecutor(gWide).evaluate(wide.getOutputsById().get("out"), Object.class));

        // The narrow lane is unchanged, wrap and all: 1 << 35 is 1 << 3 for a 32-bit value.
        assertEq(helper, "1 << 35 still wraps at 32 bits", 8,
                binary(ShiftLeftNode.class, "value", 1, "bits", 35));

        // A right shift of a large negative long keeps its sign across all 64 bits.
        var gRight = newGraph();
        var right = addNode(gRight, ShiftRightNode.class);
        wire(gRight, right.getInputsById().get("value"),
                valueSource(gRight.graphModel, "v", long.class, -0x1234_5678_9ABCL));
        setInputConstant(right, "bits", 4);
        assertEq(helper, "negative long >> 4", -0x1234_5678_9ABCL >> 4,
                new GraphExecutor(gRight).evaluate(right.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** An Integer on a wire is still 32-bit, so existing graphs answer exactly what they did. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void integerOperandsStayThirtyTwoBit(GameTestHelper helper) {
        var g = newGraph();
        var not = addNode(g, BitNotNode.class);
        wire(g, not.getInputsById().get("in"), valueSource(g.graphModel, "in", int.class, 0));
        assertEq(helper, "~0 on an int wire is the Integer -1", -1,
                new GraphExecutor(g).evaluate(not.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }
}
