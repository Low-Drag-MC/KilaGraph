package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_pow", group = "math", graphTypes = BlueprintGraph.class)
public class PowNode extends AnnotatedNode {
    @InputPort public float base = 1f;
    @InputPort public float exp = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", (float) Math.pow(
                ctx.getFloat("base", 1f),
                ctx.getFloat("exp", 1f)));
    }
}
