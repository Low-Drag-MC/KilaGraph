package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_delta_angle", group = "math", graphTypes = BlueprintGraph.class)
public class DeltaAngleNode extends AnnotatedNode {
    @InputPort public float from = 0f;
    @InputPort public float to = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float delta = ctx.getFloat("to", 0f) - ctx.getFloat("from", 0f);

        ctx.setOutput("out", WrapNode.wrap(delta, -180f, 180f));
    }
}
