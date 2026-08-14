package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>Coercion rules and lookup semantics match {@link EvalContext} — see its javadoc. Like it, this
 * context is pooled and re-bound per call rather than allocated, so a node must not retain it after
 * {@code execute} returns.</p>
 */
public final class ExecContext {
    private final GraphExecutor executor;

    private PreparedGraph.Node prepared;
    private ExecSession session;
    private ExecFrame frame;
    private Object[] staged = new Object[8];
    private long[] stagedNum = new long[8];
    private byte[] stagedKind = new byte[8];
    private boolean[] stagedSet = new boolean[8];

    ExecContext(GraphExecutor executor) {
        this.executor = executor;
    }

    /** Rebind this pooled context to the node about to run. */
    void bind(PreparedGraph.Node node, ExecSession session, ExecFrame frame) {
        this.prepared = node;
        this.session = session;
        this.frame = frame;
        int n = node.outputIds.length;
        if (staged.length < n) {
            staged = new Object[Math.max(n, staged.length * 2)];
            stagedNum = new long[staged.length];
            stagedKind = new byte[staged.length];
            stagedSet = new boolean[staged.length];
        }
        Arrays.fill(stagedSet, 0, n, false);
    }

    /**
     * Publish staged outputs. Unlike the data path, an exec node's <em>unset</em> outputs are left
     * alone rather than published as null — an exec node typically writes one data output and must
     * not blank the rest. A value explicitly staged as null is also skipped, matching the old
     * {@code if (v != null)} flush.
     */
    void flush() {
        PreparedGraph.Node n = prepared;
        for (int k = 0; k < n.outputSlots.length; k++) {
            if (stagedSet[k]) {
                if (stagedKind[k] != GraphExecutor.KIND_OBJECT) {
                    executor.writeNum(n.outputSlots[k], stagedKind[k], stagedNum[k]);
                } else if (staged[k] != null) {
                    executor.writeSlot(n.outputSlots[k], staged[k]);
                }
            }
            staged[k] = null;
        }
    }


    /**
     * Release staged references without publishing them. Called when a node throws: {@link #flush}
     * is what normally nulls them, and these contexts are pooled for the executor's lifetime, so a
     * staged {@code ItemStack} would otherwise stay reachable until some later node at the same
     * pool depth happened to flush that index.
     */
    void dropStaged() {
        Arrays.fill(staged, 0, prepared.outputIds.length, null);
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
            throw new TypeMismatchException("Input '" + inputId + "' on " + prepared.uid
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
    }

    public Optional<Object> getInput(String inputId) {
        return Optional.ofNullable(pullRaw(inputId));
    }

    @Nullable
    private Object pullRaw(String inputId) {
        int idx = prepared.inputIndex(inputId);
        if (idx >= 0) return executor.pullInput(prepared, idx, Object.class);
        PortModel pm = prepared.asNodeModel == null ? null : prepared.asNodeModel.getInputsById().get(inputId);
        if (pm == null) {
            throw new IllegalArgumentException("No input port '" + inputId + "' on " + prepared.uid);
        }
        return executor.pullInput(pm, Object.class);
    }


    /**
     * The raw value feeding {@code inputId}, or {@code null}. The same thing
     * {@code getInput(inputId).orElse(null)} returns, without wrapping it in an {@link Optional}
     * only to unwrap it again — which was the last per-node allocation left on the exec path once
     * numbers stopped being boxed.
     */
    @Nullable
    public Object getInputRaw(String inputId) {
        return pullRaw(inputId);
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

    /**
     * Read a node option. The option's port is resolved at prepare time, so this is a short scan
     * plus the constant's own value — no id lookup, and none of the {@code DataResult} +
     * {@code Optional} that {@code tryGetValue(Object.class)} allocates on every call. Measured at
     * 32 bytes per read, which for the shipped library outweighed all of the executor's own boxing.
     *
     * <p>Equivalent to the old path for {@code Object.class}, and strictly safer at one edge: a
     * present-but-null option value made {@code DataResult.result()} do {@code Optional.of(null)}
     * and throw — the constant path caught that, this one never produced it.</p>
     */
    @Nullable
    private Object rawOption(String optionId) {
        int idx = prepared.optionIndex(optionId);
        if (idx >= 0) {
            Constant constant = prepared.optionPorts[idx].getEmbeddedValue();
            return constant == null ? null : constant.getValue();
        }
        if (prepared.asNodeModel == null) return null;
        INodeOption opt = prepared.asNodeModel.getNodeOptionById(optionId);
        if (opt == null) return null;
        return opt.tryGetValue(Object.class).result().orElse(null);
    }

    // ---- output writes ----------------------------------------------------------------------

    /**
     * Stage a value to be published after {@code execute} returns, for an exec node that also
     * surfaces a data output.
     *
     * <p>No node in the shipped library uses this today — the loop nodes publish {@code index} and
     * {@code item} from {@code evaluate} instead — but it is part of the documented
     * {@code AnnotatedNode} contract, so third-party exec nodes can.</p>
     */
    public void setOutput(String outputId, Object value) {
        EvalContext.stage(prepared, staged, stagedKind, stagedSet, outputId, value);
    }


    // ---- unboxed reads ------------------------------------------------------------------------

    /**
     * The value on {@code inputId} as a {@code float}.
     *
     * <p>Identical in meaning to {@code getInput(inputId, Float.class, defaultIfMissing)}: the value
     * is used when it is a {@link Number}, and {@code defaultIfMissing} is returned when it is null
     * or is anything else — a numeric-looking {@code String} is <em>not</em> parsed. Narrowing
     * follows {@code Number.floatValue()}. The difference is only that when the producing node also
     * used the primitive {@code setOutput} overload, the value never becomes an object on the way;
     * a producer that did not is read from the object lane and coerced, so mixing is safe.</p>
     *
     * @throws IllegalArgumentException if {@code inputId} is not one of this node's input ports
     */
    public float getFloat(String inputId, float defaultIfMissing) {
        int idx = prepared.inputIndex(inputId);
        if (idx < 0) return getInput(inputId, Float.class, defaultIfMissing);
        return executor.pullFloat(prepared, idx, defaultIfMissing);
    }

    /** As {@link #getFloat}, widening per {@code Number.doubleValue()}. */
    public double getDouble(String inputId, double defaultIfMissing) {
        int idx = prepared.inputIndex(inputId);
        if (idx < 0) return getInput(inputId, Double.class, defaultIfMissing);
        return executor.pullDouble(prepared, idx, defaultIfMissing);
    }

    /**
     * As {@link #getFloat}, narrowing per {@code Number.intValue()} — which <em>saturates</em>:
     * a {@code double} of 1e300 gives {@link Integer#MAX_VALUE}, not a wrapped-around value.
     */
    public int getInt(String inputId, int defaultIfMissing) {
        int idx = prepared.inputIndex(inputId);
        if (idx < 0) return getInput(inputId, Integer.class, defaultIfMissing);
        return executor.pullInt(prepared, idx, defaultIfMissing);
    }

    /** As {@link #getFloat}, narrowing per {@code Number.longValue()}. */
    public long getLong(String inputId, long defaultIfMissing) {
        int idx = prepared.inputIndex(inputId);
        if (idx < 0) return getInput(inputId, Long.class, defaultIfMissing);
        return executor.pullLong(prepared, idx, defaultIfMissing);
    }

    /**
     * The value on {@code inputId} as a {@code boolean}, or {@code defaultIfMissing}.
     *
     * <p>Only an actual {@link Boolean} counts: a number or a string is <em>not</em> interpreted as
     * truthy, it yields the default. Booleans stay in the object lane on purpose, because
     * {@code Boolean.valueOf} is cached and so costs nothing to box.</p>
     *
     * @throws IllegalArgumentException if {@code inputId} is not one of this node's input ports
     */
    public boolean getBool(String inputId, boolean defaultIfMissing) {
        return getInput(inputId, Boolean.class, defaultIfMissing);
    }

    // ---- unboxed writes -----------------------------------------------------------------------

    /**
     * Publish a {@code float} without boxing it.
     *
     * <p>An overload of {@link #setOutput(String, Object)}, so a node that already writes a
     * {@code float} binds here on recompile without its source changing — Java picks the exact-match
     * overload before it considers boxing. A consumer reading through the object lane still sees a
     * {@link Float}, exactly as before.</p>
     */
    public void setOutput(String outputId, float value) {
        stageNum(outputId, GraphExecutor.KIND_FLOAT, Float.floatToRawIntBits(value));
    }

    public void setOutput(String outputId, double value) {
        stageNum(outputId, GraphExecutor.KIND_DOUBLE, Double.doubleToRawLongBits(value));
    }

    public void setOutput(String outputId, int value) {
        stageNum(outputId, GraphExecutor.KIND_INT, value);
    }

    public void setOutput(String outputId, long value) {
        stageNum(outputId, GraphExecutor.KIND_LONG, value);
    }


    /**
     * {@code char}, {@code byte} and {@code short} deliberately keep the object lane and their own
     * wrapper types.
     *
     * <p>They exist because adding the {@code int} overload would otherwise capture them by
     * widening — {@code setOutput("out", s.charAt(i))} would start publishing an {@code Integer}
     * where it used to publish a {@code Character}, changing {@code toString} and every
     * {@code Objects.equals} downstream. Widening beats boxing in overload resolution, but an exact
     * match beats widening, so declaring them puts the binding back where it was.</p>
     */
    public void setOutput(String outputId, char value) {
        setOutput(outputId, (Object) Character.valueOf(value));
    }

    /** See {@link #setOutput(String, char)} — keeps {@code Byte} rather than widening to int. */
    public void setOutput(String outputId, byte value) {
        setOutput(outputId, (Object) Byte.valueOf(value));
    }

    /** See {@link #setOutput(String, char)} — keeps {@code Short} rather than widening to int. */
    public void setOutput(String outputId, short value) {
        setOutput(outputId, (Object) Short.valueOf(value));
    }

    private void stageNum(String outputId, byte kind, long bits) {
        boolean matched = false;
        String[] ids = prepared.outputIds;
        for (int k = 0; k < ids.length; k++) {
            if (ids[k].equals(outputId)) {
                stagedNum[k] = bits;
                stagedKind[k] = kind;
                stagedSet[k] = true;
                matched = true;
            }
        }
        if (matched || prepared.asNodeModel == null) return;
        // Same fallback the object lane has: an id the snapshot does not know is re-resolved
        // against the live model, so a stale snapshot costs a slow path rather than a lost write.
        for (PortModel live : prepared.asNodeModel.getOutputsByDisplayOrder()) {
            if (!outputId.equals(live.getPortId())) continue;
            int idx = prepared.outputIndexOf(live);
            if (idx >= 0) {
                stagedNum[idx] = bits;
                stagedKind[idx] = kind;
                stagedSet[idx] = true;
            }
        }
    }

    // ---- flow ------------------------------------------------------------------------------

    /**
     * Continue the flow into the node connected to {@code outputId} (an {@code EXECUTION_FLOW}
     * output), within the current frame. No-op if the port is unwired.
     */
    public void flow(String outputId) {
        int idx = prepared.flowIndex(outputId);
        if (idx < 0) {
            throw new IllegalArgumentException("No output port '" + outputId + "' on " + prepared.uid);
        }
        frame.enqueueAll(prepared.flowTargets[idx]);
    }

    /**
     * Start a run-to-completion sequence: each {@code outId} in order has its whole chain run before
     * the next begins. Replaces the {@code Sequence} node's per-output {@code runIsolated}.
     */
    public void pushSequence(List<String> outIds) {
        session.push(new SequenceFrame(executor, prepared, outIds));
    }

    /**
     * Start a step-able loop. The engine drives {@code controller} for iterations, flowing
     * {@code bodyOut} into the loop frame each iteration and {@code completedOut} into the parent
     * frame when the loop ends. Replaces the loop nodes' synchronous {@code runIsolated} drive.
     */
    public void pushLoop(LoopController controller, String bodyOut, String completedOut) {
        session.push(new LoopFrame(executor, prepared, controller, bodyOut, completedOut, frame));
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
        int idx = prepared.inputIndex(inputId);
        PortModel pm = idx >= 0 ? prepared.inputPorts[idx]
                : (prepared.asNodeModel == null ? null : prepared.asNodeModel.getInputsById().get(inputId));
        if (pm == null) throw new IllegalArgumentException("No input port '" + inputId + "' on " + prepared.uid);
        var result = new ArrayList<NodeModel>();
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
        return executor.nodeState(prepared.model.getUid());
    }

    // ---- meta ------------------------------------------------------------------------------

    public NodeModel getNode() {
        return prepared.asNodeModel;
    }

    public GraphExecutor getExecutor() {
        return executor;
    }
}
