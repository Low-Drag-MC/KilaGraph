package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Sum of {@code inputs} float values: {@code in1 + in2 + ... + inN}. Default is 2 inputs.
 */
@NodeAttribute(name = "math_add", group = "math", graphTypes = BlueprintGraph.class)
public class AddNode extends AnnotatedNode {

    @Option public int inputs = 2;

    @OutputPort public float out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) {
            context.addInputPort(PortIds.in(i), Float.class);
        }
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        float sum = 0f;
        for (int i = 1; i <= n; i++) {
            sum += ctx.getFloat(PortIds.in(i), 0f);
        }
        ctx.setOutput("out", sum);
    }
}
