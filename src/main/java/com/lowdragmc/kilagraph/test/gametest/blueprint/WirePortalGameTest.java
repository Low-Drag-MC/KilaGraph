package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.DeclarationModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleInputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleOutputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalEntryModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalExitModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalModel;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Wire portals in execution: the entry↔exit gap has no wire, so the executor bridges it via the
 * shared {@link DeclarationModel}. Covers exec-flow across a portal (incl. multi-exit fan-out),
 * single-stepping across a portal, and a data value reference end-to-end.
 */
@GameTestHolder(Kilagraph.MODID)
public final class WirePortalGameTest {
    private static final String EXEC = "portal_exec_across";
    private static final String MULTI = "portal_exec_multi_exit";
    private static final String STEP = "portal_step_across";
    private static final String VALUE = "portal_value_reference";

    private WirePortalGameTest() {}

    // ---- helpers --------------------------------------------------------------------------------

    private static NodeModel oneSetVar(BlueprintGraph g, String varName) {
        var setVar = addNode(g, SetVarNode.class);
        setOption(setVar, "varName", varName);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 1.0f);
        setInputConstant(add, "in2", 0.0f);
        wire(g, setVar.getInputsById().get("value"), add.getOutputsById().get("out"));
        return setVar;
    }

    private static float num(GraphExecutor exec, String var) {
        Object v = exec.getEnvironment().variables().get(var);
        return v instanceof Number n ? n.floatValue() : Float.NaN;
    }

    // ---- 1. exec flow crosses a portal ------------------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execAcross(GameTestHelper helper) {
        var g = newGraph();
        var start = addNode(g, EntryNode.class);
        var decl = g.graphModel.createGraphPortalDeclaration("e", null, null);
        var pIn = g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);
        var pOut = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);

        g.graphModel.createWire(((ISingleInputPortNodeModel) pIn).getInputPort(), start.getOutputsById().get("next"));
        var setVar = oneSetVar(g, "ran");
        g.graphModel.createWire(setVar.getInputsById().get("trigger"), ((ISingleOutputPortNodeModel) pOut).getOutputPort());

        var exec = new GraphExecutor(g);
        exec.executeFrom(start);
        assertEq(helper, "exec crossed portal (ran==1)", 1f, num(exec, "ran"), 1e-5f);
        helper.succeed();
    }

    // ---- 2. one exec entry, two exec exits → both downstreams fire --------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execMultiExit(GameTestHelper helper) {
        var g = newGraph();
        var start = addNode(g, EntryNode.class);
        var decl = g.graphModel.createGraphPortalDeclaration("e", null, null);
        var pIn = g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);
        var pOutA = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);
        var pOutB = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);

        g.graphModel.createWire(((ISingleInputPortNodeModel) pIn).getInputPort(), start.getOutputsById().get("next"));
        var setA = oneSetVar(g, "tookA");
        var setB = oneSetVar(g, "tookB");
        g.graphModel.createWire(setA.getInputsById().get("trigger"), ((ISingleOutputPortNodeModel) pOutA).getOutputPort());
        g.graphModel.createWire(setB.getInputsById().get("trigger"), ((ISingleOutputPortNodeModel) pOutB).getOutputPort());

        var exec = new GraphExecutor(g);
        exec.executeFrom(start);
        assertEq(helper, "exit A fired", 1f, num(exec, "tookA"), 1e-5f);
        assertEq(helper, "exit B fired", 1f, num(exec, "tookB"), 1e-5f);
        helper.succeed();
    }

    // ---- 3. single-stepping reaches the node past the exit portal ---------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void stepAcross(GameTestHelper helper) {
        var g = newGraph();
        var start = addNode(g, EntryNode.class);
        var decl = g.graphModel.createGraphPortalDeclaration("e", null, null);
        var pIn = g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);
        var pOut = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.EXECUTION_FLOW, new Vector2f(), null, null, null, null);
        g.graphModel.createWire(((ISingleInputPortNodeModel) pIn).getInputPort(), start.getOutputsById().get("next"));
        var setVar = oneSetVar(g, "ran");
        g.graphModel.createWire(setVar.getInputsById().get("trigger"), ((ISingleOutputPortNodeModel) pOut).getOutputPort());

        var s = new ExecSession(new GraphExecutor(g)).begin(start);
        boolean steppedEntryPortal = false, steppedPastPortal = false;
        while (s.step()) {
            if (s.lastExecuted() == pIn) steppedEntryPortal = true;
            if (s.lastExecuted() == setVar) steppedPastPortal = true;
        }
        assertTrue(helper, "entry portal stepped as a node", steppedEntryPortal);
        assertTrue(helper, "node after exit portal reached by stepping", steppedPastPortal);
        helper.succeed();
    }

    // ---- 4. data value reference across a portal --------------------------------------------
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void valueReference(GameTestHelper helper) {
        var g = newGraph();
        var producer = addNode(g, AddNode.class);
        setInputConstant(producer, "in1", 2.0f);
        setInputConstant(producer, "in2", 3.0f);
        var decl = g.graphModel.createGraphPortalDeclaration("v", null, null);
        var pIn = g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl, TypeHandles.FLOAT, new Vector2f(), null, null, null, null);
        var pOut = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl, TypeHandles.FLOAT, new Vector2f(), null, null, null, null);
        g.graphModel.createWire(((ISingleInputPortNodeModel) pIn).getInputPort(), producer.getOutputsById().get("out"));

        var consumer = addNode(g, AddNode.class);
        setInputConstant(consumer, "in2", 10.0f);
        g.graphModel.createWire(consumer.getInputsById().get("in1"), ((ISingleOutputPortNodeModel) pOut).getOutputPort());

        Float out = new GraphExecutor(g).evaluate(consumer.getOutputsById().get("out"), Float.class);
        assertEq(helper, "value crossed portal (2+3 then +10)", 15f, out, 1e-5f);
        helper.succeed();
    }
}
