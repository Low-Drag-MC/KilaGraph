package com.lowdragmc.kilagraph.blueprint.nodes.map;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.Map;

@NodeAttribute(name = "map_size", group = "map", graphTypes = BlueprintGraph.class)
public class MapSizeNode extends AnnotatedNode {
    @InputPort public Map<?, ?> map = Map.of();
    @OutputPort public int size;
@Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("size", ctx.getInput("map", Map.class, Map.of()).size());
    }
}
