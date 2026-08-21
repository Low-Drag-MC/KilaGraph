package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * Linear remap of {@code in} from [fromMin, fromMax] → [toMin, toMax]. Degenerate range → toMin.
 */
@NodeAttribute(name = "math_remap", group = "math", graphTypes = BlueprintGraph.class)
public class RemapNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float fromMin = 0f;
    @InputPort public float fromMax = 1f;
    @InputPort public float toMin = 0f;
    @InputPort public float toMax = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        float fMin = ctx.getFloat("fromMin", 0f);
        float fMax = ctx.getFloat("fromMax", 1f);
        float tMin = ctx.getFloat("toMin", 0f);
        float tMax = ctx.getFloat("toMax", 1f);
        float span = fMax - fMin;
        if (span == 0f) { ctx.setOutput("out", tMin); return; }
        float t = (v - fMin) / span;
        ctx.setOutput("out", tMin + (tMax - tMin) * t);
    }
}
