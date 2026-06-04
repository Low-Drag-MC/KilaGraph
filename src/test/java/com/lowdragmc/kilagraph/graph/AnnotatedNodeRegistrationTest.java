package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSizeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the annotation-driven field declarations on {@link com.lowdragmc.kilagraph.graph.core.AnnotatedNode}
 * subclasses produce the expected LDLib2 ports and options.
 */
class AnnotatedNodeRegistrationTest {

    @Test
    void blueprintRegistryDiscoversAnnotatedNodes() {
        var classes = BlueprintGraph.NODE_REGISTRY.getNodeClasses();
        assertTrue(classes.contains(AddNode.class), "AddNode should auto-register");
        assertTrue(classes.contains(ListSizeNode.class), "ListSizeNode should auto-register");
        assertTrue(classes.contains(ListCombineNode.class), "ListCombineNode should auto-register");
    }

    @Test
    void addNodeHasExpectedPorts() {
        var graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        var inputs = add.getInputsById();
        assertNotNull(inputs.get("in1"));
        assertNotNull(inputs.get("in2"));
        assertEquals(TypeHandles.FLOAT, inputs.get("in1").getDataTypeHandle());
        assertEquals(TypeHandles.FLOAT, add.getOutputsById().get("out").getDataTypeHandle());
    }

    @Test
    void listSizeUsesCustomListTypeHandle() {
        var graph = GraphTestUtils.newGraph();
        NodeModel n = GraphTestUtils.addNode(graph, ListSizeNode.class);
        var list = n.getInputsById().get("list");
        assertNotNull(list);
        assertEquals("LIST", list.getDataTypeHandle().getIdentification(),
                "List-typed field should resolve to the KilaGraph LIST handle");
    }

    @Test
    void listCombineDefaultPortShapeMatchesInputsOption() {
        var graph = GraphTestUtils.newGraph();
        NodeModel n = GraphTestUtils.addNode(graph, ListCombineNode.class);
        // Default inputs=2 → in1, in2
        assertNotNull(n.getInputsById().get("in1"));
        assertNotNull(n.getInputsById().get("in2"));
        assertNotNull(n.getOutputsById().get("out"));
        assertEquals("LIST", n.getOutputsById().get("out").getDataTypeHandle().getIdentification());
    }

    @Test
    void listCombineRedefinesPortsAfterInputsChange() {
        var graph = GraphTestUtils.newGraph();
        NodeModel n = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(n, "inputs", 4);
        assertNotNull(n.getInputsById().get("in3"));
        assertNotNull(n.getInputsById().get("in4"));
    }
}
