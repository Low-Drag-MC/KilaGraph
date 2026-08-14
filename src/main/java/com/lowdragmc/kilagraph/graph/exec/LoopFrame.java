package com.lowdragmc.kilagraph.graph.exec;

/**
 * A loop activation: each time its body drains, {@link #resume} asks the {@link LoopController}
 * whether to run another iteration (re-arming {@code body} into this frame) or to finish (firing
 * {@code completed} into the parent frame and popping). Replaces the loop nodes' synchronous
 * {@code runIsolated} + {@code Break}/{@code Continue} exception handling.
 *
 * <p>{@code Break}/{@code Continue} are delivered by {@link ExecSession#applySignal()} popping
 * sub-frames down to this loop: CONTINUE leaves the frame with an empty queue so the next
 * {@link #resume} re-iterates; BREAK calls {@link #markBreak()} so the next {@link #resume}
 * completes instead.</p>
 */
public final class LoopFrame extends ExecFrame {

    private final PreparedGraph.Node node;
    private final LoopController controller;
    private final String bodyOut;
    private final String completedOut;
    private final ExecFrame parent;
    private boolean breaking = false;

    LoopFrame(GraphExecutor scope, PreparedGraph.Node node, LoopController controller,
              String bodyOut, String completedOut, ExecFrame parent) {
        super(scope);
        this.node = node;
        this.controller = controller;
        this.bodyOut = bodyOut;
        this.completedOut = completedOut;
        this.parent = parent;
    }

    @Override public Kind kind() { return Kind.LOOP; }

    /** Marked by a BREAK signal: complete on the next resume instead of iterating. */
    void markBreak() {
        breaking = true;
    }

    @Override boolean resume(ExecSession session) {
        if (!breaking && controller.hasNext(scope)) {
            controller.beginIteration(scope);
            enqueueFlow(node, bodyOut);   // body runs in THIS frame
            return true;
        }
        // loop finished (normally or via break): continue after the loop in the parent frame
        parent.enqueueFlow(node, completedOut);
        return false;
    }
}
