package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** Logarithm of {@code value} in an arbitrary {@code base}: {@code log(value) / log(base)}. */
@NodeAttribute(name = "math_log_base", group = "math", graphTypes = BlueprintGraph.class)
public class LogBaseNode extends AnnotatedNode {
    @InputPort public float value = 1f;
    @InputPort public float base = 10f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("value", 1f);
        float b = ctx.getFloat("base", 10f);
        if (v <= 0f || b <= 0f || b == 1f) { ctx.setOutput("out", 0f); return; }
        ctx.setOutput("out", (float) (Math.log(v) / Math.log(b)));
    }
}
