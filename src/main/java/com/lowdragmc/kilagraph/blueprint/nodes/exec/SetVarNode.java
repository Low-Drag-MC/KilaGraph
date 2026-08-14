package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Writes {@code value} to the graph variable named {@code varName} (in
 * {@code EvaluationEnvironment.variables()}). The companion to Phase 1's
 * {@code GraphExecutor.runOutputs()} — written values participate in the OUTPUT-variable result
 * map. Reads are still served by Phase 1's {@code IVariableNode} path.
 */
@NodeAttribute(name = "exec_set_var", group = "exec", graphTypes = BlueprintGraph.class)
public class SetVarNode extends AnnotatedNode {

    @Option public String varName = "";
    @ExecInputPort public ExecutionFlow trigger;
    @ExecOutputPort public ExecutionFlow next;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override
    public void execute(ExecContext ctx) {
        String name = ctx.getOption("varName", String.class, "");
        if (!name.isEmpty()) {
            Object value = ctx.getInputRaw("value");
            ctx.getExecutor().getEnvironment().variables().put(name, value);
        }
        ctx.flow("next");
    }
}
