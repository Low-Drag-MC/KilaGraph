package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * Steps {@code from} towards {@code to} by at most {@code maxDelta}, landing exactly on the target
 * rather than overshooting.
 *
 * <p>The difference from a lerp: this closes a fixed amount per call, so a value driven by it
 * arrives in a predictable number of ticks and stops. A lerp with a fixed {@code t} closes a
 * fraction, so it approaches forever and its speed depends on how far away it started.</p>
 */
@NodeAttribute(name = "math_move_towards", group = "math", graphTypes = BlueprintGraph.class)
public class MoveTowardsNode extends AnnotatedNode {
    @InputPort public float from = 0f;
    @InputPort public float to = 0f;
    @InputPort public float maxDelta = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        float a = ctx.getFloat("from", 0f);
        float b = ctx.getFloat("to", 0f);
        float step = ctx.getFloat("maxDelta", 1f);
        float delta = b - a;

        if (Math.abs(delta) <= step) {
            ctx.setOutput("out", b);
        } else {
            ctx.setOutput("out", a + Math.signum(delta) * step);
        }
    }
}
