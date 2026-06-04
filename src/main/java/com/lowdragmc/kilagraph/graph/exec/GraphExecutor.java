package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IConstantNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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
    /** Exec-flow pending queue. Push: enqueueFlow; pop: drainExecQueue. Lifetime = current executeFrom invocation. */
    private final Deque<NodeModel> pending = new ArrayDeque<>();
    /** Per-node persistent state, keyed by node UID. Lazy-created via {@link #nodeState(UUID)}. */
    private final Map<UUID, Map<String, Object>> nodeState = new HashMap<>();

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
            if (v.getVariableKind() != VariableKind.OUTPUT) continue;
            result.put(v.getName(), resolveOutputVariable(v, gm));
        }
        return result;
    }

    /**
     * Exec-flow kick-off. Pushes {@code entry} onto the pending queue and drains it. Each popped
     * node's {@code execute(ExecContext)} runs; the node may call {@code ctx.flow(outputId)} to
     * push its downstream targets. Returns when the queue is empty.
     *
     * <p>Throws {@link BreakException} / {@link ContinueException} if such a sentinel escapes
     * outside any enclosing loop — usually indicates a {@code Break}/{@code Continue} node was
     * placed outside any loop. Callers can catch these explicitly if they want lenient behaviour.</p>
     */
    public void executeFrom(NodeModel entry) {
        if (entry == null) throw new IllegalArgumentException("entry is null");
        pending.addLast(entry);
        drainExecQueue();
    }

    /**
     * Drain the pending exec queue until empty. Public surface: {@link #executeFrom}. Internal
     * surface (loop nodes via {@link ExecContext#runLoopBody}): drain the body's sub-tree before
     * deciding to re-iterate.
     */
    void drainExecQueue() {
        while (!pending.isEmpty()) {
            NodeModel n = pending.pollFirst();
            executeNode(n);
        }
    }

    /**
     * Run {@code body} with a fresh empty exec queue, draining whatever it enqueues to completion,
     * then restore the outer queue. Used by loop nodes to isolate one body iteration from sibling
     * nodes that are already pending in the outer queue.
     */
    void runIsolated(Runnable body) {
        var saved = new ArrayDeque<>(pending);
        pending.clear();
        try {
            body.run();
            drainExecQueue();
        } finally {
            // pending might be non-empty here if body re-pushed something that exited via
            // Break/Continue mid-drain. Items the body pushed *after* the exception should be
            // dropped — they belong to the abandoned iteration. The outer queue must come back
            // intact.
            pending.clear();
            pending.addAll(saved);
        }
    }

    /** Enqueue the node connected to {@code outputPort} onto the pending exec queue (no-op if unwired). */
    void enqueueFlow(PortModel outputPort) {
        if (outputPort == null) return;
        for (PortModel connected : outputPort.getConnectedPorts()) {
            if (connected.getNodeModel() instanceof NodeModel nm) {
                pending.addLast(nm);
            }
        }
    }

    /** Per-node persistent state — survives across {@code executeFrom} invocations on this executor instance. */
    public Map<String, Object> nodeState(UUID nodeUid) {
        return nodeState.computeIfAbsent(nodeUid, k -> new HashMap<>());
    }

    /**
     * Stash a value into the port-output cache from outside an evaluate() call. Used by exec nodes
     * (e.g. {@code For}) that need to expose a data value mid-execute() so the body can pull it
     * before the normal post-execute flush kicks in.
     */
    public void putCache(PortModel outputPort, Object value) {
        cache.put(outputPort, value);
    }

    /** Reset per-node state — call between independent runs if you want a clean slate. */
    public void clearNodeState() {
        nodeState.clear();
    }

    private void executeNode(NodeModel n) {
        if (!(n instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel cnm)) return;
        if (!(cnm.getNode() instanceof AnnotatedNode an)) return;
        var ctx = new ExecContext(this, n);
        an.execute(ctx);
        // Flush any data outputs the exec node staged via setOutput.
        for (PortModel out : n.getOutputsByDisplayOrder()) {
            Object v = ctx.outputs.get(out.getPortId());
            if (v != null) cache.put(out, v);
        }
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

        // 4) AnnotatedNode — invoke evaluate(EvalContext).
        if (!(modelNode instanceof NodeModel nm)) return;
        if (!(modelNode instanceof ICustomNodeModel customModel)) return;
        Node userNode = customModel.getNode();
        if (userNode instanceof AnnotatedNode an) {
            var ctx = new EvalContext(this, nm);
            an.evaluate(ctx);
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
            if (v == null) continue;
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
            if (v == null) continue;
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
