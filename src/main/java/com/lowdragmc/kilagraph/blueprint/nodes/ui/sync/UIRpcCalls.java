package com.lowdragmc.kilagraph.blueprint.nodes.ui.sync;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The in-flight RPC call, so {@code ldlib2_ui_rpc_return} has somewhere to put its answer.
 *
 * <h2>Why this is not just node state</h2>
 * The obvious place for a return value is the defining node's own state, written by the return node.
 * But the return node cannot reach the defining node: all it holds is the {@code rpc} handle, and
 * that handle may have travelled through a graph variable or a subgraph boundary, so the topological
 * trick {@code CacheClear} uses ({@code connectedSourceNodes}) would find the wrong node or none.
 *
 * <p>What the two nodes reliably share is the {@link RPCEvent} itself, and the fact that a call is
 * synchronous: {@code UISyncManager.handEvent} invokes the executor and writes the return value from
 * whatever it gets back, all on one thread without yielding. So a stack keyed by the event is exactly
 * the right shape — {@link #open} before the handler runs, {@link #close} after, and the return node
 * writes into the innermost frame in between.
 *
 * <p>A stack rather than a single slot because handlers nest: a handler is free to send another RPC,
 * and on a single-player integrated server the answer to <em>that</em> can arrive before the first
 * one has finished. The frame is per thread for the same reason the executor is — client and server
 * each run their own.</p>
 */
final class UIRpcCalls {

    /** One in-flight call: the event being handled, and the value the graph has answered with. */
    private static final class Frame {
        final RPCEvent event;
        @Nullable Object returnValue;

        Frame(RPCEvent event) {
            this.event = event;
        }
    }

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private UIRpcCalls() {
    }

    /** Marks the start of handling {@code event}. Always pair with {@link #close} in a finally. */
    static void open(RPCEvent event) {
        STACK.get().push(new Frame(event));
    }

    /** Ends the innermost call and yields whatever {@code ldlib2_ui_rpc_return} left in it. */
    @Nullable
    static Object close() {
        Deque<Frame> stack = STACK.get();
        Frame frame = stack.poll();
        // Leaving an empty deque behind would keep a ThreadLocal alive on every thread that ever
        // handled one call; the map itself is tiny, but a client runs for hours.
        if (stack.isEmpty()) STACK.remove();
        return frame == null ? null : frame.returnValue;
    }

    /**
     * Answers the call {@code event} belongs to, or the innermost call when {@code event} is null.
     *
     * @return false when no matching call is in progress — i.e. the graph called
     *         {@code ldlib2_ui_rpc_return} outside a handler, which is worth reporting rather than
     *         silently dropping.
     */
    static boolean answer(@Nullable RPCEvent event, @Nullable Object value) {
        for (Frame frame : STACK.get()) {
            if (event == null || frame.event == event) {
                frame.returnValue = value;
                return true;
            }
        }
        return false;
    }
}
