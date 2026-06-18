package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Natural-order sort using {@link Comparator#naturalOrder}. Non-Comparable elements compared by
 * {@code toString()}.
 */
@NodeAttribute(name = "list_sort", group = "list", graphTypes = BlueprintGraph.class)
public class ListSortNode extends AnnotatedNode {
    @Option public boolean ascending = true;
    @InputPort public List<?> list = List.of();
    @OutputPort public List<?> out;
@Override public void evaluate(EvalContext ctx) {
        List<Object> result = new ArrayList<>(ctx.getInput("list", List.class, List.of()));
        boolean asc = ctx.getOption("ascending", Boolean.class, true);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Comparator<Object> cmp = (a, b) -> {
            if (a instanceof Comparable ca && b != null && a.getClass().isInstance(b)) {
                return ca.compareTo(b);
            }
            return String.valueOf(a).compareTo(String.valueOf(b));
        };
        result.sort(asc ? cmp : cmp.reversed());
        ctx.setOutput("out", result);
    }
}
