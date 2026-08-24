package com.lowdragmc.kilagraph.blueprint.nodes.logic;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Folded XOR: true iff an odd number of inputs are true.
 */
@NodeAttribute(name = "logic_xor", group = "logic", graphTypes = BlueprintGraph.class)
public class XorNode extends AnnotatedNode {

    @Option public int inputs = 2;
    @OutputPort public boolean out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        for (int i = 1; i <= n; i++) ctx.addInputPort(PortIds.in(i), Boolean.class);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        boolean acc = false;
        for (int i = 1; i <= n; i++) acc ^= ctx.getBool(PortIds.in(i), false);
        ctx.setOutput("out", acc);
    }
}
