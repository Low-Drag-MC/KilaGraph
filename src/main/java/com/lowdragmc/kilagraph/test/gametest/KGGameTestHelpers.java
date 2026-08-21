package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomBlockNodeModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import java.util.Objects;

/**
 * Programmatic graph-construction helpers for GameTests. Same shape as the legacy
 * {@code GraphTestUtils} in src/test, but lives in main-sources because GameTests register
 * themselves at mod load.
 */
public final class KGGameTestHelpers {
    private KGGameTestHelpers() {}

    /** Fresh BlueprintGraph with KGTypeHandles bootstrapped. */
    public static BlueprintGraph newGraph() {
        KGTypeHandles.init();
        return new BlueprintGraph();
    }

    /** Spawn a node of the given type into the graph and return its underlying NodeModel. */
    public static NodeModel addNode(Graph graph, Class<? extends Node> nodeClass) {
        CustomGraphModelImpl model = graph.graphModel;
        GraphNodeCreationData data = GraphNodeCreationData.ofOrphan(model);
        AbstractNodeModel created = CustomGraphModelImpl.createNodeFromData(data, nodeClass);
        return (NodeModel) created;
    }

    /** Like {@link #addNode}, but a non-orphan (DEFAULT spawn) node — so it appears in
     * {@code graphModel.getNodeModels()} (orphan nodes don't), e.g. for graph-wide validation tests. */
    public static NodeModel addRegisteredNode(Graph graph, Class<? extends Node> nodeClass) {
        CustomGraphModelImpl model = graph.graphModel;
        var data = new GraphNodeCreationData(model, new org.joml.Vector2f(), SpawnFlags.DEFAULT, null);
        AbstractNodeModel created = CustomGraphModelImpl.createNodeFromData(data, nodeClass);
        return (NodeModel) created;
    }

    /** Set a node option's value (the option's port-backed embedded constant). */
    public static void setOption(NodeModel node, String optionId, Object value) {
        NodeOption opt = null;
        for (NodeOption o : node.getNodeOptions()) {
            if (o.id.equals(optionId)) { opt = o; break; }
        }
        if (opt == null) throw new IllegalArgumentException("Unknown option: " + optionId);
        var constant = node.getInputConstantsById().get(opt.portModel.getUniqueName());
        if (constant == null) throw new IllegalStateException("No constant for option " + optionId);
        constant.setValue(value);
        // After changing options that affect ports, force a redefine so dynamic ports update.
        node.defineNode();
    }

    /**
     * Instantiate a {@link BlockNode} and insert it into a context node, returning the block's
     * model. Mirrors {@code BlockCommands.InsertBlockCommand}: set graphModel + non-orphan spawn
     * flags + parent <em>before</em> {@code onCreateNode()} so the block's ports define & register
     * against the graph with the parent context known (so parent-dependent port types are correct).
     */
    public static NodeModel addBlock(Graph graph, NodeModel contextModel,
                                     Class<? extends BlockNode> blockClass) {
        if (!(contextModel instanceof ContextNodeModel cm)) {
            throw new IllegalArgumentException("Not a context node: " + contextModel);
        }
        BlockNode userNode;
        try {
            userNode = blockClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate block " + blockClass.getName(), e);
        }
        var block = new CustomBlockNodeModelImpl();
        block.setGraphModel(graph.graphModel);
        block.setSpawnFlags(SpawnFlags.DEFAULT);
        block.initCustomNode(userNode);
        block.setContextNodeModel(cm);
        block.onCreateNode();          // defineNode with parent known → correct port types + register
        cm.insertBlock(block, -1);     // attach (parent already == cm, so no-op re-link)
        return block;
    }

    /** Set an input port's embedded constant value (for unconnected inputs). */
    public static void setInputConstant(NodeModel node, String portId, Object value) {
        var constant = node.getInputConstantsById().get(portId);
        if (constant == null) throw new IllegalStateException("No input constant for " + portId);
        constant.setValue(value);
    }

    /** Wire an output port -> input port. */
    public static void wire(Graph graph, PortModel dst, PortModel src) {
        graph.graphModel.createWire(dst, src);
    }

    // ---- subgraphs, i.e. blueprint "functions" -----------------------------------------
    //
    // KilaGraph has no separate function-graph type — ShaderFunctionGraph serves the shader side
    // only. A blueprint function IS a subgraph: READ graph variables are its parameters, WRITE
    // variables its return values, and EXECUTION_FLOW variables its entry/exit pins. The port ids
    // the call site exposes are the inner variable's UUID (see GraphExecutor.lookupSubgraphPort),
    // which is why pin() exists rather than callers writing getUid().toString() everywhere.

    /** Spawn a node into a subgraph model. The {@link Graph} overload only reaches the root graph. */
    public static NodeModel addNode(CustomGraphModelImpl model, Class<? extends Node> nodeClass) {
        GraphNodeCreationData data = GraphNodeCreationData.ofOrphan(model);
        return (NodeModel) CustomGraphModelImpl.createNodeFromData(data, nodeClass);
    }

    /** A fresh local subgraph registered on {@code parent} — the body of one function. */
    public static CustomGraphModelImpl subgraphOf(CustomGraphModelImpl parent) {
        CustomGraphModelImpl inner = parent.createLocalSubgraphInstance();
        if (inner == null) throw new IllegalStateException("createLocalSubgraphInstance() returned null");
        parent.addLocalSubgraph(inner);
        return inner;
    }

    /** An {@code EXECUTION_FLOW} variable: {@code INPUT} is an entry pin, {@code OUTPUT} an exit pin. */
    public static VariableDeclarationModelBase execVar(CustomGraphModelImpl m, String name, VariableKind kind) {
        return (VariableDeclarationModelBase) m.createVariable(name, TypeHandles.EXECUTION_FLOW, null, kind);
    }

    /** A data variable: {@code INPUT} is a parameter, {@code OUTPUT} a return value. */
    public static VariableDeclarationModelBase dataVar(CustomGraphModelImpl m, String name, Class<?> type,
                                                       Object defaultValue, VariableKind kind) {
        return (VariableDeclarationModelBase) m.createVariable(name, type, defaultValue, kind);
    }

    /** The get/set node for a variable declaration, at the origin. */
    public static VariableNodeModel varNode(CustomGraphModelImpl m, VariableDeclarationModelBase v) {
        return varNode(m, v, 0, 0);
    }

    /** The get/set node for a variable declaration. Position only affects the editor, never execution. */
    public static VariableNodeModel varNode(CustomGraphModelImpl m, VariableDeclarationModelBase v,
                                            float x, float y) {
        return m.createVariableNode(v, new Vector2f(x, y), null, null);
    }

    /** The call-site port id mirroring {@code v}. Single-direction variables only. */
    public static String pin(VariableDeclarationModelBase v) {
        return v.getUid().toString();
    }

    /** The call-site <em>input</em> port id for a {@code READ_WRITE} variable. */
    public static String pinIn(VariableDeclarationModelBase v) {
        return v.getUid() + "-in";
    }

    /** The call-site <em>output</em> port id for a {@code READ_WRITE} variable. */
    public static String pinOut(VariableDeclarationModelBase v) {
        return v.getUid() + "-out";
    }

    /**
     * A call site for {@code inner} inside {@code parent}.
     *
     * <p>{@code defineNode()} is called here on purpose: the subgraph node only grows its mirror
     * ports once it knows its target, so every caller needs it and forgetting it produces a node
     * with no pins and a test that fails somewhere far away.</p>
     */
    public static SubgraphNodeModel callNode(CustomGraphModelImpl parent, CustomGraphModelImpl inner,
                                             String name) {
        SubgraphNodeModel sub = parent.createNodeWithType(SubgraphNodeModel.class, name,
                new Vector2f(), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        sub.defineNode();
        return sub;
    }

    // ---- assertions ------------------------------------------------------------------

    public static void assertEq(GameTestHelper helper, String label, int expected, int actual) {
        if (expected != actual) helper.fail(label + ": expected " + expected + ", got " + actual);
    }

    public static void assertEq(GameTestHelper helper, String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Float comparison with a tolerance.
     *
     * <p>The NaN handling is load-bearing, not pedantry: {@code Math.abs(expected - actual)} is NaN
     * whenever either side is, and {@code NaN > epsilon} is {@code false} — so the obvious
     * one-line version silently <em>passes</em> for any actual value that is NaN, which is what a
     * helper like {@code orNaN(executor.evaluate(...))} produces from a null result. Several
     * assertions were vacuous because of it.</p>
     */
    public static void assertEq(GameTestHelper helper, String label, float expected, float actual, float epsilon) {
        boolean ok = Float.isNaN(expected)
                ? Float.isNaN(actual)
                : !Float.isNaN(actual) && Math.abs(expected - actual) <= epsilon;
        if (!ok) {
            helper.fail(label + ": expected " + expected + " (±" + epsilon + "), got " + actual);
        }
    }

    public static void assertTrue(GameTestHelper helper, String label, boolean cond) {
        if (!cond) helper.fail(label + ": expected true");
    }

    public static void assertFalse(GameTestHelper helper, String label, boolean cond) {
        if (cond) helper.fail(label + ": expected false");
    }
}
