package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.AssertNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.PrintNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Engine-level tests for the exec runtime: queue draining, Entry → chain, Noop pass-through,
 * Print value capture via node state, Assert true/false behaviour.
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecRuntimeGameTest {
    private static final String ENTRY_FIRES_NEXT = "exec_entry_fires_next";
    private static final String UNWIRED_FLOW_NO_OP = "exec_unwired_flow_no_op";
    private static final String NOOP_PASSES_THROUGH = "exec_noop_passes_through";
    private static final String ASSERT_TRUE_CONTINUES = "exec_assert_true_continues";
    private static final String ASSERT_FALSE_THROWS = "exec_assert_false_throws";

    private ExecRuntimeGameTest() {}

    /** Entry → Print: PrintNode.state("last") captures the value, proving the queue drove it. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void entryFiresNext(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var print = addNode(g, PrintNode.class);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 7.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, print.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, print.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object captured = exec.nodeState(print.getUid()).get("last");
        if (!(captured instanceof Number n) || Math.abs(n.floatValue() - 7.0f) > 1e-5f) {
            helper.fail("expected captured 7f, got " + captured);
            return;
        }
        helper.succeed();
    }

    /** Entry with no wire on `next` is a no-op — must not throw. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void unwiredFlowNoOp(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        try {
            new GraphExecutor(g).executeFrom(entry);
        } catch (Exception e) {
            helper.fail("unwired entry should not throw: " + e);
            return;
        }
        helper.succeed();
    }

    /** Entry → Noop → Print. Noop must forward to Print. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void noopPassesThrough(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var noop = addNode(g, NoopNode.class);
        var print = addNode(g, PrintNode.class);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 99.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, noop.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, print.getInputsById().get("trigger"), noop.getOutputsById().get("out"));
        wire(g, print.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object captured = exec.nodeState(print.getUid()).get("last");
        if (!(captured instanceof Number n) || Math.abs(n.floatValue() - 99.0f) > 1e-5f) {
            helper.fail("expected 99f through Noop, got " + captured);
            return;
        }
        helper.succeed();
    }

    /** Entry → Assert(true) → Print. Should reach Print. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void assertTrueContinues(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var ass = addNode(g, AssertNode.class);
        var print = addNode(g, PrintNode.class);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 1.0f);
        setInputConstant(add, "in2", 0.0f);
        setInputConstant(ass, "condition", true);
        wire(g, ass.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, print.getInputsById().get("trigger"), ass.getOutputsById().get("next"));
        wire(g, print.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object captured = exec.nodeState(print.getUid()).get("last");
        if (captured == null) { helper.fail("Assert(true) blocked the chain"); return; }
        helper.succeed();
    }

    /** Entry → Assert(false) → Print. Should throw AssertionError before Print runs. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void assertFalseThrows(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var ass = addNode(g, AssertNode.class);
        var print = addNode(g, PrintNode.class);
        setInputConstant(ass, "condition", false);
        setInputConstant(ass, "message", "boom");
        wire(g, ass.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, print.getInputsById().get("trigger"), ass.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        try {
            exec.executeFrom(entry);
            helper.fail("expected AssertionError");
            return;
        } catch (AssertionError ok) {
            if (!"boom".equals(ok.getMessage())) {
                helper.fail("expected message 'boom', got '" + ok.getMessage() + "'");
                return;
            }
        }
        // Print must NOT have run
        Object captured = exec.nodeState(print.getUid()).get("last");
        assertEq(helper, "Print not reached", null, captured);
        helper.succeed();
    }
}
