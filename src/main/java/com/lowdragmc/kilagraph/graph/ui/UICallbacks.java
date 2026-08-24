package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;

/**
 * The re-entry mechanism behind every deferred exec output — the {@code onEvent} of
 * {@code ldlib2_ui_on_event}, the {@code onCall} of {@code ldlib2_ui_rpc_define}, and so on.
 *
 * <h2>The problem</h2>
 * A UI graph runs once to build its tree, but a button click happens minutes later. Exec outputs
 * fire synchronously, so an ordinary node cannot express "and then, whenever this happens, run that
 * chain". Unreal solves it with a Bind Event node that has two exec outputs: one continues the
 * current flow, the other is entered later.
 *
 * <h2>The mechanism</h2>
 * A deferred node runs twice, in two <em>phases</em>, and tells them apart by a flag in its own
 * per-node state:
 *
 * <ol>
 *   <li><b>Register.</b> The first run, on the normal build flow. The node subscribes to whatever it
 *       is waiting for, handing the callback a {@link Trampoline}, then flows its {@code then}
 *       output and the build continues.</li>
 *   <li><b>Dispatch.</b> Later, on whatever thread the UI fires on, the trampoline writes the phase
 *       flag plus the callback payload into node state and calls
 *       {@link GraphExecutor#executeFrom(NodeModel)} on <em>the same node</em>. The node sees the
 *       flag, publishes the payload to its data outputs, and flows {@code onEvent} instead.</li>
 * </ol>
 *
 * <p>This needs no change to the executor. {@code executeFrom} already handles re-entrancy (it
 * allocates a fresh {@code ExecSession} when the pooled one is busy), and {@code clearCache()}
 * already clears the pull cache <em>without</em> touching node state — which is exactly the split
 * this needs, and the same one the loop nodes rely on to keep an index alive across a nested loop's
 * cache clear.</p>
 *
 * <h2>Why the cache is cleared on every dispatch</h2>
 * The handler is a fresh question asked of a UI that has changed since it was built. Without the
 * clear, a {@code ldlib2_ui_get_value} in the handler would answer with the value memoised while
 * the tree was being assembled — the button's caption at build time, not the text field's contents
 * now. That is a silent wrong answer rather than a crash, so it is worth the recomputation.
 *
 * <h2>Threading</h2>
 * A dispatch runs the graph on the caller's thread: the render thread for a click, the server thread
 * for a server event or an incoming RPC. A {@link GraphExecutor} is not thread-safe, so a graph must
 * not be shared across sides — each side builds its UI with its own executor. That is already how a
 * host has to do it, because {@code UISyncManager} identifies sync values by registration order and
 * therefore needs both sides to run the same graph independently.
 */
public final class UICallbacks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Node-state key holding the exec output id to fire, present only during a dispatch. */
    private static final String PHASE = "__kg_ui_phase__";
    /** Node-state key holding the callback payload for the current dispatch. */
    private static final String PAYLOAD = "__kg_ui_payload__";

    private UICallbacks() {}

    /**
     * The exec output this node should fire right now, or {@code null} when this is the ordinary
     * registration run.
     *
     * <p>Deliberately returns the output id rather than a boolean: a node with more than one
     * deferred output needs to know <em>which</em> callback woke it.</p>
     */
    @Nullable
    public static String dispatchOutput(ExecContext ctx) {
        Object phase = ctx.state().get(PHASE);
        return phase instanceof String s ? s : null;
    }

    /**
     * The whole dispatch half of a deferred node: if this run is a callback rather than the
     * registration pass, flow the deferred output and say so.
     *
     * <pre>{@code
     * public void execute(ExecContext ctx) {
     *     if (UICallbacks.relayDispatch(ctx)) return;
     *     ... subscribe, then ctx.flow("next") ...
     * }
     * }</pre>
     *
     * <p>Every deferred node opens with this. Sharing it is not only brevity — the prologue is easy to
     * get subtly wrong (flowing {@code next} as well, or falling through and re-subscribing on every
     * event), and both mistakes look like the node working until you count.</p>
     */
    public static boolean relayDispatch(ExecContext ctx) {
        String outputId = dispatchOutput(ctx);
        if (outputId == null) return false;
        ctx.flow(outputId);
        return true;
    }

    /** One value from the payload of the dispatch in progress, or {@code null} outside one. */
    @Nullable
    public static Object payload(ExecContext ctx, String key) {
        return payloadOf(ctx.state(), key);
    }

    /**
     * The pull-side counterpart of {@link #payload(ExecContext, String)}, for the {@code evaluate}
     * half of a deferred node.
     *
     * <p>A deferred node publishes its data outputs from {@code evaluate} reading node state, not
     * from {@code execute} writing them once — the same convention the loop nodes use for their
     * per-iteration index, and for the same reason: the handler chain pulls those outputs after the
     * cache has been cleared, so the value has to be re-derivable rather than cached.</p>
     */
    @Nullable
    public static Object payload(EvalContext ctx, String key) {
        var node = ctx.getNode();
        if (node == null) return null;
        return payloadOf(ctx.getExecutor().nodeState(node.getUid()), key);
    }

    @Nullable
    private static Object payloadOf(Map<String, Object> state, String key) {
        return state.get(PAYLOAD) instanceof Map<?, ?> map ? map.get(key) : null;
    }

    /** Per-node state reachable from the pull side, for deferred nodes that stash their own things. */
    public static Map<String, Object> state(EvalContext ctx) {
        var node = ctx.getNode();
        return node == null ? Map.of() : ctx.getExecutor().nodeState(node.getUid());
    }

    /** Node-state key recording whether the registration pass actually subscribed to anything. */
    private static final String REGISTERED = "__kg_ui_registered__";

    /**
     * Records the outcome of the registration pass and publishes it as {@code ok}.
     *
     * <p>It goes into node state as well as onto the port because the port's staged value does not
     * survive the cache clear that precedes a dispatch — and a graph asking "did this listener get
     * attached" during a handler deserves the real answer rather than {@code false}.</p>
     */
    public static void markRegistered(ExecContext ctx, boolean registered) {
        ctx.state().put(REGISTERED, registered);
        ctx.setOutput("ok", registered);
    }

    /** Republishes {@code ok} on the pull side. Call from a deferred node's {@code evaluate}. */
    public static void publishRegistered(EvalContext ctx) {
        ctx.setOutput("ok", Boolean.TRUE.equals(state(ctx).get(REGISTERED)));
    }

    /**
     * Captures everything the callback will need to re-enter the graph at {@code outputId} of the
     * node currently executing. Call this during the registration phase and hand the result to
     * whatever will fire later.
     */
    public static Trampoline arm(ExecContext ctx, String outputId) {
        return new Trampoline(ctx.getExecutor(), ctx.getNode(), outputId);
    }

    /**
     * A one-way door back into the graph. Holds the executor and the node it belongs to, so it keeps
     * the graph alive for as long as the UI holds the listener — which is what a host wants: the
     * handler must outlive the call that built the tree.
     */
    public record Trampoline(GraphExecutor executor, NodeModel node, String outputId) {

        /** Runs the deferred chain with no payload. */
        public void fire() {
            fire(Map.of());
        }

        /**
         * Runs the deferred chain, exposing {@code payload} to the node's data outputs for the
         * duration.
         *
         * <p>Never throws: a listener is called from LDLib2's dispatch loop, and letting a broken
         * handler escape would take down the whole event — and with it every other listener on the
         * element. The failure is logged with the node's identity instead.</p>
         *
         * <p>A chain that has to hand something <em>back</em> — an RPC answering its caller — does not
         * do it through a return value here, because the value is produced by a node further down the
         * chain rather than by the chain's own completion. That case goes through the call stack in
         * {@code UIRpcCalls} instead.</p>
         */
        public void fire(Map<String, Object> payload) {
            if (node == null) return;
            var state = executor.nodeState(node.getUid());
            // Save and restore rather than clear: a handler is allowed to trigger the same node
            // again (a tick handler that dispatches a synthetic click on its own element), and the
            // outer dispatch still has to see its own payload when the inner one unwinds.
            Object previousPhase = state.get(PHASE);
            Object previousPayload = state.get(PAYLOAD);
            state.put(PHASE, outputId);
            state.put(PAYLOAD, payload);
            try {
                executor.clearCache();
                executor.executeFrom(node);
            } catch (Throwable t) {
                LOGGER.error("KilaGraph UI callback '{}' on node {} failed", outputId, node.getUid(), t);
            } finally {
                restore(state, PHASE, previousPhase);
                restore(state, PAYLOAD, previousPayload);
            }
        }

        private static void restore(Map<String, Object> state, String key, @Nullable Object previous) {
            if (previous == null) {
                state.remove(key);
            } else {
                state.put(key, previous);
            }
        }
    }
}
