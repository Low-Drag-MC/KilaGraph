package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Unreal's {@code FlipFlop}: the first trigger fires {@code a}, the next {@code b}, then {@code a}
 * again, and so on. {@code isA} says which one this was.
 */
@NodeAttribute(name = "exec_flip_flop", group = "exec", graphTypes = BlueprintGraph.class)
public class FlipFlopNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @ExecOutputPort public ExecutionFlow a;
    @ExecOutputPort public ExecutionFlow b;
    @OutputPort public boolean isA;

    private static final String NEXT_IS_A = "nextIsA";

    @Override
    public void execute(ExecContext ctx) {
        var state = ctx.state();
        boolean fireA = !Boolean.FALSE.equals(state.get(NEXT_IS_A));   // absent: A first, as Unreal's
        state.put(NEXT_IS_A, !fireA);
        ctx.setOutput("isA", fireA);
        ctx.flow(fireA ? "a" : "b");
    }
}
