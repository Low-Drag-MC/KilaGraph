package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;

/**
 * Programmatic graph-construction helpers — kept here so test files stay focused on what they
 * actually exercise.
 */
final class GraphTestUtils {
    private GraphTestUtils() {}

    static BlueprintGraph newGraph() {
        // ensure the LIST handle override is in place
        KGTypeHandles.init();
        return new BlueprintGraph();
    }

    /** Spawn a node of the given type into the graph and return its underlying NodeModel. */
    static NodeModel addNode(BlueprintGraph graph, Class<? extends Node> nodeClass) {
        CustomGraphModelImpl model = graph.graphModel;
        var data = GraphNodeCreationData.ofOrphan(model);
        AbstractNodeModel created = CustomGraphModelImpl.createNodeFromData(data, nodeClass);
        return (NodeModel) created;
    }

    /** Set a node's option value (the option's port-backed embedded constant). */
    static void setOption(NodeModel node, String optionId, Object value) {
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

    /** Set an input port's embedded constant value (for unconnected inputs). */
    static void setInputConstant(NodeModel node, String portId, Object value) {
        var constant = node.getInputConstantsById().get(portId);
        if (constant == null) throw new IllegalStateException("No input constant for " + portId);
        constant.setValue(value);
    }

    static String userNodeName(NodeModel m) {
        if (m instanceof ICustomNodeModel cnm) return cnm.getNode().getClass().getSimpleName();
        return m.getClass().getSimpleName();
    }
}
