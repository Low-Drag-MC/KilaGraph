package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_sqrt", group = "math", graphTypes = BlueprintGraph.class)
public class SqrtNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        ctx.setOutput("out", v < 0f ? 0f : (float) Math.sqrt(v));
    }
}
