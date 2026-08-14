package com.lowdragmc.kilagraph.blueprint.nodes.logic;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.Objects;

/**
 * Returns {@code true} iff all {@code inputs} input values are equal (via {@link Objects#equals}).
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
            if (!Objects.equals(first, ctx.getInputRaw(PortIds.in(i)))) {
                ctx.setOutput("out", false);
                return;
            }
        }
        ctx.setOutput("out", true);
    }
}
