package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code |in|}, in the {@link NumericLane} its operand asks for.
 *
 * <p>{@code Math.abs(Long.MIN_VALUE)} is {@code Long.MIN_VALUE} — there is no positive {@code long}
 * that large. Java's own behaviour, and only reachable by a value that was already at the edge.</p>
 */
@NodeAttribute(name = "math_abs", group = "math", graphTypes = BlueprintGraph.class)
public class AbsNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("in")) {
            case NumericLane.INT, NumericLane.LONG -> ctx.setOutput("out", Math.abs(ctx.getLong("in", 0L)));
            case NumericLane.DOUBLE -> ctx.setOutput("out", Math.abs(ctx.getDouble("in", 0d)));
            default -> ctx.setOutput("out", Math.abs(ctx.getFloat("in", 0f)));
        }
    }
}
