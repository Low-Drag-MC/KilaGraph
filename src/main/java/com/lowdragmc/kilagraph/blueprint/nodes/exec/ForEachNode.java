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
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Iterates a List, exposing {@code item} and {@code index} per iteration.
 */
@NodeAttribute(name = "exec_foreach", group = "exec", graphTypes = BlueprintGraph.class)
public class ForEachNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @InputPort public List<?> list = List.of();
    @ExecOutputPort public ExecutionFlow body;
    @ExecOutputPort public ExecutionFlow completed;
    @OutputPort public int index;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addOutputPort("item", TypeHandles.UNKNOWN);
    }

    @Override
    public void execute(ExecContext ctx) {
        // Pull the list once before iterating — clearing the cache between iterations would lose
        // it otherwise.
        List<?> values = ctx.getInput("list", List.class, List.of());
        var indexPort = ctx.getNode().getOutputsById().get("index");
        var itemPort = ctx.getNode().getOutputsById().get("item");
        for (int i = 0; i < values.size(); i++) {
            ctx.getExecutor().clearCache();
            if (indexPort != null) ctx.getExecutor().putCache(indexPort, i);
            if (itemPort != null) ctx.getExecutor().putCache(itemPort, values.get(i));
            try {
                ctx.runLoopBody(() -> ctx.flow("body"));
            } catch (ContinueException ignored) {
                // next iteration
            } catch (BreakException ignored) {
                break;
            }
        }
        ctx.flow("completed");
    }
}
