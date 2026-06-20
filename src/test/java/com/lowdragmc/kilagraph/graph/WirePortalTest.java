package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.DeclarationModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleInputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleOutputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalEntryModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalExitModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalModel;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wire-portal value reference (data pull) across the entry→exit gap, which has no wire — the
 * executor must bridge it via the shared {@link DeclarationModel}. No Minecraft classes needed.
 */
class WirePortalTest {

    private static WirePortalModel entryPortal(BlueprintGraph g, DeclarationModel decl) {
        return g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl, TypeHandles.FLOAT,
                new Vector2f(), null, null, null, null);
    }

    private static WirePortalModel exitPortal(BlueprintGraph g, DeclarationModel decl) {
        return g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.FLOAT,
                new Vector2f(), null, null, null, null);
    }

    /** Add(2,3)=5 → entry … exit → Add(+10) pulls 15: the value crosses the portal. */
    @Test
    void valueCrossesPortal() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel producer = GraphTestUtils.addNode(g, AddNode.class);
        GraphTestUtils.setInputConstant(producer, "in1", 2.0f);
        GraphTestUtils.setInputConstant(producer, "in2", 3.0f);

        DeclarationModel decl = g.graphModel.createGraphPortalDeclaration("p", null, null);
        WirePortalModel entry = entryPortal(g, decl);
        WirePortalModel exit = exitPortal(g, decl);
        // producer.out → entry.input  (no wire between entry and exit — that's the portal hop)
        g.graphModel.createWire(((ISingleInputPortNodeModel) entry).getInputPort(),
                producer.getOutputsById().get("out"));

        NodeModel consumer = GraphTestUtils.addNode(g, AddNode.class);
        GraphTestUtils.setInputConstant(consumer, "in2", 10.0f);
        // exit.output → consumer.in1
        g.graphModel.createWire(consumer.getInputsById().get("in1"),
                ((ISingleOutputPortNodeModel) exit).getOutputPort());

        Float out = new GraphExecutor(g).evaluate(consumer.getOutputsById().get("out"), Float.class);
        assertEquals(15.0f, out, 1e-5f);
    }

    /** One entry, two exit portals: both resolve to the same entry value. */
    @Test
    void multipleExitsShareEntry() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        NodeModel producer = GraphTestUtils.addNode(g, AddNode.class);
        GraphTestUtils.setInputConstant(producer, "in1", 7.0f);
        GraphTestUtils.setInputConstant(producer, "in2", 0.0f);

        DeclarationModel decl = g.graphModel.createGraphPortalDeclaration("p", null, null);
        WirePortalModel entry = entryPortal(g, decl);
        g.graphModel.createWire(((ISingleInputPortNodeModel) entry).getInputPort(),
                producer.getOutputsById().get("out"));

        WirePortalModel exitA = exitPortal(g, decl);
        WirePortalModel exitB = exitPortal(g, decl);

        var exec = new GraphExecutor(g);
        Float a = exec.evaluate(((ISingleOutputPortNodeModel) exitA).getOutputPort(), Float.class);
        Float b = exec.evaluate(((ISingleOutputPortNodeModel) exitB).getOutputPort(), Float.class);
        assertEquals(7.0f, a, 1e-5f);
        assertEquals(7.0f, b, 1e-5f);
    }

    /** A dangling exit (no entry shares its declaration) resolves to null without throwing. */
    @Test
    void danglingExitIsNull() {
        BlueprintGraph g = GraphTestUtils.newGraph();
        DeclarationModel decl = g.graphModel.createGraphPortalDeclaration("p", null, null);
        WirePortalModel exit = exitPortal(g, decl);   // no matching entry
        Object v = new GraphExecutor(g).evaluate(((ISingleOutputPortNodeModel) exit).getOutputPort(), Object.class);
        assertNull(v);
    }
}
