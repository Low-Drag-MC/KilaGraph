package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Sum of {@code inputs} values: {@code in1 + in2 + ... + inN}. Default is 2 inputs.
 *
 * <p>Summed in the {@link NumericLane} the operands ask for, so adding to a tick count or an id stays
 * exact instead of rounding to the nearest float once past 2^24.</p>
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
        switch (VariadicLane.of(ctx, n)) {
            case NumericLane.INT, NumericLane.LONG -> {
                long sum = 0L;
                for (int i = 1; i <= n; i++) sum += ctx.getLong(PortIds.in(i), 0L);
                ctx.setOutput("out", sum);
            }
            case NumericLane.DOUBLE -> {
                double sum = 0d;
                for (int i = 1; i <= n; i++) sum += ctx.getDouble(PortIds.in(i), 0d);
                ctx.setOutput("out", sum);
            }
            default -> {
                float sum = 0f;
                for (int i = 1; i <= n; i++) sum += ctx.getFloat(PortIds.in(i), 0f);
                ctx.setOutput("out", sum);
            }
        }
    }
}
