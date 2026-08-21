package com.lowdragmc.kilagraph.blueprint.nodes.map;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.ArrayList;
import java.util.Map;

@NodeAttribute(name = "map_values", group = "map", graphTypes = BlueprintGraph.class)
public class MapValuesNode extends AnnotatedNode {
    @InputPort public Map<?, ?> map = Map.of();

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addOutputPort("out", com.lowdragmc.kilagraph.graph.type.KGTypeHandles.LIST);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Map<?, ?> m = ctx.getInput("map", Map.class, Map.of());
        ctx.setOutput("out", new ArrayList<>(m.values()));
    }
}
