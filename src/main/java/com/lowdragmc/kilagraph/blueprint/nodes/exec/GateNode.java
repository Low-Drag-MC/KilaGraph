package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Pass-through when {@code enabled}, otherwise drops the flow.
 */
@NodeAttribute(name = "exec_gate", group = "exec", graphTypes = BlueprintGraph.class)
public class GateNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @InputPort public boolean enabled = true;
    @ExecOutputPort public ExecutionFlow out;

    @Override
    public void execute(ExecContext ctx) {
        if (ctx.getInput("enabled", Boolean.class, true)) ctx.flow("out");
    }
}
