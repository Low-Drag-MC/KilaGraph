package com.lowdragmc.kilagraph.test.gametest.rendertypegraph;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.editor.RenderTypeGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.VertexAttributeInputNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.ApplyFogNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.LightMapTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.OverlayTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.DynamicTransformsUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaDiscardBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentEmissionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogCylindricalDistanceNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogSphericalDistanceNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.LinearFogValueNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.ProjectionFromPositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.ProjectionUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.AddNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4ConstructNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.SplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.TotalFogValueNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomFloatBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec4Block;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexPositionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.VertexColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.UVNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec3Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.GraphCommands;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addRegisteredNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

public final class RenderTypeGraphGameTest {
    private static final String RESOURCE = "rendertype_resource";
    private static final String SUPPORTED_TYPES = "rendertype_supported_types";
    private static final String SUPPORTED_NODES = "rendertype_supported_nodes";
    private static final String GRAPH_SETTINGS = "rendertype_graph_settings";
    private static final String STAGE_CONTEXTS = "rendertype_stage_contexts";
    private static final String STAGE_PORTS = "rendertype_stage_ports";
    private static final String FIXED_STAGE_DELETE_POLICY = "rendertype_fixed_stage_delete_policy";
    private static final String RESOURCE_SETTINGS_ROUND_TRIP = "rendertype_resource_settings_round_trip";
    private static final String SETTINGS_TOOL_WRITES_GRAPH = "rendertype_settings_tool_writes_graph";
    private static final String DEFAULT_ENTITY_SHADER_GRAPH = "rendertype_default_entity_shader_graph";
    private static final String SHADER_VALUE_ASSIGNABILITY = "rendertype_shader_value_assignability";
    private static final String COMPILE_DEFAULT_ENTITY = "rendertype_compile_default_entity";
    private static final String COMPILE_COMPOSED_FORMAT = "rendertype_compile_composed_format";
    private static final String VERTEX_FORMAT_VALIDATION = "rendertype_vertex_format_validation";
    private static final String VERTEX_ELEMENT_FALLBACK = "rendertype_vertex_element_fallback";
    private static final String PREVIEW_GEOMETRY = "rendertype_preview_geometry";
    private static final String PREVIEW_TESSELLATOR = "rendertype_preview_tessellator";
    private static final String CHANGE_VERSION = "rendertype_change_version";
    private static final String UNDO_ROUND_TRIP = "rendertype_undo_round_trip";
    private static final String PREVIEW_SHAPE_PERSISTENCE = "rendertype_preview_shape_persistence";
    private static final String STAGE_AFFINITY_VALIDATION = "rendertype_stage_affinity_validation";

    private RenderTypeGraphGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(RESOURCE, RenderTypeGraphGameTest::resourceCreatesGraph);
        KGGameTests.registerFunction(SUPPORTED_TYPES, RenderTypeGraphGameTest::supportedTypesIncludeRenderTypeContracts);
        KGGameTests.registerFunction(SUPPORTED_NODES, RenderTypeGraphGameTest::supportedNodesIncludeBuiltins);
        KGGameTests.registerFunction(GRAPH_SETTINGS, RenderTypeGraphGameTest::graphCarriesRenderTypeSettings);
        KGGameTests.registerFunction(STAGE_CONTEXTS, RenderTypeGraphGameTest::stageContextsAcceptExpectedBlocks);
        KGGameTests.registerFunction(STAGE_PORTS, RenderTypeGraphGameTest::stageNodesExposePipelinePorts);
        KGGameTests.registerFunction(FIXED_STAGE_DELETE_POLICY, RenderTypeGraphGameTest::fixedStageModelsCannotBeDeleted);
        KGGameTests.registerFunction(RESOURCE_SETTINGS_ROUND_TRIP,
                RenderTypeGraphGameTest::resourcePersistsRenderTypeSettings);
        KGGameTests.registerFunction(SETTINGS_TOOL_WRITES_GRAPH,
                RenderTypeGraphGameTest::settingsToolWritesLoadedGraph);
        KGGameTests.registerFunction(DEFAULT_ENTITY_SHADER_GRAPH,
                RenderTypeGraphGameTest::newGraphContainsDefaultEntityShader);
        KGGameTests.registerFunction(SHADER_VALUE_ASSIGNABILITY,
                RenderTypeGraphGameTest::shaderVectorValuesAreWireCompatible);
        KGGameTests.registerFunction(COMPILE_DEFAULT_ENTITY,
                RenderTypeGraphGameTest::compileDefaultEntityShader);
        KGGameTests.registerFunction(COMPILE_COMPOSED_FORMAT,
                RenderTypeGraphGameTest::compileRespectsComposedVertexFormat);
        KGGameTests.registerFunction(VERTEX_FORMAT_VALIDATION,
                RenderTypeGraphGameTest::vertexFormatValidationFlagsMissingElement);
        KGGameTests.registerFunction(VERTEX_ELEMENT_FALLBACK,
                RenderTypeGraphGameTest::vertexElementDefaultFallsBackAndWarns);
        KGGameTests.registerFunction(PREVIEW_GEOMETRY,
                RenderTypeGraphGameTest::previewContentsBuildGeometry);
        KGGameTests.registerFunction(PREVIEW_TESSELLATOR,
                RenderTypeGraphGameTest::previewTessellatorMatchesMode);
        KGGameTests.registerFunction(CHANGE_VERSION,
                RenderTypeGraphGameTest::onGraphChangedBumpsChangeVersion);
        KGGameTests.registerFunction(UNDO_ROUND_TRIP,
                RenderTypeGraphGameTest::undoRoundTripPreservesWholeGraphCompile);
        KGGameTests.registerFunction(PREVIEW_SHAPE_PERSISTENCE,
                RenderTypeGraphGameTest::previewShapesPersistAcrossRoundTrip);
        KGGameTests.registerFunction(STAGE_AFFINITY_VALIDATION,
                RenderTypeGraphGameTest::stageAffinityViolationFlaggedOnGraphChanged);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, RESOURCE, KGGameTests.functionKey(RESOURCE), d);
        KGGameTests.registerFunctionTest(event, SUPPORTED_TYPES, KGGameTests.functionKey(SUPPORTED_TYPES), d);
        KGGameTests.registerFunctionTest(event, SUPPORTED_NODES, KGGameTests.functionKey(SUPPORTED_NODES), d);
        KGGameTests.registerFunctionTest(event, GRAPH_SETTINGS, KGGameTests.functionKey(GRAPH_SETTINGS), d);
        KGGameTests.registerFunctionTest(event, STAGE_CONTEXTS, KGGameTests.functionKey(STAGE_CONTEXTS), d);
        KGGameTests.registerFunctionTest(event, STAGE_PORTS, KGGameTests.functionKey(STAGE_PORTS), d);
        KGGameTests.registerFunctionTest(event, FIXED_STAGE_DELETE_POLICY,
                KGGameTests.functionKey(FIXED_STAGE_DELETE_POLICY), d);
        KGGameTests.registerFunctionTest(event, RESOURCE_SETTINGS_ROUND_TRIP,
                KGGameTests.functionKey(RESOURCE_SETTINGS_ROUND_TRIP), d);
        KGGameTests.registerFunctionTest(event, SETTINGS_TOOL_WRITES_GRAPH,
                KGGameTests.functionKey(SETTINGS_TOOL_WRITES_GRAPH), d);
        KGGameTests.registerFunctionTest(event, DEFAULT_ENTITY_SHADER_GRAPH,
                KGGameTests.functionKey(DEFAULT_ENTITY_SHADER_GRAPH), d);
        KGGameTests.registerFunctionTest(event, SHADER_VALUE_ASSIGNABILITY,
                KGGameTests.functionKey(SHADER_VALUE_ASSIGNABILITY), d);
        KGGameTests.registerFunctionTest(event, COMPILE_DEFAULT_ENTITY,
                KGGameTests.functionKey(COMPILE_DEFAULT_ENTITY), d);
        KGGameTests.registerFunctionTest(event, COMPILE_COMPOSED_FORMAT,
                KGGameTests.functionKey(COMPILE_COMPOSED_FORMAT), d);
        KGGameTests.registerFunctionTest(event, VERTEX_FORMAT_VALIDATION,
                KGGameTests.functionKey(VERTEX_FORMAT_VALIDATION), d);
        KGGameTests.registerFunctionTest(event, VERTEX_ELEMENT_FALLBACK,
                KGGameTests.functionKey(VERTEX_ELEMENT_FALLBACK), d);
        KGGameTests.registerFunctionTest(event, PREVIEW_GEOMETRY,
                KGGameTests.functionKey(PREVIEW_GEOMETRY), d);
        KGGameTests.registerFunctionTest(event, PREVIEW_TESSELLATOR,
                KGGameTests.functionKey(PREVIEW_TESSELLATOR), d);
        KGGameTests.registerFunctionTest(event, CHANGE_VERSION,
                KGGameTests.functionKey(CHANGE_VERSION), d);
        KGGameTests.registerFunctionTest(event, UNDO_ROUND_TRIP,
                KGGameTests.functionKey(UNDO_ROUND_TRIP), d);
        KGGameTests.registerFunctionTest(event, PREVIEW_SHAPE_PERSISTENCE,
                KGGameTests.functionKey(PREVIEW_SHAPE_PERSISTENCE), d);
        KGGameTests.registerFunctionTest(event, STAGE_AFFINITY_VALIDATION,
                KGGameTests.functionKey(STAGE_AFFINITY_VALIDATION), d);
    }

    public static void resourceCreatesGraph(GameTestHelper helper) {
        assertTrue(helper, "RenderTypeGraphResource creates RenderTypeGraph",
                RenderTypeGraphResource.INSTANCE.createGraph() instanceof RenderTypeGraph);
        helper.succeed();
    }

    /**
     * The editor's {@code onGraphChanged} hook (fired after every applied changeset — structural or
     * value edits) bumps {@link RenderTypeGraph#getChangeVersion()}, the signal the live previews gate
     * their per-frame recompile on. Drives the hook directly (no editor) to verify the wiring.
     */
    public static void onGraphChangedBumpsChangeVersion(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        long v0 = graph.getChangeVersion();
        graph.graphModel.onGraphChanged(new GraphLogger());
        long v1 = graph.getChangeVersion();
        graph.graphModel.onGraphChanged(new GraphLogger());
        long v2 = graph.getChangeVersion();
        assertTrue(helper, "change version advances on first onGraphChanged", v1 > v0);
        assertTrue(helper, "change version advances on second onGraphChanged", v2 > v1);
        helper.succeed();
    }

    /**
     * Reproduces the editor's delete-then-undo of a Texture node and verifies the WHOLE-graph compile
     * (what the graph-tool preview renders) survives it. Undo in {@code UndoableGraphCommand} is a
     * serialize-of-the-pre-edit-graph followed by {@code graphModel.deserialize(...)} back into the SAME
     * already-populated model — so this drives exactly that: snapshot, delete the Texture node, then
     * deserialize the snapshot into the live model. Asserts the node returns, the fixed stages aren't
     * duplicated, and {@code compile()} reproduces the original shader byte-for-byte (same content hash,
     * no stage errors) — the condition the persistent preview tool needs to rebuild and draw again.
     */
    public static void undoRoundTripPreservesWholeGraphCompile(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        CompiledShaderGraph before = new ShaderGraphCompiler(graph).compile();
        assertFalse(helper, "baseline graph compiles without stage errors", before.hasStageErrors());

        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, provider);
        graph.graphModel.serialize(out);
        net.minecraft.nbt.CompoundTag snapshot = out.buildResult();

        NodeModel texture = findNode(graph, TextureNode.class);
        assertTrue(helper, "default graph has a Texture node", texture != null);
        graph.graphModel.deleteElements(List.of(texture));
        assertTrue(helper, "Texture node removed by delete", findNode(graph, TextureNode.class) == null);

        // Undo: deserialize the pre-delete snapshot back into the live (already-populated) model.
        graph.graphModel.deserialize(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, provider, snapshot));

        assertTrue(helper, "undo restores the Texture node", findNode(graph, TextureNode.class) != null);
        assertEq(helper, "exactly one vertex stage after undo (no deserialize duplication)",
                1, (int) graph.getNodes().stream().filter(VaryingStageNode.class::isInstance).count());
        assertEq(helper, "exactly one fragment stage after undo (no deserialize duplication)",
                1, (int) graph.getNodes().stream().filter(FragmentStageNode.class::isInstance).count());

        CompiledShaderGraph after = new ShaderGraphCompiler(graph).compile();
        assertFalse(helper, "undone graph compiles without stage errors", after.hasStageErrors());
        // The whole-graph compile (what the persistent graph-tool preview renders) must reproduce the
        // original shader, not an empty/transparent one from stale fixed-stage refs after the round-trip.
        assertTrue(helper, "undone fragment shader still samples the texture",
                after.fragmentSource().contains("texture(kg_tex"));
        assertEq(helper, "undo preserves the compiled content hash", before.contentHash(), after.contentHash());
        helper.succeed();
    }

    /**
     * The right-click-chosen preview geometry (a {@link KGPreviewContents} key) for each node thumbnail and
     * for the graph-tool panel is persisted on {@link RenderTypeGraphModel}'s NBT, so it survives BOTH the
     * reopen path (resource serialize/deserialize into a fresh graph) AND the undo path
     * ({@code UndoableGraphCommand}: serialize, then deserialize the snapshot back into the SAME live model).
     * Drives both round-trips directly (no editor UI — see {@code verify-ui-changes-in-client}) since the
     * keys live in the model, not the ephemeral {@code NodeShaderPreview}/{@code ShaderPreviewTool} elements.
     */
    public static void previewShapesPersistAcrossRoundTrip(GameTestHelper helper) {
        String sphere = KGPreviewContents.SPHERE.key();
        String quad = KGPreviewContents.QUAD.key();
        String cube = KGPreviewContents.CUBE.key();

        // --- Reopen path: resource serialize -> deserialize into a brand-new graph. ---
        RenderTypeGraph graph = new RenderTypeGraph();
        RenderTypeGraphModel model = (RenderTypeGraphModel) graph.graphModel;
        NodeModel node = findNode(graph, SamplerTexture2DNode.class);
        assertTrue(helper, "default graph has a node to tag with a preview shape", node != null);
        java.util.UUID nodeUid = node.getUid();
        model.setNodePreviewContentKey(nodeUid, sphere);
        model.setPreviewToolContentKey(quad);

        var tag = RenderTypeGraphResource.INSTANCE.serializeGraph(graph);
        RenderTypeGraph restored = RenderTypeGraphResource.INSTANCE.deserializeGraph(tag);
        RenderTypeGraphModel restoredModel = (RenderTypeGraphModel) restored.graphModel;

        assertEq(helper, "reopen restores the node preview shape (keyed by the node's stable UID)",
                sphere, restoredModel.getNodePreviewContentKey(nodeUid));
        assertEq(helper, "reopen restores the graph-tool preview shape",
                quad, restoredModel.getPreviewToolContentKey());
        assertTrue(helper, "the tagged node still exists in the reopened graph (same UID)",
                restored.graphModel.getModel(nodeUid) != null);

        // --- Undo path: snapshot, mutate, then deserialize the snapshot back into the SAME model. ---
        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, provider);
        graph.graphModel.serialize(out);
        net.minecraft.nbt.CompoundTag snapshot = out.buildResult();

        // Edit after the snapshot: change both shapes to something else.
        model.setNodePreviewContentKey(nodeUid, cube);
        model.setPreviewToolContentKey(cube);
        assertEq(helper, "node shape reflects the post-snapshot edit", cube, model.getNodePreviewContentKey(nodeUid));

        // Undo: deserialize the pre-edit snapshot back into the live model.
        graph.graphModel.deserialize(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.Collector.DISCARDING, provider, snapshot));

        assertEq(helper, "undo restores the node preview shape from the snapshot",
                sphere, model.getNodePreviewContentKey(nodeUid));
        assertEq(helper, "undo restores the graph-tool preview shape from the snapshot",
                quad, model.getPreviewToolContentKey());
        helper.succeed();
    }

    /**
     * Stage-affinity violations surface through the editor {@link GraphLogger} on {@code onGraphChanged},
     * keyed to the offending node — so the user sees an in-canvas error, not just a silently-skipped draw.
     * A VERTEX_ONLY vertex-attribute node wired into a fragment block is pulled into the fragment stage,
     * which its affinity forbids; once the wire is removed it's no longer flagged.
     */
    public static void stageAffinityViolationFlaggedOnGraphChanged(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel attr = addRegisteredNode(graph, VertexAttributeInputNode.class); // VERTEX_ONLY, default = position
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), attr.getOutputsById().get("out"));

        GraphLogger flagged = new GraphLogger();
        graph.onGraphChanged(flagged);
        assertTrue(helper, "stage-affinity violation is flagged as an ERROR keyed to the node",
                flagged.getEntries().stream().anyMatch(e ->
                        e.context() == attr && e.level() == GraphLogger.Level.ERROR));

        // Disconnect: the node is no longer pulled into the fragment stage, so it's not flagged.
        graph.graphModel.deleteWires(List.copyOf(
                emission.getInputsById().get("color").getConnectedWires()));
        GraphLogger clear = new GraphLogger();
        graph.onGraphChanged(clear);
        assertTrue(helper, "no stage error once the vertex-only node is unwired",
                clear.getEntries().stream().noneMatch(e ->
                        e.context() == attr && e.level() == GraphLogger.Level.ERROR));
        helper.succeed();
    }

    public static void supportedTypesIncludeRenderTypeContracts(GameTestHelper helper) {
        List<TypeHandle> types = new RenderTypeGraph().getSupportTypes();
        assertTrue(helper, "supports vec2", types.contains(RenderTypeGraphTypes.VEC2));
        assertTrue(helper, "supports vec3", types.contains(RenderTypeGraphTypes.VEC3));
        assertTrue(helper, "supports vec4", types.contains(RenderTypeGraphTypes.VEC4));
        assertTrue(helper, "supports sampler2D", types.contains(RenderTypeGraphTypes.SAMPLER2D));
        assertTrue(helper, "supports mat4", types.contains(RenderTypeGraphTypes.MAT4));
        helper.succeed();
    }

    public static void supportedNodesIncludeBuiltins(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        List<Class<? extends Node>> nodes = graph.getSupportNodes();
        List<Class<? extends Node>> libraryNodes = graph.getLibrarySupportNodes();

        assertTrue(helper, "vertex stage is an internal supported node", nodes.contains(VaryingStageNode.class));
        assertTrue(helper, "fragment stage is an internal supported node", nodes.contains(FragmentStageNode.class));
        assertFalse(helper, "vertex stage is fixed, not library-spawned",
                libraryNodes.contains(VaryingStageNode.class));
        assertFalse(helper, "fragment stage is fixed, not library-spawned",
                libraryNodes.contains(FragmentStageNode.class));
        assertTrue(helper, "does not support render type output node",
                nodes.stream().noneMatch(node -> node.getSimpleName().equals("RenderTypeOutputNode")));
        assertTrue(helper, "supports vertex position output slot block", nodes.contains(VertexPositionBlock.class));
        assertTrue(helper, "supports the UV source node", nodes.contains(UVNode.class));
        assertTrue(helper, "supports the Vertex Color source node", nodes.contains(VertexColorNode.class));
        assertTrue(helper, "supports custom float interpolator", nodes.contains(VaryingCustomFloatBlock.class));
        assertTrue(helper, "supports custom vec4 interpolator", nodes.contains(VaryingCustomVec4Block.class));
        assertTrue(helper, "does not support old specialized texture sample node",
                nodes.stream().noneMatch(node -> node.getSimpleName().equals("FragmentTextureSampleNode")));
        assertTrue(helper, "supports generic sampler texture2D node", nodes.contains(SamplerTexture2DNode.class));
        assertTrue(helper, "supports texture node", nodes.contains(TextureNode.class));
        assertTrue(helper, "supports overlay texture node", nodes.contains(OverlayTextureNode.class));
        assertTrue(helper, "supports lightmap texture node", nodes.contains(LightMapTextureNode.class));
        assertTrue(helper, "supports shader split node", nodes.contains(SplitNode.class));
        assertTrue(helper, "supports vec3 construct node", nodes.contains(Vec3Node.class));
        assertTrue(helper, "supports dynamic multiply node", nodes.contains(MultiplyNode.class));
        assertTrue(helper, "supports dynamic add node", nodes.contains(AddNode.class));
        assertTrue(helper, "supports mat4 construct node", nodes.contains(Mat4ConstructNode.class));
        assertTrue(helper, "supports fog UBO node", nodes.contains(FogUboNode.class));
        assertTrue(helper, "supports linear fog function node", nodes.contains(LinearFogValueNode.class));
        assertTrue(helper, "supports total fog function node", nodes.contains(TotalFogValueNode.class));
        assertTrue(helper, "supports apply fog function node", nodes.contains(ApplyFogNode.class));
        assertTrue(helper, "supports fog spherical distance node", nodes.contains(FogSphericalDistanceNode.class));
        assertTrue(helper, "supports fog cylindrical distance node", nodes.contains(FogCylindricalDistanceNode.class));
        assertTrue(helper, "supports dynamic transforms UBO node", nodes.contains(DynamicTransformsUboNode.class));
        assertTrue(helper, "supports projection UBO node", nodes.contains(ProjectionUboNode.class));
        assertTrue(helper, "supports projection_from_position function node", nodes.contains(ProjectionFromPositionNode.class));
        assertTrue(helper, "supports fragment base color block", nodes.contains(FragmentBaseColorBlock.class));
        assertTrue(helper, "supports fragment alpha block", nodes.contains(FragmentAlphaBlock.class));
        assertTrue(helper, "supports fragment alpha discard block", nodes.contains(FragmentAlphaDiscardBlock.class));
        helper.succeed();
    }

    public static void graphCarriesRenderTypeSettings(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();

        assertTrue(helper, "graph has fixed vertex stage", graph.getVertexStageModel() != null);
        assertTrue(helper, "graph has fixed fragment stage", graph.getFragmentStageModel() != null);
        assertTrue(helper, "fixed vertex stage is stable",
                graph.getVertexStageModel() == graph.getVertexStageModel());
        assertTrue(helper, "fixed fragment stage is stable",
                graph.getFragmentStageModel() == graph.getFragmentStageModel());
        assertTrue(helper, "default vertex format is the entity preset",
                graph.getSettings().vertexFormatElements().equals(VertexFormatPresets.ENTITY));
        assertTrue(helper, "default vertex format mode is quads",
                graph.getSettings().vertexFormatMode() == RenderTypeGraph.Settings.VertexFormatMode.QUADS);
        assertTrue(helper, "default blend is opaque",
                graph.getSettings().blend() == RenderTypeGraph.Settings.BlendMode.OPAQUE);
        assertTrue(helper, "default target is main",
                graph.getSettings().outputTarget() == RenderTypeGraph.Settings.OutputTarget.MAIN);
        helper.succeed();
    }

    public static void stageContextsAcceptExpectedBlocks(GameTestHelper helper) {
        VaryingStageNode vertex = new VaryingStageNode();
        FragmentStageNode fragment = new FragmentStageNode();

        assertTrue(helper, "vertex stage supports position output slot",
                vertex.getSupportBlocks().contains(VertexPositionBlock.class));
        assertTrue(helper, "vertex stage supports custom float interpolator",
                vertex.getSupportBlocks().contains(VaryingCustomFloatBlock.class));
        assertFalse(helper, "vertex stage no longer has a specialized color block",
                vertex.getSupportBlocks().stream().anyMatch(c -> c.getSimpleName().contains("VertexColorBlock")));
        assertTrue(helper, "vertex stage supports custom vec4 interpolator",
                vertex.getSupportBlocks().contains(VaryingCustomVec4Block.class));
        assertTrue(helper, "fragment stage supports base color output slot",
                fragment.getSupportBlocks().contains(FragmentBaseColorBlock.class));
        assertTrue(helper, "fragment stage supports alpha output slot",
                fragment.getSupportBlocks().contains(FragmentAlphaBlock.class));
        assertTrue(helper, "fragment stage supports alpha discard output slot",
                fragment.getSupportBlocks().contains(FragmentAlphaDiscardBlock.class));
        helper.succeed();
    }

    public static void stageNodesExposePipelinePorts(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel vertex = graph.getVertexStageModel();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel position = addBlock(graph, vertex, VertexPositionBlock.class);
        NodeModel customFloat = addBlock(graph, vertex, VaryingCustomFloatBlock.class);
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        NodeModel discard = addBlock(graph, fragment, FragmentAlphaDiscardBlock.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);

        assertTrue(helper, "vertex stage has no graph ports",
                vertex.getInputsById().isEmpty() && vertex.getOutputsById().isEmpty());
        assertTrue(helper, "fragment stage has no vertex stage input",
                !fragment.getInputsById().containsKey("vertexStage"));
        assertTrue(helper, "fragment stage has no graph ports",
                fragment.getInputsById().isEmpty() && fragment.getOutputsById().isEmpty());
        assertTrue(helper, "vertex position override input exists", position.getInputsById().containsKey("position"));
        assertEq(helper, "vertex position writes gl_Position vec4",
                RenderTypeGraphTypes.VEC4, position.getInputsById().get("position").getDataTypeHandle());
        assertTrue(helper, "vertex position has no varying output", position.getOutputsById().isEmpty());
        assertTrue(helper, "custom float input exists", customFloat.getInputsById().containsKey("value"));
        assertTrue(helper, "custom float varying output exists", customFloat.getOutputsById().containsKey("value"));
        assertTrue(helper, "fragment base color input exists", baseColor.getInputsById().containsKey("color"));
        assertEq(helper, "fragment base color is vec3",
                RenderTypeGraphTypes.VEC3, baseColor.getInputsById().get("color").getDataTypeHandle());
        assertTrue(helper, "fragment alpha input exists", alpha.getInputsById().containsKey("alpha"));
        assertTrue(helper, "fragment alpha discard cutoff input exists", discard.getInputsById().containsKey("cutoff"));
        assertTrue(helper, "fragment emission input exists", emission.getInputsById().containsKey("color"));
        helper.succeed();
    }

    public static void fixedStageModelsCannotBeDeleted(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel vertex = graph.getVertexStageModel();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel custom = addBlock(graph, vertex, VaryingCustomFloatBlock.class);

        assertFalse(helper, "graph rejects deleting fixed vertex stage",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(vertex))));
        assertFalse(helper, "graph model rejects deleting fixed vertex stage",
                graph.graphModel.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(vertex))));
        assertFalse(helper, "graph rejects deleting fixed fragment stage",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(fragment))));
        assertTrue(helper, "graph still allows deleting ordinary stage blocks",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(custom))));
        helper.succeed();
    }

    public static void resourcePersistsRenderTypeSettings(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        RenderTypeGraph.Settings settings = new RenderTypeGraph.Settings(
                VertexFormatPresets.BLOCK,
                RenderTypeGraph.Settings.VertexFormatMode.TRIANGLES,
                RenderTypeGraph.Settings.BlendMode.TRANSLUCENT,
                RenderTypeGraph.Settings.DepthTest.ALWAYS,
                false,
                false,
                RenderTypeGraph.Settings.OutputTarget.TRANSLUCENT,
                false,
                true
        );
        graph.setSettings(settings);

        var tag = RenderTypeGraphResource.INSTANCE.serializeGraph(graph);
        RenderTypeGraph restored = RenderTypeGraphResource.INSTANCE.deserializeGraph(tag);

        assertTrue(helper, "resource round-trips rendertype settings", restored.getSettings().equals(settings));
        helper.succeed();
    }

    public static void settingsToolWritesLoadedGraph(GameTestHelper helper) {
        assertTrue(helper, "rendertype resource uses custom graph view",
                RenderTypeGraphResource.INSTANCE.getGraphViewFactory().get() instanceof RenderTypeGraphView);

        RenderTypeGraph graph = new RenderTypeGraph();
        RenderTypeGraphView view = new RenderTypeGraphView();
        view.loadGraph(graph);
        RenderTypeGraph.Settings settings = new RenderTypeGraph.Settings(
                VertexFormatPresets.POSITION_COLOR_TEX,
                RenderTypeGraph.Settings.VertexFormatMode.LINES,
                RenderTypeGraph.Settings.BlendMode.ADDITIVE,
                RenderTypeGraph.Settings.DepthTest.NONE,
                false,
                true,
                RenderTypeGraph.Settings.OutputTarget.WEATHER,
                false,
                true
        );

        view.getSettingsTool().applySettings(settings);

        assertTrue(helper, "settings tool writes loaded graph", graph.getSettings().equals(settings));
        helper.succeed();
    }

    public static void newGraphContainsDefaultEntityShader(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel vertexColor = findNode(graph, VertexColorNode.class);
        NodeModel textureSample = findNode(graph, SamplerTexture2DNode.class);
        NodeModel entityColor = findNode(graph, MultiplyNode.class);
        NodeModel colorModulator = findNode(graph, DynamicTransformsUboNode.class);
        NodeModel modulatedColor = findSecondNode(graph, MultiplyNode.class);
        NodeModel fogSub = findSubgraphNode(graph);
        NodeModel split = findNode(graph, SplitNode.class);
        NodeModel baseColor = findBlock(fragment, FragmentBaseColorBlock.class);
        NodeModel alpha = findBlock(fragment, FragmentAlphaBlock.class);

        assertTrue(helper, "new graph contains vertex stage node",
                graph.getNodes().stream().anyMatch(VaryingStageNode.class::isInstance));
        assertTrue(helper, "new graph contains fragment stage node",
                graph.getNodes().stream().anyMatch(FragmentStageNode.class::isInstance));
        assertTrue(helper, "default vertex color node exists", vertexColor != null);
        assertTrue(helper, "default generic texture2D sample exists", textureSample != null);
        assertTrue(helper, "default texture sample uv is unconnected (defaults to mesh uv)",
                textureSample != null && !textureSample.getInputsById().get("uv").isConnected());
        assertTrue(helper, "default texture-sample sampler input is fed by a Sampler2D constant",
                textureSample != null && textureSample.getInputsById().get("sampler").isConnected());
        assertTrue(helper, "default entity color multiply exists", entityColor != null);
        assertTrue(helper, "default dynamic transforms UBO exists", colorModulator != null);
        assertTrue(helper, "default modulated color multiply exists", modulatedColor != null);
        assertTrue(helper, "default fog subgraph node exists", fogSub != null);
        assertTrue(helper, "fog UBO is inside the subgraph, not top-level",
                findNode(graph, FogUboNode.class) == null);
        assertTrue(helper, "apply_fog is inside the subgraph, not top-level",
                findNode(graph, ApplyFogNode.class) == null);
        assertTrue(helper, "fog subgraph node has 1 input (inColor; distances default inside)",
                fogSub != null && fogSub.getInputsById().size() == 1);
        assertTrue(helper, "fog subgraph node has 1 output", fogSub != null && fogSub.getOutputsById().size() == 1);
        assertTrue(helper, "default split node exists", split != null);
        assertTrue(helper, "default fragment base color block exists", baseColor != null);
        assertTrue(helper, "default fragment alpha block exists", alpha != null);

        assertTrue(helper, "default wires texture color into entity color multiply",
                isWired(textureSample, "color", entityColor, "a"));
        assertTrue(helper, "default wires vertex color node into entity color multiply",
                isWired(vertexColor, "out", entityColor, "b"));
        assertTrue(helper, "default wires entity color into color modulator multiply",
                isWired(entityColor, "out", modulatedColor, "a"));
        assertTrue(helper, "default wires ColorModulator into color modulator multiply",
                isWired(colorModulator, "ColorModulator", modulatedColor, "b"));
        assertTrue(helper, "default wires modulated color into fog subgraph",
                outputConnectsToAnyInput(modulatedColor, "out", fogSub));
        // The fogged colour feeds base color (rgb) directly; Split extracts its alpha.
        assertTrue(helper, "default wires fogged color into fragment base color",
                anyOutputConnectsTo(fogSub, baseColor, "color"));
        assertTrue(helper, "default wires fogged color into split",
                anyOutputConnectsTo(fogSub, split, "in"));
        assertTrue(helper, "default wires split a into fragment alpha",
                isWired(split, "a", alpha, "alpha"));
        helper.succeed();
    }

    public static void shaderVectorValuesAreWireCompatible(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel split = addNode(graph, SplitNode.class);
        NodeModel vec3 = addNode(graph, Vec3Node.class);
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        wire(graph, split.getInputsById().get("in"), vec3.getOutputsById().get("out"));
        wire(graph, baseColor.getInputsById().get("color"), split.getOutputsById().get("r"));

        assertTrue(helper, "vec3 can connect to split input accepting shader vector values",
                split.getInputsById().get("in").getConnectedPorts().contains(vec3.getOutputsById().get("out")));
        assertTrue(helper, "float can connect to vec3 base color through shader vector compatibility",
                baseColor.getInputsById().get("color").getConnectedPorts().contains(split.getOutputsById().get("r")));
        helper.succeed();
    }

    public static void compileDefaultEntityShader(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        CompiledShaderGraph compiled = new ShaderGraphCompiler(graph).compile();
        String vsh = compiled.vertexSource();
        String fsh = compiled.fragmentSource();

        // Vertex shader
        assertTrue(helper, "vsh declares version", vsh.startsWith("#version 330"));
        assertTrue(helper, "vsh declares Position attribute", vsh.contains("in vec3 Position;"));
        assertTrue(helper, "vsh writes gl_Position", vsh.contains("gl_Position ="));
        // gl_Position + fog distance transform Position + ModelOffset (matching vanilla block.vsh).
        assertTrue(helper, "vsh adds ModelOffset to Position", vsh.contains("Position + ModelOffset"));
        assertTrue(helper, "vsh outputs vertexColor varying", vsh.contains("out vec4 vertexColor;"));
        assertTrue(helper, "vsh outputs uv0 varying", vsh.contains("out vec2 uv0;"));
        assertTrue(helper, "vsh outputs spherical distance varying", vsh.contains("out float sphericalVertexDistance;"));
        assertTrue(helper, "vsh assigns uv0 from UV0", vsh.contains("uv0 = UV0;"));
        assertTrue(helper, "vsh imports projection", vsh.contains("#moj_import <minecraft:projection.glsl>"));

        // Fragment shader
        assertTrue(helper, "fsh declares version", fsh.startsWith("#version 330"));
        assertTrue(helper, "fsh declares fragColor output", fsh.contains("out vec4 fragColor;"));
        assertTrue(helper, "fsh reads vertexColor varying", fsh.contains("in vec4 vertexColor;"));
        assertTrue(helper, "fsh reads uv0 varying", fsh.contains("in vec2 uv0;"));
        assertTrue(helper, "fsh samples the texture constant", fsh.contains("texture(kg_tex"));
        assertTrue(helper, "fsh declares the texture-constant sampler uniform", fsh.contains("uniform sampler2D kg_tex"));
        assertTrue(helper, "fsh applies fog", fsh.contains("apply_fog("));
        // Unified-UBO policy: the fog functions are inlined (FogGlsl) and the parameters come from the
        // KG_Fog slice-view of Minecraft's Fog buffer — no fog #moj_import in the fragment anymore.
        assertTrue(helper, "fsh inlines the fog functions", fsh.contains("vec4 apply_fog("));
        assertTrue(helper, "fsh reads the KG_Fog slice-view", fsh.contains("kg_fog."));
        assertFalse(helper, "fsh no longer imports fog", fsh.contains("#moj_import <minecraft:fog.glsl>"));
        assertTrue(helper, "fsh imports dynamictransforms", fsh.contains("#moj_import <minecraft:dynamictransforms.glsl>"));
        assertTrue(helper, "fsh writes fragColor", fsh.contains("fragColor = vec4("));

        // Pipeline metadata
        assertTrue(helper, "pipeline binds DynamicTransforms",
                compiled.builtinUniforms().contains("DynamicTransforms"));
        assertTrue(helper, "pipeline binds Fog", compiled.builtinUniforms().contains("Fog"));
        assertTrue(helper, "layout registers the texture-constant sampler",
                compiled.layout().samplers().stream().anyMatch(s -> s.startsWith("kg_tex")));
        helper.succeed();
    }

    /** The composed vertex format drives the generated {@code in} attribute declarations: a Block-preset
     * graph declares exactly Position/Color/UV0/UV2 and omits UV1/Normal. */
    public static void compileRespectsComposedVertexFormat(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        var s = graph.getSettings();
        graph.setSettings(new RenderTypeGraph.Settings(
                VertexFormatPresets.BLOCK, s.vertexFormatMode(), s.blend(), s.depthTest(),
                s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload()));

        String vsh = new ShaderGraphCompiler(graph).compile().vertexSource();
        assertTrue(helper, "block vsh declares Position", vsh.contains("in vec3 Position;"));
        assertTrue(helper, "block vsh declares Color", vsh.contains("in vec4 Color;"));
        assertTrue(helper, "block vsh declares UV0", vsh.contains("in vec2 UV0;"));
        assertTrue(helper, "block vsh declares UV2", vsh.contains("in ivec2 UV2;"));
        assertTrue(helper, "block vsh declares Normal", vsh.contains("in vec3 Normal;"));
        assertFalse(helper, "block vsh omits UV1", vsh.contains("in ivec2 UV1;"));
        helper.succeed();
    }

    /** A VertexAttributeInputNode whose chosen element is absent from the composed format is flagged via the
     * GraphLogger (keyed by the node), and not flagged once the element is present. */
    public static void vertexFormatValidationFlagsMissingElement(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        // Non-orphan so it appears in getNodeModels(), which the validation iterates (orphan nodes don't).
        NodeModel attr = addRegisteredNode(graph, VertexAttributeInputNode.class); // default element: position
        RenderTypeGraph.Settings s = graph.getSettings();

        // Format WITHOUT position -> the node's required element is missing.
        graph.setSettings(new RenderTypeGraph.Settings(
                List.of("color", "uv0"), s.vertexFormatMode(), s.blend(), s.depthTest(),
                s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload()));
        GraphLogger missing = new GraphLogger();
        graph.onGraphChanged(missing);
        assertTrue(helper, "missing element is flagged for the node",
                missing.getEntries().stream().anyMatch(e -> e.context() == attr));

        // Format WITH position (Entity preset) -> no error for the node.
        graph.setSettings(new RenderTypeGraph.Settings(
                VertexFormatPresets.ENTITY, s.vertexFormatMode(), s.blend(), s.depthTest(),
                s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload()));
        GraphLogger present = new GraphLogger();
        graph.onGraphChanged(present);
        assertTrue(helper, "present element is not flagged for the node",
                present.getEntries().stream().noneMatch(e -> e.context() == attr));
        helper.succeed();
    }

    /** Removing a vertex element a node DEFAULT references (the vertex Color block defaults to
     * minecraft_mix_light(Normal, Color)) degrades to a safe constant — the shader stays valid (no Color
     * attribute / undefined var), the substitution is recorded, and onGraphChanged logs a warning. */
    public static void vertexElementDefaultFallsBackAndWarns(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        RenderTypeGraph.Settings s = graph.getSettings();
        var without = new java.util.ArrayList<>(s.vertexFormatElements());
        without.remove("color");
        graph.setSettings(new RenderTypeGraph.Settings(without, s.vertexFormatMode(), s.blend(), s.depthTest(),
                s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload()));

        CompiledShaderGraph compiled = new ShaderGraphCompiler(graph).compile();
        assertTrue(helper, "removed Color is reported as a substituted attribute",
                compiled.missingAttributes().contains("Color"));
        assertFalse(helper, "vsh no longer declares the Color attribute",
                compiled.vertexSource().contains("in vec4 Color;"));

        GraphLogger logger = new GraphLogger();
        graph.onGraphChanged(logger);
        assertTrue(helper, "a warning is logged for the defaulted attribute",
                logger.getEntries().stream().anyMatch(e -> e.level() == GraphLogger.Level.WARNING));
        helper.succeed();
    }

    /** The built-in preview contents build the expected neutral geometry. */
    public static void previewContentsBuildGeometry(GameTestHelper helper) {
        var quad = new com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder();
        com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents.QUAD.build(quad);
        assertTrue(helper, "quad content is one quad", quad.quads.size() == 1 && quad.tris.isEmpty());

        var cube = new com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder();
        com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents.CUBE.build(cube);
        assertTrue(helper, "cube content is six quads", cube.quads.size() == 6 && cube.tris.isEmpty());

        var sphere = new com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder();
        com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents.SPHERE.build(sphere);
        assertTrue(helper, "sphere content has quad bands + triangle caps",
                !sphere.quads.isEmpty() && !sphere.tris.isEmpty());

        // Custom-element adaptation: a vertex carries per-element values for mod-registered elements,
        // and copies preserve them (so a content can supply data a custom writer reads).
        var pv = new com.lowdragmc.kilagraph.rendertype.preview.PreviewVertex();
        pv.setAttribute("mod_tangent", 1f, 0f, 0f);
        var cp = pv.copy();
        assertTrue(helper, "custom attribute round-trips through copy",
                cp.getAttribute("mod_tangent") != null && cp.getAttribute("mod_tangent")[0] == 1f);
        assertTrue(helper, "unset custom attribute is null", pv.getAttribute("nope") == null);
        helper.succeed();
    }

    /** The tessellator emits the right vertex count per primitive mode, and the triangle-strip stitch
     * preserves winding (every reconstructed triangle stays CCW). */
    public static void previewTessellatorMatchesMode(GameTestHelper helper) {
        var mb = new com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder();
        com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents.CUBE.build(mb); // 6 quads, 24 edges

        var QUADS = RenderTypeGraph.Settings.VertexFormatMode.QUADS;
        var TRIANGLES = RenderTypeGraph.Settings.VertexFormatMode.TRIANGLES;
        var LINES = RenderTypeGraph.Settings.VertexFormatMode.LINES;
        var LINE_STRIP = RenderTypeGraph.Settings.VertexFormatMode.LINE_STRIP;
        var stream = com.lowdragmc.kilagraph.rendertype.preview.PreviewTessellator.toStream(mb, QUADS);
        assertEq(helper, "QUADS: 6 quads -> 24 verts", 24, stream.size());
        assertEq(helper, "TRIANGLES: 12 tris -> 36 verts", 36,
                com.lowdragmc.kilagraph.rendertype.preview.PreviewTessellator.toStream(mb, TRIANGLES).size());
        assertEq(helper, "LINES: 24 edges -> 96 verts", 96,
                com.lowdragmc.kilagraph.rendertype.preview.PreviewTessellator.toStream(mb, LINES).size());
        assertEq(helper, "LINE_STRIP: 24 edges -> 48 verts", 48,
                com.lowdragmc.kilagraph.rendertype.preview.PreviewTessellator.toStream(mb, LINE_STRIP).size());

        // Triangle-strip winding: two CCW (+Z) triangles in XY -> stitched strip -> all reconstructed
        // (non-degenerate) triangles must remain CCW (+Z cross product).
        var flat = new com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder();
        flat.tri(pv(0, 0), pv(1, 0), pv(0, 1));   // CCW
        flat.tri(pv(2, 0), pv(3, 0), pv(2, 1));   // CCW, separated
        var strip = com.lowdragmc.kilagraph.rendertype.preview.PreviewTessellator.toStream(
                flat, RenderTypeGraph.Settings.VertexFormatMode.TRIANGLE_STRIP);
        int real = 0;
        for (int i = 0; i + 2 < strip.size(); i++) {
            var a = strip.get(i); var b = strip.get(i + 1); var c = strip.get(i + 2);
            if (i % 2 == 1) { var t = a; a = b; b = t; } // GL strip flips odd triangles
            double cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
            if (Math.abs(cross) < 1e-6) continue;       // degenerate bridge triangle
            real++;
            assertTrue(helper, "stitched strip keeps CCW winding", cross > 0);
        }
        assertEq(helper, "strip reconstructs both triangles", 2, real);
        helper.succeed();
    }

    private static com.lowdragmc.kilagraph.rendertype.preview.PreviewVertex pv(float x, float y) {
        return new com.lowdragmc.kilagraph.rendertype.preview.PreviewVertex(x, y, 0, 0, 0, 0, 0, 1);
    }

    private static NodeModel findNode(RenderTypeGraph graph, Class<? extends Node> nodeClass) {
        return graph.graphModel.getNodeModels().stream()
                .filter(NodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .filter(node -> isCustomNode(node, nodeClass))
                .findFirst()
                .orElse(null);
    }

    private static NodeModel findSecondNode(RenderTypeGraph graph, Class<? extends Node> nodeClass) {
        return graph.graphModel.getNodeModels().stream()
                .filter(NodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .filter(node -> isCustomNode(node, nodeClass))
                .skip(1)
                .findFirst()
                .orElse(null);
    }

    private static NodeModel findBlock(NodeModel context, Class<? extends Node> blockClass) {
        if (!(context instanceof ContextNodeModel contextNode)) return null;
        return contextNode.getBlocks().stream()
                .filter(NodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .filter(block -> isCustomNode(block, blockClass))
                .findFirst()
                .orElse(null);
    }

    private static boolean isCustomNode(NodeModel model, Class<? extends Node> nodeClass) {
        return model instanceof ICustomNodeModel customNodeModel
                && nodeClass.isInstance(customNodeModel.getNode());
    }

    private static boolean isWired(NodeModel fromNode, String fromPort, NodeModel toNode, String toPort) {
        if (fromNode == null || toNode == null) return false;
        var from = fromNode.getOutputsById().get(fromPort);
        var to = toNode.getInputsById().get(toPort);
        if (from == null || to == null) return false;
        return from.getConnectedPorts().contains(to);
    }

    private static NodeModel findSubgraphNode(RenderTypeGraph graph) {
        return graph.graphModel.getNodeModels().stream()
                .filter(com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel.class::isInstance)
                .map(NodeModel.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** Whether {@code fromNode.fromPort} connects to ANY input port of {@code subNode} (uuid-keyed). */
    private static boolean outputConnectsToAnyInput(NodeModel fromNode, String fromPort, NodeModel subNode) {
        if (fromNode == null || subNode == null) return false;
        var from = fromNode.getOutputsById().get(fromPort);
        if (from == null) return false;
        return subNode.getInputsById().values().stream().anyMatch(p -> from.getConnectedPorts().contains(p));
    }

    /** Whether ANY output port of {@code subNode} connects to {@code toNode.toPort}. */
    private static boolean anyOutputConnectsTo(NodeModel subNode, NodeModel toNode, String toPort) {
        if (subNode == null || toNode == null) return false;
        var to = toNode.getInputsById().get(toPort);
        if (to == null) return false;
        return subNode.getOutputsById().values().stream().anyMatch(p -> p.getConnectedPorts().contains(to));
    }
}
