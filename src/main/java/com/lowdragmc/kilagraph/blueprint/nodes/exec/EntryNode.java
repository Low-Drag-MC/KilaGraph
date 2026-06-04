package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Exec-flow entry point. No inputs, a single {@code next} exec output. Pass it to
 * {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor#executeFrom} to kick off an exec run.
 */
@NodeAttribute(name = "exec_entry", group = "exec", graphTypes = BlueprintGraph.class)
public class EntryNode extends AnnotatedNode {

    @ExecOutputPort public ExecutionFlow next;

    @Override
    public void execute(ExecContext ctx) {
        ctx.flow("next");
    }
}
