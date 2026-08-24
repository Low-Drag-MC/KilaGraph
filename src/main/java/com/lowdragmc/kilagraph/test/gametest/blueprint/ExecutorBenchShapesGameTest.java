package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGBench;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import com.mojang.logging.LogUtils;
import java.util.OptionalLong;
import net.minecraft.gametest.framework.GameTestHelper;
import org.slf4j.Logger;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * The shapes {@link ExecutorBenchGameTest} does not cover: heterogeneous dispatch, exec flow,
 * branching, loops, subgraph calls, variable traffic, wide nodes, vectors, and one realistic mixed
 * graph.
 *
 * <p>They were written because three of the planned optimisations — the exec VM, loop iteration
 * state, and the subgraph call path — were <em>unmeasurable</em> without them: no benchmark touched
 * those paths, so a change there could have been a large win or a large regression and the numbers
 * would have looked identical either way.</p>
 *
 * <h2>What {@link #dispatchCost} can and cannot tell you</h2>
 * It compares a chain of one node class against a chain of eight, same length and arity and wiring.
 * The intent was to isolate the cost of {@code evaluateNode}'s call site going megamorphic.
 *
 * <p><b>It cannot establish that, and the reason is worth writing down.</b>
 * {@code n.evaluable.evaluate(ctx)} is a single bytecode location shared by every graph in the
 * process. By the time any benchmark runs, several hundred other tests have driven a hundred-odd
 * node classes through it, so its profile is already polluted and the "one class" chain is not
 * monomorphic in any sense the JIT cares about. A near-zero delta here therefore does not mean
 * dispatch is cheap; it means both sides are paying for it equally, which is also what a real game
 * with a real node library looks like.</p>
 *
 * <p>So this shape is kept for what it does measure — the absolute per-node-step cost of the
 * simplest possible node, which is the number an intrinsic has to beat — and the decision about
 * bypassing {@code evaluate} needs direct evidence instead: implement one intrinsic behind a switch
 * and measure the same chain with it on and off. That is a measurement of the actual change rather
 * than a proxy for it.</p>
 *
 * <p>({@code lerpChain16} would have been a worse control still: {@code Lerp} takes three inputs and
 * {@code Abs} takes one, so it confounds arity with polymorphism.)</p>
 *
 * <p>As everywhere in this suite, <b>timing is logged and never asserted</b> — a wall-clock
 * assertion is flaky on a busy machine and vacuous on a fast one. Each shape asserts that it
 * computed the value it is supposed to, so a benchmark cannot get faster by doing less.</p>
 */
public final class ExecutorBenchShapesGameTest {
    private static final String DISPATCH_COST = "executor_bench_shapes_dispatch_cost";
    private static final String OPTION_READ_COST = "executor_bench_shapes_option_read_cost";
    private static final String EXEC_STEP_LOOKUP_COST = "executor_bench_shapes_exec_step_lookup_cost";
    private static final String INTRINSICS_COST = "executor_bench_shapes_intrinsics_cost";
    private static final String EXEC_INTRINSICS_COST = "executor_bench_shapes_exec_intrinsics_cost";
    private static final String EXEC_CHAIN32 = "executor_bench_shapes_exec_chain32";
    private static final String BRANCH_LADDER16 = "executor_bench_shapes_branch_ladder16";
    private static final String VAR_PING_PONG16 = "executor_bench_shapes_var_ping_pong16";
    private static final String WIDE_ADD32 = "executor_bench_shapes_wide_add32";
    private static final String VECTOR_CHAIN16 = "executor_bench_shapes_vector_chain16";
    private static final String FOR_LOOP1024 = "executor_bench_shapes_for_loop1024";
    private static final String SUBGRAPH_CALLS = "executor_bench_shapes_subgraph_calls";
    private static final String MIXED_WORKLOAD = "executor_bench_shapes_mixed_workload";

    private ExecutorBenchShapesGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(DISPATCH_COST, ExecutorBenchShapesGameTest::dispatchCost);
        KGGameTests.registerFunction(OPTION_READ_COST, ExecutorBenchShapesGameTest::optionReadCost);
        KGGameTests.registerFunction(EXEC_STEP_LOOKUP_COST, ExecutorBenchShapesGameTest::execStepLookupCost);
        KGGameTests.registerFunction(INTRINSICS_COST, ExecutorBenchShapesGameTest::intrinsicsCost);
        KGGameTests.registerFunction(EXEC_INTRINSICS_COST, ExecutorBenchShapesGameTest::execIntrinsicsCost);
        KGGameTests.registerFunction(EXEC_CHAIN32, ExecutorBenchShapesGameTest::execChain32);
        KGGameTests.registerFunction(BRANCH_LADDER16, ExecutorBenchShapesGameTest::branchLadder16);
        KGGameTests.registerFunction(VAR_PING_PONG16, ExecutorBenchShapesGameTest::varPingPong16);
        KGGameTests.registerFunction(WIDE_ADD32, ExecutorBenchShapesGameTest::wideAdd32);
        KGGameTests.registerFunction(VECTOR_CHAIN16, ExecutorBenchShapesGameTest::vectorChain16);
        KGGameTests.registerFunction(FOR_LOOP1024, ExecutorBenchShapesGameTest::forLoop1024);
        KGGameTests.registerFunction(SUBGRAPH_CALLS, ExecutorBenchShapesGameTest::subgraphCalls);
        KGGameTests.registerFunction(MIXED_WORKLOAD, ExecutorBenchShapesGameTest::mixedWorkload);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        KGGameTests.registerFunctionTest(event, DISPATCH_COST, KGGameTests.functionKey(DISPATCH_COST),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, OPTION_READ_COST, KGGameTests.functionKey(OPTION_READ_COST),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, EXEC_STEP_LOOKUP_COST, KGGameTests.functionKey(EXEC_STEP_LOOKUP_COST),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, INTRINSICS_COST, KGGameTests.functionKey(INTRINSICS_COST),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, EXEC_INTRINSICS_COST, KGGameTests.functionKey(EXEC_INTRINSICS_COST),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, EXEC_CHAIN32, KGGameTests.functionKey(EXEC_CHAIN32),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, BRANCH_LADDER16, KGGameTests.functionKey(BRANCH_LADDER16),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, VAR_PING_PONG16, KGGameTests.functionKey(VAR_PING_PONG16),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, WIDE_ADD32, KGGameTests.functionKey(WIDE_ADD32),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, VECTOR_CHAIN16, KGGameTests.functionKey(VECTOR_CHAIN16),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, FOR_LOOP1024, KGGameTests.functionKey(FOR_LOOP1024),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, SUBGRAPH_CALLS, KGGameTests.functionKey(SUBGRAPH_CALLS),
                KGGameTests.defaultTestData(environment, "empty", 6000));
        KGGameTests.registerFunctionTest(event, MIXED_WORKLOAD, KGGameTests.functionKey(MIXED_WORKLOAD),
                KGGameTests.defaultTestData(environment, "empty", 6000));
    }

    private static final int CHAIN = 16;

    // ---- the decision gate -------------------------------------------------------------------

    /**
     * Monomorphic versus megamorphic dispatch, everything else held fixed. See the class javadoc.
     */
    public static void dispatchCost(GameTestHelper helper) {
        var mono = KGGraphFixtures.monomorphicChain(CHAIN);
        var poly = KGGraphFixtures.polymorphicChain(CHAIN);

        var monoExec = new GraphExecutor(mono.graph());
        var polyExec = new GraphExecutor(poly.graph());
        monoExec.setGraphFrozen(true);
        polyExec.setGraphFrozen(true);

        // Both chains fix 0, so a chain that stopped computing is visible as a non-zero result.
        assertEq(helper, "monomorphic chain value", 0f, f(monoExec.evaluate(mono.outputOf("u" + (CHAIN - 1)), Float.class)), 1e-5f);
        assertEq(helper, "polymorphic chain value", 0f, f(polyExec.evaluate(poly.outputOf("u" + (CHAIN - 1)), Float.class)), 1e-5f);

        var monoOut = mono.outputOf("u" + (CHAIN - 1));
        var polyOut = poly.outputOf("u" + (CHAIN - 1));

        var c = KGBench.comparePaired(
                "abs-chain-16 (1 class)", () -> { monoExec.clearCache(); monoExec.evaluate(monoOut, Float.class); },
                "mixed-chain-16 (8 classes)", () -> { polyExec.clearCache(); polyExec.evaluate(polyOut, Float.class); },
                4_000, 20_000, 3);

        LOGGER.info("[KGBench] dispatch delta: {} ns/node-step over {} nodes — {}",
                String.format("%.2f", c.deltaNsPerRun() / CHAIN), CHAIN,
                c.conclusive()
                        ? "conclusive; a delta near zero means both chains already dispatch "
                          + "megamorphically, which is the expected state in a shared JVM"
                        : "inconclusive: this comparison measured drift, not dispatch");
        helper.succeed();
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * What one option read per node costs, measured against itself.
     *
     * <p>An option read used to go through {@code PortModel.getEmbeddedValue()}, a hash lookup keyed
     * by the port's unique name — paid by {@code Add}, {@code Multiply}, {@code And}, {@code Or},
     * {@code SetVar}, {@code Sequence} and {@code Switch} on every single evaluation. It now goes
     * through the prepared constant table.</p>
     *
     * <p>The comparison is the <b>same graph with the optimisation on and off</b>, interleaved
     * within one measurement, rather than a before-and-after across two runs of the suite. On this
     * machine a before-and-after cannot resolve a change this size: consecutive runs of the
     * unchanged suite disagree by 20–60%. Interleaving cancels that drift, and
     * {@code comparePaired} refuses to report a delta whose sign is not stable across repetitions —
     * so this either produces a number that means something or says it could not.</p>
     */
    public static void optionReadCost(GameTestHelper helper) {
        var b = KGGraphFixtures.optionChain(CHAIN);
        var out = b.outputOf("u" + (CHAIN - 1));

        var on = new GraphExecutor(b.graph());
        on.setGraphFrozen(true);
        var off = new GraphExecutor(b.graph());
        off.setGraphFrozen(true);
        off.setOptimisationEnabled(GraphExecutor.Opt.OPTION_PRERESOLVE, false);

        assertEq(helper, "chain computes with the optimisation on", 0f, f(on.evaluate(out, Float.class)), 1e-5f);
        assertEq(helper, "chain computes with it off", 0f, f(off.evaluate(out, Float.class)), 1e-5f);

        var c = KGBench.comparePaired(
                "round-chain-16 (option preresolved)", () -> { on.clearCache(); on.evaluate(out, Float.class); },
                "round-chain-16 (option via getEmbeddedValue)", () -> { off.clearCache(); off.evaluate(out, Float.class); },
                4_000, 20_000, 3);
        LOGGER.info("[KGBench] option read: {} ns saved per read ({} reads per run) — {}",
                String.format("%.2f", c.deltaNsPerRun() / CHAIN), CHAIN,
                c.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
    }

    /**
     * What the per-step node lookup cost, measured against itself on a 32-node exec chain.
     *
     * <p>{@code executeStep} used to begin by looking its node up in an {@code IdentityHashMap},
     * once per exec step, because the frame queue held models. It now holds prepared nodes.
     * Switching {@link GraphExecutor.Opt#EXEC_PRERESOLVE} off puts that lookup back — see its
     * javadoc for why that is a fair reproduction rather than an approximation.</p>
     */
    public static void execStepLookupCost(GameTestHelper helper) {
        int steps = 32;
        var b = KGGraphFixtures.execChain(steps);
        var entry = b.node("entry");

        var on = new GraphExecutor(b.graph());
        on.setGraphFrozen(true);
        var off = new GraphExecutor(b.graph());
        off.setGraphFrozen(true);
        off.setOptimisationEnabled(GraphExecutor.Opt.EXEC_PRERESOLVE, false);

        var c = KGBench.comparePaired(
                "exec-chain-32 (prepared queue)", () -> { on.clearCache(); on.executeFrom(entry); },
                "exec-chain-32 (lookup per step)", () -> { off.clearCache(); off.executeFrom(entry); },
                2_000, 10_000, 3);
        LOGGER.info("[KGBench] exec step lookup: {} ns per step over {} steps — {}",
                String.format("%.2f", c.deltaNsPerRun() / (steps + 1)), steps + 1,
                c.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
    }

    /**
     * What bypassing {@code evaluate} is worth, measured against itself.
     *
     * <p>This is the measurement {@link #dispatchCost} could not make. Rather than comparing a
     * one-class chain against an eight-class one — a proxy that a shared, already-polluted call site
     * makes meaningless — it runs the same graph with {@link GraphExecutor.Opt#INTRINSICS} on and
     * off. On skips the pooled context bind, the megamorphic call, the id scan per input and the
     * staging round trip; off is the node's own {@code evaluate}, unchanged.</p>
     *
     * <p>Two shapes because they answer different questions: a chain of one class is the best case
     * the JIT can make of the normal path, and {@code locomotion} is a realistic mixture where it
     * cannot.</p>
     */
    public static void intrinsicsCost(GameTestHelper helper) {
        // 1. a pure-data chain of one intrinsified class
        var chain = KGGraphFixtures.monomorphicChain(CHAIN);
        var chainOut = chain.outputOf("u" + (CHAIN - 1));
        var chainOn = frozen(chain.graph(), true);
        var chainOff = frozen(chain.graph(), false);
        assertEq(helper, "chain agrees", f(chainOn.evaluate(chainOut, Float.class)),
                f(chainOff.evaluate(chainOut, Float.class)), 0f);

        var c1 = KGBench.comparePaired(
                "abs-chain-16 (intrinsic)", () -> { chainOn.clearCache(); chainOn.evaluate(chainOut, Float.class); },
                "abs-chain-16 (node evaluate)", () -> { chainOff.clearCache(); chainOff.evaluate(chainOut, Float.class); },
                4_000, 20_000, 3);
        LOGGER.info("[KGBench] intrinsic, 1 class: {} ns/node over {} nodes — {}",
                String.format("%.2f", c1.deltaNsPerRun() / CHAIN), CHAIN,
                c1.conclusive() ? "conclusive" : "inconclusive on this machine");

        // 2. locomotion — every arithmetic node in it has an intrinsic
        var loco = KGGraphFixtures.locomotion();
        var entry = loco.node("entry");
        var onExec = frozen(loco.graph(), true);
        var offExec = frozen(loco.graph(), false);
        var c2 = KGBench.comparePaired(
                "locomotion (intrinsic)", () -> { onExec.clearCache(); onExec.executeFrom(entry); },
                "locomotion (node evaluate)", () -> { offExec.clearCache(); offExec.executeFrom(entry); },
                4_000, 20_000, 3);
        LOGGER.info("[KGBench] intrinsic, locomotion: {} ns/run of {} — {}",
                String.format("%.0f", c2.deltaNsPerRun()),
                String.format("%.0f", c2.bNsPerRun()),
                c2.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
    }

    private static GraphExecutor frozen(BlueprintGraph g, boolean intrinsics) {
        var exec = new GraphExecutor(g);
        exec.setGraphFrozen(true);
        exec.setOptimisationEnabled(GraphExecutor.Opt.INTRINSICS, intrinsics);
        return exec;
    }

    /**
     * What the exec-side changes are worth, each measured against itself on a 32-node exec chain.
     *
     * <p>Two separate switches because they save different things and could easily have been
     * confused for one another: {@code EXEC_INTRINSICS} removes the context bind, the megamorphic
     * {@code execute} call and the staging round trip per node; {@code FUSED_EXEC_DRIVER} removes one
     * of the two stack settles per node in {@code runToCompletion}.</p>
     */
    public static void execIntrinsicsCost(GameTestHelper helper) {
        int steps = 32;
        var b = KGGraphFixtures.execChain(steps);
        var entry = b.node("entry");

        var all = new GraphExecutor(b.graph());
        all.setGraphFrozen(true);
        var noIntrinsics = new GraphExecutor(b.graph());
        noIntrinsics.setGraphFrozen(true);
        noIntrinsics.setOptimisationEnabled(GraphExecutor.Opt.EXEC_INTRINSICS, false);
        var noFusion = new GraphExecutor(b.graph());
        noFusion.setGraphFrozen(true);
        noFusion.setOptimisationEnabled(GraphExecutor.Opt.FUSED_EXEC_DRIVER, false);

        var c1 = KGBench.comparePaired(
                "exec-chain-32 (exec intrinsics)", () -> { all.clearCache(); all.executeFrom(entry); },
                "exec-chain-32 (node execute)", () -> { noIntrinsics.clearCache(); noIntrinsics.executeFrom(entry); },
                2_000, 10_000, 3);
        LOGGER.info("[KGBench] exec intrinsic: {} ns per step over {} — {}",
                String.format("%.2f", c1.deltaNsPerRun() / (steps + 1)), steps + 1,
                c1.conclusive() ? "conclusive" : "inconclusive on this machine");

        var c2 = KGBench.comparePaired(
                "exec-chain-32 (fused driver)", () -> { all.clearCache(); all.executeFrom(entry); },
                "exec-chain-32 (step per node)", () -> { noFusion.clearCache(); noFusion.executeFrom(entry); },
                2_000, 10_000, 3);
        LOGGER.info("[KGBench] fused driver: {} ns per step over {} — {}",
                String.format("%.2f", c2.deltaNsPerRun() / (steps + 1)), steps + 1,
                c2.conclusive() ? "conclusive" : "inconclusive on this machine");
        helper.succeed();
    }

    // ---- cheap shapes ------------------------------------------------------------------------

    /** Straight-line exec flow with no data: the per-step cost of the exec VM. */
    public static void execChain32(GameTestHelper helper) {
        var b = KGGraphFixtures.execChain(32);
        var exec = new GraphExecutor(b.graph());
        exec.setGraphFrozen(true);
        var entry = b.node("entry");

        var r = KGBench.measure("exec-chain-32", 33, 2_000, 10_000, () -> {
            exec.clearCache();
            exec.executeFrom(entry);
        });
        KGBench.reportRow(r);
        helper.succeed();
    }

    /** Branch after branch, alternating sides — the bool round trip plus exec dispatch. */
    public static void branchLadder16(GameTestHelper helper) {
        var b = KGGraphFixtures.branchLadder(CHAIN);
        var exec = new GraphExecutor(b.graph());
        exec.setGraphFrozen(true);
        var entry = b.node("entry");

        exec.executeFrom(entry);
        assertTrue(helper, "the ladder reached its end",
                exec.getEnvironment().variables().get("reachedEnd") instanceof Number);

        var r = KGBench.measure("branch-ladder-16", CHAIN * 2 + 1, 2_000, 10_000, () -> {
            exec.clearCache();
            exec.executeFrom(entry);
        });
        KGBench.reportRow(r);
        helper.succeed();
    }

    /** Sixteen variable read-modify-writes over four variables: the variable store under load. */
    public static void varPingPong16(GameTestHelper helper) {
        var b = KGGraphFixtures.variablePingPong(CHAIN);
        var store = new VariableStore();
        var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.empty()));
        exec.setGraphFrozen(true);
        var entry = b.node("entry");

        exec.executeFrom(entry);
        assertEq(helper, "v0 incremented once per pass over it", 4f, num(store.get("v0")), 1e-5f);

        var r = KGBench.measure("var-ping-pong-16", CHAIN * 3, 2_000, 10_000, () -> {
            exec.clearCache();
            exec.executeFrom(entry);
        });
        KGBench.reportRow(r);
        helper.succeed();
    }

    /** One 32-input {@code Add}: how port width scales, and the judge for the indexed-port question. */
    public static void wideAdd32(GameTestHelper helper) {
        int width = 32;
        var b = KGGraphBuilder.blueprint();
        b.add("wide", AddNode.class).option("wide", "inputs", width);
        float expected = 0f;
        for (int i = 1; i <= width; i++) {
            b.add("s" + i, MultiplyNode.class).constant("s" + i + ".in1", (float) i).constant("s" + i + ".in2", 2f);
            b.wire("wide.in" + i, "s" + i);
            expected += i * 2f;
        }
        var exec = new GraphExecutor(b.graph());
        exec.setGraphFrozen(true);
        var out = b.outputOf("wide");
        assertEq(helper, "wide add value", expected, f(exec.evaluate(out, Float.class)), 1e-2f);

        var r = KGBench.measure("wide-add-32", width + 1, 2_000, 10_000, () -> {
            exec.clearCache();
            exec.evaluate(out, Float.class);
        });
        KGBench.reportRow(r);
        helper.succeed();
    }

    /**
     * A vector chain — the one shape whose allocation figure is <b>not</b> a property of the code.
     *
     * <p>{@code VectorNodes.zip} allocates four objects per evaluation: a {@code float[]} for each
     * operand's components, one for the result, and the carrier vector. Sixteen nodes of that is
     * about 1 960 bytes, and that is what this reports.</p>
     *
     * <p>It reported 424 for most of this work — 26 bytes a node, which is less than one
     * {@code Vector3f}, let alone four objects. At that point escape analysis was scalar-replacing
     * the temporaries. Which of the two numbers appears depends on what the JIT decided to inline,
     * and it moved without any of the three code changes suspected of causing it (the intrinsic
     * dispatch, its benchmark, and this method's own second measurement were each ruled out by
     * removing them and re-measuring).</p>
     *
     * <p>So treat this row as a rough figure, unlike the rest of the suite. Every allocation this
     * work actually removed — boxing into the variable store, map entries, {@code uid.toString()},
     * per-call child executors — <em>escapes</em> into something long-lived and cannot be scalar
     * replaced, which is why those figures held to the byte across dozens of runs.</p>
     */
    public static void vectorChain16(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("base", VectorNodes.Make.class)
                .constant("base.x", 1f).constant("base.y", 2f).constant("base.z", 3f);
        for (int i = 0; i < CHAIN; i++) {
            b.add("v" + i, VectorNodes.Add.class)
                    .wire("v" + i + ".a", i == 0 ? "base" : "v" + (i - 1))
                    .wire("v" + i + ".b", "base");
        }
        b.add("len", VectorNodes.Length.class).wire("len.in", "v" + (CHAIN - 1));

        var exec = new GraphExecutor(b.graph());
        exec.setGraphFrozen(true);
        var out = b.outputOf("len");
        // base added CHAIN+1 times over: (17,34,51), |v| = 17*sqrt(14)
        float expected = (float) (17.0 * Math.sqrt(14.0));
        assertEq(helper, "vector chain length", expected, f(exec.evaluate(out, Float.class)), 1e-2f);

        var r = KGBench.measure("vector-chain-16", CHAIN + 2, 2_000, 10_000, () -> {
            exec.clearCache();
            exec.evaluate(out, Float.class);
        });
        KGBench.reportRow(r);

        helper.succeed();
    }

    // ---- expensive shapes --------------------------------------------------------------------

    /** Loop iteration cost: 1024 iterations of a two-node body carrying an accumulator. */
    public static void forLoop1024(GameTestHelper helper) {
        int n = 1024;
        var b = KGGraphFixtures.accumulatingLoop(n);
        var store = new VariableStore();
        var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.empty()));
        exec.setGraphFrozen(true);
        var entry = b.node("entry");

        exec.executeFrom(entry);
        assertEq(helper, "sum of 0..1023", (float) (n * (n - 1) / 2), num(store.get("acc")), 1f);

        var r = KGBench.measure("for-loop-1024", n * 3, 40, 200, () -> {
            store.put("acc", 0f);
            exec.clearCache();
            exec.executeFrom(entry);
        });
        KGBench.reportRow(r);
        helper.succeed();
    }

    /** Subgraph call cost — expected to be the worst number in the suite. */
    public static void subgraphCalls(GameTestHelper helper) {
        for (int calls : new int[]{1, 16}) {
            var b = KGGraphFixtures.subgraphCalls(calls);
            var store = new VariableStore();
            var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.empty()));
            exec.setGraphFrozen(true);
            var entry = b.node("entry");

            exec.executeFrom(entry);
            // f(x) = 2x + 1 applied `calls` times starting from 1.
            float expected = 1f;
            for (int i = 0; i < calls; i++) expected = expected * 2f + 1f;
            assertEq(helper, calls + " chained calls", expected, num(store.get("result")), 1e-2f);

            var r = KGBench.measure(String.format("subgraph-calls x%-3d", calls), calls * 4, 200, 1_000, () -> {
                exec.clearCache();
                exec.executeFrom(entry);
            });
            KGBench.reportRow(r);
        }
        helper.succeed();
    }

    /**
     * The mixed workload — the same graph {@code MixedWorkloadGameTest} asserts the values of, so
     * the shape being measured is a shape something proves is still correct.
     */
    public static void mixedWorkload(GameTestHelper helper) {
        var b = KGGraphFixtures.mixedWorkload();
        var exec = new GraphExecutor(b.graph());
        var entry = b.node("entry");

        exec.executeFrom(entry);
        assertEq(helper, "mixed workload total", 40f,
                num(exec.getEnvironment().variables().get("total")), 1e-4f);

        var checked = KGBench.measure("mixed-workload (checked)", 44, 500, 2_000, () -> {
            exec.getEnvironment().variables().put("total", 0f);
            exec.getEnvironment().variables().put("peak", 0f);
            exec.clearCache();
            exec.executeFrom(entry);
        });
        exec.setGraphFrozen(true);
        var frozen = KGBench.measure("mixed-workload (frozen)", 44, 500, 2_000, () -> {
            exec.getEnvironment().variables().put("total", 0f);
            exec.getEnvironment().variables().put("peak", 0f);
            exec.clearCache();
            exec.executeFrom(entry);
        });

        KGBench.report("mixed workload", checked, frozen);
        KGBench.reportDigestCost("mixed-workload", checked, frozen);
        KGBench.reportRow(frozen);
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static float f(Float v) {
        return v == null ? Float.NaN : v;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
