package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.List;

@NodeAttribute(name = "list_size", group = "list", graphTypes = BlueprintGraph.class)
public class ListSizeNode extends AnnotatedNode {

    @InputPort  public List<?> list = List.of();
    @OutputPort public int     size;

    @Override
    public Component getDisplayName() {
        return Component.literal("List Size");
    }

    @Override
    public void evaluate(EvalContext ctx) {
        List<?> l = ctx.getInput("list", List.class, List.of());
        ctx.setOutput("size", l.size());
    }
}
