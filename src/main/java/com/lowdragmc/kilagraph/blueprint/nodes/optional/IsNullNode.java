package com.lowdragmc.kilagraph.blueprint.nodes.optional;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "optional_is_null", group = "optional", graphTypes = BlueprintGraph.class)
public class IsNullNode extends AnnotatedNode {

    @OutputPort public boolean out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("in").orElse(null) == null);
    }
}
