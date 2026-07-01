package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.GateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.PrintNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SwitchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class ExecPrimitivesGameTest {
    private static final String SEQUENCE = "exec_sequence_fires_in_order";
    private static final String BRANCH_TRUE = "exec_branch_true";
    private static final String BRANCH_FALSE = "exec_branch_false";
    private static final String GATE_OPEN = "exec_gate_open";
    private static final String GATE_CLOSED = "exec_gate_closed";
    private static final String SWITCH_MATCH = "exec_switch_match";
    private static final String SWITCH_DEFAULT = "exec_switch_default";

    private ExecPrimitivesGameTest() {}

    /** Helper: Add node emitting a Float constant. */
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel
            floatSource(com.lowdragmc.kilagraph.blueprint.BlueprintGraph g, float v) {
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", v);
        setInputConstant(add, "in2", 0.0f);
        return add;
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void sequence(GameTestHelper helper) {
        // Entry → Sequence(3 outs) → 3 Print nodes
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var seq = addNode(g, SequenceNode.class);
        setOption(seq, "outputs", 3);

        var p1 = addNode(g, PrintNode.class);
        var p2 = addNode(g, PrintNode.class);
        var p3 = addNode(g, PrintNode.class);
        wire(g, seq.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, p1.getInputsById().get("trigger"), seq.getOutputsById().get("out1"));
        wire(g, p2.getInputsById().get("trigger"), seq.getOutputsById().get("out2"));
        wire(g, p3.getInputsById().get("trigger"), seq.getOutputsById().get("out3"));
        wire(g, p1.getInputsById().get("value"), floatSource(g, 1f).getOutputsById().get("out"));
        wire(g, p2.getInputsById().get("value"), floatSource(g, 2f).getOutputsById().get("out"));
        wire(g, p3.getInputsById().get("value"), floatSource(g, 3f).getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        for (int i = 0; i < 3; i++) {
            Object captured = exec.nodeState(new com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel[]{p1, p2, p3}[i].getUid()).get("last");
            if (!(captured instanceof Number n) || Math.abs(n.floatValue() - (i + 1)) > 1e-5f) {
                helper.fail("p" + (i + 1) + " missing or wrong: " + captured);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void branchTrue(GameTestHelper helper) {
        runBranch(helper, true, "true-branch", "false-branch", "true-branch");
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void branchFalse(GameTestHelper helper) {
        runBranch(helper, false, "true-branch", "false-branch", "false-branch");
    }

    /** Wires Entry → Branch(cond) → trueSink / falseSink Prints; asserts only the expected one ran. */
    private static void runBranch(GameTestHelper helper, boolean cond, String tLabel, String fLabel, String expected) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var br = addNode(g, BranchNode.class);
        setInputConstant(br, "cond", cond);
        var pT = addNode(g, PrintNode.class);
        var pF = addNode(g, PrintNode.class);
        wire(g, br.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, pT.getInputsById().get("trigger"), br.getOutputsById().get("trueExec"));
        wire(g, pF.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));
        // Wire arbitrary string-shaped values via String-typed sources via floatSource (will surface as Float).
        wire(g, pT.getInputsById().get("value"), floatSource(g, 1f).getOutputsById().get("out"));
        wire(g, pF.getInputsById().get("value"), floatSource(g, 0f).getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object tCaptured = exec.nodeState(pT.getUid()).get("last");
        Object fCaptured = exec.nodeState(pF.getUid()).get("last");
        if (expected.equals(tLabel)) {
            if (tCaptured == null) { helper.fail("true branch not hit"); return; }
            if (fCaptured != null) { helper.fail("false branch hit unexpectedly"); return; }
        } else {
            if (fCaptured == null) { helper.fail("false branch not hit"); return; }
            if (tCaptured != null) { helper.fail("true branch hit unexpectedly"); return; }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void gateOpen(GameTestHelper helper) {
        runGate(helper, true, true);
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void gateClosed(GameTestHelper helper) {
        runGate(helper, false, false);
    }

    private static void runGate(GameTestHelper helper, boolean enabled, boolean expectRan) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var gate = addNode(g, GateNode.class);
        setInputConstant(gate, "enabled", enabled);
        var sink = addNode(g, PrintNode.class);
        wire(g, gate.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, sink.getInputsById().get("trigger"), gate.getOutputsById().get("out"));
        wire(g, sink.getInputsById().get("value"), floatSource(g, 42f).getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object captured = exec.nodeState(sink.getUid()).get("last");
        if (expectRan && captured == null) { helper.fail("gate=true: sink not reached"); return; }
        if (!expectRan && captured != null) { helper.fail("gate=false: sink reached unexpectedly"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void switchMatch(GameTestHelper helper) {
        // 3 cases, selector=2 → case2 fires
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var sw = addNode(g, SwitchNode.class);
        setOption(sw, "cases", 3);
        setInputConstant(sw, "selector", 2);

        var p1 = addNode(g, PrintNode.class);
        var p2 = addNode(g, PrintNode.class);
        var p3 = addNode(g, PrintNode.class);
        var pDef = addNode(g, PrintNode.class);
        wire(g, sw.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, p1.getInputsById().get("trigger"), sw.getOutputsById().get("case1"));
        wire(g, p2.getInputsById().get("trigger"), sw.getOutputsById().get("case2"));
        wire(g, p3.getInputsById().get("trigger"), sw.getOutputsById().get("case3"));
        wire(g, pDef.getInputsById().get("trigger"), sw.getOutputsById().get("defaultExec"));
        for (var p : new com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel[]{p1, p2, p3, pDef}) {
            wire(g, p.getInputsById().get("value"), floatSource(g, 1f).getOutputsById().get("out"));
        }

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        assertEq(helper, "p1 not hit", null, exec.nodeState(p1.getUid()).get("last"));
        if (exec.nodeState(p2.getUid()).get("last") == null) { helper.fail("p2 expected hit"); return; }
        assertEq(helper, "p3 not hit", null, exec.nodeState(p3.getUid()).get("last"));
        assertEq(helper, "default not hit", null, exec.nodeState(pDef.getUid()).get("last"));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void switchDefault(GameTestHelper helper) {
        // 2 cases, selector=99 → default
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var sw = addNode(g, SwitchNode.class);
        setOption(sw, "cases", 2);
        setInputConstant(sw, "selector", 99);
        var p1 = addNode(g, PrintNode.class);
        var p2 = addNode(g, PrintNode.class);
        var pDef = addNode(g, PrintNode.class);
        wire(g, sw.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, p1.getInputsById().get("trigger"), sw.getOutputsById().get("case1"));
        wire(g, p2.getInputsById().get("trigger"), sw.getOutputsById().get("case2"));
        wire(g, pDef.getInputsById().get("trigger"), sw.getOutputsById().get("defaultExec"));
        for (var p : new com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel[]{p1, p2, pDef}) {
            wire(g, p.getInputsById().get("value"), floatSource(g, 1f).getOutputsById().get("out"));
        }

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        assertEq(helper, "p1 not hit", null, exec.nodeState(p1.getUid()).get("last"));
        assertEq(helper, "p2 not hit", null, exec.nodeState(p2.getUid()).get("last"));
        if (exec.nodeState(pDef.getUid()).get("last") == null) { helper.fail("default expected hit"); return; }
        helper.succeed();
    }
}
