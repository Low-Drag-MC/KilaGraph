package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.ArrayList;
import java.util.List;

@NodeAttribute(name = "list_concat", group = "list", graphTypes = BlueprintGraph.class)
public class ListConcatNode extends AnnotatedNode {
    @Option public int inputs = 2;
    @OutputPort public List<?> out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        // KGTypeHandles.LIST, not List.class. The Type overload of addInputPort resolves through
        // LDLib2's TypeHandleHelpers, which knows nothing about the overrides registered here, so a
        // raw List.class port comes out backed by the plain class rather than by LIST — and gets the
        // embedded constant that LIST deliberately does without. The editor then builds a
        // configurator for a collection that names no element type. Every other list node reaches
        // LIST through a List<?> field, which resolves via handleFor and so picks the override up.
        for (int i = 1; i <= n; i++) ctx.addInputPort(PortIds.in(i), KGTypeHandles.LIST);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        List<Object> result = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            result.addAll(ctx.getInput(PortIds.in(i), List.class, List.of()));
        }
        ctx.setOutput("out", result);
    }
}
