package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code |a - b| <= |epsilon|} — the comparison {@code compare_eq} cannot make for floats, where a
 * value that arrived by a different route is almost never bit-identical to the one it should equal.
 */
@NodeAttribute(name = "math_nearly_equals", group = "math", graphTypes = BlueprintGraph.class)
public class NearlyEqualsNode extends AnnotatedNode {
    @InputPort public float a = 0f;
    @InputPort public float b = 0f;
    @InputPort public float epsilon = 1e-4f;
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        float difference = Math.abs(ctx.getFloat("a", 0f) - ctx.getFloat("b", 0f));
        ctx.setOutput("out", difference <= Math.abs(ctx.getFloat("epsilon", 1e-4f)));
    }
}
