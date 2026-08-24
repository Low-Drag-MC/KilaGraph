package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Names the nodes in a test graph so wiring can be written as {@code wire("sqrt.in", "sumSq")}
 * instead of {@code createWire(a.getInputsById().get("in"), b.getOutputsById().get("out"))}.
 *
 * <p>The existing tests build graphs by holding a local variable per node and threading port
 * lookups through every call. That is fine for the five-node graphs they contain, and it is why
 * there are no larger ones: the 21-node {@code locomotion} graph in {@code ExecutorBenchGameTest}
 * takes 80 lines, so a hundred-node graph would take four hundred and nobody would be able to see
 * what it computes. The purpose of this class is to make the graph in a test <em>readable</em>,
 * because a golden test nobody can read is a golden test nobody will maintain.</p>
 *
 * <h2>References</h2>
 * Every node gets a name when it is created, and ports are addressed as {@code "name.portId"}.
 * A bare {@code "name"} means the node's default port, resolved by <em>position</em>:
 * <ul>
 *   <li>as a wire <b>source</b> — its single non-exec output (or a variable node's output port);</li>
 *   <li>as a wire <b>destination</b> — its single non-exec input (or a variable node's input port).</li>
 * </ul>
 * "Single" is enforced: a node with two candidates rejects the bare form rather than guessing, so a
 * mis-wired graph fails at build time with the node's name in the message rather than producing a
 * test that quietly measures the wrong thing.
 *
 * <h2>Not a production API</h2>
 * This lives in {@code test/gametest} beside {@link KGGameTestHelpers} and exists only to build
 * graphs for tests and benchmarks. It deliberately throws {@link IllegalArgumentException} on every
 * ambiguity instead of returning null.
 */
public final class KGGraphBuilder {

    private final CustomGraphModelImpl model;
    @Nullable private final BlueprintGraph rootGraph;
    private final Map<String, NodeModel> nodes = new LinkedHashMap<>();
    private final Map<String, VariableDeclarationModelBase> vars = new LinkedHashMap<>();
    /** Call-site name → the builder of the subgraph it calls; see {@link #call}. */
    private final Map<String, KGGraphBuilder> callees = new LinkedHashMap<>();

    private KGGraphBuilder(CustomGraphModelImpl model, @Nullable BlueprintGraph rootGraph) {
        this.model = model;
        this.rootGraph = rootGraph;
    }

    /** A builder over a fresh {@link BlueprintGraph}. */
    public static KGGraphBuilder blueprint() {
        BlueprintGraph g = KGGameTestHelpers.newGraph();
        return new KGGraphBuilder(g.graphModel, g);
    }

    /** A builder over an existing graph model — used for subgraph bodies. */
    public static KGGraphBuilder on(CustomGraphModelImpl model) {
        return new KGGraphBuilder(model, null);
    }

    /** A builder over a new local subgraph of this graph, i.e. the body of one function. */
    public KGGraphBuilder subgraph() {
        return on(KGGameTestHelpers.subgraphOf(model));
    }

    // ---- accessors ---------------------------------------------------------------------------

    /** The root graph. Only present for a builder created by {@link #blueprint()}. */
    public BlueprintGraph graph() {
        if (rootGraph == null) throw new IllegalStateException("not a root-graph builder");
        return rootGraph;
    }

    public CustomGraphModelImpl model() {
        return model;
    }

    /** The node registered under {@code name}. */
    public NodeModel node(String name) {
        NodeModel n = nodes.get(name);
        if (n == null) {
            throw new IllegalArgumentException("No node named '" + name + "'; have " + nodes.keySet());
        }
        return n;
    }

    /** The variable declaration registered under {@code name}. */
    public VariableDeclarationModelBase var(String name) {
        VariableDeclarationModelBase v = vars.get(name);
        if (v == null) {
            throw new IllegalArgumentException("No variable named '" + name + "'; have " + vars.keySet());
        }
        return v;
    }

    /** Every node this builder created, in creation order. */
    public List<NodeModel> allNodes() {
        return new ArrayList<>(nodes.values());
    }

    /**
     * Resolve a reference to an output port — what {@code GraphExecutor.evaluate} wants. Accepts the
     * same {@code "node.port"} / {@code "node"} forms as {@link #wire}, including a call site's
     * {@code "call.innerVarName"}.
     */
    public PortModel outputOf(String ref) {
        Ref r = parse(ref);
        return r.portId == null ? defaultOutput(r) : outputPort(r);
    }

    /** Resolve a reference to an input port. The mirror of {@link #outputOf}. */
    public PortModel inputOf(String ref) {
        Ref r = parse(ref);
        return r.portId == null ? defaultInput(r) : inputPort(r);
    }

    // ---- node creation -----------------------------------------------------------------------

    /** Create a node and register it under {@code name}. */
    public KGGraphBuilder add(String name, Class<? extends Node> nodeClass) {
        if (nodes.containsKey(name)) throw new IllegalArgumentException("Duplicate node name '" + name + "'");
        nodes.put(name, KGGameTestHelpers.addNode(model, nodeClass));
        return this;
    }

    /**
     * Create {@code count} nodes named {@code prefix + i} for {@code i} in {@code [0, count)}.
     * For chains and fan-ins, where naming each one by hand is the bulk of the test.
     */
    public KGGraphBuilder addMany(String prefix, Class<? extends Node> nodeClass, int count) {
        for (int i = 0; i < count; i++) add(prefix + i, nodeClass);
        return this;
    }

    /**
     * Create a call site for {@code inner} — a blueprint function call — and register it under
     * {@code name}.
     *
     * <p>The call node's port ids are the inner variables' UUIDs, which would make every wire in a
     * test read {@code "call." + inner.var("x").getUid()}. Remembering the callee here lets
     * {@code "call.x"} resolve through the inner variable's <em>name</em> instead, so a function
     * call reads the way a function call should.</p>
     */
    public KGGraphBuilder call(String name, KGGraphBuilder inner) {
        if (nodes.containsKey(name)) throw new IllegalArgumentException("Duplicate node name '" + name + "'");
        nodes.put(name, KGGameTestHelpers.callNode(model, inner.model, name));
        callees.put(name, inner);
        return this;
    }

    /** Set a node option, redefining the node so option-driven ports update. */
    public KGGraphBuilder option(String nodeName, String optionId, Object value) {
        KGGameTestHelpers.setOption(node(nodeName), optionId, value);
        return this;
    }

    /** Set an unconnected input's embedded constant. {@code ref} is {@code "node.port"} or {@code "node"}. */
    public KGGraphBuilder constant(String ref, Object value) {
        Ref r = parse(ref);
        PortModel port = r.portId == null ? defaultInput(r) : inputPort(r);
        KGGameTestHelpers.setInputConstant(r.node, port.getUniqueName(), value);
        return this;
    }

    // ---- variables ---------------------------------------------------------------------------

    /**
     * Declare a data variable and create its node, registering both under {@code name}.
     * The node reads through {@code name} as a wire source and writes through it as a destination.
     */
    public KGGraphBuilder variable(String name, Class<?> type, Object defaultValue, VariableKind kind) {
        if (vars.containsKey(name)) throw new IllegalArgumentException("Duplicate variable '" + name + "'");
        VariableDeclarationModelBase v = KGGameTestHelpers.dataVar(model, name, type, defaultValue, kind);
        vars.put(name, v);
        nodes.put(name, KGGameTestHelpers.varNode(model, v));
        return this;
    }

    /** Declare an {@code EXECUTION_FLOW} variable and create its node — a subgraph entry/exit pin. */
    public KGGraphBuilder execVariable(String name, VariableKind kind) {
        if (vars.containsKey(name)) throw new IllegalArgumentException("Duplicate variable '" + name + "'");
        VariableDeclarationModelBase v = KGGameTestHelpers.execVar(model, name, kind);
        vars.put(name, v);
        nodes.put(name, KGGameTestHelpers.varNode(model, v));
        return this;
    }

    /**
     * A second node reading an already-declared variable, registered under {@code nodeName}.
     *
     * <p>Two nodes for one variable are two nodes, so they get their own slots and their own memos.
     * That matters after a loop: the body's read is memoised for the iteration that wrote it, and a
     * read placed on the {@code completed} path needs to be a different node to see the final value
     * — see {@code ExecVarInteractionGameTest.aVariableReadIsMemoisedUntilClearCache}.</p>
     */
    public KGGraphBuilder readAgain(String nodeName, String varName) {
        if (nodes.containsKey(nodeName)) throw new IllegalArgumentException("Duplicate node name '" + nodeName + "'");
        nodes.put(nodeName, KGGameTestHelpers.varNode(model, var(varName)));
        return this;
    }

    /**
     * Declare a variable without creating a node for it — for a variable that only ever crosses a
     * subgraph boundary, where the call site's mirror pin is the only thing that reads it.
     */
    public KGGraphBuilder declare(String name, Class<?> type, Object defaultValue, VariableKind kind) {
        if (vars.containsKey(name)) throw new IllegalArgumentException("Duplicate variable '" + name + "'");
        vars.put(name, KGGameTestHelpers.dataVar(model, name, type, defaultValue, kind));
        return this;
    }

    // ---- wiring ------------------------------------------------------------------------------

    /** Wire {@code src}'s output into {@code dst}'s input. Both are {@code "node.port"} or {@code "node"}. */
    public KGGraphBuilder wire(String dst, String src) {
        Ref d = parse(dst);
        Ref s = parse(src);
        PortModel dstPort = d.portId == null ? defaultInput(d) : inputPort(d);
        PortModel srcPort = s.portId == null ? defaultOutput(s) : outputPort(s);
        model.createWire(dstPort, srcPort);
        return this;
    }

    /**
     * Wire an exec chain: each name flows into the next. Every hop uses the source's single exec
     * output and the destination's single exec input, so a {@code Branch} or a loop must be wired
     * with the explicit {@link #wire} form naming the pin.
     */
    public KGGraphBuilder then(String... chain) {
        for (int i = 0; i + 1 < chain.length; i++) {
            Ref from = parse(chain[i]);
            Ref to = parse(chain[i + 1]);
            PortModel out = from.portId == null ? soleExec(from, true) : outputPort(from);
            PortModel in = to.portId == null ? soleExec(to, false) : inputPort(to);
            model.createWire(in, out);
        }
        return this;
    }

    // ---- reference resolution ----------------------------------------------------------------

    private record Ref(String name, NodeModel node, @Nullable String portId) {}

    private Ref parse(String ref) {
        int dot = ref.indexOf('.');
        if (dot < 0) return new Ref(ref, node(ref), null);
        String name = ref.substring(0, dot);
        return new Ref(name, node(name), ref.substring(dot + 1));
    }

    private PortModel inputPort(Ref r) {
        Map<String, PortModel> ins = r.node.getInputsById();
        PortModel p = ins.get(r.portId);
        if (p == null) p = calleePin(r, ins, true);
        if (p == null) {
            throw new IllegalArgumentException("No input '" + r.portId + "' on '" + r.name
                    + "'; have " + ins.keySet() + calleeHint(r));
        }
        return p;
    }

    private PortModel outputPort(Ref r) {
        Map<String, PortModel> outs = r.node.getOutputsById();
        PortModel p = outs.get(r.portId);
        if (p == null) p = calleePin(r, outs, false);
        if (p == null) {
            throw new IllegalArgumentException("No output '" + r.portId + "' on '" + r.name
                    + "'; have " + outs.keySet() + calleeHint(r));
        }
        return p;
    }

    /**
     * Resolve {@code r.portId} as the <em>name</em> of a variable in the called subgraph, mapping it
     * to the uid-derived mirror pin. {@code READ_WRITE} variables carry a direction suffix, so all
     * three spellings are tried.
     */
    @Nullable
    private PortModel calleePin(Ref r, Map<String, PortModel> ports, boolean wantInput) {
        KGGraphBuilder callee = callees.get(r.name);
        if (callee == null) return null;
        VariableDeclarationModelBase v = callee.vars.get(r.portId);
        if (v == null) return null;
        PortModel p = ports.get(KGGameTestHelpers.pin(v));
        if (p != null) return p;
        return ports.get(wantInput ? KGGameTestHelpers.pinIn(v) : KGGameTestHelpers.pinOut(v));
    }

    private String calleeHint(Ref r) {
        KGGraphBuilder callee = callees.get(r.name);
        return callee == null ? "" : " (callee variables: " + callee.vars.keySet() + ")";
    }

    /** A variable node's write side, or the node's single input — data preferred over exec. */
    private PortModel defaultInput(Ref r) {
        if (r.node instanceof VariableNodeModel v) return v.getInputPort();
        return soleDataElseExec(r, r.node.getInputsById().values(), "input");
    }

    /** A variable node's read side, or the node's single output — data preferred over exec. */
    private PortModel defaultOutput(Ref r) {
        if (r.node instanceof VariableNodeModel v) return v.getOutputPort();
        return soleDataElseExec(r, r.node.getOutputsById().values(), "output");
    }

    /**
     * The node's one data port, or — when it has none at all — its one exec port.
     *
     * <p>The fallback is what lets {@code wire("f.call", "entry")} name an {@code EntryNode}, which
     * has nothing but an exec output. Data is still preferred, so a hybrid node like {@code SetVar}
     * (exec in/out plus a {@code value} input) resolves to the data side, which is the side a bare
     * reference means.</p>
     */
    private PortModel soleDataElseExec(Ref r, Iterable<PortModel> ports, String what) {
        PortModel data = find(ports, false);
        if (data != null) return data;
        PortModel exec = find(ports, true);
        if (exec != null) return exec;
        return sole(r, ports, false, what);   // no unique port either way: report it
    }

    /** The single port matching {@code wantExec}, or null if there is not exactly one. */
    @Nullable
    private static PortModel find(Iterable<PortModel> ports, boolean wantExec) {
        PortModel found = null;
        for (PortModel p : ports) {
            if (TypeHandles.EXECUTION_FLOW.equals(p.getDataTypeHandle()) != wantExec) continue;
            if (found != null) return null;
            found = p;
        }
        return found;
    }

    private PortModel soleExec(Ref r, boolean output) {
        if (r.node instanceof VariableNodeModel v) return output ? v.getOutputPort() : v.getInputPort();
        var ports = output ? r.node.getOutputsById().values() : r.node.getInputsById().values();
        return sole(r, ports, true, output ? "exec output" : "exec input");
    }

    /**
     * The one port of {@code ports} matching {@code wantExec}. Ambiguity is an error rather than a
     * guess: picking the first would silently wire a {@code Branch} to its true side and turn a
     * broken graph into a passing test.
     */
    private PortModel sole(Ref r, Iterable<PortModel> ports, boolean wantExec, String what) {
        PortModel found = null;
        List<String> candidates = new ArrayList<>();
        for (PortModel p : ports) {
            boolean isExec = TypeHandles.EXECUTION_FLOW.equals(p.getDataTypeHandle());
            if (isExec != wantExec) continue;
            candidates.add(p.getPortId());
            found = p;
        }
        if (candidates.size() == 1) return found;
        throw new IllegalArgumentException("'" + r.name + "' has " + candidates.size() + " candidate "
                + what + " ports " + candidates + " — name one explicitly, e.g. \"" + r.name + "."
                + (candidates.isEmpty() ? "<portId>" : candidates.getFirst()) + "\"");
    }
}
