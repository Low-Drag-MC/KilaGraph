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
 * Unreal's stateful {@code Gate}: {@code enter} passes through to {@code exit} while the gate is
 * open; {@code open}, {@code close} and {@code toggle} set it, and {@code startClosed} says how it
 * begins. Different from {@link GateNode}, which passes the flow while a boolean input is true and
 * remembers nothing.
 */
@NodeAttribute(name = "exec_toggle_gate", group = "exec", graphTypes = BlueprintGraph.class)
public class ToggleGateNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow enter;
    @ExecInputPort public ExecutionFlow open;
    @ExecInputPort public ExecutionFlow close;
    @ExecInputPort public ExecutionFlow toggle;
    @Option public boolean startClosed = false;
    @ExecOutputPort public ExecutionFlow exit;

    private static final String OPEN = "open";

    @Override
    public void execute(ExecContext ctx) {
        var state = ctx.state();
        boolean isOpen = state.containsKey(OPEN)
                ? Boolean.TRUE.equals(state.get(OPEN))
                : !ctx.getOption("startClosed", Boolean.class, startClosed);
        String through = ctx.enteredPort();
        if ("open".equals(through)) {
            state.put(OPEN, true);
        } else if ("close".equals(through)) {
            state.put(OPEN, false);
        } else if ("toggle".equals(through)) {
            state.put(OPEN, !isOpen);
        } else if (isOpen) {
            ctx.flow("exit");
        }
    }
}
