package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.IGraphEvaluable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IConstantNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.IVariable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
 *   <li>Future: {@code executeFrom(entryNode)} for exec-flow graphs.</li>
 * </ol>
 *
 * <p>Subgraphs ({@link SubgraphNodeModel}) get a transparent child executor: outer-input ports
 * mirror inner {@code READ} variables (parent feeds value in), outer-output ports mirror inner
 * {@code WRITE} variables (parent reads value out). Port ids are the inner variable's UUID
 * (with {@code -in}/{@code -out} suffixes for {@code READ_WRITE} variables).</p>
 *
 * <p>Not thread-safe; create a fresh executor per logical evaluation, or guard externally.</p>
 */
public final class GraphExecutor {

    private final Graph graph;
    private final EvaluationEnvironment env;
    /** output-port -> value (kept by identity since PortModel has no value-equality). */
    private final Map<PortModel, Object> cache = new IdentityHashMap<>();
    /** Per-evaluation visiting set for cycle detection. */
    private final LinkedHashSet<AbstractNodeModel> visiting = new LinkedHashSet<>();
    /** Lazily-instantiated RNG. */
    private Random rng;
    /** Per-node persistent state, keyed by node UID. Lazy-created via {@link #nodeState(UUID)}. */
    private final Map<UUID, Map<String, Object>> nodeState = new HashMap<>();
    /**
     * Names of {@code EXECUTION_FLOW} WRITE (OUTPUT) graph variables whose set-node this executor's
     * exec flow reached — i.e. the exec "exit" pins a subgraph run fired. A parent executor reads a
     * child's set after entering the child (see {@link #subgraphEnter} / {@link SubgraphFrame}) to
     * fire only the matching outer exec-out pins. Populated in {@link #executeStep}; one child
     * executor per subgraph entry.
     */
    private final Set<String> reachedExecOutputs = new LinkedHashSet<>();

    public GraphExecutor(Graph graph) {
        this(graph, EvaluationEnvironment.defaults());
    }

    public GraphExecutor(Graph graph, EvaluationEnvironment env) {
        this.graph = Objects.requireNonNull(graph);
        this.env = Objects.requireNonNull(env);
    }

    /** Reset the result cache. Call between independent evaluations if reusing the executor. */
    public void clearCache() {
        cache.clear();
    }

    /** Compute the value of an output port. */
    @SuppressWarnings("unchecked")
    public <T> T evaluate(PortModel outputPort, Class<T> expected) {
        if (outputPort == null) throw new IllegalArgumentException("outputPort is null");
        if (outputPort.getDirection() != PortDirection.OUTPUT) {
            throw new IllegalArgumentException("evaluate() requires an OUTPUT port, got " + outputPort.getDirection());
        }
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
        new ExecSession(this).begin(entry).runToCompletion();
    }

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
        if (n instanceof SubgraphNodeModel sub) {
            subgraphEnter(sub, session, frame);
            return;
        }
        IVariableNode vn = asVariableNode(n);
        if (vn != null) {
            IVariable var = vn.getVariable();
            // An exec "exit": a variable set-node with an EXECUTION_FLOW input port that flow reached.
            // (IVariable#getDataType is a java Type, not a TypeHandle — test the port handle instead.)
            if (var != null && hasExecPort(n.getInputsById().values())) {
                reachedExecOutputs.add(var.getName());
            }
            return;
        }
        if (!(n instanceof ICustomNodeModel cnm)) return;
        if (!(cnm.getNode() instanceof AnnotatedNode an)) return;
        var ctx = new ExecContext(this, n, session, frame);
        an.execute(ctx);
        // Flush any data outputs the exec node staged via setOutput.
        for (PortModel out : n.getOutputsByDisplayOrder()) {
            Object v = ctx.outputs.get(out.getPortId());
            if (v != null) cache.put(out, v);
        }
    }

    /**
     * Enter a subgraph on the exec path: seed the child env from the inner READ data variables, then
     * push a {@link SubgraphFrame} (scoped to a child executor) primed at the inner exec-IN
     * variable's downstream. The frame's {@code resume} harvests WRITE data + fires reached exec-out
     * pins when the inner exec drains. Unresolved/self-ref → fire all exec-out pins into {@code frame}
     * so the outer flow doesn't dead-end.
     */
    private void subgraphEnter(SubgraphNodeModel sub, ExecSession session, ExecFrame frame) {
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)
                || inner == graph.graphModel || inner.getGraph() == null) {
            for (PortModel pin : allExecOutPins(sub)) frame.enqueueFlow(pin);
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
        var childEnv = new EvaluationEnvironment(childStore, env.seed());
        var childExec = new GraphExecutor(inner.getGraph(), childEnv);
        var subFrame = new SubgraphFrame(childExec, this, frame, sub, inner);
        subFrame.enqueueEntry(findExecEntryNode(inner));
        session.push(subFrame);
    }

    /** Per-node persistent state — survives across {@code executeFrom} invocations on this executor instance. */
    public Map<String, Object> nodeState(UUID nodeUid) {
        return nodeState.computeIfAbsent(nodeUid, k -> new HashMap<>());
    }

    /** Reset per-node state — call between independent runs if you want a clean slate. */
    public void clearNodeState() {
        nodeState.clear();
    }

    /**
     * Invalidate a single node: drop its per-node {@link #nodeState} entry and evict all its
     * output ports from the pull {@link #cache}. The next pull of any of its outputs recomputes.
     *
     * <p>Used by {@code CacheClear}: unlike {@link #clearCache} (which wipes the whole pull cache
     * but leaves {@code nodeState} — so a {@code Cache} would keep serving its memo) and
     * {@link #clearNodeState} (which drops every node's state), this targets exactly one node so a
     * {@code Cache} recomputes while unrelated memoised values stay put.</p>
     */
    public void invalidateNode(NodeModel target) {
        if (target == null) return;
        nodeState.remove(target.getUid());
        for (PortModel out : target.getOutputsByDisplayOrder()) {
            cache.remove(out);
        }
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

    /**
     * Harvest a finished subgraph's inner WRITE <em>data</em> variables into this (parent) scope's
     * output cache, so downstream data pulls on the subgraph node see them. Called by
     * {@link SubgraphFrame#resume} once the inner exec has drained.
     */
    void harvestSubgraphOutputs(SubgraphNodeModel sub, CustomGraphModelImpl inner, GraphExecutor childExec) {
        Map<String, Object> innerResults = childExec.runOutputs();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
            PortModel outerOutput = lookupSubgraphPort(sub, v, false, mods);
            if (outerOutput == null) continue;
            cache.put(outerOutput, innerResults.get(v.getName()));
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

    /** Every exec output pin of {@code sub} (leniency target for unresolved/self-ref subgraphs). */
    private List<PortModel> allExecOutPins(SubgraphNodeModel sub) {
        List<PortModel> pins = new ArrayList<>();
        for (PortModel out : sub.getOutputsByDisplayOrder()) {
            if (TypeHandles.EXECUTION_FLOW.equals(out.getDataTypeHandle())) pins.add(out);
        }
        return pins;
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

    // ---- internal --------------------------------------------------------------------------

    /**
     * Public: resolve the value feeding an input port (upstream pull or embedded constant). Used by
     * meta-nodes that must read a port belonging to a <em>different</em> node — e.g. an InfoNode
     * field block reading its parent context's {@code target} input.
     */
    public Object pullInputValue(PortModel inputPort) {
        if (inputPort == null) return null;
        return pullInput(inputPort, Object.class);
    }

    /** Internal: read an input port — either via constant lookup or upstream pull. */
    Object pullInput(PortModel inputPort, Class<?> expected) {
        if (!inputPort.isConnected()) {
            // unconnected -> use the input port's embedded constant (if any).
            // Ports declared via withoutConfigurator() have no SyncValueHolder and
            // tryGetValue may throw — treat that as "no value".
            try {
                var result = inputPort.tryGetValue(expected != null ? expected : Object.class);
                return result.result().orElse(null);
            } catch (RuntimeException e) {
                return null;
            }
        }
        var connected = inputPort.getConnectedPorts();
        if (connected.isEmpty()) return null;
        // First wire is enough for single-source data inputs; evaluate regardless of direction
        // (rare topology edge cases — the executor still memoises by port).
        return evaluateOutput(connected.getFirst());
    }

    private Object evaluateOutput(PortModel outputPort) {
        if (cache.containsKey(outputPort)) return cache.get(outputPort);
        AbstractNodeModel ownerModel = outputPort.getNodeModel() instanceof AbstractNodeModel a ? a : null;
        if (ownerModel == null) return null;
        if (!visiting.add(ownerModel)) {
            throw new CycleException(new ArrayList<>(visiting));
        }
        try {
            evaluateNode(ownerModel);
        } finally {
            visiting.remove(ownerModel);
        }
        return cache.get(outputPort);
    }

    private void evaluateNode(AbstractNodeModel modelNode) {
        // 1) Constant node — read the constant value directly.
        if (modelNode instanceof IConstantNode constant) {
            Object value = constant.tryGetValue(constant.getDataType()).result().orElse(null);
            if (modelNode instanceof NodeModel nm) {
                for (PortModel out : nm.getOutputsByDisplayOrder()) {
                    cache.put(out, value);
                }
            }
            return;
        }

        // 2) Variable node — defer to the environment (which checks store then default).
        if (modelNode instanceof IVariableNode varNode) {
            Object value = env.lookupVariable(varNode.getVariable());
            if (modelNode instanceof NodeModel nm) {
                for (PortModel out : nm.getOutputsByDisplayOrder()) {
                    cache.put(out, value);
                }
            }
            return;
        }

        // 3) Subgraph node — invoke the inner graph with mirrored variables.
        if (modelNode instanceof SubgraphNodeModel sub) {
            evaluateSubgraph(sub);
            return;
        }

        // 4) Any evaluable user node (AnnotatedNode, or a BlockNode-based reader like the InfoNode
        //    field blocks) — invoke evaluate(EvalContext) and flush its staged outputs.
        if (!(modelNode instanceof NodeModel nm)) return;
        if (!(modelNode instanceof ICustomNodeModel customModel)) return;
        Node userNode = customModel.getNode();
        if (userNode instanceof IGraphEvaluable ev) {
            var ctx = new EvalContext(this, nm);
            ev.evaluate(ctx);
            List<PortModel> outs = nm.getOutputsByDisplayOrder();
            for (PortModel out : outs) {
                Object v = ctx.outputs.get(out.getPortId());
                cache.put(out, v);
            }
            return;
        }

        // 5) Unknown node type — best-effort: leave outputs at null.
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
     *       entry into the outer OUTPUT port's cache slot (id = uid, or uid+"-out").</li>
     * </ol>
     */
    private void evaluateSubgraph(SubgraphNodeModel sub) {
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)) {
            // Unresolved — leave every outer output null.
            for (PortModel out : sub.getOutputsByDisplayOrder()) cache.put(out, null);
            return;
        }
        // Guard against trivial self-reference (inner graph is the same model we're already in).
        if (inner == graph.graphModel) {
            for (PortModel out : sub.getOutputsByDisplayOrder()) cache.put(out, null);
            return;
        }

        VariableStore childStore = new VariableStore();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null || isExecVar(v)) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.READ)) continue;
            PortModel outerInput = lookupSubgraphPort(sub, v, true, mods);
            if (outerInput == null) continue;
            Object value = pullInput(outerInput, Object.class);
            childStore.put(v.getName(), value);
        }

        var childEnv = new EvaluationEnvironment(childStore, env.seed());
        Graph innerGraph = inner.getGraph();
        if (innerGraph == null) {
            for (PortModel out : sub.getOutputsByDisplayOrder()) cache.put(out, null);
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
            cache.put(outerOutput, innerResults.get(v.getName()));
        }
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
            // VariableNodeModelImpl directly implements IVariableNode (not via ICustomNodeModel)
            IVariableNode vn = null;
            if (nm instanceof IVariableNode direct) {
                vn = direct;
            } else if (nm instanceof ICustomNodeModel cnm && cnm.getNode() instanceof IVariableNode wrapped) {
                vn = wrapped;
            }
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
}
