package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;

/**
 * Unreal's {@code DoN}: {@code enter} passes through the first {@code n} times and is swallowed
 * after that, until {@code reset} starts the count over. {@code counter} is how many have passed
 * so far, including this one.
 */
@NodeAttribute(name = "exec_do_n", group = "exec", graphTypes = BlueprintGraph.class)
public class DoNNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow enter;
    @ExecInputPort public ExecutionFlow reset;
    @InputPort public int n = 1;
    @ExecOutputPort public ExecutionFlow exit;
    @OutputPort public int counter;

    private static final String COUNT = "count";

    @Override
    public void execute(ExecContext ctx) {
        var state = ctx.state();
        int count = state.get(COUNT) instanceof Integer i ? i : 0;
        if (ctx.enteredThrough("reset")) {
            state.put(COUNT, 0);
            ctx.setOutput("counter", 0);
            return;
        }
        int limit = ctx.getInt("n", n);
        if (count >= limit) {
            ctx.setOutput("counter", count);
            return;
        }
        count++;
        state.put(COUNT, count);
        ctx.setOutput("counter", count);
        ctx.flow("exit");
    }
}
