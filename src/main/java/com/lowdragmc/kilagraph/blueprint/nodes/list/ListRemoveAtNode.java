package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

@NodeAttribute(name = "list_remove_at", group = "list", graphTypes = BlueprintGraph.class)
public class ListRemoveAtNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @InputPort public int index = 0;
    @OutputPort public List<?> out;

    @Override public Component getDisplayName() { return Component.literal("List Remove At"); }

    @Override public void evaluate(EvalContext ctx) {
        List<?> src = ctx.getInput("list", List.class, List.of());
        int i = ctx.getInput("index", Integer.class, 0);
        List<Object> result = new ArrayList<>(src);
        if (i >= 0 && i < result.size()) result.remove(i);
        ctx.setOutput("out", result);
    }
}
