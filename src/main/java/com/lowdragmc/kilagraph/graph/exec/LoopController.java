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
 * <p>What it must <b>not</b> do is write the index straight into the loop node's output slot: that
 * would make it a published value, which stays for the run, rather than a pull re-read at each step.</p>
 *
 * <p>Nothing is cleared between iterations — a pure read goes stale at every step anyway, and what an
 * exec node published has to stay readable in the body. What the loop itself reads is read again
 * before each iteration, as Unreal's {@code ForLoop}/{@code ForEachLoop} do.</p>
 */
public interface LoopController {

    /** Whether another iteration should run. Called once per iteration decision, after the body's last step. */
    boolean hasNext(GraphExecutor scope);

    /** Set up the iteration that {@link #hasNext} just approved (advance the counter). */
    void beginIteration(GraphExecutor scope);

    /** The current iteration's index, as the loop node's {@code index} output reports it. */
    int index();

    /** The current iteration's element, for a loop that has one. */
    @Nullable
    default Object item() {
        return null;
    }

    /**
     * Counted loop: runs while the index is below {@code count}, publishing {@code index} 0, 1, ….
     * Built on the loop node, it reads its {@code count} again before each iteration; built on a
     * number, it runs that many times.
     */
    final class ForController implements LoopController {
        @Nullable
        private final PreparedGraph.Node node;
        private final int countInput;
        private final int fixedCount;
        private int i = 0;

        public ForController(int count) {
            this.node = null;
            this.countInput = -1;
            this.fixedCount = Math.max(0, count);
        }

        public ForController(PreparedGraph.Node node) {
            this.node = node;
            this.countInput = node.inputIndex("count");
            this.fixedCount = 0;
        }

        @Override
        public boolean hasNext(GraphExecutor scope) {
            int count = node == null ? fixedCount : countInput < 0 ? 0 : scope.pullInt(node, countInput, 0);
            return i < count;
        }

        @Override
        public void beginIteration(GraphExecutor scope) {
            i++;
        }

        /** {@code beginIteration} has already advanced past the iteration now running. */
        @Override
        public int index() { return i - 1; }
    }

    /**
     * Iterates a List, publishing {@code item} and {@code index} per element. Built on the loop node, it
     * reads its {@code list} again before each iteration; built on a list, it walks that one.
     */
    final class ForEachController implements LoopController {
        @Nullable
        private final PreparedGraph.Node node;
        private final int listInput;
        private List<?> values;
        private int idx = 0;

        public ForEachController(@Nullable List<?> values) {
            this.node = null;
            this.listInput = -1;
            this.values = values == null ? List.of() : values;
        }

        public ForEachController(PreparedGraph.Node node) {
            this.node = node;
            this.listInput = node.inputIndex("list");
            this.values = List.of();
        }

        @Override
        public boolean hasNext(GraphExecutor scope) {
            if (node != null) {
                values = listInput < 0 ? List.of()
                        : scope.pullInput(node, listInput, List.class) instanceof List<?> list ? list : List.of();
            }
            return idx < values.size();
        }

        @Override
        public void beginIteration(GraphExecutor scope) {
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

    /** Re-pulls {@code cond} before each iteration; {@code maxIterations} guards runaway loops. */
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
