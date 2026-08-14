package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "math_multiply", group = "math", graphTypes = BlueprintGraph.class)
public class MultiplyNode extends AnnotatedNode {
    @Option public int inputs = 2;
    @OutputPort public float out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort(PortIds.in(i), Float.class);
    }

    @Override public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        float p = 1f;
        for (int i = 1; i <= n; i++) p *= ctx.getFloat(PortIds.in(i), 1f);
        ctx.setOutput("out", p);
    }
}
