package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BreakNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ContinueNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JUnit coverage of the {@link ExecSession} step VM mechanics — no Minecraft classes needed.
 * Loops, branches, sequences, break/continue, breakpoints, and reset are all exercised by stepping.
 */
class ExecSessionTest {

    private static NodeModel n(BlueprintGraph g, Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node> c) {
        return GraphTestUtils.addNode(g, c);
    }

    private static void wire(BlueprintGraph g, NodeModel dstNode, String dstId, NodeModel srcNode, String srcId) {
        g.graphModel.createWire(dstNode.getInputsById().get(dstId), srcNode.getOutputsById().get(srcId));
    }

    /** Entry → Noop → Noop: three steps, then finished. lastExecuted/stepCount/currentNode track. */
    @Test
    void linearChainStepsOneNodeAtATime() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel a = n(g, NoopNode.class);
        NodeModel b = n(g, NoopNode.class);
        wire(g, a, "in", entry, "next");
        wire(g, b, "in", a, "out");

        var session = new ExecSession(new GraphExecutor(g)).begin(entry);
        assertSame(entry, session.currentNode());
        assertEquals(0, session.stepCount());

        assertTrue(session.step());
        assertSame(entry, session.lastExecuted());
        assertSame(a, session.currentNode());

        assertTrue(session.step());
        assertSame(a, session.lastExecuted());
        assertSame(b, session.currentNode());

        assertTrue(session.step());
        assertSame(b, session.lastExecuted());

        assertFalse(session.step());      // nothing left
        assertTrue(session.isFinished());
        assertEquals(3, session.stepCount());
        assertNull(session.currentNode());
    }

    /** For(3) body = SetVar(counter = index+1): body node is stepped each iteration; final counter == 3. */
    @Test
    void stepsIntoForLoopBody() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel forNode = n(g, ForNode.class);
        GraphTestUtils.setInputConstant(forNode, "count", 3);
        NodeModel setVar = n(g, SetVarNode.class);
        GraphTestUtils.setOption(setVar, "varName", "counter");
        NodeModel add = n(g, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in2", 1.0f);
        wire(g, add, "in1", forNode, "index");
        wire(g, forNode, "in", entry, "next");
        wire(g, setVar, "trigger", forNode, "body");
        wire(g, setVar, "value", add, "out");

        var executor = new GraphExecutor(g);
        var session = new ExecSession(executor).begin(entry);

        // The SetVar body node must be executed exactly 3 times across stepping.
        int setVarRuns = 0;
        while (session.step()) {
            if (session.lastExecuted() == setVar) setVarRuns++;
        }
        assertEquals(3, setVarRuns, "body SetVar should run once per iteration");
        Object counter = executor.getEnvironment().variables().get("counter");
        assertEquals(3.0f, ((Number) counter).floatValue(), 1e-5f);
    }

    /** Branch(false) steps only the false path. */
    @Test
    void branchStepsChosenPathOnly() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel branch = n(g, BranchNode.class);
        GraphTestUtils.setInputConstant(branch, "cond", false);
        NodeModel truePath = n(g, SetVarNode.class);
        GraphTestUtils.setOption(truePath, "varName", "tookTrue");
        NodeModel falsePath = n(g, SetVarNode.class);
        GraphTestUtils.setOption(falsePath, "varName", "tookFalse");
        wire(g, branch, "in", entry, "next");
        wire(g, truePath, "trigger", branch, "trueExec");
        wire(g, falsePath, "trigger", branch, "falseExec");

        var executor = new GraphExecutor(g);
        var session = new ExecSession(executor).begin(entry);
        boolean steppedTrue = false, steppedFalse = false;
        while (session.step()) {
            if (session.lastExecuted() == truePath) steppedTrue = true;
            if (session.lastExecuted() == falsePath) steppedFalse = true;
        }
        assertFalse(steppedTrue, "true path must not be stepped");
        assertTrue(steppedFalse, "false path must be stepped");
    }

    /** Sequence(out1→A, out2→B): A is stepped fully before B (run-to-completion order). */
    @Test
    void sequenceStepsInOrder() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel seq = n(g, SequenceNode.class);
        GraphTestUtils.setOption(seq, "outputs", 2);
        NodeModel a = n(g, NoopNode.class);
        NodeModel b = n(g, NoopNode.class);
        wire(g, seq, "in", entry, "next");
        wire(g, a, "in", seq, "out1");
        wire(g, b, "in", seq, "out2");

        var session = new ExecSession(new GraphExecutor(g)).begin(entry);
        int aStep = -1, bStep = -1, i = 0;
        while (session.step()) {
            if (session.lastExecuted() == a) aStep = i;
            if (session.lastExecuted() == b) bStep = i;
            i++;
        }
        assertTrue(aStep >= 0 && bStep >= 0, "both branches stepped");
        assertTrue(aStep < bStep, "out1 before out2");
    }

    /** For(5) body: Branch(index>=2 ? Break). counter reaches 2 then loop breaks while stepping. */
    @Test
    void breakWhileStepping() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel forNode = n(g, ForNode.class);
        GraphTestUtils.setInputConstant(forNode, "count", 5);
        NodeModel ge = n(g, com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode.class);
        GraphTestUtils.setInputConstant(ge, "b", 2.0f);
        wire(g, ge, "a", forNode, "index");
        NodeModel branch = n(g, BranchNode.class);
        wire(g, branch, "cond", ge, "out");
        NodeModel brk = n(g, BreakNode.class);
        NodeModel setVar = n(g, SetVarNode.class);
        GraphTestUtils.setOption(setVar, "varName", "counter");
        NodeModel add = n(g, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in2", 1.0f);
        wire(g, add, "in1", forNode, "index");
        wire(g, setVar, "value", add, "out");
        wire(g, forNode, "in", entry, "next");
        wire(g, branch, "in", forNode, "body");
        wire(g, brk, "in", branch, "trueExec");
        wire(g, setVar, "trigger", branch, "falseExec");

        var executor = new GraphExecutor(g);
        new ExecSession(executor).begin(entry).runToCompletion();
        // index 0,1 ran SetVar (1,2); index 2 broke before SetVar.
        assertEquals(2.0f, ((Number) executor.getEnvironment().variables().get("counter")).floatValue(), 1e-5f);
    }

    /** For(5) body: Branch(index>=2 ? Continue : SetVar). counter == 2 (writes only for 0,1). */
    @Test
    void continueWhileStepping() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel forNode = n(g, ForNode.class);
        GraphTestUtils.setInputConstant(forNode, "count", 5);
        NodeModel ge = n(g, com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode.class);
        GraphTestUtils.setInputConstant(ge, "b", 2.0f);
        wire(g, ge, "a", forNode, "index");
        NodeModel branch = n(g, BranchNode.class);
        wire(g, branch, "cond", ge, "out");
        NodeModel cont = n(g, ContinueNode.class);
        NodeModel setVar = n(g, SetVarNode.class);
        GraphTestUtils.setOption(setVar, "varName", "counter");
        NodeModel add = n(g, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in2", 1.0f);
        wire(g, add, "in1", forNode, "index");
        wire(g, setVar, "value", add, "out");
        wire(g, forNode, "in", entry, "next");
        wire(g, branch, "in", forNode, "body");
        wire(g, cont, "in", branch, "trueExec");
        wire(g, setVar, "trigger", branch, "falseExec");

        var executor = new GraphExecutor(g);
        new ExecSession(executor).begin(entry).runToCompletion();
        assertEquals(2.0f, ((Number) executor.getEnvironment().variables().get("counter")).floatValue(), 1e-5f);
    }

    /** runToBreakpoint pauses BEFORE the marked node; resuming finishes. */
    @Test
    void breakpointPausesBeforeNode() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel a = n(g, NoopNode.class);
        NodeModel b = n(g, NoopNode.class);
        wire(g, a, "in", entry, "next");
        wire(g, b, "in", a, "out");

        var session = new ExecSession(new GraphExecutor(g)).begin(entry);
        session.addBreakpoint(b);
        NodeModel hit = session.runToBreakpoint();
        assertSame(b, hit, "paused on breakpoint b");
        assertSame(a, session.lastExecuted(), "b has NOT executed yet");
        assertFalse(session.isFinished());
        session.runToCompletion();
        assertSame(b, session.lastExecuted());
        assertTrue(session.isFinished());
    }

    /** reset() + clear state reruns from entry with the same outcome. */
    @Test
    void resetReruns() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel forNode = n(g, ForNode.class);
        GraphTestUtils.setInputConstant(forNode, "count", 3);
        NodeModel setVar = n(g, SetVarNode.class);
        GraphTestUtils.setOption(setVar, "varName", "counter");
        NodeModel add = n(g, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in2", 1.0f);
        wire(g, add, "in1", forNode, "index");
        wire(g, forNode, "in", entry, "next");
        wire(g, setVar, "trigger", forNode, "body");
        wire(g, setVar, "value", add, "out");

        var executor = new GraphExecutor(g);
        var session = new ExecSession(executor).begin(entry);
        session.runToCompletion();
        assertEquals(3.0f, ((Number) executor.getEnvironment().variables().get("counter")).floatValue(), 1e-5f);

        executor.clearCache();
        executor.clearNodeState();
        executor.getEnvironment().variables().clear();
        session.reset();
        assertFalse(session.isFinished());
        assertEquals(0, session.stepCount());
        session.runToCompletion();
        assertEquals(3.0f, ((Number) executor.getEnvironment().variables().get("counter")).floatValue(), 1e-5f);
    }

    /** step(n) takes at most n steps and stops at completion. */
    @Test
    void stepNBounded() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel entry = n(g, EntryNode.class);
        NodeModel a = n(g, NoopNode.class);
        wire(g, a, "in", entry, "next");

        var session = new ExecSession(new GraphExecutor(g)).begin(entry);
        assertEquals(1, session.step(1));
        assertEquals(1, session.stepCount());
        assertEquals(1, session.step(5), "only one node left, so one more step");
        assertTrue(session.isFinished());
    }
}
