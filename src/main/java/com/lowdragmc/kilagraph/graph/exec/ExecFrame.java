package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One activation record on the {@link ExecSession}'s exec-flow stack. Each frame owns a queue of
 * nodes pending execution <em>within this frame's scope</em> and a {@link GraphExecutor scope} the
 * nodes run in (the same as the session's root scope, except {@link SubgraphFrame} which switches to
 * a child executor). The session pops one node per {@code step()} from the topmost non-empty frame.
 *
 * <p>When a frame's {@link #pending} empties, the session calls {@link #resume(ExecSession)}: a plain
 * {@link ChainFrame} is done (returns false → pop), while {@link SequenceFrame}/{@link LoopFrame}/
 * {@link SubgraphFrame} use it as a continuation hook to re-arm the next output / iteration / harvest
 * step (return true to stay on the stack).</p>
 */
public abstract class ExecFrame {

    /** Frame kind — purely for {@link ExecSession#callStack()} introspection. */
    public enum Kind { CHAIN, SEQUENCE, LOOP, SUBGRAPH }

    final GraphExecutor scope;
    final Deque<NodeModel> pending = new ArrayDeque<>();

    protected ExecFrame(GraphExecutor scope) {
        this.scope = scope;
    }

    public abstract Kind kind();

    /** Enqueue the node(s) wired to {@code outputPort} (within this frame's scope). No-op if unwired. */
    void enqueueFlow(PortModel outputPort) {
        if (outputPort == null) return;
        for (PortModel connected : outputPort.getConnectedPorts()) {
            if (connected.getNodeModel() instanceof NodeModel nm) pending.addLast(nm);
        }
    }

    /** Enqueue the downstream of {@code node}'s output {@code outId} (within this frame's scope). */
    void enqueueFlow(NodeModel node, String outId) {
        if (node == null) return;
        enqueueFlow(node.getOutputsById().get(outId));
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    NodeModel poll() {
        return pending.pollFirst();
    }

    /** Peek the next node this frame would run (without removing it). Null if none pending. */
    NodeModel peek() {
        return pending.peekFirst();
    }

    void clearPending() {
        pending.clear();
    }

    /**
     * Called by the session when {@link #pending} is empty, to decide what happens next.
     * @return {@code true} to keep this frame on the stack (it re-armed work or wants another
     *         iteration), {@code false} to pop it.
     */
    abstract boolean resume(ExecSession session);
}
