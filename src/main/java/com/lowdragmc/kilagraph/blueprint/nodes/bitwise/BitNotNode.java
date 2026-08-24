package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "bitwise_not", group = "bitwise", graphTypes = BlueprintGraph.class)
public class BitNotNode extends AnnotatedNode {
    @InputPort public int in = 0;
    @OutputPort public int out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ~ctx.getInt("in", 0));
    }
}
