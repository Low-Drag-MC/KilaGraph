package com.lowdragmc.kilagraph.blueprint.nodes.string;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "string_concat", group = "string", graphTypes = BlueprintGraph.class)
public class ConcatNode extends AnnotatedNode {
    @Option public int inputs = 2;
    @OutputPort public String out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort(PortIds.in(i), String.class);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append(ctx.getInput(PortIds.in(i), String.class, ""));
        ctx.setOutput("out", sb.toString());
    }
}
