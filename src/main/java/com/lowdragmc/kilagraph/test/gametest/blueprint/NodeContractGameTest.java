package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.sync.UIRpcNodes;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

/**
 * What a node <em>declares</em> about itself, as opposed to what it computes — starting with the
 * order its ports appear in.
 *
 * <p>Invisible to every other test in this suite: a node evaluates identically whichever order its
 * pins are drawn in, so the only thing that can notice this going wrong is a test that asks.
 */
public final class NodeContractGameTest {
    private static final String PORT_ORDER = "node_contract_declared_ports_come_first";

    public static void registerFunctions() {
        KGGameTests.registerFunction(PORT_ORDER, NodeContractGameTest::declaredPortsComeBeforeDynamicOnes);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{PORT_ORDER}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), data);
        }
    }

    private NodeContractGameTest() {
    }

    /**
     * A node's own {@code @…Port} fields sit above the ports it defines in
     * {@code onDefineDynamicPorts}, and exec pins sit above data pins.
     *
     * <p>This is an invariant, not a mechanism: a port takes its place in the display order at the
     * moment its builder is <em>built</em>, so the order depends on when each one happens to be. It
     * currently holds two ways over — {@code NodeMetadata.applyPort} builds eagerly, and LDLib2's
     * {@code finish()} would drain the rest in creation order anyway — and the case that would break
     * it is a dynamic hook that builds a port of its own, jumping the whole declared block. Asking
     * for the invariant rather than for either mechanism is what would catch that.</p>
     */
    public static void declaredPortsComeBeforeDynamicOnes(GameTestHelper helper) {
        var g = newGraph();
        NodeModel send = addNode(g, UIRpcNodes.Send.class);
        setOption(send, "argCount", 2);

        List<String> inputs = ids(send.getInputsByDisplayOrder());
        assertTrue(helper, "the rpc call has its argument pins, got " + inputs,
                inputs.contains("arg1") && inputs.contains("arg2"));
        assertBefore(helper, inputs, "trigger", "arg1");
        assertBefore(helper, inputs, "rpc", "arg1");
        assertBefore(helper, inputs, "arg1", "arg2");

        List<String> outputs = ids(send.getOutputsByDisplayOrder());
        assertBefore(helper, outputs, "next", "result");
        assertBefore(helper, outputs, "onReturn", "result");
        assertBefore(helper, outputs, "ok", "result");

        // The exec-before-data rule applyPorts() exists for still holds on top of it.
        NodeModel setVar = addNode(g, SetVarNode.class);
        assertBefore(helper, ids(setVar.getInputsByDisplayOrder()), "trigger", "value");
        helper.succeed();
    }

    private static List<String> ids(List<PortModel> ports) {
        List<String> out = new ArrayList<>(ports.size());
        for (PortModel p : ports) out.add(p.getPortId());
        return out;
    }

    private static void assertBefore(GameTestHelper helper, List<String> order, String first, String second) {
        int a = order.indexOf(first);
        int b = order.indexOf(second);
        if (a < 0 || b < 0) {
            helper.fail("expected both " + first + " and " + second + " in " + order);
            return;
        }
        if (a > b) {
            helper.fail(first + " should come before " + second + ", order was " + order);
        }
    }
}
