package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

import java.util.List;

/**
 * Drives a loop's iteration for the {@link LoopFrame} — the engine asks the controller whether to
 * run another body iteration and to set up per-iteration state. This is the control logic that used
 * to live synchronously inside the loop nodes' {@code execute()} (around {@code runIsolated}); moving
 * it here lets the frame engine step through loop bodies one node at a time.
 *
 * <p>Per-iteration {@code nodeState} keys (e.g. {@code "index"}, {@code "item"}) match what the loop
 * nodes' {@code evaluate()} reads to publish their data outputs — that side is unchanged.</p>
 */
public interface LoopController {

    /** Whether another iteration should run. Called once per iteration decision (may have side effects, e.g. While re-pulls cond). */
    boolean hasNext(GraphExecutor scope);

    /** Set up the iteration that {@link #hasNext} just approved (clear cache, publish index/item, advance the counter). */
    void beginIteration(GraphExecutor scope);

    /** Counted loop: runs {@code count} times, publishing {@code index} 0..count-1. */
    final class ForController implements LoopController {
        private final NodeModel node;
        private final int count;
        private int i = 0;

        public ForController(NodeModel node, int count) {
            this.node = node;
            this.count = Math.max(0, count);
        }

        @Override public boolean hasNext(GraphExecutor scope) { return i < count; }

        @Override public void beginIteration(GraphExecutor scope) {
            scope.clearCache();
            scope.nodeState(node.getUid()).put("index", i);
            i++;
        }
    }

    /** Iterates a List, publishing {@code item} and {@code index} per element. */
    final class ForEachController implements LoopController {
        private final NodeModel node;
        private final List<?> values;
        private int idx = 0;

        public ForEachController(NodeModel node, List<?> values) {
            this.node = node;
            this.values = values == null ? List.of() : values;
        }

        @Override public boolean hasNext(GraphExecutor scope) { return idx < values.size(); }

        @Override public void beginIteration(GraphExecutor scope) {
            scope.clearCache();
            var state = scope.nodeState(node.getUid());
            state.put("index", idx);
            state.put("item", values.get(idx));
            idx++;
        }
    }

    /** Re-pulls {@code cond} each iteration (after clearing the cache); {@code maxIterations} guards runaway loops. */
    final class WhileController implements LoopController {
        private final NodeModel node;
        private final int maxIterations;
        private int i = 0;

        public WhileController(NodeModel node, int maxIterations) {
            this.node = node;
            this.maxIterations = Math.max(1, maxIterations);
        }

        @Override public boolean hasNext(GraphExecutor scope) {
            if (i >= maxIterations) return false;
            scope.clearCache();  // re-pull cond (and let the body see fresh values)
            PortModel condPort = node.getInputsById().get("cond");
            if (condPort == null) return false;
            Boolean cond = EvalContext.coerce(scope.pullInput(condPort, Boolean.class), Boolean.class);
            return cond != null && cond;
        }

        @Override public void beginIteration(GraphExecutor scope) { i++; }
    }
}
