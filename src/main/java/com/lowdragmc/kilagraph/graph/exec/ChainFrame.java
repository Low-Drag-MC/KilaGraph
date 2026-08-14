package com.lowdragmc.kilagraph.graph.exec;

/**
 * A plain linear run of exec nodes — the kind a {@code flow()} call appends to. The root frame (the
 * entry node) is a chain. When its queue empties there's nothing more to do, so it pops.
 *
 * <p>The session keeps one of these to reuse as the root of every run, so kicking off a flow does
 * not allocate a frame.</p>
 */
public final class ChainFrame extends ExecFrame {

    ChainFrame(GraphExecutor scope) {
        super(scope);
    }

    @Override public Kind kind() { return Kind.CHAIN; }

    @Override boolean resume(ExecSession session) {
        return false;  // nothing left — pop
    }
}
