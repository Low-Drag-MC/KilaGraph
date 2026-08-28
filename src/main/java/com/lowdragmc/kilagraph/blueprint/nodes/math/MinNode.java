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

/** Smallest of {@code inputs} values, in the {@link NumericLane} they ask for. */
@NodeAttribute(name = "math_min", group = "math", graphTypes = BlueprintGraph.class)
public class MinNode extends AnnotatedNode {
    @Option public int inputs = 2;
    @OutputPort public float out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort(PortIds.in(i), Float.class);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        switch (VariadicLane.of(ctx, n)) {
            // Long.MAX_VALUE is the identity here, as +Inf is in the float lanes: a real operand is
            // never larger, and there is always at least one.
            case NumericLane.INT, NumericLane.LONG -> {
                long m = Long.MAX_VALUE;
                for (int i = 1; i <= n; i++) m = Math.min(m, ctx.getLong(PortIds.in(i), 0L));
                ctx.setOutput("out", m);
            }
            case NumericLane.DOUBLE -> {
                double m = Double.POSITIVE_INFINITY;
                for (int i = 1; i <= n; i++) m = Math.min(m, ctx.getDouble(PortIds.in(i), 0d));
                ctx.setOutput("out", m);
            }
            default -> {
                float m = Float.POSITIVE_INFINITY;
                for (int i = 1; i <= n; i++) m = Math.min(m, ctx.getFloat(PortIds.in(i), 0f));
                ctx.setOutput("out", m);
            }
        }
    }
}
