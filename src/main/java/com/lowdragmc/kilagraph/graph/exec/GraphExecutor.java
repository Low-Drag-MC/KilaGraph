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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

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
    /**
     * Not final: a pooled subgraph child is handed a fresh environment on every call, because
     * {@code createChild} is a documented override point for a host carrying its own context
     * down and must not be skipped. See {@link #resetForReuse}.
     */
    private EvaluationEnvironment env;

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

    /**
     * The variable cell each variable-touching node resolved, by node index, plus the name it was
     * resolved for.
     *
     * <p>Per executor, not per prepared graph: a cell belongs to one {@link VariableStore}, and a
     * prepared graph is shared by executors with different environments. Cleared whenever the
     * environment or the prepared graph is replaced, which is the only way a cached cell could come
     * to belong to the wrong store.</p>
     *
     * <p>The name is re-checked by identity. {@code SetVar} takes its name from an option whose
     * {@code String} is stable until edited, and a variable node's name likewise — so the check
     * costs a reference compare and an edit resolves afresh.</p>
     */
    private VarCell[] varCells = EMPTY_CELLS;
    private String[] varCellNames = EMPTY_CELL_NAMES;

    /**
     * The controller of each loop node currently iterating, by node index.
     *
     * <p>A loop's {@code index} and {@code item} outputs are recomputed from here whenever they are
     * demanded, rather than written into the node's slot when the iteration begins. The engine clears
     * the value cache between iterations, so a slot written at iteration start would be wiped by a
     * <em>nested</em> loop's clear and the enclosing loop's index would vanish — which is the bug
     * {@code ForNode}'s javadoc has always warned about.</p>
     */
    private LoopController[] activeLoops = EMPTY_LOOPS;

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

    /**
     * Recorder for what this run actually evaluated, or {@code null} — see {@link EvalTrace}.
     * Null for every non-test caller, so the hot paths pay one perfectly-predicted null check.
     */
    @Nullable private EvalTrace trace;

    /**
     * An executor optimisation that can be switched off.
     *
     * <p>Every optimisation keeps the path it replaced, reachable through here, so a test can run the
     * same graph both ways and demand the results be indistinguishable — see
     * {@code KGDifferential}. That is the safety argument for each of them, and it only works if
     * turning one off really does restore the old behaviour rather than an approximation of it.</p>
     */
    public enum Opt {
        /**
         * Resolve a subgraph call site's mirror pins, callee and entry node at prepare time instead
         * of re-deriving them — {@code uid.toString()} and a concatenation per variable per call.
         */
        SUBGRAPH_PRERESOLVE,
        /**
         * Reuse a subgraph's child executor across calls instead of building one — and its value
         * tables, its context pools and its two collections — every time.
         */
        SUBGRAPH_POOLING,
        /**
         * Read a node option through the prepared constant table instead of
         * {@code PortModel.getEmbeddedValue()}, which is a hash lookup keyed by the port's unique
         * name — paid on every option read of every evaluation.
         */
        OPTION_PRERESOLVE,
        /**
         * Dispatch an exec step straight off the prepared node the frame queue holds.
         *
         * <p>Switching this off does not restore an older code path — there isn't one, the queue now
         * holds prepared nodes throughout. It puts back exactly the work that removing that lookup
         * took away: the {@code IdentityHashMap} hop from model to prepared node, and the
         * {@code mayCycle} re-read that came with it. That makes the change measurable against itself
         * on a machine where a before-and-after across two runs of the suite cannot resolve it.</p>
         */
        EXEC_PRERESOLVE,
        /**
         * Read and write graph variables through a cached {@link VarCell} instead of the store's
         * name-keyed map, keeping numbers in the raw-bits lane on the way through.
         */
        VARIABLE_CELLS,
        /**
         * Evaluate the arithmetic nodes {@link Intrinsics} knows directly, instead of calling
         * their {@code evaluate} through the shared megamorphic call site.
         */
        INTRINSICS,
        /**
         * Run the simple exec nodes — {@code Entry}, {@code Noop}, {@code Branch}, {@code Gate},
         * {@code SetVar} — without binding an {@link ExecContext} or calling their {@code execute}.
         */
        EXEC_INTRINSICS,
        /**
         * Drain a frame's queue without re-settling the stack between nodes, in
         * {@link ExecSession#runToCompletion()}. Stepping one node at a time is unaffected.
         */
        FUSED_EXEC_DRIVER,
        /**
         * Let a numeric node pick its {@link NumericLane} from what reaches it.
         *
         * <p><b>Not an optimisation, and the only entry here that is not one.</b> Switching this off
         * does not restore a slower path that computes the same thing — it makes the executor
         * <em>answer differently</em>: every promoted node reverts to working in {@code float}, which
         * is the pre-lane behaviour and is wrong above 2^24. It exists because the project's bench
         * rule is that only an interleaved paired comparison against an off switch counts, and
         * without one the cost of the lane check could only ever be estimated. Never ship with it
         * off; nothing but a benchmark should touch it.</p>
         */
        NUMERIC_PROMOTION,
        /**
         * Fold each promoted intrinsic's lane <em>as it reads</em> its operands, instead of walking
         * them once to decide the lane and again to read them.
         *
         * <p>A real optimisation — both paths compute the same thing. It has its own switch because
         * once the fold is fused into the pull, {@link #NUMERIC_PROMOTION} can no longer isolate its
         * cost: the off side would still be computing the lane it then ignores. This is the only
         * comparison that answers "is one pass cheaper than two".</p>
         */
        SINGLE_PASS_LANE
    }

    /** @see Opt#FUSED_EXEC_DRIVER */
    boolean fusedExecDriver() {
        return opt(Opt.FUSED_EXEC_DRIVER);
    }

    /** @see Opt#OPTION_PRERESOLVE */
    boolean optionPreresolve() {
        return opt(Opt.OPTION_PRERESOLVE);
    }

    /** @see Opt#NUMERIC_PROMOTION */
    boolean numericPromotion() {
        return opt(Opt.NUMERIC_PROMOTION);
    }

    /** @see Opt#SINGLE_PASS_LANE */
    private boolean singlePassLane() {
        return opt(Opt.SINGLE_PASS_LANE);
    }

    /** Bitmask over {@link Opt}; every optimisation on by default. Propagated to child executors. */
    private int enabledOpts = -1;

    private boolean opt(Opt o) {
        return (enabledOpts & (1 << o.ordinal())) != 0;
    }

    /**
     * Turn an executor optimisation on or off. Test and diagnostic use only — the off path exists to
     * be compared against, not to be shipped. Applies to subgraph child executors too.
     */
    public void setOptimisationEnabled(Opt o, boolean enabled) {
        int bit = 1 << o.ordinal();
        enabledOpts = enabled ? (enabledOpts | bit) : (enabledOpts & ~bit);
    }

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
    private static final VarCell[] EMPTY_CELLS = new VarCell[0];
    private static final String[] EMPTY_CELL_NAMES = new String[0];
    private static final LoopController[] EMPTY_LOOPS = new LoopController[0];

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
                varCells = EMPTY_CELLS;
                varCellNames = EMPTY_CELL_NAMES;
                activeLoops = EMPTY_LOOPS;
            }
        }
        cycleChecks = prepared == null || prepared.mayCycle();
        ensureCapacity();
    }

    /**
     * Refresh the prepared form, but only when this really is a top-level entry.
     *
     * <p>Swapping it mid-evaluation would pull the slot table out from under every caller further
     * up that already holds slot indices. {@code InfoPropertyBlock} reaches {@link #pullInputValue}
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
            varCells = Arrays.copyOf(varCells, size);
            varCellNames = Arrays.copyOf(varCellNames, size);
            activeLoops = Arrays.copyOf(activeLoops, size);
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

    /**
     * Resolve a node named by its model, for the enqueue paths that start from one. Prepares the
     * graph first: a subgraph child is seeded with its entry node before it has ever run.
     */
    @Nullable
    PreparedGraph.Node resolveForFlow(@Nullable NodeModel m) {
        if (prepared == null) syncPrepared();
        return resolve(m);
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
    void executeStep(PreparedGraph.Node node, ExecSession session, ExecFrame frame) {
        if (node == null) return;
        // The queue is addressed by prepared node now, so a node from another graph would index this
        // executor's slot table and quietly read someone else's values. Frames resolve against their
        // own scope, so this cannot happen — but "cannot" here rested on every enqueue path being
        // right, and the failure is silent wrong data rather than a crash, which is worth one
        // reference compare per step to rule out.
        if (node.ownerGraph() != prepared) {
            throw new IllegalStateException("exec step for a node from a different prepared graph: "
                    + node.uid);
        }
        if (!opt(Opt.EXEC_PRERESOLVE)) {
            // Measurement path only — see Opt.EXEC_PRERESOLVE. Same node back, same work as before.
            node = resolve(node.model);
            if (node == null) return;
        }
        if (trace != null) trace.recordExec(node.model);
        if (node.execOp != Intrinsics.NONE && opt(Opt.EXEC_INTRINSICS)) {
            execIntrinsic(node, frame);
            return;
        }
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
     * Run an exec node directly, without binding a context or calling it.
     *
     * <p>As on the data side, each case is a transcription of the node's {@code execute} body. The
     * saving is larger here: an exec node's context bind clears a staged-output table and its flush
     * walks every output slot, and {@code Branch}, {@code Gate} and {@code Noop} stage nothing at
     * all — so the whole staging round trip was pure overhead for the commonest control-flow nodes
     * in any graph.</p>
     */
    private void execIntrinsic(PreparedGraph.Node n, ExecFrame frame) {
        switch (n.execOp) {
            // Entry / Noop: ctx.flow(the one exec output)
            case Intrinsics.XOP_FLOW -> frame.enqueueAll(n.flowTargets[n.execFlowA]);

            // Branch: ctx.flow(ctx.getBool("cond", false) ? "trueExec" : "falseExec")
            case Intrinsics.XOP_BRANCH ->
                    frame.enqueueAll(n.flowTargets[pullBool(n, n.execIn, false) ? n.execFlowA : n.execFlowB]);

            // Gate: if (ctx.getBool("enabled", true)) ctx.flow("out")
            case Intrinsics.XOP_GATE -> {
                if (pullBool(n, n.execIn, true)) frame.enqueueAll(n.flowTargets[n.execFlowA]);
            }

            // SetVar: ctx.setVariable(ctx.getOption("varName", String, ""), "value"); ctx.flow("next")
            case Intrinsics.XOP_SETVAR -> {
                Object raw = optionValue(n, n.execAux);
                if (raw instanceof String name) assignVariable(n, n.execIn, name);
                frame.enqueueAll(n.flowTargets[n.execFlowA]);
            }

            default -> { }
        }
    }

    /** An input as a {@code boolean}, matching {@code EvalContext.getBool}: only a Boolean counts. */
    private boolean pullBool(PreparedGraph.Node owner, int inputIndex, boolean def) {
        Object raw = pullInput(owner, inputIndex, Object.class);
        return raw instanceof Boolean b ? b : def;
    }

    /** A node option's current value, read the way {@code EvalContext.getOption} reads it. */
    @Nullable
    private Object optionValue(PreparedGraph.Node n, int optionIndex) {
        int ii = n.optionInputIndex[optionIndex];
        Constant c = ii >= 0 && opt(Opt.OPTION_PRERESOLVE) ? n.inputConstants[ii]
                : n.optionPorts[optionIndex].getEmbeddedValue();
        return c == null ? null : c.getValue();
    }

    /**
     * Enter a subgraph on the exec path: seed the child env from the inner READ data variables, then
     * push a {@link SubgraphFrame} (scoped to a child executor) primed at the inner exec-IN
     * variable's downstream. The frame's {@code resume} harvests WRITE data + fires reached exec-out
     * pins when the inner exec drains. Unresolved/self-ref → fire all exec-out pins into {@code frame}
     * so the outer flow doesn't dead-end.
     */
    private void subgraphEnter(PreparedGraph.Node node, ExecSession session, ExecFrame frame) {
        if (!opt(Opt.SUBGRAPH_PRERESOLVE)) {
            subgraphEnterUnresolved(node, session, frame);
            return;
        }
        CustomGraphModelImpl inner = node.subInner;
        if (inner == null) {
            // Unresolved or self-referential callee: fire every exec pin so the outer flow does not
            // dead-end, exactly as before.
            for (PortModel p : node.execOutputs) frame.enqueueFlow(p);
            return;
        }
        VariableStore childStore = childStoreFor(node);
        for (PreparedGraph.SubBinding bind : node.subBindings) {
            if (bind.isExec() || !bind.read() || bind.outerIn() == null) continue;
            Object value = bind.outerInIndex() >= 0
                    ? pullInput(node, bind.outerInIndex(), Object.class)
                    : pullInput(bind.outerIn(), Object.class);
            childStore.put(bind.decl().getName(), value);
        }
        var childExec = checkoutChild(node, inner, childStore);
        var subFrame = new SubgraphFrame(childExec, this, frame, node, inner);
        subFrame.enqueueEntry(node.subEntry);
        session.push(subFrame);
    }

    /** The pre-{@link Opt#SUBGRAPH_PRERESOLVE} path, kept so the two can be compared. */
    private void subgraphEnterUnresolved(PreparedGraph.Node node, ExecSession session, ExecFrame frame) {
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
        var childExec = checkoutChild(null, inner, childStore);
        var subFrame = new SubgraphFrame(childExec, this, frame, node, inner);
        subFrame.enqueueEntry(findExecEntryNode(inner));
        session.push(subFrame);
    }

    /**
     * Free child executors, keyed by the call site that made them. Lazily created — a graph with no
     * subgraphs never allocates it.
     *
     * <p>A list per call site rather than one executor, because a subgraph can be entered while an
     * earlier entry of the same call site is still live: recursion, and a loop body whose
     * continuation outlives the call. The list is a stack, so the common depth-1 case touches
     * index 0 every time.</p>
     */
    @Nullable private Map<PreparedGraph.Node, List<GraphExecutor>> subPool;

    /**
     * A child executor for {@code inner}, taken from the pool if one is free.
     *
     * <p>Building one per call meant building its value tables, its two context pools, its node-state
     * map and its reached-exits set every time — and then throwing all of it away. Reuse keeps the
     * arrays, which is most of what a subgraph call was allocating.</p>
     */
    /**
     * The variable store to seed for a call of {@code site}: the free child's, emptied for reuse, or
     * a new one.
     *
     * <p>Pooling the store as well as the executor matters more than it looks. A store now holds a
     * {@link VarCell} per name rather than a boxed value, and a fresh store per call means a fresh
     * cell per parameter per call — which cost more than the boxing the cells removed. Reusing it
     * makes a repeated call allocate neither.</p>
     *
     * <p>{@code clear()} marks the cells absent without dropping them, so a node that resolved one
     * keeps a valid reference. This peeks the same end of the free list that
     * {@link #checkoutChild} pops from, so the store and the executor that get used are a pair.</p>
     *
     * <p>The contract this asks of a host: the store handed to {@code createChild} belongs to the
     * call and must not be retained past it. Nothing retains one today, and the environment wrapping
     * it is still built fresh every time.</p>
     */
    private VariableStore childStoreFor(@Nullable PreparedGraph.Node site) {
        if (site == null || !opt(Opt.SUBGRAPH_POOLING) || subPool == null) return new VariableStore();
        List<GraphExecutor> free = subPool.get(site);
        if (free == null || free.isEmpty()) return new VariableStore();
        VariableStore store = free.get(free.size() - 1).env.variables();
        store.clear();
        return store;
    }

    private GraphExecutor checkoutChild(@Nullable PreparedGraph.Node site, CustomGraphModelImpl inner,
                                        VariableStore childStore) {
        // createChild is a documented override point for a host carrying its own context down, so it
        // is called on every entry rather than cached or reused.
        EvaluationEnvironment childEnv = env.createChild(childStore);
        if (site != null && opt(Opt.SUBGRAPH_POOLING) && subPool != null) {
            List<GraphExecutor> free = subPool.get(site);
            if (free != null && !free.isEmpty()) {
                GraphExecutor reused = free.remove(free.size() - 1);
                reused.resetForReuse(childEnv);
                reused.trace = trace;
                reused.enabledOpts = enabledOpts;
                reused.graphFrozen = graphFrozen;
                return reused;
            }
        }
        var childExec = new GraphExecutor(inner.getGraph(), childEnv);
        childExec.trace = trace;                  // one trace spans the whole call tree; see EvalTrace
        childExec.enabledOpts = enabledOpts;      // a mode applies to the whole call tree, not just its root
        // Freezing is a statement about the asset, and a function is part of the asset that calls it.
        // Without this a child re-derives the structural digest on every single invocation — the
        // largest per-entry cost there is — and, worse, a digest that ever came back false would have
        // it rebuild a PreparedGraph that sibling threads are reading. See PreparedGraph#seal().
        childExec.graphFrozen = graphFrozen;
        return childExec;
    }

    /**
     * Hand a finished child executor back for the next call of the same site.
     *
     * <p>Only ever called on the path where the child completed normally. A child whose run threw is
     * simply dropped: its depth counters and cycle-detection stack are mid-unwind, and putting that
     * back into a pool would carry the damage into an unrelated later call.</p>
     */
    private void releaseChild(@Nullable PreparedGraph.Node site, GraphExecutor child) {
        if (site == null || !opt(Opt.SUBGRAPH_POOLING)) return;
        if (subPool == null) subPool = new HashMap<>();
        subPool.computeIfAbsent(site, k -> new ArrayList<>(2)).add(child);
    }

    /**
     * Make a pooled executor indistinguishable from a newly built one, then bind it to {@code newEnv}.
     *
     * <p>Every difference a caller could observe has to be undone, and each of these is load-bearing
     * rather than defensive:</p>
     * <ul>
     *   <li><b>node state</b> — a fresh executor has no {@code Cache} memo, so a subgraph containing
     *       a {@code Cache} recomputes on every call. Keeping the map would silently turn that into
     *       a memo across calls;</li>
     *   <li><b>the RNG</b> — a fresh executor builds a new {@code Random} from the environment's
     *       seed, so a seeded subgraph draws the same sequence on every call. Keeping it would make
     *       the sequence continue instead;</li>
     *   <li><b>reached exits</b> — the parent reads these to decide which exec-out pins to fire;</li>
     *   <li><b>the value tables</b> — kept deliberately. That is the entire point.</li>
     * </ul>
     */
    private void resetForReuse(EvaluationEnvironment newEnv) {
        // A cached cell belongs to a store, not to an environment. The environment is rebuilt on
        // every call (createChild is a host override point), but the store behind it is pooled — so
        // the cache survives exactly when the store does.
        if (this.env == null || this.env.variables() != newEnv.variables()) {
            Arrays.fill(varCells, null);
            Arrays.fill(varCellNames, null);
        }
        this.env = newEnv;
        Arrays.fill(activeLoops, null);
        this.rng = null;
        nodeState.clear();
        reachedExecOutputs.clear();
        clearCache();
        for (int i = 0; i < visitDepth; i++) {
            onStack[visitStack[i].index] = false;
            visitStack[i] = null;
        }
        visitDepth = 0;
        evalDepth = 0;
        execDepth = 0;
        pooledSessionBusy = false;
    }

    // ---- loop iteration state ------------------------------------------------------------------

    /** Register {@code controller} as {@code node}'s running loop. @see #activeLoops */
    void beginLoop(PreparedGraph.Node node, LoopController controller) {
        if (node.index < activeLoops.length) activeLoops[node.index] = controller;
    }

    /**
     * Forget every loop's iteration state.
     *
     * <p>Paired with {@link #clearNodeState()}, which is where this state used to live: a loop's
     * index and item were entries in the per-node map, so clearing that cleared them. Nothing else
     * clears it — in particular the end of a loop does not, so its final index stays readable.</p>
     */
    void clearActiveLoops() {
        Arrays.fill(activeLoops, null);
    }

    /** The controller of the loop {@code nodeIndex} is running, or null. */
    @Nullable
    LoopController activeLoop(int nodeIndex) {
        return nodeIndex < activeLoops.length ? activeLoops[nodeIndex] : null;
    }

    // ---- per-node state ----------------------------------------------------------------------

    /** Per-node persistent state — survives across {@code executeFrom} invocations on this executor. */
    public Map<String, Object> nodeState(UUID nodeUid) {
        return nodeState.computeIfAbsent(nodeUid, k -> new HashMap<>());
    }

    /** Reset per-node state — call between independent runs if you want a clean slate. */
    public void clearNodeState() {
        nodeState.clear();
        clearActiveLoops();   // loop index/item used to live in nodeState; clearing it cleared them
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
    /**
     * Drops a node's cached outputs, keeping its memory.
     *
     * <p>The distinction matters: {@link #invalidateNode} exists for {@code CacheClear}, whose whole
     * job is to forget, while a host that writes a variable mid-run only needs the readers
     * downstream of it to recompute. Erasing {@code nodeState} on that path silently resets every
     * damped value, previous-frame edge and loop counter downstream of the write — once per frame,
     * so they never accumulate anything and no error is ever raised.
     */
    public void invalidateNodeOutputs(NodeModel target) {
        if (target == null) return;
        PreparedGraph.Node n = resolve(target);
        if (n == null) return;
        for (int slot : n.outputSlots) {
            if (slot < stamps.length) {
                stamps[slot] = 0;
                slots[slot] = null;
            }
        }
    }

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

    /**
     * Write a reference value, logging the slot so {@link #clearCache()} can release it.
     *
     * <p>The log is keyed on <em>storing a reference</em>, not on being the first write of the
     * generation. Those two used to be the same thing, and are not any more: {@link #writeNum} marks
     * a slot's stamp without logging it, so a later object write to that same slot would have found
     * the stamp current and skipped the log — and the object would then have survived
     * {@code clearCache} with nothing holding a record of it. No path reaches that today, but the
     * consequence is a retained {@code Entity} or {@code ItemStack} rather than a wrong answer, and
     * an invariant that has to be argued from the whole program is not one worth relying on.
     * Logging every non-null store keeps it local: <b>if a slot holds a reference, it is in the
     * log.</b> A null store needs no entry because there is nothing to release, and duplicates are
     * harmless — {@code clearCache} just nulls the slot twice.</p>
     */
    void writeSlot(int slot, Object value) {
        kinds[slot] = KIND_OBJECT;
        stamps[slot] = generation;
        if (value != null) {
            if (writtenCount == written.length) {
                written = Arrays.copyOf(written, Math.max(16, written.length * 2));
            }
            written[writtenCount++] = slot;
        }
        slots[slot] = value;
    }

    /**
     * Write an unboxed number. {@code slots[slot]} is cleared so the old value is not retained.
     *
     * <p>Unlike {@link #writeSlot} this does not log the slot in {@link #written}, and does not need
     * to: the only thing {@link #clearCache()} does with that log is null the object lane, and this
     * method has just nulled it. Skipping the log removes a load, a branch, a bounds check and a
     * store from every numeric write — and shortens {@code clearCache} to the object writes, which
     * for an arithmetic graph is almost none of them.</p>
     */
    void writeNum(int slot, byte kind, long bits) {
        slots[slot] = null;
        nums[slot] = bits;
        kinds[slot] = kind;
        stamps[slot] = generation;
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

    /**
     * Which {@link NumericLane} the value feeding {@code inputIndex} asks the reading node to work in.
     *
     * <p>The distinction the lane rule turns on — is this input <em>connected</em>, or is it an
     * unconnected embedded constant — is already resolved here: a source slot of -1 is precisely
     * "unconnected", so it costs the array read the pull was going to do anyway. See
     * {@link NumericLane#ofConstant} for why the two cases answer differently.</p>
     *
     * <p>A connected input has to be computed before its kind means anything, so this evaluates the
     * producer exactly as a pull would. That is memoised, so the pull that follows is a stamp
     * comparison, and — since every node that asks about a lane goes on to read every input it
     * asked about, in the same order — nothing is evaluated that would not have been.</p>
     */
    byte pullLane(PreparedGraph.Node owner, int inputIndex) {
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) {
            return NumericLane.ofConstant(readConstant(owner.inputConstants[inputIndex], Object.class));
        }
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) return NumericLane.NONE;
        byte k = kinds[srcSlot];
        return k == KIND_OBJECT ? NumericLane.of(slots[srcSlot]) : NumericLane.ofKind(k);
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
        // Recorded before the intrinsic check, so the trace — and therefore the evaluation count the
        // differential harness compares — is identical whichever path runs.
        if (trace != null) trace.recordEval(n.model);
        if (n.op != Intrinsics.NONE && opt(Opt.INTRINSICS) && evalIntrinsic(n)) return;
        switch (n.dataKind) {
            // 1) Constant node — read the constant value directly.
            case CONSTANT -> writeAllOutputs(n,
                    n.constantNode.tryGetValue(n.constantNode.getDataType()).result().orElse(null));

            // 2) Variable node — defer to the environment (which checks store then default).
            case VARIABLE -> evaluateVariable(n);

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
     * Evaluate {@code n} directly, without calling the node.
     *
     * <p>Each case transcribes one node's {@code evaluate} body — the same reads, in the same order,
     * with the same default literals. See {@link Intrinsics} for why that is the whole trick, and for
     * the size limit this method has to stay under.</p>
     *
     * @return whether it was handled; {@code false} sends the node down its normal path
     */
    private boolean evalIntrinsic(PreparedGraph.Node n) {
        return n.op < Intrinsics.OP_PREDICATE_BASE ? evalArithmetic(n) : evalPredicate(n);
    }

    /**
     * The arithmetic half of the intrinsic dispatch.
     *
     * <p>Split from {@link #evalPredicate} deliberately. One switch over every opcode would grow
     * toward HotSpot's {@code DontCompileHugeMethods} limit and eventually stop being compiled at
     * all — an "optimisation" that is a large regression and looks like nothing. Keep each half
     * small, and split again rather than merging them to tidy up.</p>
     */
    private boolean evalArithmetic(PreparedGraph.Node n) {
        int[] in = n.opIn;
        // The variadic shape check, hoisted out of the four cases that used to make it individually.
        // It has to come before the lane fold as well as before the body: a node whose arity option
        // disagrees with its port count reads a different set of inputs than opIn names, and folding
        // a lane over the wrong set would evaluate a producer the node never asks for — which the
        // differential harness would see, since it compares which nodes ran. opAux is -1 for every
        // non-variadic opcode, so this is a single predictable branch for the rest.
        if (n.opAux >= 0 && variadicArity(n) != in.length) return false;
        if (!singlePassLane() && !isFloatLane(n)) return false;
        switch (n.op) {
            // Math.abs(getFloat("in", 0f))
            case Intrinsics.OP_ABS -> {
                float v = pullFloatLane(n, in[0], 0f);
                if (!floatLane(lastLane)) return false;
                writeFloat(n.opOutSlot, Math.abs(v));
            }

            // v = getFloat("in", 0f); v < 0f ? 0f : (float) Math.sqrt(v)
            case Intrinsics.OP_SQRT -> {
                float v = pullFloat(n, in[0], 0f);
                writeFloat(n.opOutSlot, v < 0f ? 0f : (float) Math.sqrt(v));
            }

            // -getFloat("in", 0f)
            case Intrinsics.OP_NEGATE -> {
                float v = pullFloatLane(n, in[0], 0f);
                if (!floatLane(lastLane)) return false;
                writeFloat(n.opOutSlot, -v);
            }

            // v = getFloat("in", 0f); v > 0f ? 1f : v < 0f ? -1f : 0f
            case Intrinsics.OP_SIGN -> {
                float v = pullFloatLane(n, in[0], 0f);
                if (!floatLane(lastLane)) return false;
                writeFloat(n.opOutSlot, v > 0f ? 1f : v < 0f ? -1f : 0f);
            }

            // v = getFloat("in", 0f); v - (float) Math.floor(v)
            case Intrinsics.OP_FRACT -> {
                float v = pullFloat(n, in[0], 0f);
                writeFloat(n.opOutSlot, v - (float) Math.floor(v));
            }

            // (float) Math.exp(getFloat("in", 0f))
            case Intrinsics.OP_EXP -> writeFloat(n.opOutSlot, (float) Math.exp(pullFloat(n, in[0], 0f)));

            // v=getFloat("in",0f); lo=getFloat("min",0f); hi=getFloat("max",1f); max(lo, min(hi, v))
            case Intrinsics.OP_CLAMP -> {
                float v = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float lo = pullFloatLane(n, in[1], 0f);
                lane = NumericLane.widen(lane, lastLane);
                float hi = pullFloatLane(n, in[2], 1f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeFloat(n.opOutSlot, Math.max(lo, Math.min(hi, v)));
            }

            // va=getFloat("a",0f); vb=getFloat("b",0f); va - vb
            case Intrinsics.OP_SUB -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 0f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeFloat(n.opOutSlot, va - vb);
            }

            // va=getFloat("a",0f); vb=getFloat("b",1f); vb == 0f ? 0f : va / vb
            case Intrinsics.OP_DIV -> {
                float va = pullFloat(n, in[0], 0f);
                float vb = pullFloat(n, in[1], 1f);
                writeFloat(n.opOutSlot, vb == 0f ? 0f : va / vb);
            }

            // va=getFloat("a",0f); vb=getFloat("b",1f); vb == 0f ? 0f : va % vb
            case Intrinsics.OP_MOD -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 1f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeFloat(n.opOutSlot, vb == 0f ? 0f : va % vb);
            }

            // (float) Math.pow(getFloat("base", 1f), getFloat("exp", 1f))
            case Intrinsics.OP_POW -> writeFloat(n.opOutSlot,
                    (float) Math.pow(pullFloat(n, in[0], 1f), pullFloat(n, in[1], 1f)));

            // vy=getFloat("y",0f); vx=getFloat("x",1f); (float) Math.atan2(vy, vx)
            case Intrinsics.OP_ATAN2 -> {
                float vy = pullFloat(n, in[0], 0f);
                float vx = pullFloat(n, in[1], 1f);
                writeFloat(n.opOutSlot, (float) Math.atan2(vy, vx));
            }

            // v=getFloat("in",1f); b=getFloat("base",E); guard, then log(v)/log(b)
            case Intrinsics.OP_LOG -> {
                float v = pullFloat(n, in[0], 1f);
                float b = pullFloat(n, in[1], (float) Math.E);
                writeFloat(n.opOutSlot, v <= 0f || b <= 0f || b == 1f
                        ? 0f : (float) (Math.log(v) / Math.log(b)));
            }

            // v=getFloat("value",1f); b=getFloat("base",10f); same guard
            case Intrinsics.OP_LOGBASE -> {
                float v = pullFloat(n, in[0], 1f);
                float b = pullFloat(n, in[1], 10f);
                writeFloat(n.opOutSlot, v <= 0f || b <= 0f || b == 1f
                        ? 0f : (float) (Math.log(v) / Math.log(b)));
            }

            // va=getFloat("a",0f); vb=getFloat("b",1f); vt=getFloat("t",0f); va + (vb - va) * vt
            case Intrinsics.OP_LERP -> {
                float va = pullFloat(n, in[0], 0f);
                float vb = pullFloat(n, in[1], 1f);
                float vt = pullFloat(n, in[2], 0f);
                writeFloat(n.opOutSlot, va + (vb - va) * vt);
            }

            // in/fromMin/fromMax/toMin/toMax, defaults 0,0,1,0,1; a zero span answers toMin
            case Intrinsics.OP_REMAP -> {
                float v = pullFloat(n, in[0], 0f);
                float fMin = pullFloat(n, in[1], 0f);
                float fMax = pullFloat(n, in[2], 1f);
                float tMin = pullFloat(n, in[3], 0f);
                float tMax = pullFloat(n, in[4], 1f);
                float span = fMax - fMin;
                if (span == 0f) {
                    writeFloat(n.opOutSlot, tMin);
                } else {
                    writeFloat(n.opOutSlot, tMin + (tMax - tMin) * ((v - fMin) / span));
                }
            }

            // n = max(1, option("inputs")); sum = 0f; sum += getFloat("in"+i, 0f)
            case Intrinsics.OP_ADD -> {
                byte lane = NumericLane.NONE;
                float sum = 0f;
                for (int i = 0; i < in.length; i++) {
                    sum += pullFloatLane(n, in[i], 0f);
                    lane = NumericLane.widen(lane, lastLane);
                }
                if (!floatLane(lane)) return false;
                writeFloat(n.opOutSlot, sum);
            }

            // n = max(1, option("inputs")); p = 1f; p *= getFloat("in"+i, 1f)
            case Intrinsics.OP_MUL -> {
                byte lane = NumericLane.NONE;
                float p = 1f;
                for (int i = 0; i < in.length; i++) {
                    p *= pullFloatLane(n, in[i], 1f);
                    lane = NumericLane.widen(lane, lastLane);
                }
                if (!floatLane(lane)) return false;
                writeFloat(n.opOutSlot, p);
            }

            // n = max(1, option("inputs")); m = +Inf; m = min(m, getFloat("in"+i, 0f))
            case Intrinsics.OP_MIN -> {
                byte lane = NumericLane.NONE;
                float m = Float.POSITIVE_INFINITY;
                for (int i = 0; i < in.length; i++) {
                    m = Math.min(m, pullFloatLane(n, in[i], 0f));
                    lane = NumericLane.widen(lane, lastLane);
                }
                if (!floatLane(lane)) return false;
                writeFloat(n.opOutSlot, m);
            }

            // n = max(1, option("inputs")); m = -Inf; m = max(m, getFloat("in"+i, 0f))
            case Intrinsics.OP_MAX -> {
                byte lane = NumericLane.NONE;
                float m = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < in.length; i++) {
                    m = Math.max(m, pullFloatLane(n, in[i], 0f));
                    lane = NumericLane.widen(lane, lastLane);
                }
                if (!floatLane(lane)) return false;
                writeFloat(n.opOutSlot, m);
            }

            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * The predicate half of the intrinsic dispatch. See {@link #evalArithmetic} for why it is split.
     *
     * <p>These stay in the object lane, as their nodes do: neither has a boolean {@code setOutput}
     * overload, {@code Boolean.valueOf} is cached so nothing is allocated, and the wrapper a
     * consumer sees is the same one.</p>
     */
    private boolean evalPredicate(PreparedGraph.Node n) {
        int[] in = n.opIn;
        switch (n.op) {
            // getFloat("a", 0f) OP getFloat("b", 0f)
            case Intrinsics.OP_GT -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 0f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeSlot(n.opOutSlot, va > vb);
            }
            case Intrinsics.OP_GE -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 0f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeSlot(n.opOutSlot, va >= vb);
            }
            case Intrinsics.OP_LT -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 0f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeSlot(n.opOutSlot, va < vb);
            }
            case Intrinsics.OP_LE -> {
                float va = pullFloatLane(n, in[0], 0f);
                byte lane = lastLane;
                float vb = pullFloatLane(n, in[1], 0f);
                if (!floatLane(NumericLane.widen(lane, lastLane))) return false;
                writeSlot(n.opOutSlot, va <= vb);
            }

            // !NumericLane.valuesEqual(getInputRaw("a"), getInputRaw("b"))
            case Intrinsics.OP_NEQ -> writeSlot(n.opOutSlot, !NumericLane.valuesEqual(
                    pullInput(n, in[0], Object.class), pullInput(n, in[1], Object.class)));

            // !getBool("in", false)
            case Intrinsics.OP_NOT -> writeSlot(n.opOutSlot, !pullBool(n, in[0], false));

            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * The arity a variadic arithmetic node's option asks for, or -1 when it cannot be read as a
     * number.
     *
     * <p>Checked against the port count rather than trusted. The two agree in practice — the ports
     * are defined from this same option — but if they ever disagreed the node would read a different
     * number of inputs than the intrinsic, or throw. Returning -1 there sends it down its own path
     * to do whichever of those it was going to do.</p>
     */
    private int variadicArity(PreparedGraph.Node n) {
        Object raw = optionValue(n, n.opAux);
        return raw instanceof Number num ? Math.max(1, num.intValue()) : -1;
    }

    /**
     * The lane the operand most recently read by {@link #pullFloatLane} asked for.
     *
     * <p>A side channel rather than a return value, because the caller wants the {@code float} and
     * the lane from one read and Java has no cheap pair. <b>It must be consumed immediately</b>, before
     * the next pull — which is also what makes it re-entrancy-safe: {@code pullFloatLane} writes it
     * after {@code ensureComputed} has returned, so a nested intrinsic evaluated inside that call has
     * already finished with its own value by the time ours is written.</p>
     */
    private byte lastLane;

    /**
     * {@link #pullFloat}, plus the {@link NumericLane} the source asked for, in {@link #lastLane}.
     *
     * <p>This is the whole of the single-pass rewrite. A promoting intrinsic used to walk its inputs
     * twice — once through {@code pullLane} to decide whether it could answer at all, then again
     * through {@code pullFloat} to read them — which for an embedded constant meant calling
     * {@code Constant.getValue()} twice per evaluation, and for a wire meant a second
     * {@code ensureComputed}. Measured at 1.5-2 ns per input; see
     * {@code ExecutorBenchShapesGameTest.numericPromotionCost}. Now each operand is touched once and
     * reports both, and the bail-out happens before anything is written, so a node that turns out not
     * to be in the float lane is left in exactly the state the old pre-check left it in.</p>
     */
    private float pullFloatLane(PreparedGraph.Node owner, int inputIndex, float def) {
        // Hoisted, so Opt.NUMERIC_PROMOTION costs one mask test per pull rather than one per
        // classification site — and so its off position skips the classification itself rather than
        // taking a different route to the same place. See Opt.NUMERIC_PROMOTION.
        boolean lanes = numericPromotion();
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) {
            Object v = readConstant(owner.inputConstants[inputIndex], Object.class);
            lastLane = lanes ? NumericLane.ofConstant(v) : NumericLane.NONE;
            return v instanceof Number num ? num.floatValue() : def;
        }
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) {
            lastLane = NumericLane.NONE;
            return def;
        }
        byte k = kinds[srcSlot];
        long bits = nums[srcSlot];
        lastLane = lanes ? NumericLane.ofKind(k) : NumericLane.NONE;
        return switch (k) {
            case KIND_FLOAT -> Float.intBitsToFloat((int) bits);
            case KIND_DOUBLE -> (float) Double.longBitsToDouble(bits);
            case KIND_INT -> (float) (int) bits;
            case KIND_LONG -> (float) bits;
            default -> {
                Object o = slots[srcSlot];
                if (lanes) lastLane = NumericLane.of(o);
                yield o instanceof Number num ? num.floatValue() : def;
            }
        };
    }

    /**
     * Whether a folded lane is the one the float-only transcriptions can answer in.
     *
     * <p>{@code Opt.NUMERIC_PROMOTION} is consulted <em>here</em>, not inside the pull, and that
     * placement is load-bearing for the benchmark rather than for correctness. Checking it in
     * {@link #pullFloatLane} made the off side take a different code path — an extra call layer the
     * on side did not have — so the comparison was measuring the switch's own shape and two
     * consecutive runs disagreed by 6 ns per node step in opposite directions. Here both sides run
     * byte-identical pull code and differ only in this comparison, which is the thing under test.</p>
     */
    private boolean floatLane(byte lane) {
        if (!numericPromotion()) return true;
        if (!singlePassLane()) return true;   // the two-pass prologue already decided
        return NumericLane.resolve(lane) == NumericLane.FLOAT;
    }

    /** The two-pass fold: walk the operands once purely to decide the lane. @see Opt#SINGLE_PASS_LANE */
    private boolean isFloatLane(PreparedGraph.Node n) {
        if (!numericPromotion()) return true;
        byte lane = NumericLane.NONE;
        for (int i = 0; i < n.opIn.length; i++) {
            lane = NumericLane.widen(lane, pullLane(n, n.opIn[i]));
        }
        return NumericLane.resolve(lane) == NumericLane.FLOAT;
    }

    private void writeFloat(int slot, float value) {
        writeNum(slot, KIND_FLOAT, Float.floatToRawIntBits(value));
    }

    /**
     * Publish a variable node's current value.
     *
     * <p>Through a cached {@link VarCell} rather than a name-keyed lookup, and staying in the
     * numeric lane when the value is there — so a float written by {@code SetVar} and read back by a
     * get-node never becomes a {@code Float} on either leg.</p>
     */
    private void evaluateVariable(PreparedGraph.Node n) {
        IVariable var = n.variableNode.getVariable();
        if (!opt(Opt.VARIABLE_CELLS) || var == null) {
            writeAllOutputs(n, env.lookupVariable(var));
            return;
        }
        VarCell cell = cellFor(n.index, var.getName());
        if (!cell.present) {
            writeAllOutputs(n, var.tryGetDefaultValue(var.getDataType()).result().orElse(null));
        } else if (cell.kind == KIND_OBJECT) {
            writeAllOutputs(n, cell.value);
        } else {
            for (int slot : n.outputSlots) writeNum(slot, cell.kind, cell.num);
        }
    }

    /**
     * Assign the value feeding {@code owner}'s input {@code inputIndex} to the variable {@code name},
     * preserving the lane it arrived in.
     *
     * <p>This is what lets {@code SetVar} stop boxing. The old path read the input as an
     * {@code Object} — which boxed every float on the way out of the port table — and then put that
     * box into a {@code Map<String,Object>}. Here the producing slot's kind is read directly and raw
     * bits are copied across.</p>
     */
    void assignVariable(PreparedGraph.Node owner, int inputIndex, String name) {
        if (name == null || name.isEmpty()) return;
        if (!opt(Opt.VARIABLE_CELLS)) {
            env.variables().put(name, pullInput(owner, inputIndex, Object.class));
            return;
        }
        VarCell cell = cellFor(owner.index, name);
        int srcSlot = owner.inputSourceSlots[inputIndex];
        if (srcSlot < 0) {
            cell.setObject(readConstant(owner.inputConstants[inputIndex], Object.class));
            return;
        }
        PreparedGraph.Node src = owner.inputSourceOwners[inputIndex];
        if (!ensureComputed(src, srcSlot)) {
            cell.setObject(null);
            return;
        }
        byte k = kinds[srcSlot];
        if (k == KIND_OBJECT) cell.setObject(slots[srcSlot]);
        else cell.setNum(k, nums[srcSlot]);
    }

    /** The cell {@code name} resolves to for the node at {@code nodeIndex}. @see #varCells */
    private VarCell cellFor(int nodeIndex, String name) {
        // Identity, not equals: the name comes from a Constant or an IVariable and is the same
        // String object until edited, so a reference compare is both correct and the point.
        //noinspection StringEquality
        if (nodeIndex < varCells.length && varCellNames[nodeIndex] == name) {
            VarCell cached = varCells[nodeIndex];
            if (cached != null) return cached;
        }
        VarCell cell = env.variables().cell(name);
        if (nodeIndex < varCells.length) {
            varCells[nodeIndex] = cell;
            varCellNames[nodeIndex] = name;
        }
        return cell;
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
        if (opt(Opt.SUBGRAPH_PRERESOLVE)) {
            CustomGraphModelImpl inner = node.subInner;
            if (inner == null) {
                writeAllOutputs(node, null);
                return;
            }
            VariableStore childStore = childStoreFor(node);
            for (PreparedGraph.SubBinding bind : node.subBindings) {
                if (bind.isExec() || !bind.read() || bind.outerIn() == null) continue;
                Object value = bind.outerInIndex() >= 0
                        ? pullInput(node, bind.outerInIndex(), Object.class)
                        : pullInput(bind.outerIn(), Object.class);
                childStore.put(bind.decl().getName(), value);
            }
            GraphExecutor childExec = checkoutChild(node, inner, childStore);
            // The child has run nothing yet, so nothing has resolved its graph. runOutputs() used to
            // do this on the way in; harvesting straight out of the child means doing it here — and
            // once, rather than once per harvested variable.
            childExec.prepareForRun();
            for (PreparedGraph.SubBinding bind : node.subBindings) {
                if (bind.isExec() || !bind.write() || bind.outerOutSlot() < 0) continue;
                writeSlot(bind.outerOutSlot(), childExec.resolveOutputValue(bind.decl()));
            }
            releaseChild(node, childExec);
            return;
        }
        evaluateSubgraphUnresolved(node);
    }

    /** The pre-{@link Opt#SUBGRAPH_PRERESOLVE} data path, kept so the two can be compared. */
    private void evaluateSubgraphUnresolved(PreparedGraph.Node node) {
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
        childExec.trace = trace;                  // one trace spans the whole call tree; see EvalTrace
        childExec.enabledOpts = enabledOpts;      // a mode applies to the whole call tree
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
     * A finished subgraph: publish its inner WRITE data variables into this (parent) scope's value
     * table, then fire the outer exec-out pins whose inner exit the child actually reached.
     *
     * <p>One call rather than a harvest followed by a list of pins, because the pins were collected
     * into an {@code ArrayList} only to be walked once, and the data values were collected into a
     * {@code LinkedHashMap} only to be looked up by the names that had just been used to build it.
     * Both maps existed to cross a method boundary that no longer needs crossing.</p>
     */
    void finishSubgraph(PreparedGraph.Node node, CustomGraphModelImpl inner,
                        GraphExecutor childExec, ExecFrame parentFrame) {
        if (!opt(Opt.SUBGRAPH_PRERESOLVE)) {
            harvestSubgraphOutputs(node.subgraphNode, inner, childExec);
            for (PortModel pin : reachedExecOutPins(node.subgraphNode, inner, childExec)) {
                parentFrame.enqueueFlow(pin);
            }
            return;
        }
        // Normally the child is already resolved — it just ran nodes — but an inner graph with no
        // exec entry runs nothing at all, and its data outputs still have to be harvestable.
        childExec.prepareForRun();
        Set<String> reached = childExec.reachedExecOutputs();
        for (PreparedGraph.SubBinding bind : node.subBindings) {
            if (!bind.write() || bind.outerOut() == null) continue;
            if (bind.isExec()) {
                if (reached.contains(bind.decl().getName())) parentFrame.enqueueFlow(bind.outerOut());
            } else if (bind.outerOutSlot() >= 0) {
                writeSlot(bind.outerOutSlot(), childExec.resolveOutputValue(bind.decl()));
            }
        }
        releaseChild(node, childExec);
    }

    /**
     * Harvest a finished subgraph's inner WRITE <em>data</em> variables into this (parent) scope's
     * value table. The pre-{@link Opt#SUBGRAPH_PRERESOLVE} path, kept for comparison.
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

    private static boolean hasExecPort(Collection<PortModel> ports) {
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
        if (opt(Opt.SUBGRAPH_PRERESOLVE) && prepared != null) return resolveOutputValue(v);
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

    /**
     * The current value of graph OUTPUT variable {@code v}: pulled from its writer-form variable node
     * if the graph has one, else the env store, else the declared default.
     *
     * <p>Same rule as {@link #resolveOutputVariable}, but the writer is looked for among the nodes
     * {@link PreparedGraph#variableWriters()} already identified rather than by walking the whole
     * model again. The name comparison stays live: a rename is invisible to the structural digest,
     * so the <em>pairing</em> must not be cached even though the candidate set can be.</p>
     */
    Object resolveOutputValue(VariableDeclarationModelBase v) {
        if (prepared != null) {
            String wanted = v.getName();
            for (PreparedGraph.Node n : prepared.variableWriters()) {
                IVariable refVar = n.variableNode.getVariable();
                if (refVar == null || !Objects.equals(refVar.getName(), wanted)) continue;
                return pullInput(n, 0, Object.class);
            }
        }
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
     * This executor's resolved view of the graph, or null before its first run.
     *
     * <p>Exists so a host can {@link PreparedGraph#seal()} the instance and read
     * {@link PreparedGraph#sealBreached()} afterwards — the prepared form is shared by every executor
     * over the same graph model, so sealing through any one of them seals it for all of them. See
     * {@link PreparedGraph}'s class javadoc for the sequence running several executors against one
     * instance requires.</p>
     */
    @Nullable
    public PreparedGraph preparedGraph() {
        return prepared;
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
     * <p><b>The promise covers the graphs this one calls, too.</b> Child executors inherit the flag,
     * so a subgraph no longer re-derives its own digest on every invocation — which is most of what
     * made a call expensive, and which was also the last thing that would have noticed an inner graph
     * being edited while only the outer executor was frozen. Nothing does now.</p>
     *
     * <p>Editing a frozen graph produces wrong answers, not an error. Call
     * {@link #invalidatePrepared()} if you must change one.</p>
     */
    public void setGraphFrozen(boolean frozen) {
        this.graphFrozen = frozen;
    }

    /**
     * Record what this executor evaluates, for a test that needs to check evaluation order and
     * count rather than only the final values — see {@link EvalTrace} for why that distinction
     * matters. {@code null} (the default) disables recording.
     *
     * <p>The trace is handed to child executors on subgraph entry, so it covers the whole call
     * tree. Set it before the run; changing it mid-run traces only the remainder.</p>
     */
    public void setTrace(@Nullable EvalTrace trace) {
        this.trace = trace;
    }

    /** The recorder set by {@link #setTrace}, or {@code null}. */
    @Nullable
    public EvalTrace getTrace() {
        return trace;
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
