package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * End-to-end subgraph evaluation: outer feeds INPUT variables, inner runs (with its own
 * AddNode + variable wiring), outer reads OUTPUT variables — all through a single
 * {@link GraphExecutor#evaluate} call on the outer subgraph node's port.
 */
public final class SubgraphGameTest {
    private static final String DATA_PASSES_THROUGH_LOCAL_SUBGRAPH = "subgraph_data_passes_through_local";
    private static final String UNRESOLVED_EXTERNAL_RETURNS_NULL = "subgraph_unresolved_external_returns_null";
    private static final String CONSTANT_INPUT_FEEDS_SUBGRAPH = "subgraph_constant_input_feeds_subgraph";

    private SubgraphGameTest() {}

    static void registerFunctions() {
        KGGameTests.registerFunction(DATA_PASSES_THROUGH_LOCAL_SUBGRAPH, SubgraphGameTest::dataPassesThroughLocalSubgraph);
        KGGameTests.registerFunction(UNRESOLVED_EXTERNAL_RETURNS_NULL, SubgraphGameTest::unresolvedExternalReturnsNull);
        KGGameTests.registerFunction(CONSTANT_INPUT_FEEDS_SUBGRAPH, SubgraphGameTest::constantInputFeedsSubgraph);
    }

    static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, DATA_PASSES_THROUGH_LOCAL_SUBGRAPH, KGGameTests.functionKey(DATA_PASSES_THROUGH_LOCAL_SUBGRAPH), data);
        KGGameTests.registerFunctionTest(event, UNRESOLVED_EXTERNAL_RETURNS_NULL, KGGameTests.functionKey(UNRESOLVED_EXTERNAL_RETURNS_NULL), data);
        KGGameTests.registerFunctionTest(event, CONSTANT_INPUT_FEEDS_SUBGRAPH, KGGameTests.functionKey(CONSTANT_INPUT_FEEDS_SUBGRAPH), data);
    }

    // --- 1. Outer feeds vIn → inner Add(+10) → vOut → outer reads ----------------------------------
    public static void dataPassesThroughLocalSubgraph(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("createLocalSubgraphInstance returned null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        // Variables declared on the inner graph
        var vIn = (VariableDeclarationModelBase) inner.createVariable("vIn", int.class, 0, VariableKind.INPUT);
        var vOut = (VariableDeclarationModelBase) inner.createVariable("vOut", int.class, 0, VariableKind.OUTPUT);

        // Inner: vIn (get) + 10 → vOut (set)
        var vInNode = inner.createVariableNode(vIn, new Vector2f(0, 0), null, null);
        var vOutNode = inner.createVariableNode(vOut, new Vector2f(400, 0), null, null);
        if (vInNode.getOutputPort() == null) { helper.fail("inner vIn get-node missing output port"); return; }
        if (vOutNode.getInputPort() == null) { helper.fail("inner vOut set-node missing input port"); return; }

        // Inner: AddNode pulling vIn and 10, output → vOut
        com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData innerAddData =
                com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData.ofOrphan(inner);
        var innerAdd = (com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel)
                com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl
                        .createNodeFromData(innerAddData, AddNode.class);
        inner.createWire(innerAdd.getInputsById().get("in1"), vInNode.getOutputPort());
        var in2Constant = innerAdd.getInputConstantsById().get("in2");
        if (in2Constant == null) { helper.fail("inner add in2 constant missing"); return; }
        in2Constant.setValue(10.0f);
        inner.createWire(vOutNode.getInputPort(), innerAdd.getOutputsById().get("out"));

        // Outer: spawn SubgraphNodeModel bound to inner
        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null,
                n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        subNode.defineNode();

        // Outer subNode should have one input (vIn) and one output (vOut). Ids = variable UIDs.
        assertEq(helper, "outer subNode inputs", 1, subNode.getInputsById().size());
        assertEq(helper, "outer subNode outputs", 1, subNode.getOutputsById().size());

        // Outer: feed 5 into vIn-mirror; read vOut-mirror via runOutputs (the outer has no OUTPUT vars,
        // so runOutputs would be empty — instead, evaluate the subNode's output port directly).
        var vInPort = subNode.getInputsById().get(vIn.getUid().toString());
        var vOutPort = subNode.getOutputsById().get(vOut.getUid().toString());
        if (vInPort == null || vOutPort == null) {
            helper.fail("subNode mirrored ports not found by variable uid");
            return;
        }
        // Pre-load via input constant (no wire needed)
        var vInConst = subNode.getInputConstantsById().get(vInPort.getPortId());
        if (vInConst == null) { helper.fail("vIn-mirror input constant missing"); return; }
        vInConst.setValue(5);

        var executor = new GraphExecutor(outer);
        Object result = executor.evaluate(vOutPort, Object.class);
        if (!(result instanceof Number n)) { helper.fail("vOut not a number: " + result); return; }
        assertEq(helper, "5 + 10 through subgraph", 15.0f, n.floatValue(), 1e-5f);

        helper.succeed();
    }

    // --- 2. External subgraph w/o resolver → all outer outputs null, no throw ---------------------
    public static void unresolvedExternalReturnsNull(GameTestHelper helper) {
        var outer = newGraph();
        // Build a target external graph just to capture variable port shape via portCache
        var target = newGraph();
        target.graphModel.createVariable("eOut", int.class, 0, VariableKind.OUTPUT);

        var path = new FilePath("kilagraph_test/unresolved.tag");
        // Resolver that DOES return our external — gives portCache a chance to capture shape
        outer.graphModel.setReferenceResolver(p -> p.equals(path) ? target : null);
        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "ext",
                new Vector2f(0, 0), null,
                n -> n.setExternalSubgraph(path), SpawnFlags.DEFAULT);
        subNode.defineNode();
        // Now drop the resolver — getSubgraphModel returns null, port cache keeps ports
        outer.graphModel.setReferenceResolver(p -> null);
        subNode.invalidateResolvedSubgraph();

        if (subNode.getOutputsById().isEmpty()) {
            helper.fail("portCache should have preserved the output port shape");
            return;
        }
        var outPort = subNode.getOutputsById().values().iterator().next();
        var executor = new GraphExecutor(outer);
        Object value = executor.evaluate(outPort, Object.class);
        if (value != null) {
            helper.fail("expected null from unresolved subgraph, got " + value);
            return;
        }
        helper.succeed();
    }

    // --- 3. Wire a constant into the outer subgraph input port ---------------------------------
    public static void constantInputFeedsSubgraph(GameTestHelper helper) {
        var outer = newGraph();
        var inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("createLocalSubgraphInstance returned null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var vIn = (VariableDeclarationModelBase) inner.createVariable("vIn", int.class, 0, VariableKind.INPUT);
        var vOut = (VariableDeclarationModelBase) inner.createVariable("vOut", int.class, 0, VariableKind.OUTPUT);

        var vInNode = inner.createVariableNode(vIn, new Vector2f(0, 0), null, null);
        var vOutNode = inner.createVariableNode(vOut, new Vector2f(0, 0), null, null);
        // Inner: just pass through (vIn → vOut directly via AddNode +0)
        var add = addNode(inner.getGraph() instanceof com.lowdragmc.kilagraph.blueprint.BlueprintGraph bg ? bg : null, AddNode.class);
        if (add == null) {
            // Inner graph is a separate BlueprintGraph already — fall through with a manual path
            helper.fail("inner graph is not a BlueprintGraph instance — unexpected");
            return;
        }
        inner.createWire(add.getInputsById().get("in1"), vInNode.getOutputPort());
        setInputConstant(add, "in2", 0.0f);
        inner.createWire(vOutNode.getInputPort(), add.getOutputsById().get("out"));

        // Outer: feed via constant on the mirrored input
        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "sub",
                new Vector2f(0, 0), null,
                n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        subNode.defineNode();
        var vInConst = subNode.getInputConstantsById().get(vIn.getUid().toString());
        if (vInConst == null) { helper.fail("vIn constant missing"); return; }
        vInConst.setValue(99);

        var vOutPort = subNode.getOutputsById().get(vOut.getUid().toString());
        var executor = new GraphExecutor(outer);
        Object result = executor.evaluate(vOutPort, Object.class);
        if (!(result instanceof Number n) || Math.abs(n.floatValue() - 99.0f) > 1e-5f) {
            helper.fail("expected 99, got " + result);
            return;
        }
        helper.succeed();
    }
}
