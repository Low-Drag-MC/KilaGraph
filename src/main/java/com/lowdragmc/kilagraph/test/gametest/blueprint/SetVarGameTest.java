package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Exec-side variable writes through {@link SetVarNode} round-tripping with Phase 1's
 * {@code runOutputs()} via {@link com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment#variables()}.
 */
public final class SetVarGameTest {
    private static final String WRITES_TO_STORE = "exec_setvar_writes_to_store";
    private static final String OUTPUT_VAR_SURFACES_VIA_RUN_OUTPUTS = "exec_setvar_runoutputs_pickup";
    private static final String UNNAMED_NOOP = "exec_setvar_unnamed_noop";

    private SetVarGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(WRITES_TO_STORE, SetVarGameTest::writesToStore);
        KGGameTests.registerFunction(OUTPUT_VAR_SURFACES_VIA_RUN_OUTPUTS, SetVarGameTest::runOutputsPickup);
        KGGameTests.registerFunction(UNNAMED_NOOP, SetVarGameTest::unnamedNoop);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{WRITES_TO_STORE, OUTPUT_VAR_SURFACES_VIA_RUN_OUTPUTS, UNNAMED_NOOP}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** Entry → SetVar(x = 42) → verify env.variables().get("x") == 42. */
    public static void writesToStore(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var setX = addNode(g, SetVarNode.class);
        setOption(setX, "varName", "x");
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 42.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, setX.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, setX.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object x = exec.getEnvironment().variables().get("x");
        if (!(x instanceof Number n) || Math.abs(n.floatValue() - 42.0f) > 1e-5f) {
            helper.fail("expected x=42, got " + x);
            return;
        }
        helper.succeed();
    }

    /** SetVar writes an OUTPUT-kind graph variable; runOutputs() picks it up via the variable store fallback path. */
    public static void runOutputsPickup(GameTestHelper helper) {
        var g = newGraph();
        // Declare 'result' as an OUTPUT variable.
        g.graphModel.createVariable("result", int.class, 0, VariableKind.OUTPUT);

        var entry = addNode(g, EntryNode.class);
        var setR = addNode(g, SetVarNode.class);
        setOption(setR, "varName", "result");
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 7.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, setR.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, setR.getInputsById().get("value"), add.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        // No "set var" IVariableNode wired — runOutputs falls back to the env store, where SetVar wrote.
        Map<String, Object> outs = exec.runOutputs();
        Object r = outs.get("result");
        if (!(r instanceof Number n) || Math.abs(n.floatValue() - 7.0f) > 1e-5f) {
            helper.fail("expected result=7 in runOutputs, got " + r);
            return;
        }
        helper.succeed();
    }

    /** SetVar with no varName is a no-op; just flows through. */
    public static void unnamedNoop(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var setNothing = addNode(g, SetVarNode.class);
        // varName defaults to ""
        wire(g, setNothing.getInputsById().get("trigger"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        try {
            exec.executeFrom(entry);
        } catch (Exception e) {
            helper.fail("empty-name SetVar shouldn't throw: " + e);
            return;
        }
        assertEq(helper, "no stray vars", 0, exec.getEnvironment().variables().snapshot().size());
        helper.succeed();
    }
}
