package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "math_abs", group = "math", graphTypes = BlueprintGraph.class)
public class AbsNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Abs"); }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", Math.abs(ctx.getInput("in", Float.class, 0f)));
    }
}
