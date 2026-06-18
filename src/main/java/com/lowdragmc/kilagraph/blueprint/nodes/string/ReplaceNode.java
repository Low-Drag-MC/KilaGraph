package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

@NodeAttribute(name = "string_replace", group = "string", graphTypes = BlueprintGraph.class)
public class ReplaceNode extends AnnotatedNode {
    @InputPort public String in = "";
    @InputPort public String search = "";
    @InputPort public String replacement = "";
    @OutputPort public String out;
@Override public void evaluate(EvalContext ctx) {
        String s = ctx.getInput("in", String.class, "");
        String search = ctx.getInput("search", String.class, "");
        String repl = ctx.getInput("replacement", String.class, "");
        ctx.setOutput("out", search.isEmpty() ? s : s.replace(search, repl));
    }
}
