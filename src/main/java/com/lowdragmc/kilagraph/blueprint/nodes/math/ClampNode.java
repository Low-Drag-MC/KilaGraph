package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code in} held between {@code min} and {@code max}, in the {@link NumericLane} its operands ask
 * for.
 *
 * <p>{@code max(lo, min(hi, v))} in every lane, so an inverted range — {@code lo} above {@code hi} —
 * answers {@code lo}, as it always did.</p>
 */
@NodeAttribute(name = "math_clamp", group = "math", graphTypes = BlueprintGraph.class)
public class ClampNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float min = 0f;
    @InputPort public float max = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("in", "min", "max")) {
            case NumericLane.INT, NumericLane.LONG -> {
                long v = ctx.getLong("in", 0L);
                long lo = ctx.getLong("min", 0L);
                long hi = ctx.getLong("max", 1L);
                ctx.setOutput("out", Math.max(lo, Math.min(hi, v)));
            }
            case NumericLane.DOUBLE -> {
                double v = ctx.getDouble("in", 0d);
                double lo = ctx.getDouble("min", 0d);
                double hi = ctx.getDouble("max", 1d);
                ctx.setOutput("out", Math.max(lo, Math.min(hi, v)));
            }
            default -> {
                float v = ctx.getFloat("in", 0f);
                float lo = ctx.getFloat("min", 0f);
                float hi = ctx.getFloat("max", 1f);
                ctx.setOutput("out", Math.max(lo, Math.min(hi, v)));
            }
        }
    }
}
