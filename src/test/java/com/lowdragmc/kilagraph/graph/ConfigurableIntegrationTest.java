package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSizeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the field-native framework wires reflection metadata into LDLib2 builders
 * correctly, and that v3's relaxed wire-compatibility rules let Number↔Number and *→String
 * connections through.
 */
class ConfigurableIntegrationTest {

    @Test
    void inputPortCarriesFieldContext() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        // After v3, AddNode uses dynamic in1/in2 ports declared from onDefineDynamicPorts (not
        // annotated fields). The annotated field is the 'inputs' option — assert that path.
        var option = add.getNodeOptions().stream()
                .filter(o -> o.id.equals("inputs")).findFirst().orElseThrow();
        PortModel optPort = option.portModel;
        assertNotNull(optPort.getValueField(),
                "annotated option field must carry the field handle via withFieldContext");
        assertEquals("inputs", optPort.getValueField().getName());
        assertSame(((com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel) add).getNode(),
                optPort.getValueOwer(),
                "owner should be the user-defined Node instance");
    }

    @Test
    void outputPortHasNoFieldContext() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        PortModel out = add.getOutputsById().get("out");
        assertNotNull(out);
        assertNull(out.getValueField(),
                "Output ports must not carry a field context (got " + out.getValueField() + ")");
    }

    @Test
    void listFieldDisablesConfigurator() {
        // Repro of the v3 crash: List-typed input field used to drive LDLib2's default
        // configurator which casts the raw List to ParameterizedType and crashes. v3 fix:
        // NodeMetadata calls withoutConfigurator() when no AccessorRegistries accessor exists.
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel size = GraphTestUtils.addNode(graph, ListSizeNode.class);
        PortModel list = size.getInputsById().get("list");
        assertNotNull(list);
        assertEquals(false, list.isConfiguratorEnabled(),
                "List-typed input port must have its configurator disabled to avoid UI crash");
    }

    @Test
    void kgGraphModelAllowsUnknownToAny() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        NodeModel cast = GraphTestUtils.addNode(graph,
                com.lowdragmc.kilagraph.blueprint.nodes.convert.CastNode.class);
        assertTrue(graph.graphModel.isCompatiblePort(
                add.getOutputsById().get("out"),
                cast.getInputsById().get("in")));
        assertTrue(graph.graphModel.isCompatiblePort(
                cast.getInputsById().get("in"),
                add.getOutputsById().get("out")));
        assertEquals(TypeHandles.UNKNOWN, cast.getInputsById().get("in").getDataTypeHandle());
    }

    @Test
    void kgGraphModelAllowsNumberToNumber() {
        // AddNode.out is FLOAT; ListSizeNode.size is INT. With v3 KGGraphModel we accept the wire.
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        NodeModel size = GraphTestUtils.addNode(graph, ListSizeNode.class);
        // Float → Integer
        assertTrue(graph.graphModel.isCompatiblePort(
                add.getInputsById().get("in1"),       // FLOAT
                size.getOutputsById().get("size")));  // INT
        // Integer → Float
        assertTrue(graph.graphModel.isCompatiblePort(
                size.getOutputsById().get("size"),
                add.getInputsById().get("in1")));
    }

    @Test
    void kgGraphModelAllowsAnythingToString() {
        // We need a String-typed input port to assert the rule.
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        // Set elementType to STRING so in1 is STRING-typed.
        GraphTestUtils.setOption(combine, "type", TypeHandles.STRING.getIdentification());
        PortModel stringIn = combine.getInputsById().get("in1");
        assertNotNull(stringIn);
        assertEquals(TypeHandles.STRING, stringIn.getDataTypeHandle());

        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        // Float → String must be allowed at wire time (coerced at evaluate time).
        assertTrue(graph.graphModel.isCompatiblePort(
                stringIn,
                add.getOutputsById().get("out")));
    }
}
