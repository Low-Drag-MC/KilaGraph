package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.EqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.NotNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.XorNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class LogicNodeGameTest {
    private static final String NOT = "logic_not_basic";
    private static final String XOR_2 = "logic_xor_pair";
    private static final String XOR_3 = "logic_xor_odd_parity";

    private LogicNodeGameTest() {}

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void notBasic(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, NotNode.class);
        setInputConstant(n, "in", true);
        var e = new GraphExecutor(g);
        assertEq(helper, "!true", Boolean.FALSE, e.evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, NotNode.class);
        setInputConstant(n2, "in", false);
        assertEq(helper, "!false", Boolean.TRUE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void xorPair(GameTestHelper helper) {
        // 4 cases: FF=F, FT=T, TF=T, TT=F
        boolean[][] cases = {{false,false,false},{false,true,true},{true,false,true},{true,true,false}};
        for (var c : cases) {
            var g = newGraph();
            var n = addNode(g, XorNode.class);
            setInputConstant(n, "in1", c[0]);
            setInputConstant(n, "in2", c[1]);
            boolean expected = c[2];
            Boolean actual = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class);
            assertEq(helper, c[0] + "^" + c[1], expected, actual);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void xorOddParity(GameTestHelper helper) {
        // 3 inputs: T T T → T (odd count)
        var g = newGraph();
        var n = addNode(g, XorNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", true);
        setInputConstant(n, "in2", true);
        setInputConstant(n, "in3", true);
        assertEq(helper, "T^T^T", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        // T T F → F (even count of trues)
        var g2 = newGraph();
        var n2 = addNode(g2, XorNode.class);
        setOption(n2, "inputs", 3);
        setInputConstant(n2, "in1", true);
        setInputConstant(n2, "in2", true);
        setInputConstant(n2, "in3", false);
        assertEq(helper, "T^T^F", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    /**
     * Equal numbers are equal however they arrived.
     *
     * <p>The mirror of the precision bug: {@code Objects.equals} asks the <em>wrapper</em>, so an
     * {@code Integer} 5 and a {@code Float} 5.0 came out unequal even though every player reading the
     * graph sees two fives. Which wrapper a wire carries is a detail of whichever node produced it,
     * and the editor never shows it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void equalsComparesNumbersByValue(GameTestHelper helper) {
        record Case(String label, Class<?> ta, Object a, Class<?> tb, Object b, boolean equal) {}
        var cases = new Case[]{
                new Case("int 5 vs long 5", int.class, 5, long.class, 5L, true),
                new Case("int 5 vs float 5.0", int.class, 5, float.class, 5f, true),
                new Case("float 5.0 vs double 5.0", float.class, 5f, double.class, 5d, true),
                new Case("int 5 vs long 6", int.class, 5, long.class, 6L, false),
                new Case("int 5 vs float 5.5", int.class, 5, float.class, 5.5f, false),
        };
        for (Case c : cases) {
            var g = newGraph();
            var n = addNode(g, EqualsNode.class);
            wire(g, n.getInputsById().get("in1"), valueSource(g.graphModel, "a", c.ta(), c.a()));
            wire(g, n.getInputsById().get("in2"), valueSource(g.graphModel, "b", c.tb(), c.b()));
            assertEq(helper, c.label(), c.equal(),
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        }
        helper.succeed();
    }

    /** Equality across more than two inputs, and non-numbers still going through equals. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void equalsAcrossThreeInputsAndNonNumbers(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, EqualsNode.class);
        setOption(n, "inputs", 3);
        wire(g, n.getInputsById().get("in1"), valueSource(g.graphModel, "a", int.class, 5));
        wire(g, n.getInputsById().get("in2"), valueSource(g.graphModel, "b", long.class, 5L));
        wire(g, n.getInputsById().get("in3"), valueSource(g.graphModel, "c", float.class, 5f));
        assertEq(helper, "5 == 5L == 5.0f", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, EqualsNode.class);
        setOption(n2, "inputs", 3);
        wire(g2, n2.getInputsById().get("in1"), valueSource(g2.graphModel, "a", int.class, 5));
        wire(g2, n2.getInputsById().get("in2"), valueSource(g2.graphModel, "b", long.class, 5L));
        wire(g2, n2.getInputsById().get("in3"), valueSource(g2.graphModel, "c", float.class, 6f));
        assertEq(helper, "one input differs", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));

        var g3 = newGraph();
        var n3 = addNode(g3, EqualsNode.class);
        wire(g3, n3.getInputsById().get("in1"), valueSource(g3.graphModel, "a", String.class, "x"));
        wire(g3, n3.getInputsById().get("in2"), valueSource(g3.graphModel, "b", String.class, "x"));
        assertEq(helper, "\"x\" == \"x\"", Boolean.TRUE,
                new GraphExecutor(g3).evaluate(n3.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }
}
