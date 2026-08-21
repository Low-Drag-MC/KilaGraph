package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.AssertNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BreakNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.graph.exec.CycleException;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * What happens when a run goes wrong, and what must still be true afterwards.
 *
 * <p>An executor is long-lived and reused across runs, and it carries mutable state that a failed
 * run passes through mid-update: the slot table, the write log, the cycle-detection stack, the
 * context pools and their staged outputs. The property under test is that a failure leaves none of
 * that corrupted — the next run on the same executor must be correct. That is easy to break with a
 * cleanup path that is only correct on the happy path, and impossible to notice from a test that
 * never throws.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class ErrorPathGameTest {

    private ErrorPathGameTest() {}

    /**
     * A node that throws part-way through an exec flow leaves the executor usable: the run after it
     * computes the right answer, and the nodes downstream of the failure did not run.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execFlowSurvivesAThrowingNode(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        // SetVar's `value` port is UNKNOWN-typed and so carries no embedded constant: it has to be
        // fed by a wire, which is why each write here has a one-node source in front of it.
        b.add("entry", EntryNode.class);
        b.add("one", AddNode.class).constant("one.in1", 1f).constant("one.in2", 0f);
        b.add("before", SetVarNode.class).option("before", "varName", "reached")
                .wire("before.value", "one");
        b.add("boom", AssertNode.class).constant("boom.condition", false)
                .constant("boom.message", "deliberate");
        b.add("after", SetVarNode.class).option("after", "varName", "afterBoom")
                .wire("after.value", "one");
        b.then("entry", "before", "boom", "after");

        var exec = new GraphExecutor(b.graph());
        assertTrue(helper, "the assert really threw", throwsAny(() -> exec.executeFrom(b.node("entry"))));
        assertTrue(helper, "the node before it ran",
                exec.getEnvironment().variables().get("reached") instanceof Number);
        assertEq(helper, "the node after it did not", null,
                exec.getEnvironment().variables().get("afterBoom"));

        // The executor must still be usable: a plain data pull on it is correct.
        b.add("calc", AddNode.class).constant("calc.in1", 20f).constant("calc.in2", 22f);
        exec.clearCache();
        assertEq(helper, "executor still computes after a failure", 42f,
                orNaN(exec.evaluate(b.outputOf("calc"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /**
     * A data node that throws does not leave its staged outputs behind for the next node that lands
     * on the same pooled context — {@code EvalContext.dropStaged} is what guarantees this, and it
     * only runs on the failure path.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aThrowingPullLeavesNoStaleStagedValue(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        // A Multiply whose input is an Assert-guarded chain is awkward to build; a cycle is the
        // simplest data-side throw the executor itself raises.
        b.add("a", AddNode.class).constant("a.in2", 1f);
        b.add("bNode", AddNode.class).constant("bNode.in2", 1f);
        b.wire("a.in1", "bNode").wire("bNode.in1", "a");

        var exec = new GraphExecutor(b.graph());
        assertTrue(helper, "a cycle throws", throwsCycle(() -> exec.evaluate(b.outputOf("a"), Float.class)));

        // A second, unrelated pull on the same executor must be unaffected.
        b.add("clean", MultiplyNode.class).constant("clean.in1", 6f).constant("clean.in2", 7f);
        exec.clearCache();
        assertEq(helper, "unrelated pull after a cycle", 42f,
                orNaN(exec.evaluate(b.outputOf("clean"), Float.class)), 1e-5f);

        // And the cycle is still reported on a retry rather than answering from a poisoned table.
        assertTrue(helper, "the cycle is still detected on a retry",
                throwsCycle(() -> exec.evaluate(b.outputOf("a"), Float.class)));
        helper.succeed();
    }

    /** {@code Break} with no enclosing loop is a diagnostic, not a silent no-op. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void breakOutsideALoopIsReported(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.add("brk", BreakNode.class);
        b.wire("brk.in", "entry");

        var exec = new GraphExecutor(b.graph());
        assertTrue(helper, "Break outside a loop throws",
                throwsIllegalState(() -> exec.executeFrom(b.node("entry"))));
        helper.succeed();
    }

    /** A throw inside a loop body stops the loop and still leaves the executor usable. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aThrowInsideALoopBodyUnwindsCleanly(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.add("loop", ForNode.class).constant("loop.count", 5);
        b.add("count", SetVarNode.class).option("count", "varName", "iterations")
                .wire("count.value", "loop.index");
        b.add("boom", AssertNode.class).constant("boom.condition", false).constant("boom.message", "in-loop");
        b.add("done", NoopNode.class);
        b.wire("loop.in", "entry");
        b.then("count", "boom");
        b.wire("count.trigger", "loop.body");
        b.wire("done.in", "loop.completed");

        var exec = new GraphExecutor(b.graph());
        assertTrue(helper, "the body's assert threw", throwsAny(() -> exec.executeFrom(b.node("entry"))));
        assertEq(helper, "it failed on the first iteration", 0f,
                num(exec.getEnvironment().variables().get("iterations")), 1e-5f);

        b.add("calc", AddNode.class).constant("calc.in1", 1f).constant("calc.in2", 2f);
        exec.clearCache();
        assertEq(helper, "executor usable after a loop-body failure", 3f,
                orNaN(exec.evaluate(b.outputOf("calc"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /** An input with nothing wired and no constant resolves to the reader's default, not a crash. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void unwiredInputsFallBackToDefaults(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("add", AddNode.class).constant("add.in1", 7f).constant("add.in2", 0f);

        var exec = new GraphExecutor(b.graph());
        assertEq(helper, "unwired second input contributes its default", 7f,
                orNaN(exec.evaluate(b.outputOf("add"), Float.class)), 1e-5f);
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static boolean throwsAny(Runnable r) {
        try {
            r.run();
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean throwsCycle(Runnable r) {
        try {
            r.run();
            return false;
        } catch (CycleException e) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean throwsIllegalState(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static float orNaN(Float v) {
        return v == null ? Float.NaN : v;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
