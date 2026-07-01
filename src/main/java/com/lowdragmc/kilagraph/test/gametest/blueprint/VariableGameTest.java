package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Exercises the executor's variable runtime: env-store-driven {@code IVariableNode} reads
 * and {@link GraphExecutor#runOutputs()} writes through "set var" form variable nodes.
 */
@GameTestHolder(Kilagraph.MODID)
public final class VariableGameTest {
    private static final String INPUT_VAR_READ_FROM_STORE = "var_input_read_from_store";
    private static final String OUTPUT_VAR_RUN_OUTPUTS = "var_output_run_outputs";
    private static final String OUTPUT_VAR_DEFAULT_WHEN_UNWIRED = "var_output_default_when_unwired";
    private static final String STORE_NULL_OVERRIDES_DEFAULT = "var_store_null_overrides_default";

    private VariableGameTest() {}

    // --- 1. INPUT variable's "get" node reads from the env store ----------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void inputVarReadFromStore(GameTestHelper helper) {
        var graph = newGraph();

        // INPUT variable x:int defaults to ModifierFlags.READ → variable node has OUTPUT port.
        var xVar = (com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase)
                graph.graphModel.createVariable("x", int.class, 0, VariableKind.INPUT);
        var xNode = graph.graphModel.createVariableNode(xVar, new Vector2f(0, 0), null, null);
        if (xNode.getOutputPort() == null) { helper.fail("get-form variable node missing output port"); return; }

        var add = addNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("in1"), xNode.getOutputPort());
        setInputConstant(add, "in2", 10.0f);

        // env preloaded with x=5
        var env = EvaluationEnvironment.with(Map.of("x", 5));
        var executor = new GraphExecutor(graph, env);
        Float out = executor.evaluate(add.getOutputsById().get("out"), Float.class);
        assertEq(helper, "5 + 10 via INPUT var", 15.0f, out, 1e-5f);

        helper.succeed();
    }

    // --- 2. runOutputs() pulls AddNode result through OUTPUT variable's "set" node ----------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void outputVarRunOutputs(GameTestHelper helper) {
        var graph = newGraph();

        // INPUT x (READ → get-form) and OUTPUT y (WRITE → set-form)
        var xVar = (com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase)
                graph.graphModel.createVariable("x", int.class, 0, VariableKind.INPUT);
        var yVar = (com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase)
                graph.graphModel.createVariable("y", int.class, 0, VariableKind.OUTPUT);

        var xNode = graph.graphModel.createVariableNode(xVar, new Vector2f(0, 0), null, null);
        var yNode = graph.graphModel.createVariableNode(yVar, new Vector2f(200, 0), null, null);
        if (xNode.getOutputPort() == null) { helper.fail("x get-node missing output port"); return; }
        if (yNode.getInputPort() == null) { helper.fail("y set-node missing input port"); return; }

        var add = addNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("in1"), xNode.getOutputPort());
        setInputConstant(add, "in2", 7.0f);
        wire(graph, yNode.getInputPort(), add.getOutputsById().get("out"));

        var env = EvaluationEnvironment.with(Map.of("x", 3));
        var executor = new GraphExecutor(graph, env);
        Map<String, Object> results = executor.runOutputs();

        // Only OUTPUT variables are in the result map.
        assertEq(helper, "result size", 1, results.size());
        if (!results.containsKey("y")) { helper.fail("missing 'y' in results: " + results.keySet()); return; }
        Object yVal = results.get("y");
        // AddNode emits Float (its declared OutputPort type), so the wire delivers Float to yNode.input.
        // The result the executor returns is whatever pullInput delivered — a Float here.
        if (!(yVal instanceof Number n)) { helper.fail("y not a number: " + yVal); return; }
        assertEq(helper, "y == 3 + 7", 10.0f, n.floatValue(), 1e-5f);

        helper.succeed();
    }

    // --- 3. Unwired OUTPUT variable falls back to the declared default ----------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void outputVarDefaultWhenUnwired(GameTestHelper helper) {
        var graph = newGraph();
        graph.graphModel.createVariable("y", int.class, 42, VariableKind.OUTPUT);

        // No IVariableNode for y, no writer wire → runOutputs uses variable's default (42).
        var executor = new GraphExecutor(graph);
        Map<String, Object> results = executor.runOutputs();
        Object yVal = results.get("y");
        if (!(yVal instanceof Number n) || n.intValue() != 42) {
            helper.fail("expected y=42, got " + yVal);
            return;
        }
        helper.succeed();
    }

    // --- 4. Variable store entry with null wins over declared default -----------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void storeNullOverridesDefault(GameTestHelper helper) {
        var graph = newGraph();
        graph.graphModel.createVariable("y", int.class, 42, VariableKind.OUTPUT);

        // Store has y=null explicitly; no writer node. Falls through to store, which contains null.
        var store = new com.lowdragmc.kilagraph.graph.exec.VariableStore();
        store.put("y", null);
        var env = new EvaluationEnvironment(store, java.util.OptionalLong.empty());

        var executor = new GraphExecutor(graph, env);
        Map<String, Object> results = executor.runOutputs();
        if (!results.containsKey("y")) { helper.fail("'y' missing from results"); return; }
        if (results.get("y") != null) { helper.fail("expected null override, got " + results.get("y")); return; }

        helper.succeed();
    }
}
