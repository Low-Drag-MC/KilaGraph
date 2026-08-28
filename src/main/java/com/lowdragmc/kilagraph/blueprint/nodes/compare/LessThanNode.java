package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code a < b}, compared in the lane its operands ask for. @see NumericComparison */
@NodeAttribute(name = "cmp_lt", group = "compare", graphTypes = BlueprintGraph.class)
public class LessThanNode extends AnnotatedNode {
    @InputPort  public float a = 0f;
    @InputPort  public float b = 0f;
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", NumericComparison.evaluate(ctx, NumericComparison.LT));
    }
}
