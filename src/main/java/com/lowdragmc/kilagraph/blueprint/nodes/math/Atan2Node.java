package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/** {@code atan2(y, x)} in radians. */
@NodeAttribute(name = "math_atan2", group = "math", graphTypes = BlueprintGraph.class)
public class Atan2Node extends AnnotatedNode {
    @InputPort public float y = 0f;
    @InputPort public float x = 1f;
    @OutputPort public float out;
@Override public void evaluate(EvalContext ctx) {
        float vy = ctx.getFloat("y", 0f);
        float vx = ctx.getFloat("x", 1f);
        ctx.setOutput("out", (float) Math.atan2(vy, vx));
    }
}
