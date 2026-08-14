package com.lowdragmc.kilagraph.test.gametest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;

/**
 * Headless micro-benchmark harness for the graph executor. Deliberately tiny and dependency-free:
 * it must run under {@code runGameTestServer} (no display, no client) because the real-client
 * bench occasionally dies on {@code glfwGetPrimaryMonitor failed} and takes minutes.
 *
 * <p><b>It never asserts wall-clock time.</b> Timing assertions are flaky on a busy machine and
 * meaningless on a fast one. The GameTests that use this assert only that the graph really
 * computed something; the numbers are <em>logged</em> for a human to compare before/after.</p>
 *
 * <p>Two quantities are reported, and the second is the one that matters for this work:</p>
 * <ul>
 *   <li><b>ns/run</b> and <b>ns/node-step</b> — the portable cost measure. Absolute milliseconds
 *       are machine-specific; cost per node-step and the before/after ratio are not.</li>
 *   <li><b>bytes allocated per run</b> — measured with {@code ThreadMXBean.getThreadAllocatedBytes}.
 *       "Zero heap allocation per run" is the actual goal, so this is the primary number.</li>
 * </ul>
 */
public final class KGBench {
    private static final Logger LOGGER = LogUtils.getLogger();

    private KGBench() {}

    /** Timed passes per measurement; the fastest one is reported. See {@link #measure}. */
    private static final int TIMED_PASSES = 5;

    /**
     * One measurement: timing plus allocation.
     *
     * <p>Allocation is carried as the <em>total</em> over {@link #allocRuns} rather than a
     * pre-divided per-run figure. Dividing first was an integer division by a few thousand, so any
     * allocation rarer than one byte per run printed as exactly {@code 0} — which is precisely the
     * number this harness exists to report, and precisely the one that must not be flattering.</p>
     */
    public record Result(String label, int nodeSteps, int runs, long totalNanos,
                         long totalAllocBytes, int allocRuns) {
        public double nsPerRun() {
            return (double) totalNanos / runs;
        }

        /** Bytes allocated per run, or {@code -1} if the JVM exposes no per-thread counter. */
        public double bytesPerRun() {
            return totalAllocBytes < 0 ? -1 : (double) totalAllocBytes / allocRuns;
        }

        public double nsPerNodeStep() {
            return nodeSteps <= 0 ? Double.NaN : nsPerRun() / nodeSteps;
        }

        /**
         * What this run rate costs at EntityStudio's bench scale — 1510 entities at 60 fps — so the
         * number is directly comparable to the 0.288-0.309 ms/frame that ES's own compiled Event
         * Graph costs there (doc 18 §7.2/§8).
         */
        public double msPerFrameAt1510() {
            return nsPerRun() * 1510 / 1_000_000.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "%-28s %8.0f ns/run  %7.1f ns/node-step  %9.2f B/run  (%d nodes x %d runs)  => %.3f ms/frame @1510",
                    label, nsPerRun(), nsPerNodeStep(), bytesPerRun(), nodeSteps, runs, msPerFrameAt1510());
        }
    }

    /**
     * Warm the JIT, then time {@code body}, then measure its allocation footprint in a separate
     * pass (the allocation counter is cheap but reading it inside the timed loop would pollute it).
     *
     * @param nodeSteps how many node evaluations one call of {@code body} performs — used to derive
     *                  the portable ns/node-step figure
     */
    public static Result measure(String label, int nodeSteps, int warmupRuns, int timedRuns, Runnable body) {
        long builds0 = com.lowdragmc.kilagraph.graph.exec.PreparedGraph.buildCount();
        for (int i = 0; i < warmupRuns; i++) body.run();

        // Give the JIT's OSR/compile queue a moment to settle before the timed pass.
        for (int i = 0; i < warmupRuns / 4; i++) body.run();

        // Best of several passes. This machine also runs the build and other agents, and a single
        // pass swung 35% between runs — which is enough to invent a regression that isn't there.
        // The minimum is the pass least disturbed by other load, so it is the honest comparison.
        long elapsed = Long.MAX_VALUE;
        for (int pass = 0; pass < TIMED_PASSES; pass++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < timedRuns; i++) body.run();
            elapsed = Math.min(elapsed, System.nanoTime() - t0);
        }

        int allocRuns = Math.max(1, timedRuns / 10);
        long totalAlloc = measureAllocation(body, allocRuns);

        Result r = new Result(label, nodeSteps, timedRuns, elapsed, totalAlloc, allocRuns);
        LOGGER.info("[KGBench] {}", r);
        LOGGER.info("[KGBench]   prepare: {} rebuilds over {} warmup runs",
                com.lowdragmc.kilagraph.graph.exec.PreparedGraph.buildCount() - builds0, warmupRuns);
        return r;
    }

    /**
     * Bytes allocated on this thread across {@code runs} calls of {@code body}, or {@code -1} if the
     * JVM does not expose per-thread allocation counters. The harness's own loop allocates nothing,
     * so the figure is the executor's plus the nodes' — which is exactly what "zero allocation per
     * run" is about. Returned as a total so the caller can divide in floating point.
     */
    private static long measureAllocation(Runnable body, int runs) {
        com.sun.management.ThreadMXBean tmx;
        try {
            if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean)
                    || !bean.isThreadAllocatedMemorySupported()) {
                return -1L;
            }
            tmx = bean;
            tmx.setThreadAllocatedMemoryEnabled(true);
        } catch (Throwable t) {
            return -1L;
        }
        long id = Thread.currentThread().threadId();
        // Prime: the first getThreadAllocatedBytes call itself allocates.
        tmx.getThreadAllocatedBytes(id);
        body.run();
        long before = tmx.getThreadAllocatedBytes(id);
        for (int i = 0; i < runs; i++) body.run();
        long after = tmx.getThreadAllocatedBytes(id);
        long delta = after - before;
        return delta < 0 ? -1L : delta;
    }

    /** Log a labelled block of results together so before/after diffs are readable in one place. */
    public static void report(String title, Result... results) {
        LOGGER.info("[KGBench] ===== {} =====", title);
        for (Result r : results) LOGGER.info("[KGBench] {}", r);
        LOGGER.info("[KGBench] ===== end {} =====", title);
    }
}
