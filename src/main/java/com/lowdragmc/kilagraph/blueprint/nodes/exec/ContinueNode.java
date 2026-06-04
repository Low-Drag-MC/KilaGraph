package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.exec.ContinueException;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Throws {@link ContinueException}, caught by the nearest enclosing loop to skip to the next
 * iteration.
 */
@NodeAttribute(name = "exec_continue", group = "exec", graphTypes = BlueprintGraph.class)
public class ContinueNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;

    @Override
    public void execute(ExecContext ctx) {
        throw ContinueException.INSTANCE;
    }
}
