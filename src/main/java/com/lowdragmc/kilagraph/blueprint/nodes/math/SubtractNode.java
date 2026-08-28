package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code a - b}, in the {@link NumericLane} its operands ask for. */
@NodeAttribute(name = "math_subtract", group = "math", graphTypes = BlueprintGraph.class)
public class SubtractNode extends AnnotatedNode {

    @InputPort  public float a = 0f;
    @InputPort  public float b = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("a", "b")) {
            case NumericLane.INT, NumericLane.LONG -> {
                long va = ctx.getLong("a", 0L);
                long vb = ctx.getLong("b", 0L);
                ctx.setOutput("out", va - vb);
            }
            case NumericLane.DOUBLE -> {
                double va = ctx.getDouble("a", 0d);
                double vb = ctx.getDouble("b", 0d);
                ctx.setOutput("out", va - vb);
            }
            default -> {
                float va = ctx.getFloat("a", 0f);
                float vb = ctx.getFloat("b", 0f);
                ctx.setOutput("out", va - vb);
            }
        }
    }
}
