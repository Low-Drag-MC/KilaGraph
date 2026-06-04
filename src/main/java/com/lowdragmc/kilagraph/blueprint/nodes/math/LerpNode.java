package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "math_lerp", group = "math", graphTypes = BlueprintGraph.class)
public class LerpNode extends AnnotatedNode {
    @InputPort public float a = 0f;
    @InputPort public float b = 1f;
    @InputPort public float t = 0f;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Lerp"); }

    @Override public void evaluate(EvalContext ctx) {
        float va = ctx.getInput("a", Float.class, 0f);
        float vb = ctx.getInput("b", Float.class, 1f);
        float vt = ctx.getInput("t", Float.class, 0f);
        ctx.setOutput("out", va + (vb - va) * vt);
    }
}
