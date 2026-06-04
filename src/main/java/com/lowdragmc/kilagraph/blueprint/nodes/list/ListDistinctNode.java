package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Distinct, preserves first-occurrence order. */
@NodeAttribute(name = "list_distinct", group = "list", graphTypes = BlueprintGraph.class)
public class ListDistinctNode extends AnnotatedNode {
    @InputPort public List<?> list = List.of();
    @OutputPort public List<?> out;

    @Override public Component getDisplayName() { return Component.literal("List Distinct"); }

    @Override public void evaluate(EvalContext ctx) {
        ctx.setOutput("out",
                new ArrayList<>(new LinkedHashSet<>(ctx.getInput("list", List.class, List.of()))));
    }
}
