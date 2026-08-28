package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code value << bits}, in 64 bits when {@code value} is a {@code long} and 32 bits otherwise.
 *
 * <p>The distance wraps the way Java's operator does — modulo 32 in the narrow lane and modulo 64 in
 * the wide one — so the same graph shifting by 35 answers differently depending on the width of what
 * it is shifting. That is the reason {@link BitwiseLane} takes only actual {@code long}s as wide.</p>
 */
@NodeAttribute(name = "bitwise_shift_left", group = "bitwise", graphTypes = BlueprintGraph.class)
public class ShiftLeftNode extends AnnotatedNode {
    @InputPort public int value = 0;
    @InputPort public int bits = 0;
    @OutputPort public int out;

    @Override
    public void evaluate(EvalContext ctx) {
        if (BitwiseLane.wide(ctx, "value")) {
            ctx.setOutput("out", ctx.getLong("value", 0L) << ctx.getInt("bits", 0));
        } else {
            ctx.setOutput("out", ctx.getInt("value", 0) << ctx.getInt("bits", 0));
        }
    }
}
