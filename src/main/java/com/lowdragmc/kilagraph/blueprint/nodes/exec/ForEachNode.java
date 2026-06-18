package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.exec.LoopController;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
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
        // Pull the list once before iterating — clearing the cache between iterations would lose it.
        List<?> values = ctx.getInput("list", List.class, List.of());
        // The controller publishes "index"/"item" into node state per iteration (read by evaluate());
        // the engine steps the body and fires "completed" when the list is exhausted.
        ctx.pushLoop(new LoopController.ForEachController((NodeModel) getNodeModel(), values), "body", "completed");
    }

    @Override
    public void evaluate(EvalContext ctx) {
        var state = ctx.getExecutor().nodeState(getNodeModel().getUid());
        Object i = state.get("index");
        ctx.setOutput("index", i == null ? 0 : i);
        ctx.setOutput("item", state.get("item"));
    }
}
