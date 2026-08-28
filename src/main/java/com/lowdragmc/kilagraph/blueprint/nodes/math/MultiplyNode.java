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
 * Product of {@code inputs} values, in the {@link NumericLane} the operands ask for.
 *
 * <p>A whole-number product that outgrows a {@code long} wraps around, the way Java's {@code *} does.
 * The float lane would instead have drifted into an approximation — for a multiply that is the more
 * likely of the two to happen and the less likely to be noticed, so if the numbers are that large,
 * convert to float deliberately with {@code To Float} first.</p>
 */
@NodeAttribute(name = "math_multiply", group = "math", graphTypes = BlueprintGraph.class)
public class MultiplyNode extends AnnotatedNode {
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
            case NumericLane.INT, NumericLane.LONG -> {
                long p = 1L;
                for (int i = 1; i <= n; i++) p *= ctx.getLong(PortIds.in(i), 1L);
                ctx.setOutput("out", p);
            }
            case NumericLane.DOUBLE -> {
                double p = 1d;
                for (int i = 1; i <= n; i++) p *= ctx.getDouble(PortIds.in(i), 1d);
                ctx.setOutput("out", p);
            }
            default -> {
                float p = 1f;
                for (int i = 1; i <= n; i++) p *= ctx.getFloat(PortIds.in(i), 1f);
                ctx.setOutput("out", p);
            }
        }
    }
}
