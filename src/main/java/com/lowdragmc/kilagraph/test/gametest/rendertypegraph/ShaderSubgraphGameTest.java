package com.lowdragmc.kilagraph.test.gametest.rendertypegraph;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.NormalVertexFormatInputNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.CrossNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec3Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Verifies the {@link ShaderFunctionGraph} subgraph machinery: the shader compiler inlines a
 * SubgraphNodeModel (inner READ vars ← outer inputs, inner WRITE vars → outer outputs), the
 * selection→subgraph redirect produces a pure ShaderFunctionGraph (no stages), and stage affinity
 * propagates through inlining.
 */
public final class ShaderSubgraphGameTest {
    private static final String INLINE = "rendertype_subgraph_inline";
    private static final String REDIRECT = "rendertype_subgraph_redirect_type";
    private static final String AFFINITY = "rendertype_subgraph_affinity";
    private static final String RESOURCE_ROUNDTRIP = "rendertype_subgraph_resource_roundtrip";

    private ShaderSubgraphGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(INLINE, ShaderSubgraphGameTest::subgraphInlinesIntoFragment);
        KGGameTests.registerFunction(REDIRECT, ShaderSubgraphGameTest::selectionSubgraphIsShaderFunctionGraph);
        KGGameTests.registerFunction(AFFINITY, ShaderSubgraphGameTest::subgraphStageAffinityPropagates);
        KGGameTests.registerFunction(RESOURCE_ROUNDTRIP, ShaderSubgraphGameTest::functionGraphResourceRoundTrips);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, INLINE, KGGameTests.functionKey(INLINE), d);
        KGGameTests.registerFunctionTest(event, REDIRECT, KGGameTests.functionKey(REDIRECT), d);
        KGGameTests.registerFunctionTest(event, AFFINITY, KGGameTests.functionKey(AFFINITY), d);
        KGGameTests.registerFunctionTest(event, RESOURCE_ROUNDTRIP, KGGameTests.functionKey(RESOURCE_ROUNDTRIP), d);
    }

    private static NodeModel innerNode(CustomGraphModelImpl inner, Class<?> nodeClass) {
        AbstractNodeModel m = CustomGraphModelImpl.createNodeFromData(
                GraphNodeCreationData.ofOrphan(inner), nodeClass.asSubclass(
                        com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node.class));
        return (NodeModel) m;
    }

    /**
     * A ShaderFunctionGraph (READ a,b vec3 → Cross → WRITE out vec3), embedded as a subgraph node in a
     * RenderTypeGraph and wired into base color, is inlined: {@code cross(...)} appears in the fragment
     * GLSL and its result drives {@code kg_baseColor} — proving the READ-var binding + WRITE-var output.
     */
    public static void subgraphInlinesIntoFragment(GameTestHelper helper) {
        RenderTypeGraph outer = new RenderTypeGraph();
        CustomGraphModelImpl inner = outer.graphModel.createLocalSubgraphInstance(ShaderFunctionGraph.class);
        if (inner == null) { helper.fail("createLocalSubgraphInstance(ShaderFunctionGraph) returned null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        // Inner function: out = cross(a, b)
        var aVar = (VariableDeclarationModelBase) inner.createVariable("a", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.INPUT);
        var bVar = (VariableDeclarationModelBase) inner.createVariable("b", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.INPUT);
        var outVar = (VariableDeclarationModelBase) inner.createVariable("out", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.OUTPUT);
        var aNode = inner.createVariableNode(aVar, new Vector2f(0, 0), null, null);
        var bNode = inner.createVariableNode(bVar, new Vector2f(0, 64), null, null);
        var outNode = inner.createVariableNode(outVar, new Vector2f(400, 0), null, null);
        NodeModel cross = innerNode(inner, CrossNode.class);
        inner.createWire(cross.getInputsById().get("a"), aNode.getOutputPort());
        inner.createWire(cross.getInputsById().get("b"), bNode.getOutputPort());
        inner.createWire(outNode.getInputPort(), cross.getOutputsById().get("out"));

        // Outer subgraph node bound to the inner function.
        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "fn",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        subNode.defineNode();
        assertEq(helper, "subgraph node inputs (a,b)", 2, subNode.getInputsById().size());
        assertEq(helper, "subgraph node outputs (out)", 1, subNode.getOutputsById().size());

        // Outer: two Vec3 nodes feed a,b; the subgraph output drives base color.
        NodeModel va = addNode(outer, Vec3Node.class);
        NodeModel vb = addNode(outer, Vec3Node.class);
        wire(outer, subNode.getInputsById().get(aVar.getUid().toString()), va.getOutputsById().get("out"));
        wire(outer, subNode.getInputsById().get(bVar.getUid().toString()), vb.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(outer, outer.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(outer, baseColor.getInputsById().get("color"), subNode.getOutputsById().get(outVar.getUid().toString()));

        CompiledShaderGraph compiled = new ShaderGraphCompiler(outer).compile();
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "inner cross() inlined into fragment", fsh.contains("cross("));
        assertTrue(helper, "subgraph output reaches base color", fsh.contains("kg_baseColor"));
        assertTrue(helper, "no stage errors", !compiled.hasStageErrors());
        helper.succeed();
    }

    /**
     * The no-arg subgraph creation on a RenderTypeGraph (used by selection→subgraph) yields a pure
     * ShaderFunctionGraph with no fixed stage nodes / entity-shader init — not a cloned RenderTypeGraph.
     */
    public static void selectionSubgraphIsShaderFunctionGraph(GameTestHelper helper) {
        RenderTypeGraph outer = new RenderTypeGraph();
        CustomGraphModelImpl inner = outer.graphModel.createLocalSubgraphInstance();
        if (inner == null) { helper.fail("no-arg createLocalSubgraphInstance returned null"); return; }
        assertTrue(helper, "inner is a ShaderFunctionGraph", inner.getGraph() instanceof ShaderFunctionGraph);
        assertTrue(helper, "inner has no stage nodes / entity init", inner.getNodeModels().isEmpty());
        assertTrue(helper, "RenderTypeGraph accepts ShaderFunctionGraph subgraph",
                outer.acceptsSubgraphGraph(new ShaderFunctionGraph()));
        helper.succeed();
    }

    /**
     * The {@link com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource} stores a reusable function
     * graph: a populated {@code ShaderFunctionGraph} (out = cross(a,b)) serialized and deserialized as a
     * resource preserves its variables and nodes, and the restored copy still inlines correctly when
     * embedded as a subgraph — the foundation for cross-graph reuse (drag a function asset into any
     * RenderTypeGraph as an external subgraph). The editor/import/dive-in UI itself is client-verified.
     */
    public static void functionGraphResourceRoundTrips(GameTestHelper helper) {
        // Author a function graph via the resource, exactly as a saved asset would be created.
        var resource = com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource.INSTANCE;
        ShaderFunctionGraph authored = resource.createGraph();
        var aVar = (VariableDeclarationModelBase) authored.graphModel.createVariable("a", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.INPUT);
        var bVar = (VariableDeclarationModelBase) authored.graphModel.createVariable("b", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.INPUT);
        var outVar = (VariableDeclarationModelBase) authored.graphModel.createVariable("out", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.OUTPUT);
        var aNode = authored.graphModel.createVariableNode(aVar, new Vector2f(0, 0), null, null);
        var bNode = authored.graphModel.createVariableNode(bVar, new Vector2f(0, 64), null, null);
        var outNode = authored.graphModel.createVariableNode(outVar, new Vector2f(400, 0), null, null);
        // A registered (non-orphan) node so it lands in nodeModels and survives serialization — an
        // orphan node would be wire-reachable in-memory but dropped on save.
        NodeModel cross = (NodeModel) CustomGraphModelImpl.createNodeFromData(
                new GraphNodeCreationData(authored.graphModel, new Vector2f(200, 32), SpawnFlags.DEFAULT, null),
                CrossNode.class);
        authored.graphModel.createWire(cross.getInputsById().get("a"), aNode.getOutputPort());
        authored.graphModel.createWire(cross.getInputsById().get("b"), bNode.getOutputPort());
        authored.graphModel.createWire(outNode.getInputPort(), cross.getOutputsById().get("out"));

        // Serialize → deserialize (the resource save/load round-trip).
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        authored.graphModel.serialize(output);
        var tag = output.buildResult();

        ShaderFunctionGraph restored = resource.createGraph();
        restored.graphModel.deserialize(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), tag));

        assertTrue(helper, "restored graph is a ShaderFunctionGraph", restored instanceof ShaderFunctionGraph);
        long varCount = restored.graphModel.getGraphVariableModels().stream().filter(java.util.Objects::nonNull).count();
        assertEq(helper, "restored graph keeps its 3 variables", 3L, varCount);
        assertTrue(helper, "restored graph keeps the cross node",
                restored.graphModel.getNodeModels().stream().anyMatch(
                        n -> n instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel c
                                && c.getNode() instanceof CrossNode));

        // The restored function graph still inlines when embedded as a local subgraph in a host.
        RenderTypeGraph outer = new RenderTypeGraph();
        outer.graphModel.addLocalSubgraph(restored.graphModel);
        var restoredVars = restored.graphModel.getGraphVariableModels().stream()
                .filter(java.util.Objects::nonNull).toList();
        var ra = restoredVars.stream().filter(v -> "a".equals(v.getName())).findFirst().orElseThrow();
        var rb = restoredVars.stream().filter(v -> "b".equals(v.getName())).findFirst().orElseThrow();
        var rout = restoredVars.stream().filter(v -> "out".equals(v.getName())).findFirst().orElseThrow();
        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "fn",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(restored.graphModel), SpawnFlags.DEFAULT);
        subNode.defineNode();
        NodeModel va = addNode(outer, Vec3Node.class);
        NodeModel vb = addNode(outer, Vec3Node.class);
        wire(outer, subNode.getInputsById().get(ra.getUid().toString()), va.getOutputsById().get("out"));
        wire(outer, subNode.getInputsById().get(rb.getUid().toString()), vb.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(outer, outer.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(outer, baseColor.getInputsById().get("color"), subNode.getOutputsById().get(rout.getUid().toString()));

        CompiledShaderGraph compiled = new ShaderGraphCompiler(outer).compile();
        assertTrue(helper, "restored function graph inlines cross() into fragment",
                compiled.fragmentSource().contains("cross("));
        assertTrue(helper, "no stage errors after round-trip", !compiled.hasStageErrors());
        helper.succeed();
    }

    /** A VERTEX_ONLY node inside a function subgraph, inlined into the fragment stage, is a stage error. */
    public static void subgraphStageAffinityPropagates(GameTestHelper helper) {
        RenderTypeGraph outer = new RenderTypeGraph();
        CustomGraphModelImpl inner = outer.graphModel.createLocalSubgraphInstance(ShaderFunctionGraph.class);
        if (inner == null) { helper.fail("createLocalSubgraphInstance(ShaderFunctionGraph) returned null"); return; }
        outer.graphModel.addLocalSubgraph(inner);

        var outVar = (VariableDeclarationModelBase) inner.createVariable("out", RenderTypeGraphTypes.VEC3, new Vector3f(), VariableKind.OUTPUT);
        var outNode = inner.createVariableNode(outVar, new Vector2f(400, 0), null, null);
        NodeModel normal = innerNode(inner, NormalVertexFormatInputNode.class); // VERTEX_ONLY
        inner.createWire(outNode.getInputPort(), normal.getOutputsById().get("out"));

        var subNode = outer.graphModel.createNodeWithType(SubgraphNodeModel.class, "fn",
                new Vector2f(0, 0), null, n -> n.setLocalSubgraph(inner), SpawnFlags.DEFAULT);
        subNode.defineNode();
        NodeModel baseColor = addBlock(outer, outer.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(outer, baseColor.getInputsById().get("color"), subNode.getOutputsById().get(outVar.getUid().toString()));

        CompiledShaderGraph compiled = new ShaderGraphCompiler(outer).compile();
        assertTrue(helper, "Normal (VERTEX_ONLY) inside subgraph inlined into fragment is a stage error",
                compiled.hasStageErrors());
        helper.succeed();
    }
}
