package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * The {@code t} that {@code math_lerp} would have needed: where {@code value} sits between
 * {@code a} and {@code b}. Not clamped — feeding it back through a lerp is an exact round trip.
 * A zero-width range answers 0, there being no position to report.
 */
@NodeAttribute(name = "math_inverse_lerp", group = "math", graphTypes = BlueprintGraph.class)
public class InverseLerpNode extends AnnotatedNode {
    @InputPort public float a = 0f;
    @InputPort public float b = 1f;
    @InputPort public float value = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float va = ctx.getFloat("a", 0f);
        float vb = ctx.getFloat("b", 1f);
        float span = vb - va;
        ctx.setOutput("out", span == 0f ? 0f : (ctx.getFloat("value", 0f) - va) / span);
    }
}
