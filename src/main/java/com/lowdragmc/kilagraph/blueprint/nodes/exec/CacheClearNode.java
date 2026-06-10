package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.type.NodeRef;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;

/**
 * Invalidates the {@code Cache} node(s) wired into its {@code ref} input when executed, forcing
 * them to recompute on the next pull. Then flows {@code next}.
 *
 * <p>It reads the {@code ref} wire <em>topologically</em> ({@link ExecContext#connectedSourceNodes})
 * instead of pulling its value — pulling would evaluate the upstream {@code Cache} (the very thing
 * we're about to clear). Unwired {@code ref} → no-op (still flows {@code next}).</p>
 */
@NodeAttribute(name = "exec_cache_clear", group = "exec", graphTypes = BlueprintGraph.class)
public class CacheClearNode extends AnnotatedNode {

    @ExecInputPort public ExecutionFlow in;
    /** NODE_REF input — wire a Cache node's {@code ref} output here. No embedded constant. */
    @InputPort public NodeRef ref;
    @ExecOutputPort public ExecutionFlow next;

    @Override
    public void execute(ExecContext ctx) {
        for (NodeModel src : ctx.connectedSourceNodes("ref")) {
            ctx.getExecutor().invalidateNode(src);
        }
        ctx.flow("next");
    }
}
