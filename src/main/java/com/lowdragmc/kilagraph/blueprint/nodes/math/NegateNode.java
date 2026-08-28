package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code -in}, in the {@link NumericLane} its operand asks for.
 *
 * <p>In the whole-number lane {@code Long.MIN_VALUE} negates to itself, because there is no positive
 * {@code long} that far out. That is Java's answer for {@code -x} and the same thing {@code Abs}
 * does.</p>
 */
@NodeAttribute(name = "math_negate", group = "math", graphTypes = BlueprintGraph.class)
public class NegateNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("in")) {
            case NumericLane.INT, NumericLane.LONG -> ctx.setOutput("out", -ctx.getLong("in", 0L));
            case NumericLane.DOUBLE -> ctx.setOutput("out", -ctx.getDouble("in", 0d));
            default -> ctx.setOutput("out", -ctx.getFloat("in", 0f));
        }
    }
}
