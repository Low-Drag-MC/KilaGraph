package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "string_index_of", group = "string", graphTypes = BlueprintGraph.class)
public class IndexOfNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String search = "";
    @OutputPort public int out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out",
                ctx.getInput("in", String.class, "").indexOf(ctx.getInput("search", String.class, "")));
    }
}
