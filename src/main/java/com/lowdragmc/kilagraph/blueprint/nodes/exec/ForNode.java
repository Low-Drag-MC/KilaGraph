package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.BreakException;
import com.lowdragmc.kilagraph.graph.exec.ContinueException;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Counted loop. Runs {@code body} {@code count} times; on each iteration {@code index} (data
 * output) is set to the current index 0..count-1. After the loop, fires {@code completed}.
 */
@NodeAttribute(name = "exec_for", group = "exec", graphTypes = BlueprintGraph.class)
public class ForNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @InputPort public int count = 0;
    @ExecOutputPort public ExecutionFlow body;
    @ExecOutputPort public ExecutionFlow completed;
    @OutputPort public int index;

    @Override
    public void execute(ExecContext ctx) {
        int n = Math.max(0, ctx.getInput("count", Integer.class, 0));
        var indexPort = ctx.getNode().getOutputsById().get("index");
        for (int i = 0; i < n; i++) {
            // The pull cache holds last iteration's values — invalidate so body recomputes.
            ctx.getExecutor().clearCache();
            // Stash this iteration's index eagerly so body nodes can pull it immediately.
            if (indexPort != null) ctx.getExecutor().putCache(indexPort, i);
            try {
                ctx.runLoopBody(() -> ctx.flow("body"));
            } catch (ContinueException ignored) {
                // skip to next iteration
            } catch (BreakException ignored) {
                break;
            }
        }
        ctx.flow("completed");
    }
}
