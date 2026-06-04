package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

@NodeAttribute(name = "math_min", group = "math", graphTypes = BlueprintGraph.class)
public class MinNode extends AnnotatedNode {
    @Option public int inputs = 2;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Min"); }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort("in" + i, Float.class);
    }

    @Override public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        float m = Float.POSITIVE_INFINITY;
        for (int i = 1; i <= n; i++) m = Math.min(m, ctx.getInput("in" + i, Float.class, 0f));
        ctx.setOutput("out", m);
    }
}
