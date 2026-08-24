package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "string_starts_with", group = "string", graphTypes = BlueprintGraph.class)
public class StartsWithNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String needle = "";
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out",
                ctx.getInput("in", String.class, "").startsWith(ctx.getInput("needle", String.class, "")));
    }
}
