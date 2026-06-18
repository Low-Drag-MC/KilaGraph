package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Signals the engine to break the nearest enclosing loop. Outside any loop, the session surfaces an
 * {@code IllegalStateException} — diagnostic for "Break placed outside a loop".
 */
@NodeAttribute(name = "exec_break", group = "exec", graphTypes = BlueprintGraph.class)
public class BreakNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;

    @Override
    public void execute(ExecContext ctx) {
        ctx.signalBreak();
    }
}
