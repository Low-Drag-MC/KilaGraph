package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.List;

@NodeAttribute(name = "string_join", group = "string", graphTypes = BlueprintGraph.class)
public class JoinNode extends AnnotatedNode {
    @InputPort public List<?> in = List.of();
    @InputPort public String delimiter = ",";
    @OutputPort public String out;

    @Override public Component getDisplayName() { return Component.literal("Join"); }

    @Override public void evaluate(EvalContext ctx) {
        List<?> list = ctx.getInput("in", List.class, List.of());
        String d = ctx.getInput("delimiter", String.class, ",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(d);
            Object e = list.get(i);
            sb.append(e == null ? "" : e.toString());
        }
        ctx.setOutput("out", sb.toString());
    }
}
