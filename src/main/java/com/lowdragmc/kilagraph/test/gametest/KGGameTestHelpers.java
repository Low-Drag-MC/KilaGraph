package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.CustomBlockNodeModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTestHelper;

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

    // ---- assertions ------------------------------------------------------------------

    public static void assertEq(GameTestHelper helper, String label, int expected, int actual) {
        if (expected != actual) helper.fail(label + ": expected " + expected + ", got " + actual);
    }

    public static void assertEq(GameTestHelper helper, String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void assertEq(GameTestHelper helper, String label, float expected, float actual, float epsilon) {
        if (Math.abs(expected - actual) > epsilon) {
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
