package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "bitwise_shift_left", group = "bitwise", graphTypes = BlueprintGraph.class)
public class ShiftLeftNode extends AnnotatedNode {
    @InputPort public int value = 0;
    @InputPort public int bits = 0;
    @OutputPort public int out;

    @Override public Component getDisplayName() { return Component.literal("Shift Left"); }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("value", Integer.class, 0) << ctx.getInput("bits", Integer.class, 0));
    }
}
