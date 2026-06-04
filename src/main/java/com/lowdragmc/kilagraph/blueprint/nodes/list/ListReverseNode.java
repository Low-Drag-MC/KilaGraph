package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NodeAttribute(name = "list_reverse", group = "list", graphTypes = BlueprintGraph.class)
public class ListReverseNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public List<?> out;

    @Override public Component getDisplayName() { return Component.literal("List Reverse"); }

    @Override public void evaluate(EvalContext ctx) {
        List<Object> result = new ArrayList<>(ctx.getInput("list", List.class, List.of()));
        Collections.reverse(result);
        ctx.setOutput("out", result);
    }
}
