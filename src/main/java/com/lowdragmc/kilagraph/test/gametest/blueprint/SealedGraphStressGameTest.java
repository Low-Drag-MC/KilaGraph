package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.PreparedGraph;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Many threads, one sealed {@link PreparedGraph} per asset, across every graph shape in the fixtures.
 *
 * <h2>What this adds over {@code SealedGraphConcurrencyGameTest}</h2>
 * That one proves the seal refuses admission, on one shape. This one asks whether concurrent
 * execution is actually <em>usable</em>: whether every kind of graph the library can build agrees
 * with its own serial answer under load, and whether it goes faster at all.
 *
 * <p>The shapes are not decoration. Each exercises a different piece of shared state:</p>
 * <ul>
 *   <li>the chains — the recursive data pull and the value-slot table;</li>
 *   <li>{@code locomotion}, {@code branch-ladder} — the exec VM and its frame stack;</li>
 *   <li>{@code accumulating-loop}, {@code variable-ping-pong} — loop controllers and the variable
 *       store, which must be per-executor or every thread scribbles on every other;</li>
 *   <li><b>{@code subgraph-calls}</b> — the one that matters most. A call runs on a <em>child</em>
 *       executor over the callee's own prepared graph, shared between siblings exactly as the outer
 *       one is. It is what proves {@code seal()} covers the call tree and not just the graph the host
 *       happens to be holding;</li>
 *   <li>{@code mixed-workload} — all of it at once, and the shape the scaling number is taken on.</li>
 * </ul>
 *
 * <p>Every executor gets the <em>same seed</em>, so a graph containing {@code Random} still has one
 * right answer and the comparison stays exact rather than approximate.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class SealedGraphStressGameTest {

    private SealedGraphStressGameTest() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int THREADS = 16;
    private static final int STRESS_ITERATIONS = 1000;
    private static final int SCALE_ITERATIONS = 400;
    private static final long SEED = 20260828L;
    /** Generous on purpose: the work is milliseconds, so anything near this is a hang. */
    private static final int JOIN_TIMEOUT_SECONDS = 60;

    private static GraphExecutor frozenExecutor(KGGraphBuilder b) {
        var exec = new GraphExecutor(b.graph(),
                new EvaluationEnvironment(new VariableStore(), OptionalLong.of(SEED)));
        exec.setGraphFrozen(true);
        return exec;
    }

    /**
     * One shape to hammer, plus how to run it and how to read its answer.
     *
     * <p>The answer is the whole variable store for an exec-driven graph and the tip value for a
     * data-driven one — deliberately not each fixture's own output variable, so adding a shape needs
     * no knowledge of what it computes.</p>
     */
    private record Shape(String name, Supplier<KGGraphBuilder> build, @Nullable String tip,
                         boolean callsSubgraphs) {
        Shape(String name, Supplier<KGGraphBuilder> build, @Nullable String tip) {
            this(name, build, tip, false);
        }

        String run(KGGraphBuilder b, GraphExecutor exec) {
            exec.clearCache();
            if (tip != null) return String.valueOf(exec.evaluate(b.outputOf(tip), Object.class));
            exec.executeFrom(b.node("entry"));
            return String.valueOf(new TreeMap<>(exec.getEnvironment().variables().snapshot()));
        }
    }

    private static List<Shape> shapes() {
        return List.of(
                new Shape("chain-of-adds-32", () -> KGGraphFixtures.chainOfAdds(32), "n31.out"),
                new Shape("monomorphic-32", () -> KGGraphFixtures.monomorphicChain(32), "u31.out"),
                new Shape("polymorphic-32", () -> KGGraphFixtures.polymorphicChain(32), "u31.out"),
                new Shape("locomotion", KGGraphFixtures::locomotion, null),
                new Shape("accumulating-loop-64", () -> KGGraphFixtures.accumulatingLoop(64), null),
                new Shape("branch-ladder-16", () -> KGGraphFixtures.branchLadder(16), null),
                new Shape("variable-ping-pong-16", () -> KGGraphFixtures.variablePingPong(16), null),
                new Shape("subgraph-calls-8", () -> KGGraphFixtures.subgraphCalls(8), null, true),
                new Shape("mixed-workload", KGGraphFixtures::mixedWorkload, null, true));
    }

    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void everyShapeAgreesUnderConcurrency(GameTestHelper helper) throws Exception {
        List<Shape> shapes = shapes();
        for (Shape shape : shapes) {
            String failure = hammer(shape);
            if (failure != null) {
                helper.fail(failure);
                return;
            }
        }
        LOGGER.info("[KGStress] {} shapes x {} threads x {} iterations agreed with serial",
                shapes.size(), THREADS, STRESS_ITERATIONS);
        helper.succeed();
    }

    /** @return the first disagreement, or null. */
    @Nullable
    private static String hammer(Shape shape) throws Exception {
        // The prepared graph lives in a shared cache keyed by the model, so leaving it sealed on a
        // failure path would make every later executor over it answer null for an unknown node. Every
        // exit after seal() goes through the finally below.

        var b = shape.build().get();

        var executors = new GraphExecutor[THREADS];
        for (int i = 0; i < THREADS; i++) {
            executors[i] = frozenExecutor(b);
            shape.run(b, executors[i]);              // warm up, on this thread — see PreparedGraph
        }
        PreparedGraph prepared = executors[0].preparedGraph();
        if (prepared == null) return shape.name() + ": warm-up resolved no prepared graph";
        for (int i = 1; i < THREADS; i++) {
            if (executors[i].preparedGraph() != prepared) {
                return shape.name() + ": executor " + i + " resolved a different PreparedGraph, so "
                        + "this shape is not actually sharing one";
            }
        }

        // The serial reference: a fresh executor doing exactly the sequence each thread will do.
        var reference = frozenExecutor(b);
        shape.run(b, reference);
        String expected = null;
        for (int k = 0; k < STRESS_ITERATIONS; k++) expected = shape.run(b, reference);

        prepared.seal();
        try {
        // After sealing, not before: admitCount() aggregates over the sealed tree, and before seal()
        // that tree is just this graph. Reading it on the other side of seal() would compare the outer
        // graph's warm-up count against the whole call tree's, and any admission inside a callee
        // during warm-up would then look like the concurrent run having mutated the structure.
        int admittedBefore = prepared.admitCount();
        // A subgraph call runs on a child executor over the callee's own prepared graph, which is
        // shared between siblings just as this one is. Sealing has to reach it, and no result can
        // show whether it did — an inner graph that never admits behaves the same either way. So the
        // cascade is asserted directly.
        int sealedGraphs = prepared.sealedGraphCount();
        if (shape.callsSubgraphs() && sealedGraphs < 2) {
            return shape.name() + ": seal() covered only " + sealedGraphs + " prepared graph(s), but "
                    + "this shape calls subgraphs — the callees were left unsealed and are shared";
        }
        if (!shape.callsSubgraphs() && sealedGraphs != 1) {
            return shape.name() + ": seal() covered " + sealedGraphs + " prepared graphs for a shape "
                    + "with no subgraph calls";
        }

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(THREADS);
        var failure = new AtomicReference<String>();
        var threads = new ArrayList<Thread>(THREADS);
        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            final String want = expected;
            var t = new Thread(() -> {
                try {
                    start.await();
                    String last = null;
                    for (int k = 0; k < STRESS_ITERATIONS; k++) last = shape.run(b, executors[id]);
                    if (!Objects.equals(last, want)) {
                        failure.compareAndSet(null, shape.name() + ": thread " + id + " ended at "
                                + last + ", serial ended at " + want);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, shape.name() + ": thread " + id + " threw " + e);
                } finally {
                    done.countDown();
                }
            }, "kg-stress-" + shape.name() + "-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        start.countDown();
        if (!done.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            // A hang is the failure mode a concurrency bug actually produces, and "it timed out" is
            // not a diagnosis. Say where the threads are.
            var where = new StringBuilder();
            for (Thread t : threads) {
                if (!t.isAlive()) continue;
                where.append("\n  ").append(t.getName()).append(' ').append(t.getState());
                StackTraceElement[] stack = t.getStackTrace();
                for (int i = 0; i < Math.min(8, stack.length); i++) {
                    where.append("\n      at ").append(stack[i]);
                }
            }
            return shape.name() + ": threads did not finish in " + JOIN_TIMEOUT_SECONDS
                    + "s — stuck at:" + where;
        }
        for (Thread t : threads) t.join(2000);

        if (failure.get() != null) return failure.get();
        // The structural assertions carry more weight than the value check: eight threads mutating a
        // shared IdentityHashMap has been observed to leave every computed value correct.
        if (prepared.sealBreached()) {
            return shape.name() + ": a thread was refused a node — the prepared set was incomplete, "
                    + "so this shape cannot be sealed";
        }
        if (prepared.admitCount() != admittedBefore) {
            return shape.name() + ": the structure was mutated during the concurrent run ("
                    + admittedBefore + " -> " + prepared.admitCount() + " admissions)";
        }
        return null;
        } finally {
            prepared.unseal();
        }
    }

    /**
     * Does it actually go faster? Logged, never asserted — a wall-clock assertion is flaky on a busy
     * machine and vacuous on a fast one, as everywhere else in this suite.
     *
     * <p>Same total work either way, measured interleaved and repeated in both orders so the
     * comparison is against itself rather than against a number from another run. A speed-up near 1
     * would mean something on the shared path is serialising and the whole exercise is pointless;
     * producing that finding is the reason this exists, not the timing.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void concurrentExecutionActuallyScales(GameTestHelper helper) throws Exception {
        var b = KGGraphFixtures.mixedWorkload();
        int total = THREADS * SCALE_ITERATIONS;

        var serial = frozenExecutor(b);
        serial.executeFrom(b.node("entry"));
        var parallel = new GraphExecutor[THREADS];
        for (int i = 0; i < THREADS; i++) {
            parallel[i] = frozenExecutor(b);
            parallel[i].executeFrom(b.node("entry"));
        }
        PreparedGraph prepared = serial.preparedGraph();
        if (prepared == null) {
            helper.fail("warm-up resolved no prepared graph");
            return;
        }
        prepared.seal();
        int admittedBefore = prepared.admitCount();   // after sealing — see hammer()
        try {
        double bestSerial = Double.MAX_VALUE;
        double bestParallel = Double.MAX_VALUE;
        for (int rep = 0; rep < 3; rep++) {
            bestSerial = Math.min(bestSerial, timeSerial(b, serial, total));
            bestParallel = Math.min(bestParallel, timeParallel(b, parallel));
            bestParallel = Math.min(bestParallel, timeParallel(b, parallel));
            bestSerial = Math.min(bestSerial, timeSerial(b, serial, total));
        }

        LOGGER.info("[KGBench] sealed-graph scaling: {} runs of mixed-workload — 1 thread {} ms, "
                        + "{} threads {} ms, speed-up x{} ({} cores)",
                total, String.format("%.1f", bestSerial / 1e6), THREADS,
                String.format("%.1f", bestParallel / 1e6),
                String.format("%.2f", bestSerial / bestParallel),
                Runtime.getRuntime().availableProcessors());

        if (prepared.sealBreached() || prepared.admitCount() != admittedBefore) {
            helper.fail("the scaling run mutated the prepared graph");
            return;
        }
        helper.succeed();
        } finally {
            prepared.unseal();
        }
    }

    private static double timeSerial(KGGraphBuilder b, GraphExecutor exec, int iterations) {
        var entry = b.node("entry");
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            exec.clearCache();
            exec.executeFrom(entry);
        }
        return System.nanoTime() - t0;
    }

    private static double timeParallel(KGGraphBuilder b, GraphExecutor[] execs) throws Exception {
        var entry = b.node("entry");
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(execs.length);
        var threads = new ArrayList<Thread>(execs.length);
        for (GraphExecutor exec : execs) {
            var t = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < SCALE_ITERATIONS; i++) {
                        exec.clearCache();
                        exec.executeFrom(entry);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "kg-scale");
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - t0;
        for (Thread t : threads) t.join(2000);
        return elapsed;
    }
}
