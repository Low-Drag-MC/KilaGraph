package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "math_subtract", group = "math", graphTypes = BlueprintGraph.class)
public class SubtractNode extends AnnotatedNode {

    @InputPort  public float a = 0f;
    @InputPort  public float b = 0f;
    @OutputPort public float out;

    @Override
    public Component getDisplayName() {
        return Component.literal("Subtract");
    }

    @Override
    public void evaluate(EvalContext ctx) {
        float va = ctx.getInput("a", Float.class, 0f);
        float vb = ctx.getInput("b", Float.class, 0f);
        ctx.setOutput("out", va - vb);
    }
}
