package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import net.minecraft.gametest.framework.GameTestHelper;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;

/**
 * Graphs that are deep, wide, or heavily shared.
 *
 * <p>Everything else in the suite runs on graphs of a handful of nodes, where the executor's growth
 * paths never fire: the slot table, the stamp table, the cycle-detection stack and the context pools
 * all start at a size a small graph never exceeds. These shapes push past those thresholds, so a
 * mistake in {@code ensureCapacity} or in the pooling shows up as a wrong answer here rather than in
 * whichever real graph first happened to be large.</p>
 *
 * <p>Depth matters for a second reason: the data side of the executor is genuinely recursive
 * ({@code ensureComputed} → {@code evaluateNode} → a node's {@code evaluate} → {@code pullInput} →
 * {@code ensureComputed}), so a long chain is a deep Java stack.</p>
 */
public final class DeepGraphGameTest {
    private static final String DEEP_DATA_CHAIN = "deep_graph_deep_data_chain";
    private static final String DEEP_CHAIN_RECOMPUTES_AFTER_CLEAR_CACHE = "deep_graph_deep_chain_recomputes_after_clear_cache";
    private static final String WIDE_FAN_IN = "deep_graph_wide_fan_in";
    private static final String WIDE_FAN_OUT_EVALUATES_THE_SOURCE_ONCE = "deep_graph_wide_fan_out_evaluates_the_source_once";
    private static final String DEEP_EXEC_CHAIN = "deep_graph_deep_exec_chain";
    private static final String DEEP_CHAIN_DRIVING_A_BRANCH = "deep_graph_deep_chain_driving_a_branch";

    private DeepGraphGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(DEEP_DATA_CHAIN, DeepGraphGameTest::deepDataChain);
        KGGameTests.registerFunction(DEEP_CHAIN_RECOMPUTES_AFTER_CLEAR_CACHE, DeepGraphGameTest::deepChainRecomputesAfterClearCache);
        KGGameTests.registerFunction(WIDE_FAN_IN, DeepGraphGameTest::wideFanIn);
        KGGameTests.registerFunction(WIDE_FAN_OUT_EVALUATES_THE_SOURCE_ONCE, DeepGraphGameTest::wideFanOutEvaluatesTheSourceOnce);
        KGGameTests.registerFunction(DEEP_EXEC_CHAIN, DeepGraphGameTest::deepExecChain);
        KGGameTests.registerFunction(DEEP_CHAIN_DRIVING_A_BRANCH, DeepGraphGameTest::deepChainDrivingABranch);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                DEEP_DATA_CHAIN, DEEP_CHAIN_RECOMPUTES_AFTER_CLEAR_CACHE, WIDE_FAN_IN,
                WIDE_FAN_OUT_EVALUATES_THE_SOURCE_ONCE, DEEP_EXEC_CHAIN, DEEP_CHAIN_DRIVING_A_BRANCH
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    private static final int DEEP = 64;
    private static final int WIDE = 32;

    /** A 64-long data chain: each link adds one, so the value is the depth actually traversed. */
    public static void deepDataChain(GameTestHelper helper) {
        var b = chainOfAdds(DEEP);
        var exec = new GraphExecutor(b.graph());
        Float out = exec.evaluate(b.outputOf("n" + (DEEP - 1)), Float.class);
        assertEq(helper, DEEP + "-deep chain", (float) DEEP, orNaN(out), 1e-3f);
        helper.succeed();
    }

    /**
     * The same chain evaluated twice on one executor, with a {@code clearCache()} between — the
     * second run re-walks the whole depth against tables the first run already grew, which is the
     * path a long-lived executor actually takes.
     */
    public static void deepChainRecomputesAfterClearCache(GameTestHelper helper) {
        var b = chainOfAdds(DEEP);
        var exec = new GraphExecutor(b.graph());
        var tip = b.outputOf("n" + (DEEP - 1));

        Float first = exec.evaluate(tip, Float.class);
        exec.clearCache();
        var trace = new EvalTrace();
        exec.setTrace(trace);
        Float second = exec.evaluate(tip, Float.class);

        assertEq(helper, "same value after clearCache", orNaN(first), orNaN(second), 1e-6f);
        assertEq(helper, "every link re-evaluated exactly once", DEEP, trace.countByLabel("AddNode"));
        helper.succeed();
    }

    /** A 32-input {@code Add}, each input produced by its own node: one node, thirty-two edges. */
    public static void wideFanIn(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("wide", AddNode.class).option("wide", "inputs", WIDE);
        float expected = 0f;
        for (int i = 1; i <= WIDE; i++) {
            b.add("src" + i, MultiplyNode.class)
                    .constant("src" + i + ".in1", (float) i).constant("src" + i + ".in2", 2f);
            b.wire("wide.in" + i, "src" + i);
            expected += i * 2f;
        }

        var exec = new GraphExecutor(b.graph());
        Float out = exec.evaluate(b.outputOf("wide"), Float.class);
        assertEq(helper, "sum of " + WIDE + " inputs", expected, orNaN(out), 1e-3f);
        helper.succeed();
    }

    /** One node feeding 32 consumers is still evaluated once. Fan-out, where the memo earns its keep. */
    public static void wideFanOutEvaluatesTheSourceOnce(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("shared", AddNode.class).constant("shared.in1", 2f).constant("shared.in2", 3f);
        b.add("sink", AddNode.class).option("sink", "inputs", WIDE);
        for (int i = 1; i <= WIDE; i++) {
            b.add("use" + i, MultiplyNode.class).wire("use" + i + ".in1", "shared").constant("use" + i + ".in2", 1f);
            b.wire("sink.in" + i, "use" + i);
        }

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        Float out = exec.evaluate(b.outputOf("sink"), Float.class);

        assertEq(helper, "5 counted " + WIDE + " times", 5f * WIDE, orNaN(out), 1e-3f);
        assertEq(helper, "the shared source ran once", 1, trace.evalCount(b.node("shared").getUid()));
        helper.succeed();
    }

    /** A 64-node exec chain runs every node exactly once, in order. */
    public static void deepExecChain(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.addMany("step", NoopNode.class, DEEP);
        b.wire("step0.in", "entry");
        for (int i = 1; i < DEEP; i++) b.wire("step" + i + ".in", "step" + (i - 1) + ".out");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        var session = new ExecSession(exec).begin(b.node("entry"));
        session.runToCompletion();

        assertEq(helper, "entry plus every step", DEEP + 1, session.stepCount());
        assertEq(helper, "each Noop ran once", DEEP, trace.countByLabel("NoopNode"));
        helper.succeed();
    }

    /**
     * A deep chain whose tip decides a branch: the whole depth is pulled to evaluate the condition,
     * and only the taken side runs. Depth and control flow interleaved, which neither shape alone
     * exercises.
     */
    public static void deepChainDrivingABranch(GameTestHelper helper) {
        var b = chainOfAdds(DEEP);
        b.add("gt", GreaterThanNode.class)
                .wire("gt.a", "n" + (DEEP - 1)).constant("gt.b", (float) DEEP - 1f);
        b.add("entry", EntryNode.class);
        b.add("branch", BranchNode.class).wire("branch.cond", "gt");
        b.add("taken", NoopNode.class);
        b.add("untaken", NoopNode.class);
        b.wire("branch.in", "entry");
        b.wire("taken.in", "branch.trueExec");
        b.wire("untaken.in", "branch.falseExec");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        exec.executeFrom(b.node("entry"));

        assertEq(helper, "the whole chain was pulled for the condition", DEEP, trace.countByLabel("AddNode"));
        assertEq(helper, "taken side ran", 1, trace.execCount(b.node("taken").getUid()));
        assertEq(helper, "untaken side did not", 0, trace.execCount(b.node("untaken").getUid()));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** {@code n0 = 0 + 1}, {@code n[i] = n[i-1] + 1} — value at the tip is the chain length. */
    private static KGGraphBuilder chainOfAdds(int length) {
        var b = KGGraphBuilder.blueprint();
        b.addMany("n", AddNode.class, length);
        b.constant("n0.in1", 0f);
        for (int i = 0; i < length; i++) {
            b.constant("n" + i + ".in2", 1f);
            if (i > 0) b.wire("n" + i + ".in1", "n" + (i - 1));
        }
        return b;
    }

    private static float orNaN(Float v) {
        return v == null ? Float.NaN : v;
    }
}
