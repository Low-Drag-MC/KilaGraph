package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_smoothstep", group = "math", graphTypes = BlueprintGraph.class)
public class SmoothstepNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float edge0 = 0f;
    @InputPort public float edge1 = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        float e0 = ctx.getFloat("edge0", 0f);
        float e1 = ctx.getFloat("edge1", 1f);
        if (e0 == e1) {
            ctx.setOutput("out", v >= e1 ? 1f : 0f);
            return;
        }
        float t = (v - e0) / (e1 - e0);
        t = Math.max(0f, Math.min(1f, t));
        ctx.setOutput("out", t * t * (3f - 2f * t));
    }
}
