package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.NodeRef;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Memoises its {@code value} input across the lifetime of one {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor}
 * instance. The first pull computes {@code value} and stores it in per-node {@code state}; every
 * later pull returns the stored copy — even across loop iterations, because a loop's
 * {@code clearCache()} wipes the pull cache but <em>not</em> node state.
 *
 * <p>This is a pure-data node (no exec ports). To force a recompute, wire its {@code ref} output
 * (a {@link NodeRef} to itself) into a {@code CacheClear} node placed in the exec flow.</p>
 */
@NodeAttribute(name = "exec_cache", group = "exec", graphTypes = BlueprintGraph.class)
public class CacheNode extends AnnotatedNode {

    /** Self-reference handle so a CacheClear can target this node. NODE_REF-typed (no constant). */
    @OutputPort public NodeRef ref;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", TypeHandles.UNKNOWN);
        ctx.addOutputPort("cached", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        var state = ctx.getExecutor().nodeState(getNodeModel().getUid());
        // containsKey (not get != null) so a memoised null is honoured rather than recomputed.
        if (!state.containsKey("cached")) {
            state.put("cached", ctx.getInputRaw("value"));
        }
        ctx.setOutput("cached", state.get("cached"));
        ctx.setOutput("ref", new NodeRef(getNodeModel().getUid()));
    }
}
