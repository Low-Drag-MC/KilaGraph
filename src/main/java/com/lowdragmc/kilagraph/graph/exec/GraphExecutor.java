package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.IVariable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.DeclarationModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleInputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleOutputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Pull-based evaluator for a {@link Graph}. Demand-driven: callers request the value of an
 * {@link PortModel} (output port), and the executor recursively resolves upstream nodes,
 * memoising results for the lifetime of this executor instance.
 *
 * <p>Three "kick-off" surfaces:</p>
 * <ol>
 *   <li>{@link #evaluate(PortModel, Class)} — pull a single output port.</li>
 *   <li>{@link #runOutputs()} — evaluate every {@code OUTPUT}-kind graph variable. The current
 *       convention: a graph variable is considered "written" when an {@link IVariableNode} for it
 *       has an INPUT-side port (the "set" form) with a connected wire. The executor pulls that
 *       wire to populate the result map. Unwritten OUTPUT variables fall back to their declared
 *       default (or to the env's variable store, if preloaded).</li>
 *   <li>{@link #executeFrom(NodeModel)} — run an exec-flow graph to completion.</li>
 * </ol>
 *
 * <p>Subgraphs ({@link SubgraphNodeModel}) get a transparent child executor: outer-input ports
 * mirror inner {@code READ} variables (parent feeds value in), outer-output ports mirror inner
 * {@code WRITE} variables (parent reads value out). Port ids are the inner variable's UUID
 * (with {@code -in}/{@code -out} suffixes for {@code READ_WRITE} variables).</p>
 *
 * <p>Not thread-safe; create a fresh executor per logical evaluation, or guard externally.</p>
 *
 * <h2>How a run is addressed</h2>
 * Everything structural is resolved once by {@link PreparedGraph} and shared across executors of the
 * same graph: which port an id names, which port feeds an input, which nodes an exec pin flows to,
 * what kind of node this is. A run therefore touches only integer-indexed arrays owned by this
 * executor — no map lookups against the editor model, and, after the first run has grown them, no
 * allocation at all:
 * <ul>
 *   <li>values live in {@link #slots}, one entry per port, validated by a {@link #generation}
 *       counter in {@link #stamps} — so {@link #clearCache()} costs what the run wrote rather than
 *       the size of the table, and "cached null" stays distinguishable from "not cached" the way
 *       {@code containsKey} did;</li>
 *   <li>{@link EvalContext}/{@link ExecContext} come from a depth-indexed pool, because pulling is
 *       re-entrant: a node's {@code evaluate} pulls upstream nodes from inside its own call;</li>
 *   <li>cycle detection is a {@code boolean[]} over node indices instead of a {@code LinkedHashSet}.</li>
 * </ul>
 * None of this is visible to nodes — {@code getInput(String, Class, T)} means exactly what it did.
 */
public final class GraphExecutor {

    private final Graph graph;
    private final EvaluationEnvironment env;

    // ---- resolved structure (shared, rebuilt when the graph is edited) ----
    @Nullable private PreparedGraph prepared;

    // ---- per-run value table: one slot per port, validity by generation stamp ----
    //
    // Two lanes. Numbers live unboxed in `nums` (a long holding either the raw integral value or
    // Double.doubleToRawLongBits), everything else in `slots`; `kinds` says which, and is only
    // meaningful where `stamps` says the slot was computed this generation, so it never needs
    // clearing. A float flowing from one node to the next therefore never becomes a Float —
    // Float.valueOf has no cache, so under the old single lane every numeric edge allocated.
    private Object[] slots = EMPTY_VALUES;
    private long[] nums = EMPTY_NUMS;
    private byte[] kinds = EMPTY_KINDS;
    private int[] stamps = EMPTY_STAMPS;

    /** Slot holds a boxed/reference value in {@link #slots}. */
    static final byte KIND_OBJECT = 0;
    /** Slot holds a {@code float}, as raw int bits in {@link #nums}. */
    static final byte KIND_FLOAT = 1;
    /** Slot holds a {@code double}, as raw long bits in {@link #nums}. */
    static final byte KIND_DOUBLE = 2;
    /** Slot holds an {@code int} in {@link #nums}. */
    static final byte KIND_INT = 3;
    /** Slot holds a {@code long} in {@link #nums}. */
    static final byte KIND_LONG = 4;
    /** Never 0 — {@link #invalidateNode} uses 0 as the "definitely stale" stamp. */
    private int generation = 1;
    /**
     * Slots written this generation, so {@link #clearCache()} can null them out. Bumping the
     * generation alone would make them read as uncached but keep the objects reachable, and the
     * old {@code cache.clear()} released them — for a long-lived per-entity executor that is the
     * difference between dropping a dead {@code Entity}/{@code ItemStack} and pinning it forever.
     * Sized to the slot table, so the sweep costs what the run actually wrote, not what it could.
     */
    private int[] written = EMPTY_STAMPS;
    private int writtenCount;

    // ---- cycle detection: an on-stack flag per node index, plus the path for the exception ----
    private boolean[] onStack = EMPTY_FLAGS;
    private PreparedGraph.Node[] visitStack = EMPTY_NODES;
    private int visitDepth;

    // ---- pooled contexts, indexed by recursion depth ----
    private EvalContext[] evalPool = new EvalContext[8];
    private int evalDepth;
    private ExecContext[] execPool = new ExecContext[8];
    private int execDepth;

    /** Reused by {@link #executeFrom}; a nested call falls back to a fresh session. */
    @Nullable private ExecSession pooledSession;
    private boolean pooledSessionBusy;

    /**
     * Per-node persistent state, keyed by node UID.
     *
     * <p>Deliberately still a uid map rather than an index into the prepared graph: {@code Model.setUid}
     * is public and paste rewrites it, and the public {@link #nodeState(UUID)} that tests and
     * {@code CacheClear} call would then address a different bag than {@code ctx.state()} does. Only
     * four nodes use state at all, so there is nothing here worth that risk.</p>
     */
    private final Map<UUID, Map<String, Object>> nodeState = new HashMap<>();

    /** Lazily-instantiated RNG. */
    private Random rng;

    /** See {@link #setGraphFrozen}. Off by default — correctness is not opt-in. */
    private boolean graphFrozen;

    /** Mirrors {@link PreparedGraph#mayCycle()} — false lets a pull skip the visiting stack. */
    private boolean cycleChecks = true;

    /**
     * Names of {@code EXECUTION_FLOW} WRITE (OUTPUT) graph variables whose set-node this executor's
     * exec flow reached — i.e. the exec "exit" pins a subgraph run fired. A parent executor reads a
     * child's set after entering the child (see {@link #subgraphEnter} / {@link SubgraphFrame}) to
     * fire only the matching outer exec-out pins. Populated in {@link #executeStep}; one child
     * executor per subgraph entry.
     */
    private final Set<String> reachedExecOutputs = new LinkedHashSet<>();

    private static final Object[] EMPTY_VALUES = new Object[0];
    private static final long[] EMPTY_NUMS = new long[0];
    private static final byte[] EMPTY_KINDS = new byte[0];
    private static final int[] EMPTY_STAMPS = new int[0];
    private static final boolean[] EMPTY_FLAGS = new boolean[0];
    private static final PreparedGraph.Node[] EMPTY_NODES = new PreparedGraph.Node[0];

    public GraphExecutor(Graph graph) {
        this(graph, EvaluationEnvironment.defaults());
    }

    public GraphExecutor(Graph graph, EvaluationEnvironment env) {
        this.graph = Objects.requireNonNull(graph);
        this.env = Objects.requireNonNull(env);
    }

    // ---- kick-off surfaces -------------------------------------------------------------------

    /** Reset the result cache. Call between independent evaluations if reusing the executor. */
    public void clearCache() {
        for (int i = 0; i < writtenCount; i++) slots[written[i]] = null;
        writtenCount = 0;
        if (++generation == Integer.MAX_VALUE) {
            // Wrap-around would make ancient stamps look current. Cheap and effectively never taken.
            Arrays.fill(stamps, 0);
            generation = 1;
        }
    }

    /** Compute the value of an output port. */
    @SuppressWarnings("unchecked")
    public <T> T evaluate(PortModel outputPort, Class<T> expected) {
        if (outputPort == null) throw new IllegalArgumentException("outputPort is null");
        if (outputPort.getDirection() != PortDirection.OUTPUT) {
            throw new IllegalArgumentException("evaluate() requires an OUTPUT port, got " + outputPort.getDirection());
        }
        syncPreparedAtTopLevel();
        Object value = evaluateOutput(outputPort);
        if (value == null) return null;
        if (expected == null) return (T) value;
        T coerced = EvalContext.coerce(value, expected);
        if (coerced != null) return coerced;
        throw new TypeMismatchException("evaluate() value " + value.getClass().getName()
                + " not assignable to " + expected);
    }

    /**
     * Evaluate every {@link VariableKind#OUTPUT} variable in this graph and return a map keyed by
     * variable name. For each output variable: look for an {@link IVariableNode} that references it
     * and has an INPUT-side port (the "set" form); pull that port's wire. Missing writer → use the
     * env's variable store, then the variable's declared default.
     */
    public Map<String, Object> runOutputs() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(graph.graphModel instanceof CustomGraphModelImpl gm)) return result;
        syncPreparedAtTopLevel();
        for (var v : gm.getGraphVariableModels()) {
            if (v == null) continue;
            if (isExecVar(v)) continue;  // exec-flow vars are not data outputs
            if (v.getVariableKind() != VariableKind.OUTPUT) continue;
            result.put(v.getName(), resolveOutputVariable(v, gm));
        }
        return result;
    }

    /**
     * Exec-flow kick-off. Runs the whole flow from {@code entry} to completion on the
     * step-able {@link ExecSession} VM (one engine for both batch and stepping). For interactive
     * single-stepping, construct an {@link ExecSession} directly:
     * {@code new ExecSession(executor).begin(entry)} then {@code step()}.
     *
     * <p>If a {@code Break}/{@code Continue} is executed outside any loop, the session throws an
     * {@link IllegalStateException} — mirroring the old behavior where the sentinel exception
     * escaped {@code executeFrom}.</p>
     */
    public void executeFrom(NodeModel entry) {
        if (entry == null) throw new IllegalArgumentException("entry is null");
        // No syncPrepared() here: ExecSession.begin -> reset() does it for whichever session runs.
        if (pooledSessionBusy) {
            // Re-entrant call (a node ran another flow inside its own execute) — don't reuse.
            new ExecSession(this).begin(entry).runToCompletion();
            return;
        }
        if (pooledSession == null) pooledSession = new ExecSession(this);
        pooledSessionBusy = true;
        try {
            pooledSession.begin(entry).runToCompletion();
        } finally {
            pooledSessionBusy = false;
        }
    }

    // ---- prepared structure ------------------------------------------------------------------

    /**
     * Make sure {@link #prepared} describes the graph as it is now, and that the value arrays are
     * big enough for it. Runs once per top-level entry, never per node.
     *
     * <p>The freshness check is the one place the live model is still consulted structurally; see
     * {@link PreparedGraph} for why LDLib2 leaves no cheaper option.</p>
     */
    private void syncPrepared() {
        if (prepared != null && graphFrozen) {
            ensureCapacity();
            return;
        }
        if (prepared == null || !prepared.isFresh()) {
            PreparedGraph next = graph.graphModel instanceof CustomGraphModelImpl gm
                    ? PreparedGraph.of(gm) : null;
            if (next != prepared) {
                prepared = next;
                // Slot indices mean something different now; drop every memoised value.
                slots = EMPTY_VALUES;
                nums = EMPTY_NUMS;
                kinds = EMPTY_KINDS;
                stamps = EMPTY_STAMPS;
                written = EMPTY_STAMPS;
                writtenCount = 0;
            }
        }
        cycleChecks = prepared == null || prepared.mayCycle();
        ensureCapacity();
    }

    /**
     * Refresh the prepared form, but only when this really is a top-level entry.
     *
     * <p>Swapping it mid-evaluation would pull the slot table out from under every caller further
     * up that already holds slot indices. {@code InfoFieldBlock} reaches {@link #pullInputValue}
     * from inside its own {@code evaluate}, and nothing stops a third-party node calling
     * {@link #evaluate} the same way — so the guard belongs on all three entry points rather than
     * on the one where a caller happens to exist today.</p>
     */
    private void syncPreparedAtTopLevel() {
        if (prepared == null || (evalDepth == 0 && execDepth == 0)) syncPrepared();
    }

    /** Grow the per-port and per-node arrays to cover the prepared graph. */
    private void ensureCapacity() {
        if (prepared == null) return;
        int need = prepared.slotCount();
        if (slots.length < need) {
            int size = Math.max(need, Math.max(16, slots.length * 2));
            slots = Arrays.copyOf(slots, size);
            nums = Arrays.copyOf(nums, size);
            kinds = Arrays.copyOf(kinds, size);
            stamps = Arrays.copyOf(stamps, size);
            // never shrink: writeSlot grows `written` on its own policy, and invalidateNode can
            // re-log a slot inside one generation, so writtenCount is not bounded by the slot count
            written = Arrays.copyOf(written, Math.max(size, written.length));
        }
        int nodes = prepared.nodeCount();
        if (onStack.length < nodes) {
            int size = Math.max(nodes, Math.max(16, onStack.length * 2));
            onStack = Arrays.copyOf(onStack, size);
            visitStack = Arrays.copyOf(visitStack, size);
        }
    }

    /** Resolve a model to its prepared node, admitting it (and growing the arrays) if it is new. */
    @Nullable
    private PreparedGraph.Node resolve(@Nullable AbstractNodeModel m) {
        if (prepared == null || m == null) return null;
        PreparedGraph.Node n = prepared.node(m);
        if (n != null && (n.index >= onStack.length || prepared.slotCount() > slots.length)) {
            ensureCapacity();
        }
        // Admitting a node can add an edge, and therefore a cycle, after syncPrepared() decided.
        cycleChecks = prepared.mayCycle();
        return n;
    }

    // ---- exec stepping -----------------------------------------------------------------------

    /**
     * Execute exactly one exec node within {@code frame} (driven by {@link ExecSession#step()}):
     * <ul>
     *   <li>{@link SubgraphNodeModel} → enter the inner graph (push a {@link SubgraphFrame}), or for
     *       an unresolved/self-ref subgraph fire all its exec-out pins into {@code frame}.</li>
     *   <li>An {@code EXECUTION_FLOW} variable set-node → record the reached exec exit (no
     *       propagation; it's a subgraph "return").</li>
     *   <li>Otherwise an {@link AnnotatedNode} → run {@code execute(ctx)} and flush staged data
     *       outputs. The node's {@code ctx.flow/pushSequence/pushLoop/signalBreak/signalContinue}
     *       drive the session's frame stack.</li>
     * </ul>
     */
    void executeStep(NodeModel n, ExecSession session, ExecFrame frame) {
        // A child scope entered through a SubgraphFrame is stepped without ever passing through one
        // of this executor's own kick-off surfaces, so this is its first chance to be prepared.
        if (prepared == null) syncPrepared();
        PreparedGraph.Node node = resolve(n);
        if (node == null) return;
        switch (node.execKind) {
            case SUBGRAPH -> subgraphEnter(node, session, frame);
            case PORTAL_ENTRY -> {
                // Wire-portal ENTRY reached by exec flow: fan out to every matching exit portal's
                // downstream (the entry↔exit gap has no wire — bridge it via the DeclarationModel).
                for (WirePortalModel exit : exitPortals(node.portal.getDeclarationModel())) {
                    if (exit instanceof ISingleOutputPortNodeModel out) frame.enqueueFlow(out.getOutputPort());
                }
            }
            case VARIABLE -> {
                // An exec "exit": a variable set-node with an EXECUTION_FLOW input that flow reached.
                // Asked live rather than frozen at prepare time. Changing a variable's declared type
                // redefines its nodes but keeps the same port objects and the same port counts, so
                // the structural digest cannot see it — and this is the only place a port *type*
                // decides control flow. Reading it live costs one scan of a one-port node.
                IVariable var = node.variableNode.getVariable();
                if (var != null && hasExecPort(node.inputPorts)) reachedExecOutputs.add(var.getName());
            }
            case ANNOTATED -> {
                ExecContext ctx = acquireExec(node, session, frame);
                try {
                    node.annotated.execute(ctx);
                    ctx.flush();
                } catch (Throwable t) {
                    ctx.dropStaged();
                    throw t;
                } finally {
                    execDepth--;
                }
            }
            case NONE -> { }
        }
    }

    /**
     * Enter a subgraph on the exec path: seed the child env from the inner READ data variables, then
     * push a {@link SubgraphFrame} (scoped to a child executor) primed at the inner exec-IN
     * variable's downstream. The frame's {@code resume} harvests WRITE data + fires reached exec-out
     * pins when the inner exec drains. Unresolved/self-ref → fire all exec-out pins into {@code frame}
     * so the outer flow doesn't dead-end.
     */
    private void subgraphEnter(PreparedGraph.Node node, ExecSession session, ExecFrame frame) {
        SubgraphNodeModel sub = node.subgraphNode;
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)
                || inner == graph.graphModel || inner.getGraph() == null) {
            for (int k = 0; k < node.outputPorts.length; k++) {
                if (TypeHandles.EXECUTION_FLOW.equals(node.outputPorts[k].getDataTypeHandle())) {
                    frame.enqueueFlow(node.outputPorts[k]);
                }
            }
            return;
        }
        VariableStore childStore = new VariableStore();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.READ)) continue;
            PortModel outerInput = lookupSubgraphPort(sub, v, true, mods);
            if (outerInput == null) continue;
            childStore.put(v.getName(), pullInput(outerInput, Object.class));
        }
        var childEnv = env.createChild(childStore);
        var childExec = new GraphExecutor(inner.getGraph(), childEnv);
        var subFrame = new SubgraphFrame(childExec, this, frame, sub, inner);
        subFrame.enqueueEntry(findExecEntryNode(inner));
        session.push(subFrame);
    }

    // ---- per-node state ----------------------------------------------------------------------

    /** Per-node persistent state — survives across {@code executeFrom} invocations on this executor. */
    public Map<String, Object> nodeState(UUID nodeUid) {
        return nodeState.computeIfAbsent(nodeUid, k -> new HashMap<>());
    }

    /** Reset per-node state — call between independent runs if you want a clean slate. */
    public void clearNodeState() {
        nodeState.clear();
    }

    /**
     * Invalidate a single node: drop its per-node {@link #nodeState} entry and evict all its
     * output ports from the pull cache. The next pull of any of its outputs recomputes.
     *
     * <p>Used by {@code CacheClear}: unlike {@link #clearCache} (which invalidates the whole pull
     * cache but leaves node state — so a {@code Cache} would keep serving its memo) and
     * {@link #clearNodeState} (which drops every node's state), this targets exactly one node so a
     * {@code Cache} recomputes while unrelated memoised values stay put.</p>
     */
    public void invalidateNode(NodeModel target) {
        if (target == null) return;
        nodeState.remove(target.getUid());
        PreparedGraph.Node n = resolve(target);
        if (n == null) return;
        for (int slot : n.outputSlots) {
            if (slot < stamps.length) {
                stamps[slot] = 0;
                slots[slot] = null;
            }
        }
    }

    // ---- context pool ------------------------------------------------------------------------

    /**
     * Contexts are pooled by recursion depth rather than reused as a single instance: pulling is
     * re-entrant, so a node's {@code evaluate} is frequently on the stack underneath another's.
     */
    private EvalContext acquireEval(PreparedGraph.Node n) {
        if (evalDepth == evalPool.length) evalPool = Arrays.copyOf(evalPool, evalDepth * 2);
        EvalContext c = evalPool[evalDepth];
        if (c == null) c = evalPool[evalDepth] = new EvalContext(this);
        evalDepth++;
        c.bind(n);
        return c;
    }

    private ExecContext acquireExec(PreparedGraph.Node n, ExecSession session, ExecFrame frame) {
        if (execDepth == execPool.length) execPool = Arrays.copyOf(execPool, execDepth * 2);
        ExecContext c = execPool[execDepth];
        if (c == null) c = execPool[execDepth] = new ExecContext(this);
        execDepth++;
        c.bind(n, session, frame);
        return c;
    }

    // ---- slot access -------------------------------------------------------------------------

    void writeSlot(int slot, Object value) {
        kinds[slot] = KIND_OBJECT;
        if (stamps[slot] != generation) {
            stamps[slot] = generation;
            // Grow rather than drop: invalidateNode clears a stamp mid-generation, so one slot can
            // be logged twice, and a log that silently stops recording would leak the rest.
            if (writtenCount == written.length) {
                written = Arrays.copyOf(written, Math.max(16, written.length * 2));
            }
            written[writtenCount++] = slot;
        }
        slots[slot] = value;
    }

    /** Write an unboxed number. {@code slots[slot]} is cleared so the old value is not retained. */
    void writeNum(int slot, byte kind, long bits) {
        slots[slot] = null;
        nums[slot] = bits;
        kinds[slot] = kind;
        if (stamps[slot] != generation) {
            stamps[slot] = generation;
            if (writtenCount == written.length) {
                written = Arrays.copyOf(written, Math.max(16, written.length * 2));
            }
            written[writtenCount++] = slot;
        }
    }

    /**
     * Box a numeric slot back into the object lane, for a caller that asked for an {@code Object}.
     *
     * <p>The kind records the <em>declared width</em>, not just "a number", so this reproduces the
     * exact wrapper the old single-lane executor would have held. That matters: a node reading
     * {@code getInput(id, Float.class, 0f)} from a {@code Float} gets it back untouched, whereas
     * from a {@code Double} it would coerce and allocate a second time. Without this, adding the
     * numeric lane would have made every not-yet-migrated consumer allocate twice instead of once.</p>
     */
    static Object boxed(byte kind, long bits) {
        return switch (kind) {
            case KIND_FLOAT -> Float.valueOf(Float.intBitsToFloat((int) bits));
            case KIND_DOUBLE -> Double.valueOf(Double.longBitsToDouble(bits));
            case KIND_INT -> Integer.valueOf((int) bits);
            default -> Long.valueOf(bits);
        };
    }

    /** The numeric value of a slot as a {@code double}, by kind. */
    private static double numAsDouble(byte kind, long bits) {
        return switch (kind) {
            case KIND_FLOAT -> Float.intBitsToFloat((int) bits);
            case KIND_DOUBLE -> Double.longBitsToDouble(bits);
            default -> (double) bits;
        };
    }

    /** The numeric value of a slot as a {@code long}, truncating like {@code Number.longValue()}. */
    private static long numAsLong(byte kind, long bits) {
        return switch (kind) {
            case KIND_FLOAT -> (long) Float.intBitsToFloat((int) bits);
            case KIND_DOUBLE -> (long) Double.longBitsToDouble(bits);
            default -> bits;
        };
    }

    /** Write every output of {@code n} to the same value — the constant / variable node shape. */
    private void writeAllOutputs(PreparedGraph.Node n, Object value) {
        for (int slot : n.outputSlots) writeSlot(slot, value);
    }

    private void writeSlotFor(PreparedGraph.Node n, PortModel port, Object value) {
        int slot = n.slotOf(port);
        if (slot >= 0) writeSlot(slot, value);
    }

    // ---- internal: the pull path -------------------------------------------------------------

    /**
     * Public: resolve the value feeding an input port (upstream pull or embedded constant). Used by
     * meta-nodes that must read a port belonging to a <em>different</em> node — e.g. an InfoNode
     * field block reading its parent context's {@code target} input.
     */
    public Object pullInputValue(PortModel inputPort) {
        if (inputPort == null) return null;
        syncPreparedAtTopLevel();
        return pullInput(inputPort, Object.class);
    }

    /** Internal: read an input port — either via constant lookup or upstream pull. */
    Object pullInput(PortModel inputPort, Class<?> expected) {
        PreparedGraph.Node owner = resolve(nodeModelOf(inputPort));
        if (owner != null) {
            int idx = owner.inputIndexOf(inputPort);
            if (idx >= 0) return pullInput(owner, idx, expected);
        }
        return pullInputUnprepared(inputPort, expected);
    }

    /** The resolved pull: the source slot and the constant were both worked out at prepare time. */
    Object pullInput(PreparedGraph.Node owner, int inputIndex, Class<?> expected) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) return readConstant(owner.inputConstants[inputIndex], expected);
        return demandSlot(owner.inputSourceOwners[inputIndex], srcSlot);
    }

    /**
     * Read an unconnected input's embedded constant.
     *
     * <p>{@code PortModel.tryGetValue} would re-derive "am I connected" (two list allocations) and
     * wrap the answer in a {@code DataResult} plus an {@code Optional}. We already know the port is
     * unconnected, and for the {@code Object.class} the executor asks for, the type test inside
     * {@code Constant.tryGetValue} always succeeds — so the raw value is the same answer.</p>
     */
    /**
     * Read an already-resolved embedded constant. The reference comes from the prepared graph, so
     * this costs one virtual call instead of a name-keyed map lookup; the value itself is still
     * read live, which is what keeps an edited constant visible after {@code clearCache()}.
     *
     * <p>The guard is around the value read deliberately. Resolving the constant is a map lookup
     * and does not throw; reading one can. Two concrete ways: a constant backed by a user-supplied
     * getter, and — the one that actually bites — {@code tryGetValue(t).result()}, which is
     * {@code Optional.of(value)} and so throws on a constant holding {@code null} for an otherwise
     * assignable type. The pre-rewrite code caught both here; caching the reference moved the
     * resolution to prepare time, so the guard had to stay with the call it was written for.</p>
     */
    private Object readConstant(@Nullable Constant constant, Class<?> expected) {
        if (constant == null) return null;
        try {
            if (expected == null || expected == Object.class) return constant.getValue();
            return constant.tryGetValue(expected).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Fallback for a port the prepared snapshot does not know: behave exactly as before. */
    private Object pullInputUnprepared(PortModel inputPort, Class<?> expected) {
        if (!inputPort.isConnected()) {
            try {
                var result = inputPort.tryGetValue(expected != null ? expected : Object.class);
                return result.result().orElse(null);
            } catch (RuntimeException e) {
                return null;
            }
        }
        var connected = inputPort.getConnectedPorts();
        if (connected.isEmpty()) return null;
        return evaluateOutput(connected.getFirst());
    }

    private Object evaluateOutput(PortModel outputPort) {
        PreparedGraph.Node owner = resolve(nodeModelOf(outputPort));
        if (owner == null) return null;
        int slot = owner.slotOf(outputPort);
        if (slot < 0) return null;
        return demandSlot(owner, slot);
    }

    /** Make sure {@code slot} holds this generation's value, evaluating {@code owner} if needed. */
    private boolean ensureComputed(@Nullable PreparedGraph.Node owner, int slot) {
        if (owner == null || slot < 0) return false;
        if (stamps[slot] == generation) return true;
        // An acyclic graph cannot revisit a node that is already being evaluated, and the prepared
        // form already knows which it is — so the bookkeeping only runs where it can actually fire.
        if (!cycleChecks) {
            evaluateNode(owner);
            return stamps[slot] == generation;
        }
        enterNode(owner);
        try {
            evaluateNode(owner);
        } finally {
            exitNode();
        }
        return stamps[slot] == generation;
    }

    /** Memoised evaluation of {@code slot} as an object — boxes if the slot is in the numeric lane. */
    private Object demandSlot(@Nullable PreparedGraph.Node owner, int slot) {
        if (!ensureComputed(owner, slot)) return null;
        byte k = kinds[slot];
        return k == KIND_OBJECT ? slots[slot] : boxed(k, nums[slot]);
    }

    /**
     * Read an input as a {@code double} without ever materialising a box, when the producer wrote
     * to the numeric lane. Falls back to the object lane (and to the same {@code Number} coercion
     * {@code getInput(id, Float.class, def)} performs) for anything else, so mixing migrated and
     * unmigrated nodes on the same wire stays correct.
     */
    double pullDouble(PreparedGraph.Node owner, int inputIndex, double def) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) return asDouble(readConstant(owner.inputConstants[inputIndex], Object.class), def);
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) return def;
        byte k = kinds[srcSlot];
        if (k != KIND_OBJECT) return numAsDouble(k, nums[srcSlot]);
        return asDouble(slots[srcSlot], def);
    }

    /** As {@link #pullDouble}, but exact for integral values (no round trip through double). */
    long pullLong(PreparedGraph.Node owner, int inputIndex, long def) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) return asLong(readConstant(owner.inputConstants[inputIndex], Object.class), def);
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) return def;
        byte k = kinds[srcSlot];
        if (k != KIND_OBJECT) return numAsLong(k, nums[srcSlot]);
        return asLong(slots[srcSlot], def);
    }

    /**
     * As {@link #pullLong}, but narrowing to {@code int} the way {@code Number.intValue()} does.
     *
     * <p>Not {@code (int) pullLong(...)}: a {@code double} of 1e300 narrows to
     * {@code Integer.MAX_VALUE} directly but to {@code -1} through a {@code long}, because the
     * long saturates first and the int then keeps its low bits. The old executor coerced with
     * {@code Number.intValue()}, so the direct narrowing is the one that matches.</p>
     */
    int pullInt(PreparedGraph.Node owner, int inputIndex, int def) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) return asInt(readConstant(owner.inputConstants[inputIndex], Object.class), def);
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) return def;
        byte k = kinds[srcSlot];
        long bits = nums[srcSlot];
        return switch (k) {
            case KIND_FLOAT -> (int) Float.intBitsToFloat((int) bits);
            case KIND_DOUBLE -> (int) Double.longBitsToDouble(bits);
            case KIND_INT, KIND_LONG -> (int) bits;
            default -> asInt(slots[srcSlot], def);
        };
    }

    /**
     * As {@link #pullDouble}, but producing a {@code float} in a single rounding step.
     *
     * <p>Not {@code (float) pullDouble(...)}: a {@code long} past 2^53 does not fit a
     * {@code double}, so going through one rounds twice and can land a ULP away from what
     * {@code Number.floatValue()} gives. 9007199791611905L is such a value. Random longs
     * essentially never hit it — it needs the discarded bits to sit exactly on a halfway point —
     * which is precisely why it has to be reasoned about rather than fuzzed.</p>
     */
    float pullFloat(PreparedGraph.Node owner, int inputIndex, float def) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) return asFloat(readConstant(owner.inputConstants[inputIndex], Object.class), def);
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) return def;
        byte k = kinds[srcSlot];
        long bits = nums[srcSlot];
        return switch (k) {
            case KIND_FLOAT -> Float.intBitsToFloat((int) bits);
            case KIND_DOUBLE -> (float) Double.longBitsToDouble(bits);
            case KIND_INT -> (float) (int) bits;
            case KIND_LONG -> (float) bits;
            default -> asFloat(slots[srcSlot], def);
        };
    }

    private static float asFloat(Object raw, float def) {
        return raw instanceof Number n ? n.floatValue() : def;
    }

    private static int asInt(Object raw, int def) {
        return raw instanceof Number n ? n.intValue() : def;
    }

    private static double asDouble(Object raw, double def) {
        return raw instanceof Number n ? n.doubleValue() : def;
    }

    private static long asLong(Object raw, long def) {
        return raw instanceof Number n ? n.longValue() : def;
    }

    private void enterNode(PreparedGraph.Node n) {
        if (onStack[n.index]) {
            List<AbstractNodeModel> path = new ArrayList<>(visitDepth);
            for (int i = 0; i < visitDepth; i++) path.add(visitStack[i].model);
            throw new CycleException(path);
        }
        onStack[n.index] = true;
        visitStack[visitDepth++] = n;
    }

    private void exitNode() {
        PreparedGraph.Node n = visitStack[--visitDepth];
        visitStack[visitDepth] = null;
        onStack[n.index] = false;
    }

    private void evaluateNode(PreparedGraph.Node n) {
        switch (n.dataKind) {
            // 1) Constant node — read the constant value directly.
            case CONSTANT -> writeAllOutputs(n,
                    n.constantNode.tryGetValue(n.constantNode.getDataType()).result().orElse(null));

            // 2) Variable node — defer to the environment (which checks store then default).
            case VARIABLE -> writeAllOutputs(n, env.lookupVariable(n.variableNode.getVariable()));

            // 3) Subgraph node — invoke the inner graph with mirrored variables.
            case SUBGRAPH -> evaluateSubgraph(n);

            // 3b) Wire-portal EXIT — a "teleport wire" has no edge linking it to its entry; resolve
            //     the value through the matching entry portal and pull its input.
            case PORTAL_EXIT -> writeSlotFor(n,
                    ((ISingleOutputPortNodeModel) n.model).getOutputPort(), resolvePortalValue(n.portal));

            // 4) Any evaluable user node (AnnotatedNode, or a BlockNode-based reader like the
            //    InfoNode field blocks) — invoke evaluate(EvalContext) and flush its staged outputs.
            case EVALUABLE -> {
                EvalContext ctx = acquireEval(n);
                try {
                    n.evaluable.evaluate(ctx);
                    ctx.flush();
                } catch (Throwable t) {
                    // flush() is what releases the staged references; a node that throws never
                    // reaches it, and the context is pooled for the executor's lifetime.
                    ctx.dropStaged();
                    throw t;
                } finally {
                    evalDepth--;
                }
            }

            // 5) Unknown node type — best-effort: leave outputs at null.
            case NONE -> { }
        }
    }

    /**
     * Walk a {@link SubgraphNodeModel}:
     * <ol>
     *   <li>Resolve the inner graph. Unresolved → all outer output ports stay null.</li>
     *   <li>For each inner variable with the {@code READ} flag: locate the matching outer INPUT
     *       port (id = variable uid, or uid+"-in" for {@code READ_WRITE}), pull it, and bind the
     *       value into a fresh {@link VariableStore} under the variable's name.</li>
     *   <li>Run a child executor on the inner graph using that store. Collect its output map.</li>
     *   <li>For each inner variable with the {@code WRITE} flag: write the matching result-map
     *       entry into the outer OUTPUT port's slot (id = uid, or uid+"-out").</li>
     * </ol>
     */
    private void evaluateSubgraph(PreparedGraph.Node node) {
        SubgraphNodeModel sub = node.subgraphNode;
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)
                // Guard against trivial self-reference (inner graph is the model we're already in).
                || inner == graph.graphModel) {
            writeAllOutputs(node, null);
            return;
        }
        VariableStore childStore = new VariableStore();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.READ)) continue;
            PortModel outerInput = lookupSubgraphPort(sub, v, true, mods);
            if (outerInput == null) continue;
            childStore.put(v.getName(), pullInput(outerInput, Object.class));
        }

        var childEnv = env.createChild(childStore);
        Graph innerGraph = inner.getGraph();
        if (innerGraph == null) {
            writeAllOutputs(node, null);
            return;
        }
        var childExec = new GraphExecutor(innerGraph, childEnv);
        Map<String, Object> innerResults = childExec.runOutputs();

        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
            PortModel outerOutput = lookupSubgraphPort(sub, v, false, mods);
            if (outerOutput == null) continue;
            writeSlotFor(node, outerOutput, innerResults.get(v.getName()));
        }
    }

    // ---- subgraph plumbing -------------------------------------------------------------------

    /**
     * Harvest a finished subgraph's inner WRITE <em>data</em> variables into this (parent) scope's
     * value table, so downstream data pulls on the subgraph node see them. Called by
     * {@link SubgraphFrame#resume} once the inner exec has drained.
     */
    void harvestSubgraphOutputs(SubgraphNodeModel sub, CustomGraphModelImpl inner, GraphExecutor childExec) {
        Map<String, Object> innerResults = childExec.runOutputs();
        PreparedGraph.Node node = resolve(sub);
        if (node == null) return;
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
            PortModel outerOutput = lookupSubgraphPort(sub, v, false, mods);
            if (outerOutput == null) continue;
            writeSlotFor(node, outerOutput, innerResults.get(v.getName()));
        }
    }

    /**
     * The outer exec-out pins whose inner WRITE exec-variable exit the child run actually reached —
     * the pins {@link SubgraphFrame#resume} should fire to continue the outer flow.
     */
    List<PortModel> reachedExecOutPins(SubgraphNodeModel sub, CustomGraphModelImpl inner, GraphExecutor childExec) {
        Set<String> reached = childExec.reachedExecOutputs();
        List<PortModel> pins = new ArrayList<>();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || !isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
            if (!reached.contains(v.getName())) continue;
            PortModel outerOut = lookupSubgraphPort(sub, v, false, mods);
            if (outerOut != null) pins.add(outerOut);
        }
        return pins;
    }

    /** Find the inner {@code EXECUTION_FLOW} READ variable's get-node (its exec output is the entry). */
    private NodeModel findExecEntryNode(CustomGraphModelImpl gm) {
        for (var nm : gm.getNodeModels()) {
            if (!(nm instanceof NodeModel n)) continue;
            if (asVariableNode(n) == null) continue;
            // READ ("get") form of an exec var exposes an EXECUTION_FLOW output port — the entry.
            if (hasExecPort(n.getOutputsById().values())) return n;
        }
        return null;
    }

    private static boolean hasExecPort(java.util.Collection<PortModel> ports) {
        for (PortModel p : ports) {
            if (TypeHandles.EXECUTION_FLOW.equals(p.getDataTypeHandle())) return true;
        }
        return false;
    }

    private static boolean hasExecPort(PortModel[] ports) {
        for (PortModel p : ports) {
            if (TypeHandles.EXECUTION_FLOW.equals(p.getDataTypeHandle())) return true;
        }
        return false;
    }

    /**
     * Resolve the value an exit wire-portal delivers: find the single entry portal sharing its
     * {@link DeclarationModel} and pull the entry's input wire. Null if there's no entry (dangling
     * exit) or the entry is unwired.
     */
    private Object resolvePortalValue(WirePortalModel exit) {
        DeclarationModel decl = exit.getDeclarationModel();
        if (decl == null || !(graph.graphModel instanceof CustomGraphModelImpl gm)) return null;
        for (var entry : gm.getEntryPortals(decl)) {
            if (entry instanceof ISingleInputPortNodeModel in) {
                return pullInput(in.getInputPort(), Object.class);
            }
        }
        return null;
    }

    /** Exit portals sharing {@code decl} (empty if the graph model isn't resolvable). */
    private List<WirePortalModel> exitPortals(DeclarationModel decl) {
        if (decl == null || !(graph.graphModel instanceof CustomGraphModelImpl gm)) return List.of();
        return gm.getExitPortals(decl);
    }

    /**
     * Find the outer port that mirrors a given inner variable. SubgraphNodeModel's port ids are:
     * {@code uid.toString()} for single-direction variables, or {@code uid+"-in"}/{@code uid+"-out"}
     * for READ_WRITE.
     */
    private PortModel lookupSubgraphPort(SubgraphNodeModel sub, VariableDeclarationModelBase v,
                                         boolean wantInput, ModifierFlags mods) {
        String base = v.getUid().toString();
        String suffix = (mods == ModifierFlags.READ_WRITE) ? (wantInput ? "-in" : "-out") : "";
        String portId = base + suffix;
        return wantInput ? sub.getInputsById().get(portId) : sub.getOutputsById().get(portId);
    }

    /**
     * For a graph OUTPUT variable: find a writer-form {@link IVariableNode} (one with an INPUT-side
     * port — the "set" representation) and pull its value. Falls back to env store, then to the
     * variable's declared default.
     */
    private Object resolveOutputVariable(VariableDeclarationModelBase v, CustomGraphModelImpl gm) {
        for (var nm : gm.getNodeModels()) {
            if (!(nm instanceof NodeModel n)) continue;
            IVariableNode vn = asVariableNode(n);
            if (vn == null) continue;
            IVariable refVar = vn.getVariable();
            if (refVar == null || !Objects.equals(refVar.getName(), v.getName())) continue;
            // "set" form: this variable node exposes an INPUT-side port.
            var inputs = n.getInputsById();
            if (inputs.isEmpty()) continue;
            PortModel inputPort = inputs.values().iterator().next();
            return pullInput(inputPort, Object.class);
        }
        // No writer node — fall back to env, then default.
        if (env.variables().contains(v.getName())) return env.variables().get(v.getName());
        return v.tryGetDefaultValue(v.getDataType()).result().orElse(null);
    }

    // ---- misc --------------------------------------------------------------------------------

    @Nullable
    private static AbstractNodeModel nodeModelOf(@Nullable PortModel port) {
        return port != null && port.getNodeModel() instanceof AbstractNodeModel a ? a : null;
    }

    private static IVariableNode asVariableNode(NodeModel n) {
        if (n instanceof IVariableNode direct) return direct;
        if (n instanceof ICustomNodeModel cnm && cnm.getNode() instanceof IVariableNode wrapped) return wrapped;
        return null;
    }

    /** Names of exec exits this executor's flow reached (for the entering parent to read). */
    public Set<String> reachedExecOutputs() {
        return reachedExecOutputs;
    }

    private static boolean isExecVar(VariableDeclarationModelBase v) {
        return v != null && TypeHandles.EXECUTION_FLOW.equals(v.getDataTypeHandle());
    }

    /** Shared RNG for {@code Random}/{@code RandomInt} nodes. Seeded from {@link EvaluationEnvironment#seed()}. */
    public Random rng() {
        if (rng == null) {
            rng = env.seed().isPresent() ? new Random(env.seed().getAsLong()) : new Random();
        }
        return rng;
    }

    public Graph getGraph() {
        return graph;
    }

    public EvaluationEnvironment getEnvironment() {
        return env;
    }

    /**
     * Resolve the graph ahead of a run driven from outside this class — an {@link ExecSession} the
     * caller stepped itself, rather than {@link #executeFrom}. Same work {@link #evaluate} does on
     * entry: refresh the prepared view if the graph changed, and size the value table.
     */
    void prepareForRun() {
        syncPrepared();
    }

    /**
     * Promise that nobody will edit this graph while the executor is alive, letting each run skip
     * the structural freshness check.
     *
     * <p>That check is the one piece of per-run work the prepared form cannot get rid of: LDLib2
     * has no version counter to consult, so detecting an edit means re-deriving a digest over the
     * wires and port counts (see {@link PreparedGraph}). For an editor that is the right trade. For
     * a host running one fixed graph per entity every frame — the case this whole layer exists for —
     * it is pure overhead, and this switch removes it.</p>
     *
     * <p>Editing a frozen graph produces wrong answers, not an error. Call
     * {@link #invalidatePrepared()} if you must change one.</p>
     */
    public void setGraphFrozen(boolean frozen) {
        this.graphFrozen = frozen;
    }

    /** Drop this executor's resolved view of the graph; it is rebuilt on the next run. */
    public void invalidatePrepared() {
        if (graph.graphModel instanceof CustomGraphModelImpl gm) PreparedGraph.invalidate(gm);
        prepared = null;
        slots = EMPTY_VALUES;
        nums = EMPTY_NUMS;
        kinds = EMPTY_KINDS;
        stamps = EMPTY_STAMPS;
        written = EMPTY_STAMPS;
        writtenCount = 0;
    }
}
