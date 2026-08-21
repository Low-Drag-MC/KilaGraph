package com.lowdragmc.kilagraph.blueprint.nodes.map;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.LinkedHashMap;
import java.util.Map;

/** Returns a new map. On key conflict, {@code b} wins. */
@NodeAttribute(name = "map_merge", group = "map", graphTypes = BlueprintGraph.class)
public class MapMergeNode extends AnnotatedNode {
    @InputPort public Map<?, ?> a = Map.of();
    @InputPort public Map<?, ?> b = Map.of();
    @OutputPort public Map<?, ?> out;

    @Override
    public void evaluate(EvalContext ctx) {
        Map<?, ?> aMap = ctx.getInput("a", Map.class, Map.of());
        Map<?, ?> bMap = ctx.getInput("b", Map.class, Map.of());
        Map<Object, Object> result = new LinkedHashMap<>(aMap);
        result.putAll(bMap);
        ctx.setOutput("out", result);
    }
}
