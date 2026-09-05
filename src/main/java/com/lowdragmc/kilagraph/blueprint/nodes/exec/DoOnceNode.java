package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Unreal's {@code DoOnce}: {@code in} passes through the first time and is swallowed after that,
 * until {@code reset} opens it again. {@code startClosed} makes it need a reset before the first
 * pass. The state lives in the node's per-executor state, so each running graph has its own.
 *
 * <p>Two exec inputs on one node — told apart by {@link ExecContext#enteredPort()}, which is what
 * that method exists for.</p>
 */
@NodeAttribute(name = "exec_do_once", group = "exec", graphTypes = BlueprintGraph.class)
public class DoOnceNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @ExecInputPort public ExecutionFlow reset;
    @Option public boolean startClosed = false;
    @ExecOutputPort public ExecutionFlow out;

    private static final String CLOSED = "closed";

    @Override
    public void execute(ExecContext ctx) {
        var state = ctx.state();
        if (ctx.enteredThrough("reset")) {
            state.put(CLOSED, false);
            return;
        }
        boolean closed = state.containsKey(CLOSED)
                ? Boolean.TRUE.equals(state.get(CLOSED))
                : ctx.getOption("startClosed", Boolean.class, startClosed);
        if (closed) return;
        state.put(CLOSED, true);
        ctx.flow("out");
    }
}
