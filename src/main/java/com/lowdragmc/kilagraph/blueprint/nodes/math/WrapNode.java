package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_wrap", group = "math", graphTypes = BlueprintGraph.class)
public class WrapNode extends AnnotatedNode {
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
                long span = hi - lo;

                ctx.setOutput("out", span <= 0L ? lo : lo + Math.floorMod(v - lo, span));
            }
            case NumericLane.DOUBLE -> {
                ctx.setOutput("out", wrap(ctx.getDouble("in", 0d),
                        ctx.getDouble("min", 0d), ctx.getDouble("max", 1d)));
            }
            default -> {
                ctx.setOutput("out", wrap(ctx.getFloat("in", 0f),
                        ctx.getFloat("min", 0f), ctx.getFloat("max", 1f)));
            }
        }
    }

    public static float wrap(float v, float lo, float hi) {
        float span = hi - lo;

        if (!(span > 0f)) return lo;
        float t = (v - lo) % span;
        if (t < 0f) t += span;
        float r = lo + t;
        return r < hi ? r : lo;
    }

    public static double wrap(double v, double lo, double hi) {
        double span = hi - lo;
        if (!(span > 0d)) return lo;
        double t = (v - lo) % span;
        if (t < 0d) t += span;
        double r = lo + t;
        return r < hi ? r : lo;
    }
}
