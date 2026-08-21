package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code log_base(in)}. Default base is {@code e}. Non-positive {@code in} → 0.
 */
@NodeAttribute(name = "math_log", group = "math", graphTypes = BlueprintGraph.class)
public class LogNode extends AnnotatedNode {
    @InputPort public float in = 1f;
    @InputPort public float base = (float) Math.E;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 1f);
        float b = ctx.getFloat("base", (float) Math.E);
        if (v <= 0f || b <= 0f || b == 1f) { ctx.setOutput("out", 0f); return; }
        ctx.setOutput("out", (float) (Math.log(v) / Math.log(b)));
    }
}
