package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code a % b}, in the {@link NumericLane} its operands ask for.
 *
 * <p>This is the node the lane rule was written for. {@code gameTime % 40} is the standard way to
 * make something happen every two seconds, and with the arithmetic pinned to {@code float} it stopped
 * working after about 9.7 days of world time: past 2^24 a tick and the next tick are the same float,
 * so the remainder froze on one value and every "is it time yet" downstream froze with it. Now the
 * {@code long} on the wire names the lane and the {@code 40} typed into {@code b} goes along with it.
 *
 * <p>The sign follows the dividend in every lane, matching Java's {@code %}: {@code -7 % 3} is
 * {@code -1}, not {@code 2}. A divisor of zero yields zero rather than throwing.</p>
 */
@NodeAttribute(name = "math_modulo", group = "math", graphTypes = BlueprintGraph.class)
public class ModuloNode extends AnnotatedNode {
    @InputPort public float a = 0f;
    @InputPort public float b = 1f;
    @OutputPort public float out;

    @Override
    public void evaluate(EvalContext ctx) {
        switch (ctx.lane("a", "b")) {
            case NumericLane.INT, NumericLane.LONG -> {
                long va = ctx.getLong("a", 0L);
                long vb = ctx.getLong("b", 1L);
                ctx.setOutput("out", vb == 0L ? 0L : va % vb);
            }
            case NumericLane.DOUBLE -> {
                double va = ctx.getDouble("a", 0d);
                double vb = ctx.getDouble("b", 1d);
                ctx.setOutput("out", vb == 0d ? 0d : va % vb);
            }
            default -> {
                float va = ctx.getFloat("a", 0f);
                float vb = ctx.getFloat("b", 1f);
                ctx.setOutput("out", vb == 0f ? 0f : va % vb);
            }
        }
    }
}
