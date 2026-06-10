package com.lowdragmc.kilagraph.test.gametest.rendertypegraph;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.editor.RenderTypeGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
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
import com.lowdragmc.kilagraph.rendertype.nodes.math.ShaderFloatMultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderSplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderVec3MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderVec4MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.TotalFogValueNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomFloatBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec4Block;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingVertexColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCylindricalVertexDistanceBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexPositionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingSphericalVertexDistanceBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingStageNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingTexCoordBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec3Node;
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
    private static final String CHANGE_VERSION = "rendertype_change_version";

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
        KGGameTests.registerFunction(CHANGE_VERSION,
                RenderTypeGraphGameTest::onGraphChangedBumpsChangeVersion);
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
        KGGameTests.registerFunctionTest(event, CHANGE_VERSION,
                KGGameTests.functionKey(CHANGE_VERSION), d);
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
        assertTrue(helper, "supports vertex color varying block", nodes.contains(VaryingVertexColorBlock.class));
        assertTrue(helper, "supports vertex spherical fog distance block", nodes.contains(VaryingSphericalVertexDistanceBlock.class));
        assertTrue(helper, "supports vertex cylindrical fog distance block", nodes.contains(VaryingCylindricalVertexDistanceBlock.class));
        assertTrue(helper, "supports vertex tex coord varying block", nodes.contains(VaryingTexCoordBlock.class));
        assertTrue(helper, "supports custom float interpolator", nodes.contains(VaryingCustomFloatBlock.class));
        assertTrue(helper, "supports custom vec4 interpolator", nodes.contains(VaryingCustomVec4Block.class));
        assertTrue(helper, "does not support old specialized texture sample node",
                nodes.stream().noneMatch(node -> node.getSimpleName().equals("FragmentTextureSampleNode")));
        assertTrue(helper, "supports generic sampler texture2D node", nodes.contains(SamplerTexture2DNode.class));
        assertTrue(helper, "supports texture node", nodes.contains(TextureNode.class));
        assertTrue(helper, "supports overlay texture node", nodes.contains(OverlayTextureNode.class));
        assertTrue(helper, "supports lightmap texture node", nodes.contains(LightMapTextureNode.class));
        assertTrue(helper, "supports shader split node", nodes.contains(ShaderSplitNode.class));
        assertTrue(helper, "supports vec3 construct node", nodes.contains(Vec3Node.class));
        assertTrue(helper, "supports float multiply node", nodes.contains(ShaderFloatMultiplyNode.class));
        assertTrue(helper, "supports vec3 multiply node", nodes.contains(ShaderVec3MultiplyNode.class));
        assertTrue(helper, "supports vec4 multiply node", nodes.contains(ShaderVec4MultiplyNode.class));
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
        assertTrue(helper, "default vertex format preset is entity",
                graph.getSettings().vertexFormatPreset() == RenderTypeGraph.Settings.VertexFormatPreset.ENTITY);
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
        assertTrue(helper, "vertex stage supports color output slot",
                vertex.getSupportBlocks().contains(VaryingVertexColorBlock.class));
        assertTrue(helper, "vertex stage supports spherical fog distance output slot",
                vertex.getSupportBlocks().contains(VaryingSphericalVertexDistanceBlock.class));
        assertTrue(helper, "vertex stage supports cylindrical fog distance output slot",
                vertex.getSupportBlocks().contains(VaryingCylindricalVertexDistanceBlock.class));
        assertTrue(helper, "vertex stage supports tex coord output slot",
                vertex.getSupportBlocks().contains(VaryingTexCoordBlock.class));
        assertTrue(helper, "vertex stage supports custom float interpolator",
                vertex.getSupportBlocks().contains(VaryingCustomFloatBlock.class));
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
        NodeModel color = addBlock(graph, vertex, VaryingVertexColorBlock.class);
        NodeModel sphericalFog = addBlock(graph, vertex, VaryingSphericalVertexDistanceBlock.class);
        NodeModel texCoord = addBlock(graph, vertex, VaryingTexCoordBlock.class);
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
        assertTrue(helper, "vertex color input exists", color.getInputsById().containsKey("color"));
        assertTrue(helper, "vertex color varying output exists", color.getOutputsById().containsKey("color"));
        assertTrue(helper, "spherical fog distance input exists", sphericalFog.getInputsById().containsKey("distance"));
        assertTrue(helper, "spherical fog distance varying output exists", sphericalFog.getOutputsById().containsKey("distance"));
        assertTrue(helper, "tex coord input exists", texCoord.getInputsById().containsKey("texCoord"));
        assertTrue(helper, "tex coord varying output exists", texCoord.getOutputsById().containsKey("texCoord"));
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
        NodeModel color = addBlock(graph, vertex, VaryingVertexColorBlock.class);

        assertFalse(helper, "graph rejects deleting fixed vertex stage",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(vertex))));
        assertFalse(helper, "graph model rejects deleting fixed vertex stage",
                graph.graphModel.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(vertex))));
        assertFalse(helper, "graph rejects deleting fixed fragment stage",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(fragment))));
        assertTrue(helper, "graph still allows deleting ordinary stage blocks",
                graph.canExecuteCommand(new GraphCommands.DeleteElementsCommand(List.of(color))));
        helper.succeed();
    }

    public static void resourcePersistsRenderTypeSettings(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        RenderTypeGraph.Settings settings = new RenderTypeGraph.Settings(
                RenderTypeGraph.Settings.VertexFormatPreset.BLOCK,
                RenderTypeGraph.Settings.VertexFormatMode.TRIANGLES,
                RenderTypeGraph.Settings.BlendMode.ALPHA,
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
                RenderTypeGraph.Settings.VertexFormatPreset.POSITION_COLOR_TEX,
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
        NodeModel vertex = graph.getVertexStageModel();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel vertexColor = findBlock(vertex, VaryingVertexColorBlock.class);
        NodeModel vertexTexCoord = findBlock(vertex, VaryingTexCoordBlock.class);
        NodeModel sphericalDistance = findBlock(vertex, VaryingSphericalVertexDistanceBlock.class);
        NodeModel cylindricalDistance = findBlock(vertex, VaryingCylindricalVertexDistanceBlock.class);
        NodeModel textureSample = findNode(graph, SamplerTexture2DNode.class);
        NodeModel entityColor = findNode(graph, ShaderVec4MultiplyNode.class);
        NodeModel colorModulator = findNode(graph, DynamicTransformsUboNode.class);
        NodeModel modulatedColor = findSecondNode(graph, ShaderVec4MultiplyNode.class);
        NodeModel fogSub = findSubgraphNode(graph);
        NodeModel split = findNode(graph, ShaderSplitNode.class);
        NodeModel baseColorVec = findNode(graph, Vec3Node.class);
        NodeModel baseColor = findBlock(fragment, FragmentBaseColorBlock.class);
        NodeModel alpha = findBlock(fragment, FragmentAlphaBlock.class);

        assertTrue(helper, "new graph contains vertex stage node",
                graph.getNodes().stream().anyMatch(VaryingStageNode.class::isInstance));
        assertTrue(helper, "new graph contains fragment stage node",
                graph.getNodes().stream().anyMatch(FragmentStageNode.class::isInstance));
        assertTrue(helper, "default vertex color varying exists", vertexColor != null);
        assertTrue(helper, "default vertex tex coord varying exists", vertexTexCoord != null);
        assertTrue(helper, "default spherical fog varying exists", sphericalDistance != null);
        assertTrue(helper, "default cylindrical fog varying exists", cylindricalDistance != null);
        assertTrue(helper, "default generic texture2D sample exists", textureSample != null);
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
        assertTrue(helper, "fog subgraph node has 3 inputs (inColor + 2 distances)",
                fogSub != null && fogSub.getInputsById().size() == 3);
        assertTrue(helper, "fog subgraph node has 1 output", fogSub != null && fogSub.getOutputsById().size() == 1);
        assertTrue(helper, "default split node exists", split != null);
        assertTrue(helper, "default vec3 base color node exists", baseColorVec != null);
        assertTrue(helper, "default fragment base color block exists", baseColor != null);
        assertTrue(helper, "default fragment alpha block exists", alpha != null);

        assertTrue(helper, "default wires texCoord into texture sample",
                isWired(vertexTexCoord, "texCoord", textureSample, "uv"));
        assertTrue(helper, "default wires texture color into entity color multiply",
                isWired(textureSample, "color", entityColor, "a"));
        assertTrue(helper, "default wires vertex color into entity color multiply",
                isWired(vertexColor, "color", entityColor, "b"));
        assertTrue(helper, "default wires entity color into color modulator multiply",
                isWired(entityColor, "out", modulatedColor, "a"));
        assertTrue(helper, "default wires ColorModulator into color modulator multiply",
                isWired(colorModulator, "ColorModulator", modulatedColor, "b"));
        assertTrue(helper, "default wires modulated color into fog subgraph",
                outputConnectsToAnyInput(modulatedColor, "out", fogSub));
        assertTrue(helper, "default wires spherical distance into fog subgraph",
                outputConnectsToAnyInput(sphericalDistance, "distance", fogSub));
        assertTrue(helper, "default wires cylindrical distance into fog subgraph",
                outputConnectsToAnyInput(cylindricalDistance, "distance", fogSub));
        assertTrue(helper, "default wires fogged color into split",
                anyOutputConnectsTo(fogSub, split, "in"));
        assertTrue(helper, "default wires split r into base color x",
                isWired(split, "r", baseColorVec, "x"));
        assertTrue(helper, "default wires split g into base color y",
                isWired(split, "g", baseColorVec, "y"));
        assertTrue(helper, "default wires split b into base color z",
                isWired(split, "b", baseColorVec, "z"));
        assertTrue(helper, "default wires vec3 into fragment base color",
                isWired(baseColorVec, "out", baseColor, "color"));
        assertTrue(helper, "default wires split a into fragment alpha",
                isWired(split, "a", alpha, "alpha"));
        helper.succeed();
    }

    public static void shaderVectorValuesAreWireCompatible(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel split = addNode(graph, ShaderSplitNode.class);
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
        assertTrue(helper, "vsh outputs vertexColor varying", vsh.contains("out vec4 vertexColor;"));
        assertTrue(helper, "vsh outputs texCoord0 varying", vsh.contains("out vec2 texCoord0;"));
        assertTrue(helper, "vsh outputs spherical distance varying", vsh.contains("out float sphericalVertexDistance;"));
        assertTrue(helper, "vsh assigns texCoord0 from UV0", vsh.contains("texCoord0 = UV0;"));
        assertTrue(helper, "vsh imports projection", vsh.contains("#moj_import <minecraft:projection.glsl>"));

        // Fragment shader
        assertTrue(helper, "fsh declares version", fsh.startsWith("#version 330"));
        assertTrue(helper, "fsh declares fragColor output", fsh.contains("out vec4 fragColor;"));
        assertTrue(helper, "fsh reads vertexColor varying", fsh.contains("in vec4 vertexColor;"));
        assertTrue(helper, "fsh reads texCoord0 varying", fsh.contains("in vec2 texCoord0;"));
        assertTrue(helper, "fsh samples the texture constant", fsh.contains("texture(kg_tex"));
        assertTrue(helper, "fsh declares the texture-constant sampler uniform", fsh.contains("uniform sampler2D kg_tex"));
        assertTrue(helper, "fsh applies fog", fsh.contains("apply_fog("));
        assertTrue(helper, "fsh imports fog", fsh.contains("#moj_import <minecraft:fog.glsl>"));
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
