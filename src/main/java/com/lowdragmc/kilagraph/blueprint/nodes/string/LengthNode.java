package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "string_length", group = "string", graphTypes = BlueprintGraph.class)
public class LengthNode extends AnnotatedNode {
    @InputPort public String in = "";
    @OutputPort public int out;

    @Override public Component getDisplayName() { return Component.literal("Length"); }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("in", String.class, "").length());
    }
}
