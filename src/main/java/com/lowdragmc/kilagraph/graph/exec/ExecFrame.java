package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

import java.util.Arrays;
import java.util.List;

/**
 * One activation record on the {@link ExecSession}'s exec-flow stack. Each frame owns a queue of
 * nodes pending execution <em>within this frame's scope</em> and a {@link GraphExecutor scope} the
 * nodes run in (the same as the session's root scope, except {@link SubgraphFrame} which switches to
 * a child executor). The session pops one node per {@code step()} from the topmost non-empty frame.
 *
 * <p>When a frame's queue empties, the session calls {@link #resume(ExecSession)}: a plain
 * {@link ChainFrame} is done (returns false → pop), while {@link SequenceFrame}/{@link LoopFrame}/
 * {@link SubgraphFrame} use it as a continuation hook to re-arm the next output / iteration / harvest
 * step (return true to stay on the stack).</p>
 *
 * <p>The queue is a plain array rather than an {@code ArrayDeque} so that a frame can be reused
 * across runs without reallocating, and so enqueueing a pre-resolved list of exec targets is an
 * array copy.</p>
 */
public abstract class ExecFrame {

    /** Frame kind — purely for {@link ExecSession#callStack()} introspection. */
    public enum Kind { CHAIN, SEQUENCE, LOOP, SUBGRAPH }

    GraphExecutor scope;

    private NodeModel[] pending = new NodeModel[8];
    private int head;
    private int tail;

    protected ExecFrame(GraphExecutor scope) {
        this.scope = scope;
    }

    public abstract Kind kind();

    // ---- queue ------------------------------------------------------------------------------

    /** Enqueue pre-resolved exec targets — what {@link ExecContext#flow} hands us. */
    void enqueueAll(NodeModel[] targets) {
        if (targets.length == 0) return;
        ensureRoom(targets.length);
        System.arraycopy(targets, 0, pending, tail, targets.length);
        tail += targets.length;
    }

    /** Enqueue the node(s) wired to {@code outputPort}. For paths that hold a raw port. */
    void enqueueFlow(PortModel outputPort) {
        if (outputPort == null) return;
        List<PortModel> connected = outputPort.getConnectedPorts();
        for (int i = 0; i < connected.size(); i++) {
            if (connected.get(i).getNodeModel() instanceof NodeModel nm) enqueueOne(nm);
        }
    }

    /** Enqueue the downstream of {@code node}'s exec output {@code outId} (resolved, no wire walk). */
    void enqueueFlow(PreparedGraph.Node node, String outId) {
        if (node == null) return;
        int idx = node.flowIndex(outId);
        if (idx >= 0) enqueueAll(node.flowTargets[idx]);
    }

    void enqueueOne(NodeModel node) {
        ensureRoom(1);
        pending[tail++] = node;
    }

    private void ensureRoom(int n) {
        if (tail + n <= pending.length) return;
        int size = tail - head;
        if (size + n <= pending.length) {
            System.arraycopy(pending, head, pending, 0, size);
            Arrays.fill(pending, size, tail, null);
        } else {
            NodeModel[] bigger = new NodeModel[Math.max(size + n, pending.length * 2)];
            System.arraycopy(pending, head, bigger, 0, size);
            pending = bigger;
        }
        head = 0;
        tail = size;
    }

    boolean hasPending() {
        return head < tail;
    }

    NodeModel poll() {
        NodeModel n = pending[head];
        pending[head++] = null;
        if (head == tail) head = tail = 0;
        return n;
    }

    /** Peek the next node this frame would run (without removing it). Null if none pending. */
    NodeModel peek() {
        return head < tail ? pending[head] : null;
    }

    void clearPending() {
        Arrays.fill(pending, head, tail, null);
        head = tail = 0;
    }

    /** Ready this frame for reuse by a later run. */
    void reset(GraphExecutor scope) {
        clearPending();
        this.scope = scope;
    }

    /**
     * Called by the session when the queue is empty, to decide what happens next.
     * @return {@code true} to keep this frame on the stack (it re-armed work or wants another
     *         iteration), {@code false} to pop it.
     */
    abstract boolean resume(ExecSession session);
}
