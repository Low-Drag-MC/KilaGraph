package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Signals the engine to skip to the next iteration of the nearest enclosing loop.
 */
@NodeAttribute(name = "exec_continue", group = "exec", graphTypes = BlueprintGraph.class)
public class ContinueNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;

    @Override
    public void execute(ExecContext ctx) {
        ctx.signalContinue();
    }
}
