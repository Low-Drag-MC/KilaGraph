package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Per-evaluation context: passes graph state into a single node's {@code evaluate(...)} call.
 * <p>
 * Three ways to read values:
 * <ul>
 *   <li>{@link #getInput} — input port; resolves wire-pull or embedded-constant value.</li>
 *   <li>{@link #getOption} — node option; never wire-resolved.</li>
 *   <li>{@link #setOutput} — write an output port value (published by the executor).</li>
 * </ul>
 *
 * <p>Coercion is liberal. The "with default" overloads never throw — types in the graph are
 * declared loosely (option-driven, UNKNOWN, etc.) and each node is responsible for deciding
 * what to do when an upstream value doesn't fit. Coercion rules applied in order:</p>
 * <ol>
 *   <li>{@code null} or not a {@code Number}/{@code String}/already-of-type → returns {@code null} or the default.</li>
 *   <li>Target {@code Number} (or wrapper) ← any {@code Number}: use {@code Number.xxxValue()}.</li>
 *   <li>Target {@code String}: use {@code toString()}.</li>
 *   <li>Target {@code Boolean} ← any {@code Boolean}: pass-through. Numbers / strings / null
 *       do <em>not</em> auto-coerce to boolean — return the default.</li>
 *   <li>Otherwise return the default. ({@link #getInput(String, Class)} no-default form throws
 *       {@link TypeMismatchException} instead.)</li>
 * </ol>
 *
 * <p>Instances are pooled by the executor and re-bound per evaluation ({@link #bind}), so a run
 * allocates no contexts at all. A node must therefore not retain its {@code ctx} past the end of
 * {@code evaluate} — which was already true, since the context was per-call to begin with.
 * Staged outputs land in a reused array rather than a {@code HashMap}; they stay invisible to
 * re-entrant pulls until {@link #flush}, exactly as the old staging map did.</p>
 *
 * <p>Single-threaded by contract; the node instance never owns evaluation state, so a future
 * async/parallel executor can hand each evaluation its own {@code EvalContext}.</p>
 */
public final class EvalContext {
    private final GraphExecutor executor;

    private PreparedGraph.Node prepared;
    private Object[] staged = new Object[8];
    private long[] stagedNum = new long[8];
    private byte[] stagedKind = new byte[8];
    /**
     * Which outputs this evaluation has staged, as a generation stamp rather than a flag array.
     *
     * <p>{@code bind} used to clear a {@code boolean[]} with {@code Arrays.fill} on every node
     * evaluation. Bumping a counter is O(1) and the comparison costs the same as reading a flag. A
     * bitmask would be smaller still and is the obvious alternative, but a {@code long} caps the
     * node at 64 outputs, and {@code Sequence} and {@code Switch} are as wide as their option says.</p>
     */
    private int[] stagedStamp = new int[8];
    private int stagedGen;
    /** Whether anything was staged at all, so a node that writes no data output can skip the flush. */
    private boolean anyStaged;

    EvalContext(GraphExecutor executor) {
        this.executor = executor;
    }

    /** Rebind this pooled context to a node about to be evaluated. */
    void bind(PreparedGraph.Node node) {
        this.prepared = node;
        int n = node.outputIds.length;
        if (staged.length < n) {
            staged = new Object[Math.max(n, staged.length * 2)];
            stagedNum = new long[staged.length];
            stagedKind = new byte[staged.length];
            // Fresh zeros, and stagedGen is never 0, so every slot reads as unstaged.
            stagedStamp = new int[staged.length];
        }
        if (++stagedGen == Integer.MAX_VALUE) {
            // Wrap-around would make an ancient stamp look current. Effectively never taken.
            Arrays.fill(stagedStamp, 0);
            stagedGen = 1;
        }
        anyStaged = false;
    }

    /**
     * Publish the staged outputs. Every output port is written, including the ones the node never
     * set — they become {@code null}, which is what made a second pull of the same node a cache hit
     * rather than a re-evaluation.
     */
    void flush() {
        PreparedGraph.Node n = prepared;
        for (int k = 0; k < n.outputSlots.length; k++) {
            int slot = n.outputSlots[k];
            if (stagedStamp[k] != stagedGen) {
                executor.writeSlot(slot, null);
            } else if (stagedKind[k] == GraphExecutor.KIND_OBJECT) {
                executor.writeSlot(slot, staged[k]);
            } else {
                executor.writeNum(slot, stagedKind[k], stagedNum[k]);
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

    // ---- input port reads --------------------------------------------------------------------

    /** Typed read with default. Never throws — returns {@code defaultIfMissing} on null / unrepresentable. */
    public <T> T getInput(String inputId, Class<T> type, T defaultIfMissing) {
        Object raw = pullRaw(inputId);
        T t = coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    /** Typed read; throws {@link TypeMismatchException} if the value is null or unrepresentable. */
    public <T> T getInput(String inputId, Class<T> type) {
        Object raw = pullRaw(inputId);
        T t = coerce(raw, type);
        if (t == null) {
            throw new TypeMismatchException("Input '" + inputId + "' on " + prepared.uid
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
    }

    /** Untyped read; useful for dynamic ports. */
    public Optional<Object> getInput(String inputId) {
        return Optional.ofNullable(pullRaw(inputId));
    }

    @Nullable
    private Object pullRaw(String inputId) {
        int idx = prepared.inputIndex(inputId);
        if (idx >= 0) return executor.pullInput(prepared, idx, Object.class);
        // Not in the snapshot: either a genuinely unknown id, or a snapshot the graph has outgrown.
        // Ask the live model so staleness costs a slow path rather than a wrong answer.
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

    // ---- option reads ------------------------------------------------------------------------

    public <T> T getOption(String optionId, Class<T> type, T defaultIfMissing) {
        Object raw = rawOption(optionId);
        T t = coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    public <T> T getOption(String optionId, Class<T> type) {
        Object raw = rawOption(optionId);
        T t = coerce(raw, type);
        if (t == null) {
            throw new TypeMismatchException("Option '" + optionId + "' on " + prepared.uid
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
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
            // An option port is an input port, so its Constant is already in the prepared table and
            // this costs an array read rather than getEmbeddedValue()'s name-keyed hash lookup. The
            // value itself is still read live, so an edited option is visible immediately.
            int inputIdx = executor.optionPreresolve() ? prepared.optionInputIndex[idx] : -1;
            Constant constant = inputIdx >= 0 ? prepared.inputConstants[inputIdx]
                    : prepared.optionPorts[idx].getEmbeddedValue();
            return constant == null ? null : constant.getValue();
        }
        if (prepared.asNodeModel == null) return null;
        INodeOption opt = prepared.asNodeModel.getNodeOptionById(optionId);
        if (opt == null) return null;
        return opt.tryGetValue(Object.class).result().orElse(null);
    }

    // ---- output writes -----------------------------------------------------------------------

    /**
     * Stage a value for an output port; published by {@link #flush} after {@code evaluate} returns.
     * An id that is not one of this node's outputs is dropped, as it always was — the executor only
     * ever published values for real ports.
     */
    public void setOutput(String outputId, Object value) {
        if (stage(prepared, staged, stagedKind, stagedStamp, stagedGen, outputId, value)) anyStaged = true;
    }

    /**
     * Shared by both contexts. Two details exist to match the old flush exactly rather than
     * approximately, since a node must not be able to tell the difference:
     * <ul>
     *   <li>it writes <em>every</em> output whose {@code portId} matches, not just the first — the
     *       old flush looped the ports comparing {@code out.getPortId()}, and a top-level port and
     *       a sub-port can share a {@code portId};</li>
     *   <li>if the snapshot doesn't know the id it re-asks the live model, keeping the write side
     *       consistent with the read side: a stale snapshot should cost a slow path, not a lost
     *       value. Ports survive a rename as the same object, so identity still finds the slot.</li>
     * </ul>
     */
    static boolean stage(PreparedGraph.Node prepared, Object[] staged, byte[] stagedKind,
                         int[] stagedStamp, int stagedGen, String outputId, Object value) {
        boolean matched = false;
        String[] ids = prepared.outputIds;
        for (int k = 0; k < ids.length; k++) {
            if (ids[k].equals(outputId)) {
                staged[k] = value;
                stagedKind[k] = GraphExecutor.KIND_OBJECT;
                stagedStamp[k] = stagedGen;
                matched = true;
            }
        }
        if (matched || prepared.asNodeModel == null) return matched;
        for (PortModel live : prepared.asNodeModel.getOutputsByDisplayOrder()) {
            if (!outputId.equals(live.getPortId())) continue;
            int idx = prepared.outputIndexOf(live);
            if (idx >= 0) {
                staged[idx] = value;
                stagedKind[idx] = GraphExecutor.KIND_OBJECT;
                stagedStamp[idx] = stagedGen;
                matched = true;
            }
        }
        return matched;
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

    // ---- numeric lane -------------------------------------------------------------------------

    /**
     * The width to do this node's arithmetic in, given what arrived on {@code inputIds} — one of
     * {@link NumericLane#LONG}, {@link NumericLane#FLOAT} or {@link NumericLane#DOUBLE}, never
     * {@code NONE}.
     *
     * <p>Read it once at the top of {@code evaluate} and switch on it, then read the operands with
     * the matching {@link #getLong} / {@link #getFloat} / {@link #getDouble}. {@link NumericLane}
     * has the rule and the reasons; the short version is that a wire names the lane and an
     * unconnected constant goes along with whatever the wires decided.</p>
     */
    public byte lane(String... inputIds) {
        byte lane = NumericLane.NONE;
        for (String id : inputIds) lane = NumericLane.widen(lane, laneOf(id));
        return NumericLane.resolve(lane);
    }

    /**
     * One input's contribution to {@link #lane}, {@link NumericLane#NONE} included.
     *
     * <p>For a variadic node to fold the lane over {@code in1..inN} without building the array
     * {@link #lane}'s varargs would need — the arity is an option, so the ids are not a constant.</p>
     */
    public byte laneOf(String inputId) {
        // See Opt.NUMERIC_PROMOTION. Off, every input abstains and lane() resolves to FLOAT, which
        // is the pre-lane behaviour — for benchmarking the check against itself, nothing else.
        if (!executor.numericPromotion()) return NumericLane.NONE;
        int idx = prepared.inputIndex(inputId);
        if (idx >= 0) return executor.pullLane(prepared, idx);
        // Not in the snapshot — same slow path the reads take. The live port still says whether it is
        // connected, which is the one thing the constant-vs-wire distinction needs.
        PortModel pm = prepared.asNodeModel == null ? null
                : prepared.asNodeModel.getInputsById().get(inputId);
        if (pm == null) return NumericLane.NONE;
        Object raw = pullRaw(inputId);
        return pm.isConnected() ? NumericLane.of(raw) : NumericLane.ofConstant(raw);
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
                stagedStamp[k] = stagedGen;
                anyStaged = true;
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
                stagedStamp[idx] = stagedGen;
                anyStaged = true;
            }
        }
    }

    // ---- loop iteration state ------------------------------------------------------------------

    /**
     * The index of the iteration this node's loop is currently running, or 0 when it is not running.
     *
     * <p>For the loop nodes to republish their {@code index} output after the engine has cleared the
     * value cache. Reading it from the controller rather than from per-node state is what lets an
     * iteration cost no hash lookups and no boxing — see {@link LoopController}.</p>
     */
    public int loopIndex() {
        LoopController c = executor.activeLoop(prepared.index);
        return c == null ? 0 : c.index();
    }

    /** The element this node's loop is currently on, or null. @see #loopIndex() */
    @Nullable
    public Object loopItem() {
        LoopController c = executor.activeLoop(prepared.index);
        return c == null ? null : c.item();
    }

    // ---- meta --------------------------------------------------------------------------------

    public NodeModel getNode() {
        return prepared.asNodeModel;
    }

    public GraphExecutor getExecutor() {
        return executor;
    }

    // ---- coercion ----------------------------------------------------------------------------

    /**
     * Liberal coercion used by {@link #getInput} / {@link #getOption} and exposed for nodes that
     * want to apply the same rules themselves (e.g. {@code CastNode}). See class javadoc for the
     * full coercion table.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T coerce(@Nullable Object raw, Class<T> type) {
        if (raw == null || type == null) return null;
        if (type.isInstance(raw)) return (T) raw;

        // Number polymorphism — works uniformly across all java.lang.Number subclasses including
        // BigInteger / BigDecimal. We probe by the *destination* wrapper class.
        if (raw instanceof Number n) {
            if (type == Integer.class || type == int.class)   return (T) Integer.valueOf(n.intValue());
            if (type == Long.class    || type == long.class)  return (T) Long.valueOf(n.longValue());
            if (type == Float.class   || type == float.class) return (T) Float.valueOf(n.floatValue());
            if (type == Double.class  || type == double.class)return (T) Double.valueOf(n.doubleValue());
            if (type == Short.class   || type == short.class) return (T) Short.valueOf(n.shortValue());
            if (type == Byte.class    || type == byte.class)  return (T) Byte.valueOf(n.byteValue());
        }

        // Universal toString — everything is convertible to a String.
        if (type == String.class) return (T) raw.toString();

        // Note: we do NOT auto-coerce to Boolean from non-Boolean values. Truthiness is too
        // ambiguous (string "false" / number 0 / etc.) — leave it to the node.
        return null;
    }
}
