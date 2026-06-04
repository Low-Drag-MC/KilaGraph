package com.lowdragmc.kilagraph.graph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.CastNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListSizeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.AndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.CycleException;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.TypeMismatchException;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end pull-based evaluation tests covering caching, cycle detection, type-mismatched
 * cast paths, option-driven dynamic ports, and v3's wider coercion rules.
 */
class GraphExecutorTest {

    @Test
    void addConstantsViaInputConstants() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in1", 1.0f);
        GraphTestUtils.setInputConstant(add, "in2", 2.0f);

        var executor = new GraphExecutor(graph);
        Float out = executor.evaluate(add.getOutputsById().get("out"), Float.class);
        assertEquals(3.0f, out);
    }

    @Test
    void chainedAddsViaWire() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel inner = GraphTestUtils.addNode(graph, AddNode.class);
        NodeModel outer = GraphTestUtils.addNode(graph, AddNode.class);

        GraphTestUtils.setInputConstant(inner, "in1", 1.0f);
        GraphTestUtils.setInputConstant(inner, "in2", 2.0f);
        GraphTestUtils.setInputConstant(outer, "in2", 3.0f);

        // wire inner.out -> outer.in1
        graph.graphModel.createWire(outer.getInputsById().get("in1"), inner.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        Float out = executor.evaluate(outer.getOutputsById().get("out"), Float.class);
        assertEquals(6.0f, out);
    }

    @Test
    void addFiveInputsViaOption() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setOption(add, "inputs", 5);
        for (int i = 1; i <= 5; i++) {
            GraphTestUtils.setInputConstant(add, "in" + i, (float) i);
        }
        var executor = new GraphExecutor(graph);
        Float out = executor.evaluate(add.getOutputsById().get("out"), Float.class);
        assertEquals(1f + 2f + 3f + 4f + 5f, out);
    }

    @Test
    void cycleDetected() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel n1 = GraphTestUtils.addNode(graph, AddNode.class);
        NodeModel n2 = GraphTestUtils.addNode(graph, AddNode.class);
        // n1.out -> n2.in1, n2.out -> n1.in1   (cycle)
        graph.graphModel.createWire(n2.getInputsById().get("in1"), n1.getOutputsById().get("out"));
        graph.graphModel.createWire(n1.getInputsById().get("in1"), n2.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        assertThrows(CycleException.class,
                () -> executor.evaluate(n1.getOutputsById().get("out"), Float.class));
    }

    @Test
    void cacheReusesValueAcrossMultipleConsumers() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel src = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setInputConstant(src, "in1", 5.0f);
        GraphTestUtils.setInputConstant(src, "in2", 7.0f);

        NodeModel c1 = GraphTestUtils.addNode(graph, AddNode.class);
        NodeModel c2 = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setInputConstant(c1, "in2", 0f);
        GraphTestUtils.setInputConstant(c2, "in2", 0f);
        graph.graphModel.createWire(c1.getInputsById().get("in1"), src.getOutputsById().get("out"));
        graph.graphModel.createWire(c2.getInputsById().get("in1"), src.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        Float a = executor.evaluate(c1.getOutputsById().get("out"), Float.class);
        Float b = executor.evaluate(c2.getOutputsById().get("out"), Float.class);
        assertEquals(12.0f, a);
        assertEquals(12.0f, b);
        assertEquals(12.0f, executor.evaluate(src.getOutputsById().get("out"), Float.class));
    }

    @Test
    void unconnectedInputUsesConstantDefault() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        // No constants set -> defaults (0f, 0f).
        var executor = new GraphExecutor(graph);
        Float out = executor.evaluate(add.getOutputsById().get("out"), Float.class);
        assertEquals(0.0f, out);
    }

    @Test
    void logicAndShortCircuit() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel and = GraphTestUtils.addNode(graph, AndNode.class);
        GraphTestUtils.setInputConstant(and, "in1", true);
        GraphTestUtils.setInputConstant(and, "in2", true);

        var executor = new GraphExecutor(graph);
        assertEquals(Boolean.TRUE, executor.evaluate(and.getOutputsById().get("out"), Boolean.class));

        GraphTestUtils.setInputConstant(and, "in2", false);
        executor.clearCache();
        assertEquals(Boolean.FALSE, executor.evaluate(and.getOutputsById().get("out"), Boolean.class));
    }

    @Test
    void listCombineThenSize() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(combine, "type", TypeHandles.BOOL.getIdentification());
        GraphTestUtils.setOption(combine, "inputs", 3);
        GraphTestUtils.setInputConstant(combine, "in1", true);
        GraphTestUtils.setInputConstant(combine, "in2", false);
        GraphTestUtils.setInputConstant(combine, "in3", true);

        NodeModel size = GraphTestUtils.addNode(graph, ListSizeNode.class);
        graph.graphModel.createWire(size.getInputsById().get("list"), combine.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        Integer n = executor.evaluate(size.getOutputsById().get("size"), Integer.class);
        assertEquals(3, n);
    }

    @Test
    void listGetWithCorrectElementType() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(combine, "type", TypeHandles.BOOL.getIdentification());
        GraphTestUtils.setOption(combine, "inputs", 3);
        GraphTestUtils.setInputConstant(combine, "in1", true);
        GraphTestUtils.setInputConstant(combine, "in2", false);
        GraphTestUtils.setInputConstant(combine, "in3", true);

        NodeModel get = GraphTestUtils.addNode(graph, ListGetNode.class);
        GraphTestUtils.setOption(get, "type", TypeHandles.BOOL.getIdentification());
        GraphTestUtils.setInputConstant(get, "index", 2);
        graph.graphModel.createWire(get.getInputsById().get("list"), combine.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        Object value = executor.evaluate(get.getOutputsById().get("value"), Object.class);
        assertEquals(Boolean.TRUE, value);
    }

    @Test
    void listGetMismatchedElementTypeThrows() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(combine, "type", TypeHandles.STRING.getIdentification());
        GraphTestUtils.setOption(combine, "inputs", 1);
        GraphTestUtils.setInputConstant(combine, "in1", "i am a string, not a bool");

        NodeModel get = GraphTestUtils.addNode(graph, ListGetNode.class);
        GraphTestUtils.setOption(get, "type", TypeHandles.BOOL.getIdentification());
        GraphTestUtils.setInputConstant(get, "index", 0);
        graph.graphModel.createWire(get.getInputsById().get("list"), combine.getOutputsById().get("out"));

        var executor = new GraphExecutor(graph);
        assertThrows(TypeMismatchException.class,
                () -> executor.evaluate(get.getOutputsById().get("value"), Object.class));
    }

    @Test
    void castFromListGetUnknownToBool() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(combine, "type", TypeHandles.BOOL.getIdentification());
        GraphTestUtils.setOption(combine, "inputs", 1);
        GraphTestUtils.setInputConstant(combine, "in1", true);

        NodeModel get = GraphTestUtils.addNode(graph, ListGetNode.class);
        // leave elementType = UNKNOWN: ListGet.value port stays UNKNOWN
        GraphTestUtils.setInputConstant(get, "index", 0);
        graph.graphModel.createWire(get.getInputsById().get("list"), combine.getOutputsById().get("out"));

        NodeModel cast = GraphTestUtils.addNode(graph, CastNode.class);
        GraphTestUtils.setOption(cast, "targetType", TypeHandles.BOOL.getIdentification());
        graph.graphModel.createWire(cast.getInputsById().get("in"), get.getOutputsById().get("value"));

        var executor = new GraphExecutor(graph);
        Boolean v = executor.evaluate(cast.getOutputsById().get("out"), Boolean.class);
        assertEquals(Boolean.TRUE, v);
    }

    @Test
    void castFromUnknownWithMismatchedTargetThrows() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel combine = GraphTestUtils.addNode(graph, ListCombineNode.class);
        GraphTestUtils.setOption(combine, "type", TypeHandles.STRING.getIdentification());
        GraphTestUtils.setOption(combine, "inputs", 1);
        GraphTestUtils.setInputConstant(combine, "in1", "not a bool");

        NodeModel get = GraphTestUtils.addNode(graph, ListGetNode.class);
        GraphTestUtils.setInputConstant(get, "index", 0);
        graph.graphModel.createWire(get.getInputsById().get("list"), combine.getOutputsById().get("out"));

        NodeModel cast = GraphTestUtils.addNode(graph, CastNode.class);
        GraphTestUtils.setOption(cast, "targetType", TypeHandles.BOOL.getIdentification());
        graph.graphModel.createWire(cast.getInputsById().get("in"), get.getOutputsById().get("value"));

        var executor = new GraphExecutor(graph);
        assertThrows(TypeMismatchException.class,
                () -> executor.evaluate(cast.getOutputsById().get("out"), Object.class));
    }

    // ---- v3 new coverage --------------------------------------------------------------------

    @Test
    void evaluateCoercesFloatToInteger() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in1", 2.7f);
        GraphTestUtils.setInputConstant(add, "in2", 1.4f);   // sum = 4.1f
        var executor = new GraphExecutor(graph);
        // Float output, but we asked for Integer → coerce via Number.intValue.
        Integer n = executor.evaluate(add.getOutputsById().get("out"), Integer.class);
        assertEquals(4, n);
    }

    @Test
    void evaluateCoercesAnythingToString() {
        BlueprintGraph graph = GraphTestUtils.newGraph();
        NodeModel add = GraphTestUtils.addNode(graph, AddNode.class);
        GraphTestUtils.setInputConstant(add, "in1", 1.5f);
        GraphTestUtils.setInputConstant(add, "in2", 2.5f);
        var executor = new GraphExecutor(graph);
        String s = executor.evaluate(add.getOutputsById().get("out"), String.class);
        assertEquals("4.0", s);
    }
}
