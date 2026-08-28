package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code -1}, {@code 0} or {@code 1} by the operand's sign, answered in the {@link NumericLane} the
 * operand asks for so the result stays on the same side of the whole/fractional divide as its input.
 */
@NodeAttribute(name = "math_sign", group = "math", graphTypes = BlueprintGraph.class)
public class SignNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("in")) {
            case NumericLane.INT, NumericLane.LONG -> {
                long v = ctx.getLong("in", 0L);
                ctx.setOutput("out", v > 0L ? 1L : v < 0L ? -1L : 0L);
            }
            case NumericLane.DOUBLE -> {
                double v = ctx.getDouble("in", 0d);
                ctx.setOutput("out", v > 0d ? 1d : v < 0d ? -1d : 0d);
            }
            default -> {
                float v = ctx.getFloat("in", 0f);
                ctx.setOutput("out", v > 0f ? 1f : v < 0f ? -1f : 0f);
            }
        }
    }
}
