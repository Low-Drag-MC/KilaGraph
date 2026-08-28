package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

/**
 * {@code a & b}, in 64 bits when either side is a {@code long} and 32 bits otherwise.
 * @see BitwiseLane
 */
@NodeAttribute(name = "bitwise_and", group = "bitwise", graphTypes = BlueprintGraph.class)
public class BitAndNode extends AnnotatedNode {
    @InputPort public int a = 0;
    @InputPort public int b = 0;
    @OutputPort public int out;

    @Override
    public void evaluate(EvalContext ctx) {
        if (BitwiseLane.wide(ctx, "a", "b")) {
            ctx.setOutput("out", ctx.getLong("a", 0L) & ctx.getLong("b", 0L));
        } else {
            ctx.setOutput("out", ctx.getInt("a", 0) & ctx.getInt("b", 0));
        }
    }
}
