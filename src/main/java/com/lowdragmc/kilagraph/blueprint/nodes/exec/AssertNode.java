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
 * Throws {@link AssertionError} (with the supplied {@code message}) if {@code condition} is false.
 * Otherwise advances {@code next}.
 */
@NodeAttribute(name = "exec_assert", group = "exec", graphTypes = BlueprintGraph.class)
public class AssertNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow trigger;
    @InputPort public boolean condition = true;
    @InputPort public String message = "";
    @ExecOutputPort public ExecutionFlow next;

    @Override
    public void execute(ExecContext ctx) {
        boolean cond = ctx.getInput("condition", Boolean.class, true);
        if (!cond) {
            String msg = ctx.getInput("message", String.class, "");
            throw new AssertionError(msg.isEmpty() ? "Assert failed" : msg);
        }
        ctx.flow("next");
    }
}
