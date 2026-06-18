package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "bitwise_and", group = "bitwise", graphTypes = BlueprintGraph.class)
public class BitAndNode extends AnnotatedNode {
    @InputPort public int a = 0;
    @InputPort public int b = 0;
    @OutputPort public int out;
@Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("a", Integer.class, 0) & ctx.getInput("b", Integer.class, 0));
    }
}
