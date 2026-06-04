package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Pass-through exec node. Useful for wiring shape (e.g. fan-in points, breakpoint anchors).
 */
@NodeAttribute(name = "exec_noop", group = "exec", graphTypes = BlueprintGraph.class)
public class NoopNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @ExecOutputPort public ExecutionFlow out;

    @Override
    public void execute(ExecContext ctx) {
        ctx.flow("out");
    }
}
