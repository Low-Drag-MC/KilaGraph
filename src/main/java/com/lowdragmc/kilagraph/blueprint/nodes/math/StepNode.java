package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** GLSL's {@code step}: 1 once {@code in} reaches {@code edge}, 0 below it. */
@NodeAttribute(name = "math_step", group = "math", graphTypes = BlueprintGraph.class)
public class StepNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float edge = 0f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getFloat("in", 0f) >= ctx.getFloat("edge", 0f) ? 1f : 0f);
    }
}
