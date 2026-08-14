package com.lowdragmc.kilagraph.graph.exec;

import java.util.List;

/**
 * Run-to-completion sequencing: fires {@code out1..outN} in order, each output's whole chain
 * draining before the next starts. Replaces {@code SequenceNode}'s per-output {@code runIsolated}.
 * When the current output's chain empties, {@link #resume} arms the next output into this same frame.
 */
public final class SequenceFrame extends ExecFrame {

    private final PreparedGraph.Node node;
    private final List<String> outIds;
    private int cursor = 0;

    SequenceFrame(GraphExecutor scope, PreparedGraph.Node node, List<String> outIds) {
        super(scope);
        this.node = node;
        this.outIds = outIds;
    }

    @Override public Kind kind() { return Kind.SEQUENCE; }

    @Override boolean resume(ExecSession session) {
        if (cursor >= outIds.size()) return false;  // all outputs done — pop
        enqueueFlow(node, outIds.get(cursor));
        cursor++;
        return true;  // stay; the (possibly empty) chain for this output runs next
    }
}
