package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Uniform int in {@code [min, max)}. Min == max → min.
 */
@NodeAttribute(name = "math_random_int", group = "math", graphTypes = BlueprintGraph.class)
public class RandomIntNode extends AnnotatedNode {
    @InputPort public int min = 0;
    @InputPort public int max = 100;
    @OutputPort public int out;

    @Override public Component getDisplayName() { return Component.literal("Random Int"); }

    @Override public void evaluate(EvalContext ctx) {
        int lo = ctx.getInput("min", Integer.class, 0);
        int hi = ctx.getInput("max", Integer.class, 100);
        if (hi <= lo) { ctx.setOutput("out", lo); return; }
        ctx.setOutput("out", lo + ctx.getExecutor().rng().nextInt(hi - lo));
    }
}
