package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.IGraphEvaluable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IConstantNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.BlockNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleInputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleOutputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A graph resolved once, up front, so that running it does not have to re-derive anything.
 *
 * <p>The old executor answered every question against the editor's live model on each step: which
 * port has this id (hash lookup), is it connected ({@code new ArrayList} + {@code new PortKey}),
 * which port feeds it (another {@code ArrayList} + {@code PortKey}), what kind of node is this
 * (a chain of {@code instanceof}). None of those answers change while a graph is being run, so all
 * of them are computed here instead — once per graph model, shared by every executor over it.</p>
 *
 * <p>What a run gets in exchange:</p>
 * <ul>
 *   <li>every port owns an <b>integer slot</b>, so values live in a flat {@code Object[]} instead of
 *       an {@code IdentityHashMap};</li>
 *   <li>every input knows the slot it pulls from, or the fact that it reads an embedded constant —
 *       LDLib2's wire index is never consulted at run time;</li>
 *   <li>every exec output knows its downstream nodes as a plain array;</li>
 *   <li>every node's kind is a {@code switch} tag rather than an {@code instanceof} chain.</li>
 * </ul>
 *
 * <p><b>Nodes are not asked to do anything.</b> Port ids stay strings and {@code getInput} keeps its
 * signature; the id is resolved against a small per-node snapshot instead of the live model. That is
 * the whole point — every node benefits without its author knowing this class exists.</p>
 *
 * <p>What is deliberately <em>not</em> precomputed: when and how often a node runs. Nodes pull their
 * inputs lazily and that is observable — {@code And}/{@code Or} short-circuit, {@code Select} pulls
 * only the taken branch, {@code Cache} pulls once ever, {@code Random} draws from a shared RNG. So
 * preparation changes addressing only, never evaluation order or count.</p>
 *
 * <h2>Staleness</h2>
 * A prepared graph describes a topology, and editing the graph invalidates it. LDLib2 offers nothing
 * to hang that on: {@code GraphModel} has no version counter, {@code setGraphObjectDirty()} is an
 * empty stub, {@code PortWireIndex.isDirty} is private, and {@code GraphChangeDescription} is
 * accumulate-and-flush — the editor drains it every frame, so a second reader cannot use it. So this
 * class carries its own detection: a structural snapshot ({@link #snapshotStructure()}) of the wire
 * list, every wire's two endpoints, and every known node's port counts, re-compared by reference.
 *
 * <p>It is verified on every top-level entry into the executor, not per node — see
 * {@link GraphExecutor}. That covers rewiring (wire objects are new objects), node add/remove, and
 * the port-set changes an option edit causes via {@code defineNode()}. A same-count <em>rename</em>
 * would slip through, which is why id lookups fall back to the live model rather than failing; the
 * cost of a stale snapshot is a slow path, not a wrong answer. {@link #invalidate} is the explicit
 * hook for anything else.</p>
 *
 * <h2>Threading</h2>
 * The cache map is synchronized, but a {@code PreparedGraph} itself is <b>not</b> thread-safe: it
 * grows when a node is discovered late ({@link #node}). It inherits the confinement its graph model
 * already has — LDLib2's model layer is single-threaded, and {@link GraphExecutor} is documented the
 * same way — so two threads sharing one prepared graph means two threads were already sharing one
 * {@code GraphModel}, which was never supported. Executors on different graphs never interact.
 */
public final class PreparedGraph {

    /** How {@code evaluateNode} used to dispatch — resolved once, in the order it tested. */
    enum DataKind { CONSTANT, VARIABLE, SUBGRAPH, PORTAL_EXIT, EVALUABLE, NONE }

    /** How {@code executeStep} used to dispatch — resolved once, in the order it tested. */
    enum ExecKind { SUBGRAPH, PORTAL_ENTRY, VARIABLE, ANNOTATED, NONE }

    private static final NodeModel[] NO_TARGETS = new NodeModel[0];
    private static final WireModel[] NO_WIRES = new WireModel[0];
    private static final PortModel[] NO_PORTS = new PortModel[0];
    private static final int[] NO_COUNTS = new int[0];

    /**
     * Prepared graphs are keyed by the model they describe and shared by every executor over it —
     * preparation must not be paid per {@code new GraphExecutor(graph)}, since most callers create
     * one executor per evaluation. Identity-keyed because {@code GraphModel} does not override
     * {@code equals}/{@code hashCode}.
     *
     * <p>The value is a {@link WeakReference} rather than the prepared graph itself, and that is
     * load-bearing: a {@code PreparedGraph} refers to its own model (directly, and through every
     * {@code NodeModel}'s back-pointer), so storing it as the value would make each entry
     * self-referential and a {@code WeakHashMap} would never evict — every graph ever executed,
     * with all its nodes and ports, would be retained for the life of the JVM. Indirecting through
     * a weak reference keeps the value→key path weak. A prepared graph therefore lives exactly as
     * long as some executor holds it, which is the sharing this cache is for.</p>
     */
    private static final Map<CustomGraphModelImpl, WeakReference<PreparedGraph>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final CustomGraphModelImpl model;
    private final List<Node> nodes = new ArrayList<>();
    private final IdentityHashMap<AbstractNodeModel, Node> byModel = new IdentityHashMap<>();

    /** Next free value slot. One per port — inputs included, because oddly-wired graphs pull them. */
    private int slotCount;
    /** See {@link #mayCycle()}. */
    private boolean mayCycle = true;

    // Structural snapshot, compared by reference on every top-level entry. Reference equality
    // rather than a hash: it is both cheaper (no identityHashCode per endpoint, which is a real
    // call) and strictly stronger (no collisions). This check is the one per-run cost preparation
    // cannot remove, so its constant factor is worth caring about.
    private WireModel[] wireSnapshot = NO_WIRES;
    private PortModel[] wireFrom = NO_PORTS;
    private PortModel[] wireTo = NO_PORTS;
    private int[] inCounts = NO_COUNTS;
    private int[] outCounts = NO_COUNTS;
    private int nodeListSize;

    private PreparedGraph(CustomGraphModelImpl model) {
        this.model = model;
    }

    // ---- lookup ------------------------------------------------------------------------------

    /**
     * The prepared form of {@code model}, rebuilding it if the graph no longer matches what the
     * cached one was built from.
     */
    @Nullable
    static PreparedGraph of(@Nullable CustomGraphModelImpl model) {
        if (model == null) return null;
        WeakReference<PreparedGraph> ref = CACHE.get(model);
        PreparedGraph cached = ref == null ? null : ref.get();
        if (cached != null && cached.isFresh()) return cached;
        PreparedGraph fresh = new PreparedGraph(model);
        fresh.build();
        CACHE.put(model, new WeakReference<>(fresh));
        return fresh;
    }

    /** Drop the cached preparation for {@code model}. */
    static void invalidate(@Nullable CustomGraphModelImpl model) {
        if (model != null) CACHE.remove(model);
    }

    /**
     * How many times a prepared graph has been built, for diagnostics only - it is what
     * {@code PreparedGraphGameTest.portSetGrowthIsNoticed} asserts on to prove an edit really
     * forced a re-prepare rather than being papered over by a fallback. Not synchronised; a torn
     * read costs a wrong diagnostic, never wrong execution.
     */
    private static long buildCount;

    /** @see #buildCount */
    public static long buildCount() {
        return buildCount;
    }

    /** True while the live graph still matches the topology this was prepared from. */
    boolean isFresh() {
        var wires = model.getWireModels();
        if (wires.size() != wireSnapshot.length) return false;
        if (model.getNodeModels().size() != nodeListSize) return false;
        for (int i = 0; i < wireSnapshot.length; i++) {
            WireModel w = wires.get(i);
            if (w != wireSnapshot[i]) return false;
            if (w != null && (w.getFromPort() != wireFrom[i] || w.getToPort() != wireTo[i])) return false;
        }
        // Port counts catch what wiring cannot: an option edit redefines a node's ports without
        // touching a node or a wire, and LDLib2 re-uses the very same PortModel objects.
        for (int i = 0; i < inCounts.length; i++) {
            Node node = nodes.get(i);
            NodeModel nm = node.asNodeModel;
            if (nm == null) continue;
            if (nm.getInputsById().size() != inCounts[i]) return false;
            if (nm.getOutputsByDisplayOrder().size() != outCounts[i]) return false;
            // A retype is the one edit that swaps an input's Constant without changing any count
            // or wire, so the cached Constant references hinge on this check.
            PortModel[] ports = node.inputPorts;
            TypeHandle[] types = node.inputTypes;
            for (int k = 0; k < ports.length; k++) {
                // equals, not ==: TypeHandle is value-based and not interned. Today a reused port
                // keeps its original handle object so this short-circuits on identity anyway, but
                // if that ever stops holding, == would silently rebuild the prepared graph on every
                // single check — a 100% regression of the thing this class exists for.
                if (!java.util.Objects.equals(ports[k].getDataTypeHandle(), types[k])) return false;
            }
        }
        return true;
    }

    int slotCount() {
        return slotCount;
    }

    /**
     * Whether a pull could possibly revisit a node it is already evaluating.
     *
     * <p>Whether a data graph has a cycle is a property of its wiring, so it is decided here once
     * instead of being re-established on every node of every run. When this is false the executor
     * skips its per-node cycle bookkeeping entirely.</p>
     *
     * <p>A cycle is <em>not</em> reported at prepare time: the old executor only threw
     * {@code CycleException} when a pull actually reached the cycle, and a graph can hold an
     * unreachable one. So a cyclic graph just keeps the runtime check, and throws exactly where it
     * did before.</p>
     */
    boolean mayCycle() {
        return mayCycle;
    }

    int nodeCount() {
        return nodes.size();
    }

    /**
     * The prepared form of {@code m}, preparing it now if it was not reachable when this graph was
     * built — an orphan-spawned node with no wires reaches this, and so does anything the caller
     * hands us directly.
     */
    @Nullable
    Node node(@Nullable AbstractNodeModel m) {
        if (m == null) return null;
        Node n = byModel.get(m);
        return n != null ? n : admit(m);
    }

    // ---- construction ------------------------------------------------------------------------

    private void build() {
        buildCount++;
        // getNodeModels() is not the node set: ORPHAN-spawned nodes are never added to it (which is
        // how nearly every programmatically built graph is made), context-node blocks live only on
        // their parent, and removed nodes leave null holes behind. Discover from all three.
        for (AbstractNodeModel nm : model.getNodeModels()) {
            if (nm == null) continue;
            admitShallow(nm);
            if (nm instanceof ContextNodeModel ctx) {
                for (var block : ctx.getBlocks()) admitShallow(block);
            }
        }
        for (var wire : model.getWireModels()) {
            if (wire == null) continue;
            admitShallow(modelOf(wire.getFromPort()));
            admitShallow(modelOf(wire.getToPort()));
        }
        // Resolve only after every node has an index, so a source can be named by slot.
        for (int i = 0; i < nodes.size(); i++) nodes.get(i).link();
        mayCycle = detectCycle();
        snapshotStructure();
    }

    @Nullable
    private static AbstractNodeModel modelOf(@Nullable PortModel port) {
        return port != null && port.getNodeModel() instanceof AbstractNodeModel a ? a : null;
    }

    /** Index a node and claim its slots, without resolving where its inputs come from. */
    @Nullable
    private Node admitShallow(@Nullable AbstractNodeModel m) {
        if (m == null) return null;
        Node existing = byModel.get(m);
        if (existing != null) return existing;
        Node n = new Node(this, m, nodes.size());
        nodes.add(n);
        byModel.put(m, n);
        return n;
    }

    /**
     * Index and fully resolve a node discovered after the initial build.
     *
     * <p>Admitting changes the node set, which is part of the digest, so the digest has to be
     * re-taken — but <b>only if we were still fresh beforehand</b>. Re-taking it unconditionally
     * would absorb whatever unrelated edit had happened since {@link #build()} and declare this
     * instance permanently fresh while its layout still described the old graph. Since the instance
     * lives in the shared {@link #CACHE}, that would poison every later executor over this graph
     * too. If we were already stale, leave the old digest so the next check rebuilds.</p>
     */
    private Node admit(AbstractNodeModel m) {
        boolean wasFresh = isFresh();
        Node n = admitShallow(m);
        n.link();
        mayCycle = detectCycle();
        if (wasFresh) snapshotStructure();
        return n;
    }

    /**
     * Node count, wire count, every wire's two endpoint identities, and every known node's port
     * counts. Catches rewiring (a new wire is a new object), node add/remove, and the port-set
     * changes that an option edit produces through {@code defineNode()}.
     */
    /**
     * Iterative DFS over the resolved input edges, three-colouring nodes. Iterative rather than
     * recursive because a deep chain is a normal graph shape and this runs on whatever thread built
     * the executor.
     */
    private boolean detectCycle() {
        // Two node kinds recurse along an edge that inputSourceOwners does not model, so the DFS
        // cannot see a cycle routed through them and we must keep checking at run time:
        //
        //   - a wire-portal EXIT resolves its value by pulling the *entry* portal's input, and the
        //     exit node has no inputs of its own (the entry/exit gap has no wire, by design);
        //   - a context-node block (InfoFieldBlock) pulls its *parent* context node's "target"
        //     input, and the block has no data inputs of its own.
        //
        // Getting this wrong is not a slow path but a crash: skipping the runtime check on a graph
        // that really does cycle turns CycleException into unbounded recursion. Both kinds are rare,
        // so bailing out wholesale costs nothing on the graphs that matter.
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.portal != null || node.model instanceof BlockNodeModel) return true;
        }
        int n = nodes.size();
        byte[] colour = new byte[n];           // 0 = unvisited, 1 = on stack, 2 = done
        int[] stack = new int[n + 1];
        int[] cursor = new int[n + 1];
        for (int root = 0; root < n; root++) {
            if (colour[root] != 0) continue;
            int depth = 0;
            stack[0] = root;
            cursor[0] = 0;
            colour[root] = 1;
            while (depth >= 0) {
                Node node = nodes.get(stack[depth]);
                Node[] sources = node.inputSourceOwners;
                int i = cursor[depth]++;
                if (i >= sources.length) {
                    colour[stack[depth]] = 2;
                    depth--;
                    continue;
                }
                Node next = sources[i];
                if (next == null) continue;
                if (colour[next.index] == 1) return true;      // back edge
                if (colour[next.index] == 2) continue;
                colour[next.index] = 1;
                depth++;
                stack[depth] = next.index;
                cursor[depth] = 0;
            }
        }
        return false;
    }

    private void snapshotStructure() {
        var wires = model.getWireModels();
        int w = wires.size();
        wireSnapshot = wires.toArray(new WireModel[w]);
        wireFrom = new PortModel[w];
        wireTo = new PortModel[w];
        for (int i = 0; i < w; i++) {
            WireModel wire = wireSnapshot[i];
            wireFrom[i] = wire == null ? null : wire.getFromPort();
            wireTo[i] = wire == null ? null : wire.getToPort();
        }
        nodeListSize = model.getNodeModels().size();
        int n = nodes.size();
        inCounts = new int[n];
        outCounts = new int[n];
        for (int i = 0; i < n; i++) {
            NodeModel nm = nodes.get(i).asNodeModel;
            if (nm == null) continue;
            inCounts[i] = nm.getInputsById().size();
            outCounts[i] = nm.getOutputsByDisplayOrder().size();
        }
    }

    // ---- one node ----------------------------------------------------------------------------

    /**
     * Everything the executor needs to know about one node, answered once. The dispatch tags mirror
     * the two {@code instanceof} chains they replace ({@code evaluateNode} and {@code executeStep})
     * in the same order those tested, so the correspondence stays checkable by eye.
     */
    static final class Node {
        final AbstractNodeModel model;
        final int index;
        final UUID uid;
        @Nullable final NodeModel asNodeModel;

        final DataKind dataKind;
        final ExecKind execKind;

        @Nullable final IConstantNode constantNode;
        @Nullable final IVariableNode variableNode;
        @Nullable final SubgraphNodeModel subgraphNode;
        @Nullable final WirePortalModel portal;
        @Nullable final IGraphEvaluable evaluable;
        @Nullable final AnnotatedNode annotated;

        // inputs, in getInputsById() iteration order
        final String[] inputIds;
        final PortModel[] inputPorts;
        final int[] inputSlots;
        /**
      * The embedded {@link Constant} an unconnected input reads, resolved once.
      *
      * <p>{@code PortModel.getEmbeddedValue()} is a {@code HashMap} lookup keyed by the port's
      * unique name, and it was measured at ~8ns — on a node like {@code Remap} (one wire, four
      * constants) that was 59% of the whole node step. The <em>value</em> is still read live
      * through this reference, so editing a constant is visible immediately, as before.</p>
      */
        final Constant[] inputConstants;
        /**
      * Each input's declared type at prepare time, so {@link PreparedGraph#isFresh()} can tell when
      * LDLib2 has swapped a {@code Constant} out from under us. It only does that in
      * {@code updateConstantForInput} when a redefine makes the port's type incompatible — which
      * changes neither the port count nor any wire, so nothing else in the snapshot would notice.
      * A reference compare per input is far cheaper than the map lookup it buys back.
      */
        final TypeHandle[] inputTypes;
        /** Slot the input pulls from, or -1 when it reads its embedded constant instead. */
        final int[] inputSourceSlots;
        /** Node owning the source port, so a pull knows what to evaluate. Null when unconnected. */
        final Node[] inputSourceOwners;

        /** Node options, so a read skips the id lookup and the DataResult/Optional wrapper. */
        final String[] optionIds;
        final PortModel[] optionPorts;

        // outputs, in getOutputsByDisplayOrder() order.
        //
        // Two id arrays, because the executor addressed output ports two different ways and they
        // only coincide for top-level ports: the staged-output flush matched on getPortId(), while
        // flow()/enqueueFlow looked the port up in getOutputsById(), which is keyed by uniqueName.
        // Sub-ports have a uniqueName of "portId.parentUniqueName", so collapsing the two would
        // silently mis-address them.
        final String[] outputIds;
        final String[] outputUniqueIds;
        final PortModel[] outputPorts;
        final int[] outputSlots;
        /** Downstream nodes of each output, in wire order — what {@code enqueueFlow} used to derive. */
        final NodeModel[][] flowTargets;

        private final PreparedGraph owner;
        private boolean linked;

        Node(PreparedGraph owner, AbstractNodeModel m, int index) {
            this.owner = owner;
            this.model = m;
            this.index = index;
            this.uid = m.getUid();
            this.asNodeModel = m instanceof NodeModel nm ? nm : null;

            this.constantNode = m instanceof IConstantNode c ? c : null;
            this.subgraphNode = m instanceof SubgraphNodeModel s ? s : null;
            this.portal = m instanceof WirePortalModel p ? p : null;
            Object userNode = m instanceof ICustomNodeModel cnm ? cnm.getNode() : null;
            this.evaluable = userNode instanceof IGraphEvaluable e ? e : null;
            this.annotated = userNode instanceof AnnotatedNode a ? a : null;
            boolean directVariable = m instanceof IVariableNode;
            this.variableNode = directVariable ? (IVariableNode) m
                    : (userNode instanceof IVariableNode w ? w : null);

            // evaluateNode(): constant > variable (direct only) > subgraph > portal exit > evaluable
            if (constantNode != null) {
                this.dataKind = DataKind.CONSTANT;
            } else if (directVariable) {
                this.dataKind = DataKind.VARIABLE;
            } else if (subgraphNode != null) {
                this.dataKind = DataKind.SUBGRAPH;
            } else if (portal != null && m instanceof ISingleOutputPortNodeModel) {
                this.dataKind = DataKind.PORTAL_EXIT;
            } else if (asNodeModel != null && evaluable != null) {
                this.dataKind = DataKind.EVALUABLE;
            } else {
                this.dataKind = DataKind.NONE;
            }

            // executeStep(): subgraph > portal entry > variable (direct or wrapped) > annotated
            if (subgraphNode != null) {
                this.execKind = ExecKind.SUBGRAPH;
            } else if (portal != null && m instanceof ISingleInputPortNodeModel) {
                this.execKind = ExecKind.PORTAL_ENTRY;
            } else if (variableNode != null) {
                this.execKind = ExecKind.VARIABLE;
            } else if (asNodeModel != null && annotated != null) {
                this.execKind = ExecKind.ANNOTATED;
            } else {
                this.execKind = ExecKind.NONE;
            }

            Map<String, PortModel> ins = asNodeModel != null ? asNodeModel.getInputsById() : Map.of();
            int nIn = ins.size();
            this.inputIds = new String[nIn];
            this.inputPorts = new PortModel[nIn];
            this.inputSlots = new int[nIn];
            this.inputConstants = new Constant[nIn];
            this.inputTypes = new TypeHandle[nIn];
            this.inputSourceSlots = new int[nIn];
            this.inputSourceOwners = new Node[nIn];
            int i = 0;
            for (var e : ins.entrySet()) {
                inputIds[i] = e.getKey();
                inputPorts[i] = e.getValue();
                inputSlots[i] = owner.slotCount++;
                inputSourceSlots[i] = -1;
                i++;
            }
    
            List<NodeOption> opts = m instanceof ICustomNodeModel co ? co.getNodeOptions() : List.of();
            this.optionIds = new String[opts.size()];
            this.optionPorts = new PortModel[opts.size()];
            for (int k = 0; k < opts.size(); k++) {
                optionIds[k] = opts.get(k).getId();
                optionPorts[k] = opts.get(k).getPortModel();
            }

            List<PortModel> outs = asNodeModel != null ? asNodeModel.getOutputsByDisplayOrder() : List.of();
            int nOut = outs.size();
            this.outputIds = new String[nOut];
            this.outputUniqueIds = new String[nOut];
            this.outputPorts = new PortModel[nOut];
            this.outputSlots = new int[nOut];
            this.flowTargets = new NodeModel[nOut][];
            for (int k = 0; k < nOut; k++) {
                PortModel p = outs.get(k);
                outputIds[k] = p.getPortId();
                outputUniqueIds[k] = p.getUniqueName();
                outputPorts[k] = p;
                outputSlots[k] = owner.slotCount++;
                flowTargets[k] = NO_TARGETS;
            }
        }

        /** Second pass: name each input's source by slot, and each output's downstream nodes. */
        void link() {
            if (linked) return;
            linked = true;
            for (int i = 0; i < inputPorts.length; i++) {
                inputTypes[i] = inputPorts[i].getDataTypeHandle();
                try {
                    inputConstants[i] = inputPorts[i].getEmbeddedValue();
                } catch (RuntimeException ignored) {
                    // Defensive only: resolving is a map lookup and does not throw today. Reading
                    // the value can, and that is guarded separately in GraphExecutor.readConstant.
                    inputConstants[i] = null;
                }
                // getConnectedPorts() — deliberately the same accessor the executor used, which does
                // NOT hop wire portals (unlike getFirstConnectedPort). Portals are their own node kind.
                List<PortModel> connected = inputPorts[i].getConnectedPorts();
                if (connected.isEmpty()) continue;
                PortModel src = connected.getFirst();
                Node srcOwner = owner.node(modelOf(src));
                if (srcOwner == null) continue;
                inputSourceOwners[i] = srcOwner;
                inputSourceSlots[i] = srcOwner.slotOf(src);
            }
            for (int k = 0; k < outputPorts.length; k++) {
                List<PortModel> connected = outputPorts[k].getConnectedPorts();
                if (connected.isEmpty()) continue;
                List<NodeModel> targets = new ArrayList<>(connected.size());
                for (PortModel p : connected) {
                    if (p.getNodeModel() instanceof NodeModel nm) targets.add(nm);
                }
                flowTargets[k] = targets.toArray(NO_TARGETS);
            }
        }

        /** The slot of {@code port} on this node, or -1. */
        int slotOf(PortModel port) {
            for (int k = 0; k < outputPorts.length; k++) if (outputPorts[k] == port) return outputSlots[k];
            for (int k = 0; k < inputPorts.length; k++) if (inputPorts[k] == port) return inputSlots[k];
            return -1;
        }

        /** Index of the input port object {@code port}, or -1. */
        int inputIndexOf(PortModel port) {
            for (int k = 0; k < inputPorts.length; k++) if (inputPorts[k] == port) return k;
            return -1;
        }

        /** Index of the output port object {@code port}, or -1. */
        int outputIndexOf(PortModel port) {
            for (int k = 0; k < outputPorts.length; k++) if (outputPorts[k] == port) return k;
            return -1;
        }

        /** Index of the input declared as {@code id}, or -1. A short linear scan beats hashing here. */
        int inputIndex(String id) {
            String[] ids = inputIds;
            for (int k = 0; k < ids.length; k++) if (ids[k].equals(id)) return k;
            return -1;
        }

        /** Index of the option declared as {@code id}, or -1. */
        int optionIndex(String id) {
            String[] ids = optionIds;
            for (int k = 0; k < ids.length; k++) if (ids[k].equals(id)) return k;
            return -1;
        }

        /** Index of the output whose {@code uniqueName} is {@code id}, or -1. The {@code flow} key. */
        int flowIndex(String id) {
            String[] ids = outputUniqueIds;
            for (int k = 0; k < ids.length; k++) if (ids[k].equals(id)) return k;
            return -1;
        }
    }
}
