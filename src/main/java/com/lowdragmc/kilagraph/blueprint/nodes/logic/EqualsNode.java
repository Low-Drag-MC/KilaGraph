package com.lowdragmc.kilagraph.blueprint.nodes.logic;

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
 * Returns {@code true} iff all {@code inputs} input values are equal.
 *
 * <p>Numbers are compared by <em>value</em> — see {@link NumericLane#valuesEqual}. Plain
 * {@code Objects.equals} was the trap: {@code Long.equals} demands a {@code Long} on the other side,
 * so a 5 that arrived as an {@code Integer} and a 5 that arrived as a {@code Float} came out unequal.
 * Which of the two a wire carries depends on whichever node produced it, and no player has any way to
 * know that. Everything that is not a number still compares with {@code Objects.equals}.</p>
 */
@NodeAttribute(name = "logic_equals", group = "logic", graphTypes = BlueprintGraph.class)
public class EqualsNode extends AnnotatedNode {

    @Option public int inputs = 2;

    @OutputPort public boolean out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        int n = Math.max(2, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) {
            context.addInputPort(PortIds.in(i), Object.class);
        }
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(2, ctx.getOption("inputs", Integer.class, inputs));
        Object first = ctx.getInputRaw("in1");
        for (int i = 2; i <= n; i++) {
            if (!NumericLane.valuesEqual(first, ctx.getInputRaw(PortIds.in(i)))) {
                ctx.setOutput("out", false);
                return;
            }
        }
        ctx.setOutput("out", true);
    }
}
