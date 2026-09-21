package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addRegisteredNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.dataVar;
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
    private static final String POINTS_AT_A_DECLARATION = "exec_setvar_points_at_a_declaration";
    private static final String FOLLOWS_A_RENAME = "exec_setvar_follows_a_rename";
    private static final String SETTER_ITEM_IS_OFFERED = "exec_setvar_setter_item_is_offered";

    private SetVarGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(WRITES_TO_STORE, SetVarGameTest::writesToStore);
        KGGameTests.registerFunction(OUTPUT_VAR_SURFACES_VIA_RUN_OUTPUTS, SetVarGameTest::runOutputsPickup);
        KGGameTests.registerFunction(UNNAMED_NOOP, SetVarGameTest::unnamedNoop);
        KGGameTests.registerFunction(POINTS_AT_A_DECLARATION, SetVarGameTest::pointsAtADeclaration);
        KGGameTests.registerFunction(FOLLOWS_A_RENAME, SetVarGameTest::followsARename);
        KGGameTests.registerFunction(SETTER_ITEM_IS_OFFERED, SetVarGameTest::theSetterItemIsOffered);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var d = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{WRITES_TO_STORE, OUTPUT_VAR_SURFACES_VIA_RUN_OUTPUTS, UNNAMED_NOOP,
                POINTS_AT_A_DECLARATION, FOLLOWS_A_RENAME, SETTER_ITEM_IS_OFFERED}) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    // ---- it refers to a declaration, not to a spelling ----------------------------------------

    /**
     * A node pointed at a declaration knows its name, its uid and its type — and its value pin is
     * typed from the declaration rather than being the {@code UNKNOWN} it starts as.
     */
    public static void pointsAtADeclaration(GameTestHelper helper) {
        var g = newGraph();
        var hp = dataVar(g.graphModel, "hp", float.class, 0f, VariableKind.LOCAL);
        var node = addNode(g, SetVarNode.class);

        assertEq(helper, "an unpointed value pin is UNKNOWN", TypeHandles.UNKNOWN,
                node.getInputsById().get("value").getDataTypeHandle());

        SetVarNode.prefill(node, hp);

        assertEq(helper, "name", "hp", option(node, SetVarNode.NAME_OPTION));
        assertEq(helper, "uid", hp.getUid().toString(), option(node, SetVarNode.UID_OPTION));
        assertEq(helper, "the pin takes the declaration's type", hp.getDataTypeHandle(),
                node.getInputsById().get("value").getDataTypeHandle());
        assertEq(helper, "and the type is mirrored for the next load",
                hp.getDataTypeHandle().getIdentification(), option(node, SetVarNode.TYPE_OPTION));
        helper.succeed();
    }

    /**
     * Renaming the variable does not unhook the write.
     *
     * <p>The sync happens in {@code KGGraphModel.beforeSerialize} — LDLib2's documented pre-serialize
     * hook, which {@code PersistedParser} calls on the root before it writes the node table. Calling
     * it directly is what a save does; calling it is also the only way to observe it, since a rename
     * has no reason to walk the graph looking for writers.</p>
     */
    public static void followsARename(GameTestHelper helper) {
        var g = newGraph();
        var hp = dataVar(g.graphModel, "hp", float.class, 0f, VariableKind.LOCAL);
        // addRegisteredNode, not addNode: syncDeclarations walks getNodeModels(), and an orphan
        // spawn is deliberately not in it. A save only ever sees registered nodes.
        var node = addRegisteredNode(g, SetVarNode.class);
        SetVarNode.prefill(node, hp);

        hp.setName("health");
        assertEq(helper, "the option still spells the old name until a save",
                "hp", option(node, SetVarNode.NAME_OPTION));

        g.graphModel.beforeSerialize();

        assertEq(helper, "the write followed the rename", "health", option(node, SetVarNode.NAME_OPTION));
        assertEq(helper, "and is still the same declaration", hp.getUid().toString(),
                option(node, SetVarNode.UID_OPTION));

        // A node pointing at nothing is left alone rather than being blanked.
        var stray = addRegisteredNode(g, SetVarNode.class);
        setOption(stray, SetVarNode.NAME_OPTION, "gone");
        g.graphModel.beforeSerialize();
        assertEq(helper, "a name with no declaration behind it survives a save",
                "gone", option(stray, SetVarNode.NAME_OPTION));
        helper.succeed();
    }

    /**
     * Dropping a variable on a blueprint canvas offers Set as well as Get, and the spawned node
     * arrives already pointed at the variable that was dragged.
     */
    public static void theSetterItemIsOffered(GameTestHelper helper) {
        var g = newGraph();
        var hp = dataVar(g.graphModel, "hp", float.class, 0f, VariableKind.LOCAL);

        var item = g.graphModel.createVariableSetterItem(hp);
        assertTrue(helper, "a blueprint offers a setter for a variable", item != null);

        var created = item.createNode(
                GraphNodeCreationData.ofOrphan(g.graphModel));
        assertTrue(helper, "it spawns a node model, got " + created, created instanceof NodeModel);
        NodeModel node = (NodeModel) created;
        assertEq(helper, "pointed at the variable", "hp", option(node, SetVarNode.NAME_OPTION));
        assertEq(helper, "with a typed value pin", hp.getDataTypeHandle(),
                node.getInputsById().get("value").getDataTypeHandle());
        helper.succeed();
    }

    /** An option's current constant value, as a String. */
    private static String option(NodeModel node, String optionId) {
        for (var o : node.getNodeOptions()) {
            if (!o.id.equals(optionId)) continue;
            var constant = node.getInputConstantsById().get(o.portModel.getUniqueName());
            return constant == null ? null : String.valueOf(constant.getValue());
        }
        return null;
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
