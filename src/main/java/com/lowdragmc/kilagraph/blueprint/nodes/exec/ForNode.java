package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.exec.LoopController;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;

/**
 * Counted loop. Runs {@code body} while {@code index} is below {@code count}; on each iteration
 * {@code index} (data output) is the current index 0, 1, …. After the loop, fires {@code completed}.
 *
 * <p>{@code count} is read again before each iteration, as Unreal's {@code ForLoop} compares against
 * {@code LastIndex} on every pass: a body that changes what it is computed from changes the loop.</p>
 *
 * <p>The current index lives on the loop's controller rather than in the pull cache, and
 * {@link #evaluate} re-publishes it on demand — a read of it goes stale at each step like any pull.</p>
 */
@NodeAttribute(name = "exec_for", group = "exec", graphTypes = BlueprintGraph.class)
public class ForNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    @InputPort public int count = 0;
    @ExecOutputPort public ExecutionFlow body;
    @ExecOutputPort public ExecutionFlow completed;
    @OutputPort public int index;

    @Override
    public void execute(ExecContext ctx) {
        // The controller drives iterations on the step-able engine: it reads "count" before each one
        // and holds "index" (read back by evaluate()); the engine runs the body a node at a time and
        // fires "completed" when the index reaches the count.
        ctx.pushLoop(new LoopController.ForController(ctx.preparedNode()), "body", "completed");
    }

    @Override
    public void evaluate(EvalContext ctx) {
        // The int overload, so the index reaches downstream nodes through the numeric lane. Read
        // from the running controller rather than per-node state: no hash lookups, no boxing.
        ctx.setOutput("index", ctx.loopIndex());
    }
}
