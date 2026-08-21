package com.lowdragmc.kilagraph.blueprint.nodes.logic;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "logic_not", group = "logic", graphTypes = BlueprintGraph.class)
public class NotNode extends AnnotatedNode {

    @InputPort public boolean in = false;
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", !ctx.getBool("in", false));
    }
}
