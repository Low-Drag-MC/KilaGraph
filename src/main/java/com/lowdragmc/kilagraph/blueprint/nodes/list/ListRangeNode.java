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
 * Integer range {@code [from, to)} stepping by {@code step}. step ≤ 0 → empty list.
 */
@NodeAttribute(name = "list_range", group = "list", graphTypes = BlueprintGraph.class)
public class ListRangeNode extends AnnotatedNode {
    @InputPort public int from = 0;
    @InputPort public int to = 10;
    @InputPort public int step = 1;
    @OutputPort public List<?> out;
@Override public void evaluate(EvalContext ctx) {
        int f = ctx.getInput("from", Integer.class, 0);
        int t = ctx.getInput("to", Integer.class, 10);
        int s = ctx.getInput("step", Integer.class, 1);
        List<Integer> result = new ArrayList<>();
        if (s > 0) for (int i = f; i < t; i += s) result.add(i);
        ctx.setOutput("out", result);
    }
}
