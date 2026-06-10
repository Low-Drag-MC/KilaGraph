package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.BreakException;
import com.lowdragmc.kilagraph.graph.exec.ContinueException;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Re-pulls {@code cond} between iterations. Hard cap of {@code maxIterations} guards against
 * infinite loops (a stuck/forgotten condition shouldn't hang the server).
 */
@NodeAttribute(name = "exec_while", group = "exec", graphTypes = BlueprintGraph.class)
public class WhileNode extends AnnotatedNode {

    @Option public int maxIterations = 10000;
    @ExecInputPort public ExecutionFlow in;
    @InputPort public boolean cond = false;
    @ExecOutputPort public ExecutionFlow body;
    @ExecOutputPort public ExecutionFlow completed;

    @Override
    public void execute(ExecContext ctx) {
        int cap = Math.max(1, ctx.getOption("maxIterations", Integer.class, maxIterations));
        for (int i = 0; i < cap; i++) {
            // Invalidate cache so cond re-pulls — and so the body sees fresh values.
            ctx.getExecutor().clearCache();
            if (!ctx.getInput("cond", Boolean.class, false)) break;
            try {
                ctx.runIsolated(() -> ctx.flow("body"));
            } catch (ContinueException ignored) {
                // skip to next iteration
            } catch (BreakException ignored) {
                break;
            }
        }
        ctx.flow("completed");
    }
}
