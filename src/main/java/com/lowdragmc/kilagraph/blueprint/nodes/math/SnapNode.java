package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "math_snap", group = "math", graphTypes = BlueprintGraph.class)
public class SnapNode extends AnnotatedNode {
    @InputPort public float in = 0f;
    @InputPort public float step = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float v = ctx.getFloat("in", 0f);
        float s = ctx.getFloat("step", 1f);
        if (s == 0f) {
            ctx.setOutput("out", v);
            return;
        }

        ctx.setOutput("out", (float) (Math.floor(v / (double) s + 0.5d) * s));
    }
}
