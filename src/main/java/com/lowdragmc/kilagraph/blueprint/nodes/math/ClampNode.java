package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_clamp", group = "math", graphTypes = BlueprintGraph.class)
public class ClampNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float min = 0f;
    @InputPort public float max = 1f;
    @OutputPort public float out;
@Override public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        float lo = ctx.getFloat("min", 0f);
        float hi = ctx.getFloat("max", 1f);
        ctx.setOutput("out", Math.max(lo, Math.min(hi, v)));
    }
}
