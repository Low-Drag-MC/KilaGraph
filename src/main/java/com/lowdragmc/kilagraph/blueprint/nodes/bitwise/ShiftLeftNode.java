package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "bitwise_shift_left", group = "bitwise", graphTypes = BlueprintGraph.class)
public class ShiftLeftNode extends AnnotatedNode {
    @InputPort public int value = 0;
    @InputPort public int bits = 0;
    @OutputPort public int out;
@Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInt("value", 0) << ctx.getInt("bits", 0));
    }
}
