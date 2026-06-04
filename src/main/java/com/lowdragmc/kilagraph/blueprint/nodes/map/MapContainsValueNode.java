package com.lowdragmc.kilagraph.blueprint.nodes.map;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.Map;

@NodeAttribute(name = "map_contains_value", group = "map", graphTypes = BlueprintGraph.class)
public class MapContainsValueNode extends AnnotatedNode {
    @InputPort public Map<?, ?> map = Map.of();
    @OutputPort public boolean out;

    @Override public Component getDisplayName() { return Component.literal("Map Contains Value"); }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        Map<?, ?> m = ctx.getInput("map", Map.class, Map.of());
        Object v = ctx.getInput("value").orElse(null);
        ctx.setOutput("out", m.containsValue(v));
    }
}
