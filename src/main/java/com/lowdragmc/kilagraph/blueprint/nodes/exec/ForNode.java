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
 * Counted loop. Runs {@code body} {@code count} times; on each iteration {@code index} (data
 * output) is the current index 0..count-1. After the loop, fires {@code completed}.
 *
 * <p>The current index lives in per-node state (keyed by this loop's UID) rather than being pushed
 * into the pull cache directly — that way a nested loop's {@code clearCache()} can't destroy an
 * <em>outer</em> loop's live index. {@link #evaluate} re-publishes it on demand.</p>
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
        int n = Math.max(0, ctx.getInt("count", 0));
        // The controller drives iterations on the step-able engine: each iteration clears the cache
        // and publishes "index" into node state (read back by evaluate()); the engine runs the body
        // a node at a time and fires "completed" when the count is exhausted.
        ctx.pushLoop(new LoopController.ForController((NodeModel) getNodeModel(), n), "body", "completed");
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object i = ctx.getExecutor().nodeState(getNodeModel().getUid()).get("index");
        ctx.setOutput("index", i == null ? 0 : i);
    }
}
