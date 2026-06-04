package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

/**
 * Uniform float in {@code [min, max)} using the executor's shared RNG. Within a single executor's
 * lifetime the value is cached per node — same evaluator → same roll (the executor's port-level
 * cache handles this naturally).
 */
@NodeAttribute(name = "math_random", group = "math", graphTypes = BlueprintGraph.class)
public class RandomNode extends AnnotatedNode {
    @InputPort public float min = 0f;
    @InputPort public float max = 1f;
    @OutputPort public float out;

    @Override public Component getDisplayName() { return Component.literal("Random"); }

    @Override public void evaluate(EvalContext ctx) {
        float lo = ctx.getInput("min", Float.class, 0f);
        float hi = ctx.getInput("max", Float.class, 1f);
        if (hi <= lo) { ctx.setOutput("out", lo); return; }
        float r = lo + ctx.getExecutor().rng().nextFloat() * (hi - lo);
        ctx.setOutput("out", r);
    }
}
