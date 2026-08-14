package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Fractional part: {@code in - floor(in)}. Always in {@code [0, 1)}. */
@NodeAttribute(name = "math_fract", group = "math", graphTypes = BlueprintGraph.class)
public class FractNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;
@Override public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        ctx.setOutput("out", v - (float) Math.floor(v));
    }
}
