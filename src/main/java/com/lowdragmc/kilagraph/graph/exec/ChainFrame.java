package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;

/**
 * A plain linear run of exec nodes — the kind a {@code flow()} call appends to. The root frame (the
 * entry node) is a chain. When its queue empties there's nothing more to do, so it pops.
 */
public final class ChainFrame extends ExecFrame {

    ChainFrame(GraphExecutor scope) {
        super(scope);
    }

    /** Convenience: a root chain seeded with {@code entry}. */
    static ChainFrame entry(GraphExecutor scope, NodeModel entry) {
        ChainFrame f = new ChainFrame(scope);
        f.pending.addLast(entry);
        return f;
    }

    @Override public Kind kind() { return Kind.CHAIN; }

    @Override boolean resume(ExecSession session) {
        return false;  // nothing left — pop
    }
}
