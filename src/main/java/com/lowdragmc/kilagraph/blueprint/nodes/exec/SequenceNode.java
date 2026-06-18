package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortConnectorUI;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Fires {@code out1..outN} in order. Each output's chain runs to completion before the next starts
 * (run-to-completion semantics) — {@code ctx.runIsolated} per output prevents the global queue from
 * interleaving the branches breadth-first.
 */
@NodeAttribute(name = "exec_sequence", group = "exec", graphTypes = BlueprintGraph.class)
public class SequenceNode extends AnnotatedNode {

    @Option public int outputs = 2;
    @ExecInputPort public ExecutionFlow in;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("outputs", Integer.class, outputs));
        for (int i = 1; i <= n; i++) {
            ctx.addOutputPort("out" + i, TypeHandles.EXECUTION_FLOW)
                    .withConnectorUI(PortConnectorUI.FLOW);
        }
    }

    @Override
    public void execute(ExecContext ctx) {
        int n = Math.max(1, ctx.getOption("outputs", Integer.class, outputs));
        // Run-to-completion fan-out: out1's whole chain drains before out2 begins, etc. A
        // Break/Continue raised inside a branch unwinds this sequence frame to the enclosing loop.
        java.util.List<String> outIds = new java.util.ArrayList<>(n);
        for (int i = 1; i <= n; i++) outIds.add("out" + i);
        ctx.pushSequence(outIds);
    }
}
