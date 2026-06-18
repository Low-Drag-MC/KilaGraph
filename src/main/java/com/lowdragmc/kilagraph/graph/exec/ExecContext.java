package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-call context for an {@code AnnotatedNode#execute} invocation, bound to the
 * {@link ExecSession} that's driving the run and the {@link ExecFrame} the node is executing in.
 * Mirrors {@link EvalContext} for the data surface ({@link #getInput}/{@link #getOption}/
 * {@link #setOutput}) and adds the exec-flow control surface:
 * <ul>
 *   <li>{@link #flow(String)} — continue the flow into the node connected to {@code outputId}
 *       (within the current frame). Call zero or more times. {@code Branch} calls it once on the
 *       picked branch; an exec node with nothing downstream calls it zero times.</li>
 *   <li>{@link #pushSequence(List)} — run-to-completion fan-out (the {@code Sequence} node).</li>
 *   <li>{@link #pushLoop(LoopController, String, String)} — start a step-able loop (For/While/
 *       ForEach); the engine drives the iterations.</li>
 *   <li>{@link #signalBreak()}/{@link #signalContinue()} — loop control (the {@code Break}/
 *       {@code Continue} nodes), routed to the nearest enclosing loop frame.</li>
 *   <li>{@link #state()} — node-scoped persistent map (survives across the run on this executor).</li>
 * </ul>
 *
 * <p>Coercion rules and lookup semantics match {@link EvalContext} — see its javadoc.</p>
 */
public final class ExecContext {
    private final GraphExecutor executor;
    private final NodeModel node;
    private final ExecSession session;
    private final ExecFrame frame;
    final Map<String, Object> outputs = new HashMap<>();  // flushed into the executor's cache by the run loop

    ExecContext(GraphExecutor executor, NodeModel node, ExecSession session, ExecFrame frame) {
        this.executor = executor;
        this.node = node;
        this.session = session;
        this.frame = frame;
    }

    // ---- input port reads (same shape as EvalContext) --------------------------------------

    public <T> T getInput(String inputId, Class<T> type, T defaultIfMissing) {
        Object raw = pullRaw(inputId);
        T t = EvalContext.coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    public <T> T getInput(String inputId, Class<T> type) {
        Object raw = pullRaw(inputId);
        T t = EvalContext.coerce(raw, type);
        if (t == null) {
            throw new TypeMismatchException("Input '" + inputId + "' on " + node.getUid()
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
    }

    public Optional<Object> getInput(String inputId) {
        return Optional.ofNullable(pullRaw(inputId));
    }

    @Nullable
    private Object pullRaw(String inputId) {
        PortModel pm = node.getInputsById().get(inputId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + inputId + "' on " + node.getUid());
        return executor.pullInput(pm, Object.class);
    }

    // ---- option reads -----------------------------------------------------------------------

    public <T> T getOption(String optionId, Class<T> type, T defaultIfMissing) {
        Object raw = rawOption(optionId);
        T t = EvalContext.coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    public Optional<Object> getOption(String optionId) {
        return Optional.ofNullable(rawOption(optionId));
    }

    @Nullable
    private Object rawOption(String optionId) {
        INodeOption opt = node.getNodeOptionById(optionId);
        if (opt == null) return null;
        return opt.tryGetValue(Object.class).result().orElse(null);
    }

    // ---- output writes ----------------------------------------------------------------------

    /**
     * Stage a value to be flushed into the executor's port cache after {@code execute} returns.
     * Used by exec nodes that also surface a data value (e.g. {@code For.index}).
     */
    public void setOutput(String outputId, Object value) {
        outputs.put(outputId, value);
    }

    // ---- flow ------------------------------------------------------------------------------

    /**
     * Continue the flow into the node connected to {@code outputId} (an {@code EXECUTION_FLOW}
     * output), within the current frame. No-op if the port is unwired.
     */
    public void flow(String outputId) {
        PortModel out = node.getOutputsById().get(outputId);
        if (out == null) {
            throw new IllegalArgumentException("No output port '" + outputId + "' on " + node.getUid());
        }
        frame.enqueueFlow(out);
    }

    /**
     * Start a run-to-completion sequence: each {@code outId} in order has its whole chain run before
     * the next begins. Replaces the {@code Sequence} node's per-output {@code runIsolated}.
     */
    public void pushSequence(List<String> outIds) {
        session.push(new SequenceFrame(executor, node, outIds));
    }

    /**
     * Start a step-able loop. The engine drives {@code controller} for iterations, flowing
     * {@code bodyOut} into the loop frame each iteration and {@code completedOut} into the parent
     * frame when the loop ends. Replaces the loop nodes' synchronous {@code runIsolated} drive.
     */
    public void pushLoop(LoopController controller, String bodyOut, String completedOut) {
        session.push(new LoopFrame(executor, node, controller, bodyOut, completedOut, frame));
    }

    /** Raise a {@code Break}: the nearest enclosing loop frame ends after this node. */
    public void signalBreak() {
        session.signalBreak();
    }

    /** Raise a {@code Continue}: the nearest enclosing loop frame skips to its next iteration. */
    public void signalContinue() {
        session.signalContinue();
    }

    // ---- wire topology ---------------------------------------------------------------------

    /**
     * The source nodes wired into {@code inputId}, read straight from the port topology without
     * evaluating them. Used by meta-nodes like {@code CacheClear} that act <em>on</em> an upstream
     * node (here: the {@code Cache} feeding its {@code ref} input) rather than on its value.
     */
    public List<NodeModel> connectedSourceNodes(String inputId) {
        PortModel pm = node.getInputsById().get(inputId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + inputId + "' on " + node.getUid());
        var result = new java.util.ArrayList<NodeModel>();
        for (PortModel connected : pm.getConnectedPorts()) {
            if (connected.getNodeModel() instanceof NodeModel nm) result.add(nm);
        }
        return result;
    }

    // ---- per-node state --------------------------------------------------------------------

    /**
     * Returns this node's persistent state map (lazy-allocated). Lifetime = this {@link GraphExecutor}
     * instance.
     */
    public Map<String, Object> state() {
        return executor.nodeState(node.getUid());
    }

    // ---- meta ------------------------------------------------------------------------------

    public NodeModel getNode() {
        return node;
    }

    public GraphExecutor getExecutor() {
        return executor;
    }
}
