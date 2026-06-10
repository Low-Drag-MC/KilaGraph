package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-call context for an {@code AnnotatedNode#execute} invocation. Mirrors {@link EvalContext}
 * for the data-pull surface ({@link #getInput} / {@link #getOption} / {@link #setOutput}) and
 * adds:
 * <ul>
 *   <li>{@link #flow(String)} — push the node connected to {@code outputId} (an
 *       {@code EXECUTION_FLOW} output) onto the executor's pending queue. Call zero or more times
 *       per execute() invocation. {@code Sequence} calls it N times; {@code Branch} calls it once
 *       on the picked branch; an exec node that does nothing downstream calls it zero times.</li>
 *   <li>{@link #state()} — a node-scoped {@code Map<String, Object>} lazily allocated in the
 *       executor's {@code nodeState}, surviving across {@link GraphExecutor#executeFrom} calls
 *       on the same executor instance. Future {@code Cache} reads/writes here; for loops use it
 *       to stash counters; debug nodes can stash captured values.</li>
 *   <li>{@link #runUntilDrained()} — internal hook for loop nodes that need to drain the body's
 *       sub-execution to completion before deciding to iterate again.</li>
 * </ul>
 *
 * <p>Coercion rules and lookup semantics match {@link EvalContext} — see its javadoc.</p>
 */
public final class ExecContext {
    private final GraphExecutor executor;
    private final NodeModel node;
    final Map<String, Object> outputs = new HashMap<>();  // flushed into the executor's cache by the run loop

    ExecContext(GraphExecutor executor, NodeModel node) {
        this.executor = executor;
        this.node = node;
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
     * Enqueue the node connected to {@code outputId} (an {@code EXECUTION_FLOW} output) onto the
     * executor's pending exec queue. No-op if the port is unwired.
     */
    public void flow(String outputId) {
        PortModel out = node.getOutputsById().get(outputId);
        if (out == null) {
            throw new IllegalArgumentException("No output port '" + outputId + "' on " + node.getUid());
        }
        executor.enqueueFlow(out);
    }

    // ---- wire topology ---------------------------------------------------------------------

    /**
     * The source nodes wired into {@code inputId}, read straight from the port topology without
     * evaluating them. Used by meta-nodes like {@code CacheClear} that act <em>on</em> an upstream
     * node (here: the {@code Cache} feeding its {@code ref} input) rather than on its value — so
     * pulling the input (which would force the upstream {@code Cache} to compute) is exactly what we
     * must avoid.
     */
    public java.util.List<NodeModel> connectedSourceNodes(String inputId) {
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

    /**
     * Run {@code flowPush} (typically a single {@code flow(outId)}) with an isolated exec queue:
     * saves the outer pending queue aside, drains whatever {@code flowPush} enqueues to completion,
     * then restores the outer queue. {@link BreakException} / {@link ContinueException} thrown
     * inside propagate up to the caller after the queue is restored.
     *
     * <p>Used by two kinds of node:</p>
     * <ul>
     *   <li><b>Loops</b> ({@code For}/{@code While}/{@code ForEach}) — one call per iteration, so the
     *       body runs fully before the loop decides whether to iterate again, and so the loop catches
     *       {@code Break}/{@code Continue} at its own level.</li>
     *   <li><b>{@code Sequence}</b> — one call per output, so each output's chain runs to completion
     *       before the next starts (run-to-completion semantics, not breadth-first interleaving).</li>
     * </ul>
     */
    public void runIsolated(Runnable flowPush) {
        executor.runIsolated(flowPush);
    }
}
