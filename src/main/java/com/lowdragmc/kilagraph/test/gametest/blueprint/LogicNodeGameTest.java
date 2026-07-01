package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.NotNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.XorNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

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
}
