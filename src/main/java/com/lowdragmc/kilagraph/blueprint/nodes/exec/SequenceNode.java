package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Fires {@code out1..outN} in order on every exec tick. The queue handles the downstream traversal.
 */
@NodeAttribute(name = "exec_sequence", group = "exec", graphTypes = BlueprintGraph.class)
public class SequenceNode extends AnnotatedNode {

    @Option public int outputs = 2;
    @ExecInputPort public ExecutionFlow in;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("outputs", Integer.class, outputs));
        for (int i = 1; i <= n; i++) ctx.addOutputPort("out" + i, TypeHandles.EXECUTION_FLOW);
    }

    @Override
    public void execute(ExecContext ctx) {
        int n = Math.max(1, ctx.getOption("outputs", Integer.class, outputs));
        for (int i = 1; i <= n; i++) ctx.flow("out" + i);
    }
}
