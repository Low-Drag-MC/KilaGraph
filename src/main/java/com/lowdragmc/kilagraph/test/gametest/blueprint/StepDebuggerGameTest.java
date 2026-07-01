package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BreakNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ContinueNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.ExecFrame;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The blueprint step-debugger ({@link ExecSession}): single-stepping the exec flow with step-into
 * for loops/sequences/subgraphs, plus breakpoints, reset, and introspection (current/last node,
 * step count, call stack, variable snapshot). Runs on the server because subgraph graph-variable
 * construction loads MC classes.
 */
@GameTestHolder(Kilagraph.MODID)
public final class StepDebuggerGameTest {
    private static final String LINEAR = "step_linear_chain";
    private static final String FOR_BODY = "step_into_for_body";
    private static final String BRANCH = "step_branch_chosen_path";
    private static final String SEQUENCE = "step_sequence_order";
    private static final String BREAK = "step_break";
    private static final String CONTINUE = "step_continue";
    private static final String SUBGRAPH = "step_into_subgraph";
    private static final String BREAKPOINT = "step_breakpoint";
    private static final String RESET = "step_reset";
    private static final String CALLSTACK = "step_callstack_and_vars";

    private StepDebuggerGameTest() {}

    // ---- helpers --------------------------------------------------------------------------------

    private static float num(GraphExecutor exec, String var) {
        Object v = exec.getEnvironment().variables().get(var);
        return v instanceof Number n ? n.floatValue() : Float.NaN;
    }

    private static NodeModel counterSetVar(BlueprintGraph g, NodeModel indexSrc, String indexOut, String varName) {
        var setVar = addNode(g, SetVarNode.class);
        setOption(setVar, "varName", varName);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in2", 1.0f);
        wire(g, add.getInputsById().get("in1"), indexSrc.getOutputsById().get(indexOut));
        wire(g, setVar.getInputsById().get("value"), add.getOutputsById().get("out"));
        return setVar;
    }

    // ---- 1. linear chain --------------------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void linear(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var a = addNode(g, NoopNode.class);
        var b = addNode(g, NoopNode.class);
        wire(g, a.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, b.getInputsById().get("in"), a.getOutputsById().get("out"));

        var s = new ExecSession(new GraphExecutor(g)).begin(entry);
        assertEq(helper, "current=entry", entry, s.currentNode());
        s.step();
        assertEq(helper, "ran entry", entry, s.lastExecuted());
        assertEq(helper, "current=a", a, s.currentNode());
        s.step();
        assertEq(helper, "ran a", a, s.lastExecuted());
        s.step();
        assertEq(helper, "ran b", b, s.lastExecuted());
        assertFalse(helper, "no more steps", s.step());
        assertTrue(helper, "finished", s.isFinished());
        assertEq(helper, "3 steps", 3, s.stepCount());
        helper.succeed();
    }

    // ---- 2. step into a For loop body -------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void forBody(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var forNode = addNode(g, ForNode.class);
        setInputConstant(forNode, "count", 3);
        var setVar = counterSetVar(g, forNode, "index", "counter");
        wire(g, forNode.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setVar.getInputsById().get("trigger"), forNode.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        var s = new ExecSession(exec).begin(entry);
        int bodyRuns = 0;
        while (s.step()) {
            if (s.lastExecuted() == setVar) bodyRuns++;
        }
        assertEq(helper, "body stepped 3 times", 3, bodyRuns);
        assertEq(helper, "counter==3", 3f, num(exec, "counter"), 1e-5f);
        helper.succeed();
    }

    // ---- 3. branch steps only the chosen path -----------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void branch(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var br = addNode(g, BranchNode.class);
        setInputConstant(br, "cond", false);
        var tNoop = addNode(g, NoopNode.class);
        var fNoop = addNode(g, NoopNode.class);
        wire(g, br.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, tNoop.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, fNoop.getInputsById().get("in"), br.getOutputsById().get("falseExec"));

        var s = new ExecSession(new GraphExecutor(g)).begin(entry);
        boolean steppedTrue = false, steppedFalse = false;
        while (s.step()) {
            if (s.lastExecuted() == tNoop) steppedTrue = true;
            if (s.lastExecuted() == fNoop) steppedFalse = true;
        }
        assertFalse(helper, "true path not stepped", steppedTrue);
        assertTrue(helper, "false path stepped", steppedFalse);
        helper.succeed();
    }

    // ---- 4. sequence run-to-completion order ------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sequence(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var seq = addNode(g, SequenceNode.class);
        setOption(seq, "outputs", 2);
        var a = addNode(g, NoopNode.class);
        var b = addNode(g, NoopNode.class);
        wire(g, seq.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, a.getInputsById().get("in"), seq.getOutputsById().get("out1"));
        wire(g, b.getInputsById().get("in"), seq.getOutputsById().get("out2"));

        var s = new ExecSession(new GraphExecutor(g)).begin(entry);
        int aStep = -1, bStep = -1, i = 0;
        while (s.step()) {
            if (s.lastExecuted() == a) aStep = i;
            if (s.lastExecuted() == b) bStep = i;
            i++;
        }
        assertTrue(helper, "out1 stepped before out2", aStep >= 0 && bStep >= 0 && aStep < bStep);
        helper.succeed();
    }

    // ---- 5. break while stepping ------------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void breakStepping(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var forNode = addNode(g, ForNode.class);
        setInputConstant(forNode, "count", 5);
        var ge = addNode(g, GreaterEqualNode.class);
        setInputConstant(ge, "b", 2.0f);
        wire(g, ge.getInputsById().get("a"), forNode.getOutputsById().get("index"));
        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));
        var brk = addNode(g, BreakNode.class);
        var setVar = counterSetVar(g, forNode, "index", "counter");
        wire(g, forNode.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, br.getInputsById().get("in"), forNode.getOutputsById().get("body"));
        wire(g, brk.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, setVar.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        new ExecSession(exec).begin(entry).runToCompletion();
        assertEq(helper, "counter==2 (broke at index 2)", 2f, num(exec, "counter"), 1e-5f);
        helper.succeed();
    }

    // ---- 6. continue while stepping ---------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void continueStepping(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var forNode = addNode(g, ForNode.class);
        setInputConstant(forNode, "count", 5);
        var ge = addNode(g, GreaterEqualNode.class);
        setInputConstant(ge, "b", 2.0f);
        wire(g, ge.getInputsById().get("a"), forNode.getOutputsById().get("index"));
        var br = addNode(g, BranchNode.class);
        wire(g, br.getInputsById().get("cond"), ge.getOutputsById().get("out"));
        var cont = addNode(g, ContinueNode.class);
        var setVar = counterSetVar(g, forNode, "index", "counter");
        wire(g, forNode.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, br.getInputsById().get("in"), forNode.getOutputsById().get("body"));
        wire(g, cont.getInputsById().get("in"), br.getOutputsById().get("trueExec"));
        wire(g, setVar.getInputsById().get("trigger"), br.getOutputsById().get("falseExec"));

        var exec = new GraphExecutor(g);
        new ExecSession(exec).begin(entry).runToCompletion();
        assertEq(helper, "counter==2 (skipped from index 2)", 2f, num(exec, "counter"), 1e-5f);
        helper.succeed();
    }

    // ---- 7. step INTO a subgraph ------------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void subgraph(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("inner null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var execIn = (VariableDeclarationModelBase) inner.createVariable("execIn", TypeHandles.EXECUTION_FLOW, null, VariableKind.INPUT);
        var execOut = (VariableDeclarationModelBase) inner.createVariable("execOut", TypeHandles.EXECUTION_FLOW, null, VariableKind.OUTPUT);
        var execInNode = inner.createVariableNode(execIn, new Vector2f(0, 0), null, null);
        var execOutNode = inner.createVariableNode(execOut, new Vector2f(400, 0), null, null);
        // inner: execIn → innerNoop → execOut
        var innerNoop = addNode(inner.getGraph(), NoopNode.class);
        inner.createWire(innerNoop.getInputsById().get("in"), execInNode.getOutputPort());
        inner.createWire(execOutNode.getInputPort(), innerNoop.getOutputsById().get("out"));

        var sub = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();

        var entry = addNode(outer, EntryNode.class);
        var outerNoop = addNode(outer, NoopNode.class);
        wire(outer, sub.getInputsById().get(execIn.getUid().toString()), entry.getOutputsById().get("next"));
        wire(outer, outerNoop.getInputsById().get("in"), sub.getOutputsById().get(execOut.getUid().toString()));

        var s = new ExecSession(new GraphExecutor(outer)).begin(entry);
        boolean steppedInner = false, sawSubgraphFrame = false, steppedOuterNoop = false;
        while (s.step()) {
            if (s.lastExecuted() == innerNoop) steppedInner = true;
            if (s.lastExecuted() == outerNoop) steppedOuterNoop = true;
            for (var entryFrame : s.callStack()) {
                if (entryFrame.kind() == ExecFrame.Kind.SUBGRAPH) sawSubgraphFrame = true;
            }
        }
        assertTrue(helper, "inner node was individually stepped", steppedInner);
        assertTrue(helper, "call stack showed a SUBGRAPH frame", sawSubgraphFrame);
        assertTrue(helper, "outer flow resumed after subgraph exec-out", steppedOuterNoop);
        helper.succeed();
    }

    // ---- 8. breakpoint pauses before the node -----------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void breakpoint(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var a = addNode(g, NoopNode.class);
        var b = addNode(g, NoopNode.class);
        wire(g, a.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, b.getInputsById().get("in"), a.getOutputsById().get("out"));

        var s = new ExecSession(new GraphExecutor(g)).begin(entry);
        s.addBreakpoint(b);
        var hit = s.runToBreakpoint();
        assertEq(helper, "paused on b", b, hit);
        assertEq(helper, "b not executed yet (last was a)", a, s.lastExecuted());
        assertFalse(helper, "not finished at breakpoint", s.isFinished());
        s.runToCompletion();
        assertEq(helper, "b executed after resume", b, s.lastExecuted());
        assertTrue(helper, "finished", s.isFinished());
        helper.succeed();
    }

    // ---- 9. reset reruns from entry ---------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void reset(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var forNode = addNode(g, ForNode.class);
        setInputConstant(forNode, "count", 3);
        var setVar = counterSetVar(g, forNode, "index", "counter");
        wire(g, forNode.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setVar.getInputsById().get("trigger"), forNode.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        var s = new ExecSession(exec).begin(entry);
        s.runToCompletion();
        assertEq(helper, "first run counter==3", 3f, num(exec, "counter"), 1e-5f);

        exec.clearCache();
        exec.clearNodeState();
        exec.getEnvironment().variables().clear();
        s.reset();
        assertFalse(helper, "reset not finished", s.isFinished());
        assertEq(helper, "reset step count 0", 0, s.stepCount());
        s.runToCompletion();
        assertEq(helper, "rerun counter==3", 3f, num(exec, "counter"), 1e-5f);
        helper.succeed();
    }

    // ---- 10. callStack shows LOOP frame; variables() snapshot mid-run -----------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void callStackAndVars(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var forNode = addNode(g, ForNode.class);
        setInputConstant(forNode, "count", 3);
        var setVar = counterSetVar(g, forNode, "index", "counter");
        wire(g, forNode.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setVar.getInputsById().get("trigger"), forNode.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        var s = new ExecSession(exec).begin(entry);
        boolean sawLoopFrame = false;
        boolean sawCounterMidRun = false;
        while (s.step()) {
            if (s.lastExecuted() == setVar) {
                for (var e : s.callStack()) {
                    if (e.kind() == ExecFrame.Kind.LOOP) sawLoopFrame = true;
                }
                // after the first body SetVar, counter is live in the env snapshot
                if (s.variables().contains("counter")) sawCounterMidRun = true;
            }
        }
        assertTrue(helper, "call stack showed a LOOP frame inside the loop body", sawLoopFrame);
        assertTrue(helper, "variable snapshot reflected a mid-run write", sawCounterMidRun);
        helper.succeed();
    }
}
