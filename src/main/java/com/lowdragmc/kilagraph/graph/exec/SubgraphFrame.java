package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

/**
 * An entered subgraph. The frame's {@link #scope} is the <em>child</em> {@link GraphExecutor}, so
 * the inner exec nodes are stepped one at a time like any others. When the inner exec drains,
 * {@link #resume} harvests the inner WRITE data variables into the parent scope's output cache and
 * fires the exec-out pins the inner run reached into the parent frame — the same work the old
 * synchronous {@code executeSubgraph} did, now split across enter (push) and exit (resume) so the
 * inner nodes are individually steppable. Nested subgraphs nest these frames.
 */
public final class SubgraphFrame extends ExecFrame {

    private final GraphExecutor parentScope;
    private final ExecFrame parentFrame;
    private final PreparedGraph.Node subNode;
    private final CustomGraphModelImpl inner;

    SubgraphFrame(GraphExecutor childScope, GraphExecutor parentScope, ExecFrame parentFrame,
                  PreparedGraph.Node subNode, CustomGraphModelImpl inner) {
        super(childScope);
        this.parentScope = parentScope;
        this.parentFrame = parentFrame;
        this.subNode = subNode;
        this.inner = inner;
    }

    @Override
    public Kind kind() { return Kind.SUBGRAPH; }

    @Override boolean resume(ExecSession session) {
        // Harvest inner WRITE data vars into the parent's value table and fire the exec-out pins the
        // inner run reached, so the outer flow continues. One call rather than two, because the
        // pins used to be collected into a list only to be walked once and discarded.
        parentScope.finishSubgraph(subNode, inner, scope, parentFrame);
        return false;  // subgraph done — pop
    }

    /** Seed the entry node(s) — the downstream of the inner exec-IN variable's get-node. */
    void enqueueEntry(NodeModel innerEntryGetNode) {
        if (innerEntryGetNode == null) return;
        for (PortModel out : innerEntryGetNode.getOutputsByDisplayOrder()) {
            enqueueFlow(out);
        }
    }
}
