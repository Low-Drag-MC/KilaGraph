package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Remove the first occurrence of {@code value} (by {@link Objects#equals}). Returns a new list.
 */
@NodeAttribute(name = "list_remove", group = "list", graphTypes = BlueprintGraph.class)
public class ListRemoveNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public List<?> out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
    }

    @Override public void evaluate(EvalContext ctx) {
        List<?> src = ctx.getInput("list", List.class, List.of());
        Object target = ctx.getInputRaw("value");
        List<Object> result = new ArrayList<>(src.size());
        boolean removed = false;
        for (Object o : src) {
            if (!removed && Objects.equals(o, target)) { removed = true; continue; }
            result.add(o);
        }
        ctx.setOutput("out", result);
    }
}
