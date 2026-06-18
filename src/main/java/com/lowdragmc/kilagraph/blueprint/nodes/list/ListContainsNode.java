package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;
import java.util.Objects;

@NodeAttribute(name = "list_contains", group = "list", graphTypes = BlueprintGraph.class)
public class ListContainsNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public boolean out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        List<?> src = ctx.getInput("list", List.class, List.of());
        Object target = ctx.getInput("value").orElse(null);
        boolean found = false;
        for (Object o : src) if (Objects.equals(o, target)) { found = true; break; }
        ctx.setOutput("out", found);
    }
}
