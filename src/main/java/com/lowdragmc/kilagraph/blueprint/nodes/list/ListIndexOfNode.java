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

@NodeAttribute(name = "list_index_of", group = "list", graphTypes = BlueprintGraph.class)
public class ListIndexOfNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public int out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        List<?> src = ctx.getInput("list", List.class, List.of());
        Object target = ctx.getInputRaw("value");
        for (int i = 0; i < src.size(); i++) {
            if (Objects.equals(src.get(i), target)) { ctx.setOutput("out", i); return; }
        }
        ctx.setOutput("out", -1);
    }
}
