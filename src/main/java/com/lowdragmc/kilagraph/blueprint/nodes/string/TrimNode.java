package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "string_trim", group = "string", graphTypes = BlueprintGraph.class)
public class TrimNode extends AnnotatedNode {
    @InputPort public String in = "";
    @OutputPort public String out;
@Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("in", String.class, "").trim());
    }
}
