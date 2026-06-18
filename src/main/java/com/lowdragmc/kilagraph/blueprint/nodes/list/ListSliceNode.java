package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice with clamping. {@code from} ≥ {@code to} → empty.
 */
@NodeAttribute(name = "list_slice", group = "list", graphTypes = BlueprintGraph.class)
public class ListSliceNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @InputPort public int from = 0;
    @InputPort public int to = 0;
    @OutputPort public List<?> out;
@Override public void evaluate(EvalContext ctx) {
        List<?> src = ctx.getInput("list", List.class, List.of());
        int f = Math.max(0, Math.min(src.size(), ctx.getInput("from", Integer.class, 0)));
        int t = Math.max(0, Math.min(src.size(), ctx.getInput("to", Integer.class, src.size())));
        ctx.setOutput("out", f >= t ? List.of() : new ArrayList<>(src.subList(f, t)));
    }
}
