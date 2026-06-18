package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.List;

@NodeAttribute(name = "list_is_empty", group = "list", graphTypes = BlueprintGraph.class)
public class ListIsEmptyNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public boolean out;
@Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getInput("list", List.class, List.of()).isEmpty());
    }
}
