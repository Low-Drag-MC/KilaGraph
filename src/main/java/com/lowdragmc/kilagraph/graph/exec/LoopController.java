package com.lowdragmc.kilagraph.graph.exec;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Drives a loop's iteration for the {@link LoopFrame} — the engine asks the controller whether to
 * run another body iteration and to set up per-iteration state. This is the control logic that used
 * to live synchronously inside the loop nodes' {@code execute()} (around {@code runIsolated}); moving
 * it here lets the frame engine step through loop bodies one node at a time.
 *
 * <h2>Where the iteration state lives</h2>
 * The controller holds it, and the loop node's {@code evaluate} reads it back through
 * {@link EvalContext#loopIndex()} / {@link EvalContext#loopItem()}.
 *
 * <p>It used to be published into the executor's per-node state map, which cost a {@code UUID} hash,
 * a {@code String} hash and an {@code Integer} box <em>per iteration</em> — and the same two hashes
 * again on every read. Holding it on the controller costs nothing per iteration and keeps the index
 * in the numeric lane on the way out.</p>
 *
 * <p>What it must <b>not</b> do is write the index straight into the loop node's output slot. The
 * engine clears the value cache between iterations, and a nested loop's clear would take an enclosing
 * loop's live index with it. The output is republished on demand instead, so a cleared slot is
 * recomputed from the controller rather than lost.</p>
 */
public interface LoopController {

    /** Whether another iteration should run. Called once per iteration decision (may have side effects, e.g. While re-pulls cond). */
    boolean hasNext(GraphExecutor scope);

    /** Set up the iteration that {@link #hasNext} just approved (clear cache, advance the counter). */
    void beginIteration(GraphExecutor scope);

    /** The current iteration's index, as the loop node's {@code index} output reports it. */
    int index();

    /** The current iteration's element, for a loop that has one. */
    @Nullable
    default Object item() {
        return null;
    }

    /** Counted loop: runs {@code count} times, publishing {@code index} 0..count-1. */
    final class ForController implements LoopController {
        private final int count;
        private int i = 0;

        public ForController(int count) {
            this.count = Math.max(0, count);
        }

        @Override
        public boolean hasNext(GraphExecutor scope) { return i < count; }

        @Override
        public void beginIteration(GraphExecutor scope) {
            scope.clearCache();
            i++;
        }

        /** {@code beginIteration} has already advanced past the iteration now running. */
        @Override
        public int index() { return i - 1; }
    }

    /** Iterates a List, publishing {@code item} and {@code index} per element. */
    final class ForEachController implements LoopController {
        private final List<?> values;
        private int idx = 0;

        public ForEachController(@Nullable List<?> values) {
            this.values = values == null ? List.of() : values;
        }

        @Override
        public boolean hasNext(GraphExecutor scope) { return idx < values.size(); }

        @Override
        public void beginIteration(GraphExecutor scope) {
            scope.clearCache();
            idx++;
        }

        @Override
        public int index() { return idx - 1; }

        @Override
        public Object item() {
            int at = idx - 1;
            return at >= 0 && at < values.size() ? values.get(at) : null;
        }
    }

    /** Re-pulls {@code cond} each iteration (after clearing the cache); {@code maxIterations} guards runaway loops. */
    final class WhileController implements LoopController {
        private final PreparedGraph.Node node;
        /** Resolved once — {@code getInputsById().get("cond")} was a hash lookup per iteration. */
        private final int condInput;
        private final int maxIterations;
        private int i = 0;

        public WhileController(PreparedGraph.Node node, int maxIterations) {
            this.node = node;
            this.condInput = node.inputIndex("cond");
            this.maxIterations = Math.max(1, maxIterations);
        }

        @Override
        public boolean hasNext(GraphExecutor scope) {
            if (i >= maxIterations) return false;
            scope.clearCache();  // re-pull cond (and let the body see fresh values)
            if (condInput < 0) return false;
            Boolean cond = EvalContext.coerce(scope.pullInput(node, condInput, Boolean.class), Boolean.class);
            return cond != null && cond;
        }

        @Override
        public void beginIteration(GraphExecutor scope) { i++; }

        @Override
        public int index() { return i - 1; }
    }
}
