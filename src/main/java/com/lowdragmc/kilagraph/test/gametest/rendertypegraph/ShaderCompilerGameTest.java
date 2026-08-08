package com.lowdragmc.kilagraph.test.gametest.rendertypegraph;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.AbsNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.ClampNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.CrossNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.DotNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.FresnelNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.SphereMaskNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.ApplyFogNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.TotalFogValueNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogSphericalDistanceNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fog.FogUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.GlobalsUboNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomFloatBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexModelNormalBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexModelPositionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexPositionBlock;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.CombineNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.FlipNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.SwizzleNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.SplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.TilingAndOffsetNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.RotateNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.TwirlNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.SpherizeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.RadialShearNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.PolarCoordinatesNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.FlipbookNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.TriplanarNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.ScreenPositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.SceneColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.SceneDepthNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.CameraNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.BranchNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.CompareNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.AndNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.OrNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.NotNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.ExpressionNode;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.editor.ExportShaderFunction;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec2Block;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec2Node;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaDiscardBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentEmissionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.LengthNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation.LerpNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.MinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.NormalizeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.PowNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.LightMapTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.OverlayTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.VertexColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.UVNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.PositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.NormalNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.ViewDirectionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.VertexAttributeInputNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.VertexIdNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.InstanceIdNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.fragment.FrontFacingNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.fragment.FragmentCoordinateNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.fragment.PrimitiveIdNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.AddNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4ConstructNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4SplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4TransformNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.derivative.DDXNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.SinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.TilingAndOffsetNode;
import com.lowdragmc.kilagraph.rendertype.nodes.constant.TimeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.constant.GradientNode;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.gradient.SampleGradientNode;
import com.lowdragmc.kilagraph.rendertype.nodes.constant.CurveNode;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.curve.SampleCurveNode;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.curve.ExplicitCubicBezierCurve2;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec2Node;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec3Node;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec4Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec4Block;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec3Block;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import net.minecraft.core.Holder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addRegisteredNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Rigorous compiler behaviour tests: builds graphs on top of the default entity shader and asserts
 * the generated GLSL's structure/semantics — type conversion, memoisation/dedup, the vsh→fsh
 * varying boundary, fragment semantic blocks, sampler handling, and constant fallback.
 *
 * <p>GPU-validity (driver compilation) is not asserted here — that is verified at runtime via
 * {@code GpuDevice.precompilePipeline} (requires a client). These tests pin the generated source.</p>
 */
public final class ShaderCompilerGameTest {
    private static final String DEDUP = "rendertype_compile_dedup";
    private static final String EMISSION = "rendertype_compile_emission";
    private static final String ALPHA_DISCARD = "rendertype_compile_alpha_discard";
    private static final String CUSTOM_VARYING = "rendertype_compile_custom_varying";
    private static final String FLOAT_BROADCAST = "rendertype_compile_float_broadcast";
    private static final String VEC4_TO_VEC3_SWIZZLE = "rendertype_compile_vec4_to_vec3";
    private static final String SAMPLER_NOT_HOISTED = "rendertype_compile_sampler_not_hoisted";
    private static final String ENGINE_TIME = "rendertype_compile_engine_time";
    private static final String NODE_PREVIEW = "rendertype_compile_node_preview";
    private static final String STAGE_AFFINITY = "rendertype_compile_stage_affinity";
    private static final String VAR_UNIFORM_VS_INLINE = "rendertype_compile_var_uniform_vs_inline";
    private static final String VAR_SAMPLER = "rendertype_compile_var_sampler";
    private static final String VAR_COLOR = "rendertype_compile_var_color";
    private static final String VAR_HDR_COLOR = "rendertype_compile_var_hdr_color";
    private static final String MIX_LIGHT_DEFAULT = "rendertype_compile_mix_light_default";
    private static final String UNIFORM_FIELD_MAP = "rendertype_compile_uniform_field_map";
    private static final String MISSING_SAMPLER_FALLBACK = "rendertype_compile_missing_sampler";
    private static final String TEXTURE_NODE = "rendertype_compile_texture_node";
    private static final String OVERLAY_LIGHTMAP = "rendertype_compile_overlay_lightmap";
    private static final String SAMPLER_VALUE_CODEC = "rendertype_compile_sampler_value_codec";
    private static final String WIRE_PORTAL = "rendertype_compile_wire_portal";
    private static final String MATH_NODES = "rendertype_compile_math_nodes";
    private static final String VECTOR_NODES = "rendertype_compile_vector_nodes";
    private static final String VERTEX_FORMAT_INPUTS = "rendertype_compile_vertex_format_inputs";
    private static final String FRAGMENT_INPUTS = "rendertype_compile_fragment_inputs";
    private static final String DYNAMIC_WIDTH = "rendertype_compile_dynamic_width";
    private static final String MATRIX_NODES = "rendertype_compile_matrix_nodes";
    private static final String DERIVATIVE_NODES = "rendertype_compile_derivative_nodes";
    private static final String PREVIEW_VERTEX_ATTR = "rendertype_compile_preview_vertex_attr";
    private static final String EXTRA_MATH_NODES = "rendertype_compile_extra_math_nodes";
    private static final String TRANSFORM_NODE = "rendertype_compile_transform_node";
    private static final String CAMERA_NODE = "rendertype_compile_camera_node";
    private static final String EXP_LOG_BASE = "rendertype_compile_exp_log_base";
    private static final String KG_TRANSFORMS_UBO = "rendertype_compile_kg_transforms_ubo";
    private static final String WORLD_GRID = "rendertype_compile_world_grid";
    private static final String PROCEDURAL_NODES = "rendertype_compile_procedural_nodes";
    private static final String ARTISTIC_NODES = "rendertype_compile_artistic_nodes";
    private static final String FRESNEL_DEFAULTS = "rendertype_compile_fresnel_defaults";
    private static final String FOG_DISTANCE_VERTEX_ONLY = "rendertype_compile_fog_distance_vertex_only";
    private static final String FOG_PARAM_DEFAULTS = "rendertype_compile_fog_param_defaults";
    private static final String SPHERE_MASK_COORDS = "rendertype_compile_sphere_mask_coords";
    private static final String CHANNEL_NODES = "rendertype_compile_channel_nodes";
    private static final String UV_NODES = "rendertype_compile_uv_nodes";
    private static final String UV_CHANNEL = "rendertype_compile_uv_channel";
    private static final String UV_CHANNEL_CODEC = "rendertype_compile_uv_channel_codec";
    private static final String UV_PREVIEW = "rendertype_compile_uv_preview";
    private static final String SCENE_NODES = "rendertype_compile_scene_nodes";
    private static final String SCENE_SAMPLER_DEFAULT = "rendertype_compile_scene_sampler_default";
    private static final String SCREEN_POSITION_MODES = "rendertype_compile_screen_position_modes";
    private static final String CAMERA_NEW_OUTPUTS = "rendertype_compile_camera_new_outputs";
    private static final String SCENE_PREVIEW_UV = "rendertype_compile_scene_preview_uv";
    private static final String SCREEN_POSITION_PREVIEW = "rendertype_compile_screen_position_preview";
    private static final String SCREEN_POSITION_RAW_EYE = "rendertype_compile_screen_position_raw_eye";
    private static final String PREVIEW_OPAQUE_ALPHA = "rendertype_compile_preview_opaque_alpha";
    private static final String BRANCH_SELECT = "rendertype_compile_branch_select";
    private static final String COMPARE_LOGIC = "rendertype_compile_compare_logic";
    private static final String EXPRESSION_NODE = "rendertype_compile_expression_node";
    private static final String EXPRESSION_VALIDATION = "rendertype_compile_expression_validation";
    private static final String EXPORT_FUNCTION = "rendertype_compile_export_function";
    private static final String BUILTIN_FRAG_KEYWORDS = "rendertype_compile_builtin_frag_keywords";
    private static final String BUILTIN_VERTEX_IDS = "rendertype_compile_builtin_vertex_ids";
    private static final String POSITION_NORMAL_SPACES = "rendertype_compile_position_normal_spaces";
    private static final String INPUT_NODES_VERTEX_STAGE = "rendertype_compile_input_nodes_vertex_stage";
    private static final String VIEW_DIRECTION_NORMALIZE = "rendertype_compile_view_direction_normalize";
    private static final String GRADIENT_NODES = "rendertype_compile_gradient_nodes";
    private static final String GRADIENT_VARIABLE = "rendertype_compile_gradient_variable";
    private static final String GRADIENT_VALUE_CODEC = "rendertype_compile_gradient_value_codec";
    private static final String CURVE_NODES = "rendertype_compile_curve_nodes";
    private static final String CURVE_VARIABLE = "rendertype_compile_curve_variable";
    private static final String CURVE_VALUE_CODEC = "rendertype_compile_curve_value_codec";
    private static final String VERTEX_MODEL_IDENTITY = "rendertype_compile_vertex_model_identity";
    private static final String VERTEX_MODEL_BLOCKS = "rendertype_compile_vertex_model_blocks";
    private static final String VERTEX_MODEL_LEGACY = "rendertype_compile_vertex_model_legacy";
    private static final String INJECTION_GEOMETRY = "rendertype_injection_geometry_nodes";
    private static final String INJECTION_UNIVERSAL = "rendertype_injection_all_nodes";
    private static final String INJECTION_GATE = "rendertype_injection_blacklist_gate";

    private ShaderCompilerGameTest() {}

    public static void registerFunctions() {
        KGGameTests.registerFunction(DEDUP, ShaderCompilerGameTest::sharedSubexpressionCompiledOnce);
        KGGameTests.registerFunction(EMISSION, ShaderCompilerGameTest::emissionBlockAddsToBaseColor);
        KGGameTests.registerFunction(ALPHA_DISCARD, ShaderCompilerGameTest::alphaDiscardEmitsDiscard);
        KGGameTests.registerFunction(CUSTOM_VARYING, ShaderCompilerGameTest::customInterpolatorCrossesStages);
        KGGameTests.registerFunction(FLOAT_BROADCAST, ShaderCompilerGameTest::floatBroadcastsToVec3);
        KGGameTests.registerFunction(VEC4_TO_VEC3_SWIZZLE, ShaderCompilerGameTest::vec4SwizzlesToVec3);
        KGGameTests.registerFunction(SAMPLER_NOT_HOISTED, ShaderCompilerGameTest::samplerIsNotHoisted);
        KGGameTests.registerFunction(ENGINE_TIME, ShaderCompilerGameTest::timeNodeUsesEngineGlobals);
        KGGameTests.registerFunction(NODE_PREVIEW, ShaderCompilerGameTest::previewCompilesPortSubgraph);
        KGGameTests.registerFunction(STAGE_AFFINITY, ShaderCompilerGameTest::stageAffinityFlagsMisuse);
        KGGameTests.registerFunction(VAR_UNIFORM_VS_INLINE, ShaderCompilerGameTest::variableScopeDrivesUniformVsInline);
        KGGameTests.registerFunction(VAR_SAMPLER, ShaderCompilerGameTest::samplerVariableBecomesUniform);
        KGGameTests.registerFunction(VAR_COLOR, ShaderCompilerGameTest::colorVariableCompilesToVec4Uniform);
        KGGameTests.registerFunction(VAR_HDR_COLOR, ShaderCompilerGameTest::hdrColorVariableCompilesToPremultipliedVec4Uniform);
        KGGameTests.registerFunction(MIX_LIGHT_DEFAULT, ShaderCompilerGameTest::unconnectedVertexColorDefaultsToMixLight);
        KGGameTests.registerFunction(UNIFORM_FIELD_MAP, ShaderCompilerGameTest::uniformFieldMappingExposesVariableNames);
        KGGameTests.registerFunction(MISSING_SAMPLER_FALLBACK, ShaderCompilerGameTest::unconnectedSamplerFallsBackToMissing);
        KGGameTests.registerFunction(TEXTURE_NODE, ShaderCompilerGameTest::textureNodeBecomesUniform);
        KGGameTests.registerFunction(OVERLAY_LIGHTMAP, ShaderCompilerGameTest::overlayLightmapNodesFlagPipeline);
        KGGameTests.registerFunction(SAMPLER_VALUE_CODEC, ShaderCompilerGameTest::sampler2DValueCodecRoundTrips);
        KGGameTests.registerFunction(WIRE_PORTAL, ShaderCompilerGameTest::wirePortalRoutesConnection);
        KGGameTests.registerFunction(MATH_NODES, ShaderCompilerGameTest::mathNodesEmitGlslCalls);
        KGGameTests.registerFunction(VECTOR_NODES, ShaderCompilerGameTest::vectorNodesEmitGlslCalls);
        KGGameTests.registerFunction(VERTEX_FORMAT_INPUTS, ShaderCompilerGameTest::vertexFormatInputsAreVertexOnly);
        KGGameTests.registerFunction(FRAGMENT_INPUTS, ShaderCompilerGameTest::fragmentInputsEmitVaryings);
        KGGameTests.registerFunction(DYNAMIC_WIDTH, ShaderCompilerGameTest::dynamicMathInfersWidth);
        KGGameTests.registerFunction(MATRIX_NODES, ShaderCompilerGameTest::matrixNodesEmitGlsl);
        KGGameTests.registerFunction(DERIVATIVE_NODES, ShaderCompilerGameTest::derivativeNodesEmitGlsl);
        KGGameTests.registerFunction(PREVIEW_VERTEX_ATTR, ShaderCompilerGameTest::previewOfVertexAttributeHasNoStageError);
        KGGameTests.registerFunction(EXTRA_MATH_NODES, ShaderCompilerGameTest::extraMathNodesEmitGlsl);
        KGGameTests.registerFunction(TRANSFORM_NODE, ShaderCompilerGameTest::transformNodeUsesSpaceMatrices);
        KGGameTests.registerFunction(CAMERA_NODE, ShaderCompilerGameTest::cameraNodeReadsGlobals);
        KGGameTests.registerFunction(EXP_LOG_BASE, ShaderCompilerGameTest::expLogBaseOptionDrivesGlsl);
        KGGameTests.registerFunction(KG_TRANSFORMS_UBO, ShaderCompilerGameTest::kgTransformsUboNodeExposesMatrices);
        KGGameTests.registerFunction(WORLD_GRID, ShaderCompilerGameTest::worldGridTransformGraphCompiles);
        KGGameTests.registerFunction(PROCEDURAL_NODES, ShaderCompilerGameTest::proceduralNodesEmitGlsl);
        KGGameTests.registerFunction(ARTISTIC_NODES, ShaderCompilerGameTest::artisticNodesEmitGlsl);
        KGGameTests.registerFunction(FRESNEL_DEFAULTS, ShaderCompilerGameTest::fresnelDefaultsToMeshNormalAndViewDir);
        KGGameTests.registerFunction(FOG_DISTANCE_VERTEX_ONLY, ShaderCompilerGameTest::fogDistanceNodesAreVertexOnly);
        KGGameTests.registerFunction(FOG_PARAM_DEFAULTS, ShaderCompilerGameTest::fogParamsDefaultToUboAndVaryings);
        KGGameTests.registerFunction(SPHERE_MASK_COORDS, ShaderCompilerGameTest::sphereMaskCoordsDefaultToMeshPosition);
        KGGameTests.registerFunction(CHANNEL_NODES, ShaderCompilerGameTest::channelNodesEmitGlsl);
        KGGameTests.registerFunction(UV_NODES, ShaderCompilerGameTest::uvNodesEmitGlsl);
        KGGameTests.registerFunction(UV_CHANNEL, ShaderCompilerGameTest::uvTypeResolvesChannel);
        KGGameTests.registerFunction(UV_CHANNEL_CODEC, ShaderCompilerGameTest::uvChannelValueCodecRoundTrips);
        KGGameTests.registerFunction(UV_PREVIEW, ShaderCompilerGameTest::uvPreviewSemantics);
        KGGameTests.registerFunction(SCENE_NODES, ShaderCompilerGameTest::sceneNodesEmitGlsl);
        KGGameTests.registerFunction(SCENE_SAMPLER_DEFAULT, ShaderCompilerGameTest::sceneSamplersHaveNoBakedDefault);
        KGGameTests.registerFunction(SCREEN_POSITION_MODES, ShaderCompilerGameTest::screenPositionModesEmitGlsl);
        KGGameTests.registerFunction(CAMERA_NEW_OUTPUTS, ShaderCompilerGameTest::cameraNodeExposesNewOutputs);
        KGGameTests.registerFunction(SCENE_PREVIEW_UV, ShaderCompilerGameTest::scenePreviewMapsWholeCapture);
        KGGameTests.registerFunction(SCREEN_POSITION_PREVIEW, ShaderCompilerGameTest::screenPositionPreviewMapsToMeshUv);
        KGGameTests.registerFunction(SCREEN_POSITION_RAW_EYE, ShaderCompilerGameTest::screenPositionRawCarriesFragmentEyeDepth);
        KGGameTests.registerFunction(PREVIEW_OPAQUE_ALPHA, ShaderCompilerGameTest::scalarPreviewForcesOpaqueAlpha);
        KGGameTests.registerFunction(BRANCH_SELECT, ShaderCompilerGameTest::branchEmitsSelect);
        KGGameTests.registerFunction(COMPARE_LOGIC, ShaderCompilerGameTest::compareLogicEmitGlsl);
        KGGameTests.registerFunction(EXPRESSION_NODE, ShaderCompilerGameTest::expressionNodeEmitsFunctionAndCall);
        KGGameTests.registerFunction(EXPRESSION_VALIDATION, ShaderCompilerGameTest::expressionNodeValidationFlagsBadNames);
        KGGameTests.registerFunction(EXPORT_FUNCTION, ShaderCompilerGameTest::exportBuildsFunctionGraph);
        KGGameTests.registerFunction(BUILTIN_FRAG_KEYWORDS, ShaderCompilerGameTest::builtinFragmentKeywordsEmitGlsl);
        KGGameTests.registerFunction(BUILTIN_VERTEX_IDS, ShaderCompilerGameTest::idNodesWorkInBothStages);
        KGGameTests.registerFunction(POSITION_NORMAL_SPACES, ShaderCompilerGameTest::positionNormalNodesEmitSpaceGlsl);
        KGGameTests.registerFunction(INPUT_NODES_VERTEX_STAGE, ShaderCompilerGameTest::uvAndVertexColorUsableInVertexStage);
        KGGameTests.registerFunction(VIEW_DIRECTION_NORMALIZE, ShaderCompilerGameTest::viewDirectionNormalizeOption);
        KGGameTests.registerFunction(GRADIENT_NODES, ShaderCompilerGameTest::gradientNodesEmitGlsl);
        KGGameTests.registerFunction(GRADIENT_VARIABLE, ShaderCompilerGameTest::gradientVariableBecomesUboStruct);
        KGGameTests.registerFunction(GRADIENT_VALUE_CODEC, ShaderCompilerGameTest::gradientValueCodecRoundTrips);
        KGGameTests.registerFunction(CURVE_NODES, ShaderCompilerGameTest::curveNodesEmitGlsl);
        KGGameTests.registerFunction(CURVE_VARIABLE, ShaderCompilerGameTest::curveVariableBecomesUboStruct);
        KGGameTests.registerFunction(CURVE_VALUE_CODEC, ShaderCompilerGameTest::curveValueCodecRoundTrips);
        KGGameTests.registerFunction(VERTEX_MODEL_IDENTITY, ShaderCompilerGameTest::vertexModelIdentityParity);
        KGGameTests.registerFunction(VERTEX_MODEL_BLOCKS, ShaderCompilerGameTest::vertexModelBlocksDisplace);
        KGGameTests.registerFunction(VERTEX_MODEL_LEGACY, ShaderCompilerGameTest::vertexModelLegacyGlPosition);
        KGGameTests.registerFunction(INJECTION_GEOMETRY, ShaderCompilerGameTest::injectionGeometryNodes);
        KGGameTests.registerFunction(INJECTION_UNIVERSAL, ShaderCompilerGameTest::injectionAllNodesCompatible);
        KGGameTests.registerFunction(INJECTION_GATE, ShaderCompilerGameTest::injectionBlacklistGate);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        KGGameTests.registerFunctionTest(event, DEDUP, KGGameTests.functionKey(DEDUP), d);
        KGGameTests.registerFunctionTest(event, EMISSION, KGGameTests.functionKey(EMISSION), d);
        KGGameTests.registerFunctionTest(event, ALPHA_DISCARD, KGGameTests.functionKey(ALPHA_DISCARD), d);
        KGGameTests.registerFunctionTest(event, CUSTOM_VARYING, KGGameTests.functionKey(CUSTOM_VARYING), d);
        KGGameTests.registerFunctionTest(event, FLOAT_BROADCAST, KGGameTests.functionKey(FLOAT_BROADCAST), d);
        KGGameTests.registerFunctionTest(event, VEC4_TO_VEC3_SWIZZLE, KGGameTests.functionKey(VEC4_TO_VEC3_SWIZZLE), d);
        KGGameTests.registerFunctionTest(event, SAMPLER_NOT_HOISTED, KGGameTests.functionKey(SAMPLER_NOT_HOISTED), d);
        KGGameTests.registerFunctionTest(event, ENGINE_TIME, KGGameTests.functionKey(ENGINE_TIME), d);
        KGGameTests.registerFunctionTest(event, NODE_PREVIEW, KGGameTests.functionKey(NODE_PREVIEW), d);
        KGGameTests.registerFunctionTest(event, STAGE_AFFINITY, KGGameTests.functionKey(STAGE_AFFINITY), d);
        KGGameTests.registerFunctionTest(event, VAR_UNIFORM_VS_INLINE, KGGameTests.functionKey(VAR_UNIFORM_VS_INLINE), d);
        KGGameTests.registerFunctionTest(event, VAR_SAMPLER, KGGameTests.functionKey(VAR_SAMPLER), d);
        KGGameTests.registerFunctionTest(event, VAR_COLOR, KGGameTests.functionKey(VAR_COLOR), d);
        KGGameTests.registerFunctionTest(event, VAR_HDR_COLOR, KGGameTests.functionKey(VAR_HDR_COLOR), d);
        KGGameTests.registerFunctionTest(event, MIX_LIGHT_DEFAULT, KGGameTests.functionKey(MIX_LIGHT_DEFAULT), d);
        KGGameTests.registerFunctionTest(event, UNIFORM_FIELD_MAP, KGGameTests.functionKey(UNIFORM_FIELD_MAP), d);
        KGGameTests.registerFunctionTest(event, MISSING_SAMPLER_FALLBACK, KGGameTests.functionKey(MISSING_SAMPLER_FALLBACK), d);
        KGGameTests.registerFunctionTest(event, TEXTURE_NODE, KGGameTests.functionKey(TEXTURE_NODE), d);
        KGGameTests.registerFunctionTest(event, OVERLAY_LIGHTMAP, KGGameTests.functionKey(OVERLAY_LIGHTMAP), d);
        KGGameTests.registerFunctionTest(event, SAMPLER_VALUE_CODEC, KGGameTests.functionKey(SAMPLER_VALUE_CODEC), d);
        KGGameTests.registerFunctionTest(event, WIRE_PORTAL, KGGameTests.functionKey(WIRE_PORTAL), d);
        KGGameTests.registerFunctionTest(event, MATH_NODES, KGGameTests.functionKey(MATH_NODES), d);
        KGGameTests.registerFunctionTest(event, VECTOR_NODES, KGGameTests.functionKey(VECTOR_NODES), d);
        KGGameTests.registerFunctionTest(event, VERTEX_FORMAT_INPUTS, KGGameTests.functionKey(VERTEX_FORMAT_INPUTS), d);
        KGGameTests.registerFunctionTest(event, FRAGMENT_INPUTS, KGGameTests.functionKey(FRAGMENT_INPUTS), d);
        KGGameTests.registerFunctionTest(event, DYNAMIC_WIDTH, KGGameTests.functionKey(DYNAMIC_WIDTH), d);
        KGGameTests.registerFunctionTest(event, MATRIX_NODES, KGGameTests.functionKey(MATRIX_NODES), d);
        KGGameTests.registerFunctionTest(event, DERIVATIVE_NODES, KGGameTests.functionKey(DERIVATIVE_NODES), d);
        KGGameTests.registerFunctionTest(event, PREVIEW_VERTEX_ATTR, KGGameTests.functionKey(PREVIEW_VERTEX_ATTR), d);
        KGGameTests.registerFunctionTest(event, EXTRA_MATH_NODES, KGGameTests.functionKey(EXTRA_MATH_NODES), d);
        KGGameTests.registerFunctionTest(event, TRANSFORM_NODE, KGGameTests.functionKey(TRANSFORM_NODE), d);
        KGGameTests.registerFunctionTest(event, CAMERA_NODE, KGGameTests.functionKey(CAMERA_NODE), d);
        KGGameTests.registerFunctionTest(event, EXP_LOG_BASE, KGGameTests.functionKey(EXP_LOG_BASE), d);
        KGGameTests.registerFunctionTest(event, KG_TRANSFORMS_UBO, KGGameTests.functionKey(KG_TRANSFORMS_UBO), d);
        KGGameTests.registerFunctionTest(event, WORLD_GRID, KGGameTests.functionKey(WORLD_GRID), d);
        KGGameTests.registerFunctionTest(event, PROCEDURAL_NODES, KGGameTests.functionKey(PROCEDURAL_NODES), d);
        KGGameTests.registerFunctionTest(event, ARTISTIC_NODES, KGGameTests.functionKey(ARTISTIC_NODES), d);
        KGGameTests.registerFunctionTest(event, FRESNEL_DEFAULTS, KGGameTests.functionKey(FRESNEL_DEFAULTS), d);
        KGGameTests.registerFunctionTest(event, FOG_DISTANCE_VERTEX_ONLY, KGGameTests.functionKey(FOG_DISTANCE_VERTEX_ONLY), d);
        KGGameTests.registerFunctionTest(event, FOG_PARAM_DEFAULTS, KGGameTests.functionKey(FOG_PARAM_DEFAULTS), d);
        KGGameTests.registerFunctionTest(event, SPHERE_MASK_COORDS, KGGameTests.functionKey(SPHERE_MASK_COORDS), d);
        KGGameTests.registerFunctionTest(event, CHANNEL_NODES, KGGameTests.functionKey(CHANNEL_NODES), d);
        KGGameTests.registerFunctionTest(event, UV_NODES, KGGameTests.functionKey(UV_NODES), d);
        KGGameTests.registerFunctionTest(event, UV_CHANNEL, KGGameTests.functionKey(UV_CHANNEL), d);
        KGGameTests.registerFunctionTest(event, UV_CHANNEL_CODEC, KGGameTests.functionKey(UV_CHANNEL_CODEC), d);
        KGGameTests.registerFunctionTest(event, UV_PREVIEW, KGGameTests.functionKey(UV_PREVIEW), d);
        KGGameTests.registerFunctionTest(event, SCENE_NODES, KGGameTests.functionKey(SCENE_NODES), d);
        KGGameTests.registerFunctionTest(event, SCENE_SAMPLER_DEFAULT, KGGameTests.functionKey(SCENE_SAMPLER_DEFAULT), d);
        KGGameTests.registerFunctionTest(event, SCREEN_POSITION_MODES, KGGameTests.functionKey(SCREEN_POSITION_MODES), d);
        KGGameTests.registerFunctionTest(event, CAMERA_NEW_OUTPUTS, KGGameTests.functionKey(CAMERA_NEW_OUTPUTS), d);
        KGGameTests.registerFunctionTest(event, SCENE_PREVIEW_UV, KGGameTests.functionKey(SCENE_PREVIEW_UV), d);
        KGGameTests.registerFunctionTest(event, SCREEN_POSITION_PREVIEW, KGGameTests.functionKey(SCREEN_POSITION_PREVIEW), d);
        KGGameTests.registerFunctionTest(event, SCREEN_POSITION_RAW_EYE, KGGameTests.functionKey(SCREEN_POSITION_RAW_EYE), d);
        KGGameTests.registerFunctionTest(event, PREVIEW_OPAQUE_ALPHA, KGGameTests.functionKey(PREVIEW_OPAQUE_ALPHA), d);
        KGGameTests.registerFunctionTest(event, BRANCH_SELECT, KGGameTests.functionKey(BRANCH_SELECT), d);
        KGGameTests.registerFunctionTest(event, COMPARE_LOGIC, KGGameTests.functionKey(COMPARE_LOGIC), d);
        KGGameTests.registerFunctionTest(event, EXPRESSION_NODE, KGGameTests.functionKey(EXPRESSION_NODE), d);
        KGGameTests.registerFunctionTest(event, EXPRESSION_VALIDATION, KGGameTests.functionKey(EXPRESSION_VALIDATION), d);
        KGGameTests.registerFunctionTest(event, EXPORT_FUNCTION, KGGameTests.functionKey(EXPORT_FUNCTION), d);
        KGGameTests.registerFunctionTest(event, BUILTIN_FRAG_KEYWORDS, KGGameTests.functionKey(BUILTIN_FRAG_KEYWORDS), d);
        KGGameTests.registerFunctionTest(event, BUILTIN_VERTEX_IDS, KGGameTests.functionKey(BUILTIN_VERTEX_IDS), d);
        KGGameTests.registerFunctionTest(event, POSITION_NORMAL_SPACES, KGGameTests.functionKey(POSITION_NORMAL_SPACES), d);
        KGGameTests.registerFunctionTest(event, INPUT_NODES_VERTEX_STAGE, KGGameTests.functionKey(INPUT_NODES_VERTEX_STAGE), d);
        KGGameTests.registerFunctionTest(event, VIEW_DIRECTION_NORMALIZE, KGGameTests.functionKey(VIEW_DIRECTION_NORMALIZE), d);
        KGGameTests.registerFunctionTest(event, GRADIENT_NODES, KGGameTests.functionKey(GRADIENT_NODES), d);
        KGGameTests.registerFunctionTest(event, GRADIENT_VARIABLE, KGGameTests.functionKey(GRADIENT_VARIABLE), d);
        KGGameTests.registerFunctionTest(event, GRADIENT_VALUE_CODEC, KGGameTests.functionKey(GRADIENT_VALUE_CODEC), d);
        KGGameTests.registerFunctionTest(event, CURVE_NODES, KGGameTests.functionKey(CURVE_NODES), d);
        KGGameTests.registerFunctionTest(event, CURVE_VARIABLE, KGGameTests.functionKey(CURVE_VARIABLE), d);
        KGGameTests.registerFunctionTest(event, CURVE_VALUE_CODEC, KGGameTests.functionKey(CURVE_VALUE_CODEC), d);
        KGGameTests.registerFunctionTest(event, VERTEX_MODEL_IDENTITY, KGGameTests.functionKey(VERTEX_MODEL_IDENTITY), d);
        KGGameTests.registerFunctionTest(event, VERTEX_MODEL_BLOCKS, KGGameTests.functionKey(VERTEX_MODEL_BLOCKS), d);
        KGGameTests.registerFunctionTest(event, VERTEX_MODEL_LEGACY, KGGameTests.functionKey(VERTEX_MODEL_LEGACY), d);
        KGGameTests.registerFunctionTest(event, INJECTION_GEOMETRY, KGGameTests.functionKey(INJECTION_GEOMETRY), d);
        KGGameTests.registerFunctionTest(event, INJECTION_UNIVERSAL, KGGameTests.functionKey(INJECTION_UNIVERSAL), d);
        KGGameTests.registerFunctionTest(event, INJECTION_GATE, KGGameTests.functionKey(INJECTION_GATE), d);
    }

    private static CompiledShaderGraph compile(RenderTypeGraph graph) {
        return new ShaderGraphCompiler(graph).compile();
    }

    /** Whether the compiled graph registered the KG-managed UBO with the given block name. */
    private static boolean usesUniformBlock(CompiledShaderGraph compiled, String uboName) {
        return compiled.uniformBlocks().stream().anyMatch(b -> b.uboName().equals(uboName));
    }

    // ---- logic / branch / expression / export ------------------------------------------------

    /** A Branch node emits a ternary {@code (pred ? t : f)} select; the result width follows the wider
     *  operand (a vec3 {@code t} broadcasts the scalar {@code f} to vec3). The wired Compare emits its op. */
    public static void branchEmitsSelect(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel branch = addNode(graph, BranchNode.class);
        NodeModel compare = addNode(graph, CompareNode.class);
        setOption(compare, "op", "less");
        NodeModel vec3 = addNode(graph, Vec3Node.class);
        wire(graph, branch.getInputsById().get("predicate"), compare.getOutputsById().get("out"));
        wire(graph, branch.getInputsById().get("t"), vec3.getOutputsById().get("out"));
        wire(graph, emission.getInputsById().get("color"), branch.getOutputsById().get("out"));

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "branch emits ternary select", fsh.contains("?") && fsh.contains(" : "));
        assertTrue(helper, "compare operator < emitted", fsh.contains("<"));
        assertTrue(helper, "scalar f operand broadcast to vec3", fsh.contains("vec3("));
        helper.succeed();
    }

    /** Compare emits the chosen relational operator (→ bool); And/Or/Not emit {@code &&}/{@code ||}/{@code !}. */
    public static void compareLogicEmitGlsl(GameTestHelper helper) {
        String[][] ops = {{"equal", "=="}, {"notEqual", "!="}, {"less", "<"},
                {"lessEqual", "<="}, {"greater", ">"}, {"greaterEqual", ">="}};
        for (String[] op : ops) {
            RenderTypeGraph graph = new RenderTypeGraph();
            NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
            NodeModel compare = addNode(graph, CompareNode.class);
            setOption(compare, "op", op[0]);
            wire(graph, emission.getInputsById().get("color"), compare.getOutputsById().get("out"));
            assertTrue(helper, "compare " + op[0] + " emits " + op[1],
                    compile(graph).fragmentSource().contains(op[1]));
        }
        assertTrue(helper, "And emits &&", logicOpFsh(AndNode.class).contains("&&"));
        assertTrue(helper, "Or emits ||", logicOpFsh(OrNode.class).contains("||"));
        assertTrue(helper, "Not emits !", logicOpFsh(NotNode.class).contains("(!"));
        helper.succeed();
    }

    /** Compile a graph with a single boolean-logic node wired into emission, returning the fragment GLSL. */
    private static String logicOpFsh(Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node> nodeClass) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel node = addNode(graph, nodeClass);
        wire(graph, emission.getInputsById().get("color"), node.getOutputsById().get("out"));
        return compile(graph).fragmentSource();
    }

    /** The Expression node defines its ports from the spec and compiles to a per-instance helper function
     *  ({@code void kg_expr_…(in …, out …)}) called with the input expressions + declared out temps. */
    public static void expressionNodeEmitsFunctionAndCall(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel expr = addNode(graph, ExpressionNode.class);
        String json = ExpressionNode.toJson(new ExpressionNode.ExpressionSpec(
                java.util.List.of(new ExpressionNode.PortSpec("a", "FLOAT"), new ExpressionNode.PortSpec("b", "VEC3")),
                java.util.List.of(new ExpressionNode.PortSpec("result", "VEC3"), new ExpressionNode.PortSpec("scalar", "FLOAT")),
                "result = b * a;\nscalar = a;"));
        setOption(expr, "spec", json); // setOption calls defineNode() → ports re-derived from the spec
        assertEq(helper, "expression inputs (a,b)", 2, expr.getInputsById().size());
        assertEq(helper, "expression outputs (result,scalar)", 2, expr.getOutputsById().size());
        wire(graph, emission.getInputsById().get("color"), expr.getOutputsById().get("result"));

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "helper function declared", fsh.contains("void kg_expr_"));
        assertTrue(helper, "helper has typed in/out params",
                fsh.contains("in float a") && fsh.contains("in vec3 b")
                        && fsh.contains("out vec3 result") && fsh.contains("out float scalar"));
        assertTrue(helper, "helper body inlined", fsh.contains("result = b * a;"));
        assertTrue(helper, "function declared + called", count(fsh, "kg_expr_") >= 2);
        helper.succeed();
    }

    /** {@link ExportShaderFunction#build} turns a selection into a standalone ShaderFunctionGraph with one
     *  READ (incoming) + one WRITE (outgoing) boundary variable and the copied nodes — without modifying the
     *  source graph. (The editor menu + resource persistence is client-verified.) */
    public static void exportBuildsFunctionGraph(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel vecA = addRegisteredNode(graph, Vec3Node.class);
        NodeModel vecB = addRegisteredNode(graph, Vec3Node.class);
        NodeModel add = addRegisteredNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("a"), vecA.getOutputsById().get("out"));
        wire(graph, add.getInputsById().get("b"), vecB.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), add.getOutputsById().get("out"));

        int sourceNodesBefore = graph.graphModel.getNodeModels().size();
        long sourceVarsBefore = graph.graphModel.getGraphVariableModels().stream().filter(java.util.Objects::nonNull).count();

        // Select {vecA, add}: vecB→add.b crosses in (READ), add→baseColor crosses out (WRITE), vecA→add internal.
        java.util.List<GraphElementModel> selection = java.util.List.of(
                (GraphElementModel) vecA, (GraphElementModel) add);
        ShaderFunctionGraph fn = ExportShaderFunction.build(graph.graphModel, selection, Platform.getFrozenRegistry());
        assertTrue(helper, "export produced a function graph", fn != null);

        var vars = fn.graphModel.getGraphVariableModels().stream().filter(java.util.Objects::nonNull).toList();
        long reads = vars.stream().filter(v -> v.getModifiers() != null && v.getModifiers().hasFlag(ModifierFlags.READ)).count();
        long writes = vars.stream().filter(v -> v.getModifiers() != null && v.getModifiers().hasFlag(ModifierFlags.WRITE)).count();
        assertEq(helper, "one READ (input) boundary var", 1L, reads);
        assertEq(helper, "one WRITE (output) boundary var", 1L, writes);

        assertTrue(helper, "copied Vec3 node present",
                fn.graphModel.getNodeModels().stream().anyMatch(n -> isNodeOfType(n, Vec3Node.class)));
        assertTrue(helper, "copied Add node present",
                fn.graphModel.getNodeModels().stream().anyMatch(n -> isNodeOfType(n, AddNode.class)));

        assertEq(helper, "source nodes unchanged", sourceNodesBefore, graph.graphModel.getNodeModels().size());
        assertEq(helper, "source has no new variables", (int) sourceVarsBefore,
                (int) graph.graphModel.getGraphVariableModels().stream().filter(java.util.Objects::nonNull).count());
        helper.succeed();
    }

    /** Whether a node model wraps a user node of the given class. */
    private static boolean isNodeOfType(Object model, Class<?> nodeClass) {
        return model instanceof ICustomNodeModel c && nodeClass.isInstance(c.getNode());
    }

    /** The Expression node reports a validation error for a reserved/illegal port name (so the editor's
     *  GraphLogger surfaces it next to the node), and none for a valid spec. */
    public static void expressionNodeValidationFlagsBadNames(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel exprModel = addNode(graph, ExpressionNode.class);
        ExpressionNode expr = (ExpressionNode) ((ICustomNodeModel) exprModel).getNode();

        // Output named "out" is a GLSL reserved word → flagged.
        setOption(exprModel, "spec", ExpressionNode.toJson(new ExpressionNode.ExpressionSpec(
                java.util.List.of(new ExpressionNode.PortSpec("a", "FLOAT")),
                java.util.List.of(new ExpressionNode.PortSpec("out", "FLOAT")), "out = a;")));
        assertTrue(helper, "reserved output name flagged", !expr.validationErrors().isEmpty());

        // Valid identifiers → no validation errors.
        setOption(exprModel, "spec", ExpressionNode.toJson(new ExpressionNode.ExpressionSpec(
                java.util.List.of(new ExpressionNode.PortSpec("a", "FLOAT")),
                java.util.List.of(new ExpressionNode.PortSpec("result", "FLOAT")), "result = a;")));
        assertTrue(helper, "valid spec has no errors", expr.validationErrors().isEmpty());
        helper.succeed();
    }

    /** The fragment-stage built-in keyword nodes emit their GLSL special variables into the fsh with no
     *  stage errors: Front Facing → {@code gl_FrontFacing}, Fragment Coordinate → {@code gl_FragCoord},
     *  Primitive ID → {@code gl_PrimitiveID}. */
    public static void builtinFragmentKeywordsEmitGlsl(GameTestHelper helper) {
        // Fragment Coordinate (vec4) → emission color.
        RenderTypeGraph coordGraph = new RenderTypeGraph();
        NodeModel coordEmission = addBlock(coordGraph, coordGraph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel coord = addNode(coordGraph, FragmentCoordinateNode.class);
        wire(coordGraph, coordEmission.getInputsById().get("color"), coord.getOutputsById().get("out"));
        CompiledShaderGraph coordCompiled = compile(coordGraph);
        assertTrue(helper, "fragment coordinate emits gl_FragCoord", coordCompiled.fragmentSource().contains("gl_FragCoord"));
        assertFalse(helper, "fragment coordinate is not a stage error", coordCompiled.hasStageErrors());

        // Front Facing (bool) → Branch predicate (bool input).
        RenderTypeGraph faceGraph = new RenderTypeGraph();
        NodeModel faceEmission = addBlock(faceGraph, faceGraph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel branch = addNode(faceGraph, BranchNode.class);
        NodeModel face = addNode(faceGraph, FrontFacingNode.class);
        NodeModel vec3 = addNode(faceGraph, Vec3Node.class);
        wire(faceGraph, branch.getInputsById().get("predicate"), face.getOutputsById().get("out"));
        wire(faceGraph, branch.getInputsById().get("t"), vec3.getOutputsById().get("out"));
        wire(faceGraph, faceEmission.getInputsById().get("color"), branch.getOutputsById().get("out"));
        CompiledShaderGraph faceCompiled = compile(faceGraph);
        assertTrue(helper, "front facing emits gl_FrontFacing", faceCompiled.fragmentSource().contains("gl_FrontFacing"));
        assertFalse(helper, "front facing is not a stage error", faceCompiled.hasStageErrors());

        // Primitive ID (int) → emission color (int→vec4 convert).
        RenderTypeGraph primGraph = new RenderTypeGraph();
        NodeModel primEmission = addBlock(primGraph, primGraph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel prim = addNode(primGraph, PrimitiveIdNode.class);
        wire(primGraph, primEmission.getInputsById().get("color"), prim.getOutputsById().get("out"));
        CompiledShaderGraph primCompiled = compile(primGraph);
        assertTrue(helper, "primitive id emits gl_PrimitiveID", primCompiled.fragmentSource().contains("gl_PrimitiveID"));
        assertFalse(helper, "primitive id is not a stage error", primCompiled.hasStageErrors());
        helper.succeed();
    }

    /** The ID nodes are now stage-agnostic ({@code ANY}): the vsh reads {@code gl_VertexID}/{@code gl_InstanceID}
     *  directly, and the fragment stage receives them through an auto-forwarded {@code flat int} varying (int
     *  varyings must be {@code flat}). */
    public static void idNodesWorkInBothStages(GameTestHelper helper) {
        // Fragment stage: Vertex ID → fragment emission. No longer a stage error — it's forwarded as a flat
        // int varying, so the fsh declares `flat in int kg_vertexId` and the vsh writes it from gl_VertexID.
        RenderTypeGraph frag = new RenderTypeGraph();
        NodeModel fragEmission = addBlock(frag, frag.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel vId = addNode(frag, VertexIdNode.class);
        wire(frag, fragEmission.getInputsById().get("color"), vId.getOutputsById().get("out"));
        CompiledShaderGraph fragCompiled = compile(frag);
        assertFalse(helper, "vertex id in the fragment stage is no longer a stage error", fragCompiled.hasStageErrors());
        assertTrue(helper, "fsh declares a flat int varying", fragCompiled.fragmentSource().contains("flat in int kg_vertexId"));
        assertTrue(helper, "vsh forwards gl_VertexID into the varying", fragCompiled.vertexSource().contains("kg_vertexId = gl_VertexID"));

        // Vertex stage: Instance ID → vertex varying block → consumed in fragment. The vsh reads the built-in
        // directly (no kg_instanceId forwarding varying is created), so gl_InstanceID appears in the vsh.
        RenderTypeGraph vert = new RenderTypeGraph();
        NodeModel varying = addBlock(vert, vert.getVertexStageModel(), VaryingCustomFloatBlock.class);
        NodeModel instanceId = addNode(vert, InstanceIdNode.class);
        wire(vert, varying.getInputsById().get("value"), instanceId.getOutputsById().get("out"));
        NodeModel vertEmission = addBlock(vert, vert.getFragmentStageModel(), FragmentEmissionBlock.class);
        wire(vert, vertEmission.getInputsById().get("color"), varying.getOutputsById().get("value"));
        CompiledShaderGraph vertCompiled = compile(vert);
        assertFalse(helper, "instance id in the vertex stage is not a stage error", vertCompiled.hasStageErrors());
        assertTrue(helper, "vsh references gl_InstanceID directly", vertCompiled.vertexSource().contains("gl_InstanceID"));
        assertFalse(helper, "no forwarding varying created for a vertex-only read", vertCompiled.fragmentSource().contains("kg_instanceId"));
        helper.succeed();
    }

    /** The Position/Normal nodes emit the chosen coordinate space's matrix math and are usable in both stages.
     *  World uses the camera-relative→absolute chain (ModelViewMat, kg_transforms.IViewMat, CameraBlockPos);
     *  View uses ModelViewMat; Object reads the interpolated object-space source. */
    public static void positionNormalNodesEmitSpaceGlsl(GameTestHelper helper) {
        // Position, world space: absolute world = un-rotate view→world (kg_transforms.IViewMat) + camera position.
        String posWorld = inputNodeFsh(PositionNode.class, "space", "world");
        assertTrue(helper, "position world uses ModelViewMat", posWorld.contains("ModelViewMat"));
        assertTrue(helper, "position world un-rotates via IViewMat", posWorld.contains("kg_transforms.IViewMat"));
        assertTrue(helper, "position world adds the camera position", posWorld.contains("CameraBlockPos"));

        // Position, object space: the interpolated model-space position varying (kg_modelPos).
        assertTrue(helper, "position object reads the interpolated model position",
                inputNodeFsh(PositionNode.class, "space", "object").contains("kg_modelPos"));

        // Normal, world space: object normal (kg_objectNormal varying) rotated object→world, normalized.
        String nrmWorld = inputNodeFsh(NormalNode.class, "space", "world");
        assertTrue(helper, "normal world reads the object-normal varying", nrmWorld.contains("kg_objectNormal"));
        assertTrue(helper, "normal world rotates via ModelViewMat + IViewMat",
                nrmWorld.contains("ModelViewMat") && nrmWorld.contains("kg_transforms.IViewMat"));
        assertTrue(helper, "normal is normalized", nrmWorld.contains("normalize("));

        // Normal, view space: object normal rotated by ModelViewMat only.
        assertTrue(helper, "normal view uses ModelViewMat",
                inputNodeFsh(NormalNode.class, "space", "view").contains("ModelViewMat"));
        helper.succeed();
    }

    /** Compile an input node with one option set, wired into fragment emission, and return the fragment GLSL. */
    private static String inputNodeFsh(Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node> nodeClass,
                                       String option, String value) {
        return compile(inputNodeGraph(nodeClass, option, value)).fragmentSource();
    }

    /** A minimal graph: one node (with an optional option set) wired into the fragment Emission block. */
    private static RenderTypeGraph inputNodeGraph(Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node> nodeClass,
                                                  @org.jetbrains.annotations.Nullable String option,
                                                  @org.jetbrains.annotations.Nullable String value) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel node = addNode(graph, nodeClass);
        if (option != null) setOption(node, option, value);
        wire(graph, emission.getInputsById().get("color"), node.getOutputsById().get("out"));
        return graph;
    }

    /** The View Direction node is unnormalized by default (its length is the camera distance); the
     *  {@code normalize} option wraps the output in a {@code normalize()} for a unit-length direction. */
    public static void viewDirectionNormalizeOption(GameTestHelper helper) {
        assertTrue(helper, "the normalize option adds a normalize() call around the view direction",
                countOccurrences(viewDirectionFsh(true), "normalize(")
                        > countOccurrences(viewDirectionFsh(false), "normalize("));
        helper.succeed();
    }

    /** A View Direction node (given {@code normalize}) wired into fragment emission; returns the fragment GLSL. */
    private static String viewDirectionFsh(boolean normalize) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel vd = addNode(graph, ViewDirectionNode.class);
        setOption(vd, "normalize", normalize);
        wire(graph, emission.getInputsById().get("color"), vd.getOutputsById().get("out"));
        return compile(graph).fragmentSource();
    }

    /**
     * The Unity-like vertex-block refactor's byte-parity pin: the new default graph (an unconnected
     * model-space Position block) must emit GLSL byte-identical to the old default (the advanced
     * glPosition block) - so the block swap changes no existing pipeline hash and no vanilla visuals.
     */
    public static void vertexModelIdentityParity(GameTestHelper helper) {
        RenderTypeGraph current = new RenderTypeGraph();
        RenderTypeGraph legacy = new RenderTypeGraph() {
            @Override
            protected Class<? extends BlockNode> defaultVertexPositionBlockClass() {
                return VertexPositionBlock.class;
            }
        };
        CompiledShaderGraph a = compile(current);
        CompiledShaderGraph b = compile(legacy);
        // Byte-identical modulo per-graph-instance node uids (the default texture sampler's name carries
        // its node's uid, so two separately-built graphs can never be raw-equal — normalize just that).
        assertEq(helper, "identity Position block: byte-identical vsh",
                normalizeUids(b.vertexSource()), normalizeUids(a.vertexSource()));
        assertEq(helper, "identity Position block: byte-identical fsh",
                normalizeUids(b.fragmentSource()), normalizeUids(a.fragmentSource()));
        assertFalse(helper, "identity emits no displaced temp", a.vertexSource().contains("kg_vertexPos"));
        assertTrue(helper, "identity keeps the standard MVP chain", a.vertexSource()
                .contains("gl_Position = ProjMat * ModelViewMat * vec4((Position + ModelOffset), 1.0);"));
        helper.succeed();
    }

    /** Replace per-graph-instance uid-bearing identifiers ({@code kg_tex_<uid>}) with a fixed token. */
    private static String normalizeUids(String glsl) {
        return glsl.replaceAll("kg_tex_[0-9a-f_]+", "kg_tex");
    }

    /**
     * Driven Position/Normal blocks displace through the single seams: gl_Position, the fog distances
     * and kg_modelPos all read {@code kg_vertexPos}; the lit vertex colour ({@code minecraft_mix_light}),
     * the world-normal varying and the Normal node's object source all read {@code kg_vertexNormal}.
     */
    public static void vertexModelBlocksDisplace(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        // Position block <- Add(Position(object), Vec3): a position-dependent offset.
        NodeModel posBlock = addBlock(graph, graph.getVertexStageModel(), VertexModelPositionBlock.class);
        NodeModel pos = addNode(graph, PositionNode.class);
        setOption(pos, "space", "object");
        NodeModel offset = addNode(graph, Vec3Node.class);
        NodeModel add = addNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("a"), pos.getOutputsById().get("out"));
        wire(graph, add.getInputsById().get("b"), offset.getOutputsById().get("out"));
        wire(graph, posBlock.getInputsById().get("position"), add.getOutputsById().get("out"));
        // Normal block <- Vec3.
        NodeModel nrmBlock = addBlock(graph, graph.getVertexStageModel(), VertexModelNormalBlock.class);
        NodeModel nrm = addNode(graph, Vec3Node.class);
        wire(graph, nrmBlock.getInputsById().get("normal"), nrm.getOutputsById().get("out"));
        // A fragment Normal node (object space), so the kg_objectNormal varying is built.
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel normalNode = addNode(graph, NormalNode.class);
        setOption(normalNode, "space", "object");
        wire(graph, emission.getInputsById().get("color"), normalNode.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "displacement graph has no stage errors", compiled.hasStageErrors());
        String vsh = compiled.vertexSource();
        assertTrue(helper, "vsh hoists the displaced position", vsh.contains("vec3 kg_vertexPos = "));
        assertTrue(helper, "gl_Position transforms the displaced position",
                vsh.contains("gl_Position = ProjMat * ModelViewMat * vec4(kg_vertexPos, 1.0);"));
        assertTrue(helper, "fog distance follows the displaced position",
                vsh.contains("fog_spherical_distance(kg_vertexPos)"));
        assertTrue(helper, "vsh hoists the displaced normal", vsh.contains("vec3 kg_vertexNormal = "));
        assertTrue(helper, "default lighting re-lights with the displaced normal",
                vsh.contains("minecraft_mix_light(Light0_Direction, Light1_Direction, kg_vertexNormal"));
        assertTrue(helper, "Normal node's object source reads the displaced normal",
                vsh.contains("kg_objectNormal = kg_vertexNormal;"));

        // The Iris side: the same driven blocks compile into vertex snippet parts — the mesh position
        // resolves to the kg_pos function parameter, never to raw attributes / fragment-only identifiers,
        // and the KG_Transforms dependency (PositionNode's object-space math) rides the snippet.
        var snippet = compiled.injectionSnippet();
        assertTrue(helper, "displacement graph has a snippet", snippet != null);
        assertTrue(helper, "snippet carries the position displacement", snippet.vertexPositionExpr() != null);
        assertTrue(helper, "snippet carries the normal modification", snippet.vertexNormalExpr() != null);
        assertFalse(helper, "snippet is not legacy", snippet.legacyVertexBlock());
        String vtext = (snippet.vertexPositionBody() != null ? snippet.vertexPositionBody() : "")
                + "\n" + snippet.vertexPositionExpr()
                + "\n" + (snippet.vertexNormalBody() != null ? snippet.vertexNormalBody() : "")
                + "\n" + snippet.vertexNormalExpr()
                + "\n" + String.join("\n", snippet.vertexFunctions());
        assertTrue(helper, "vertex snippet reads the kg_pos parameter", vtext.contains("kg_pos"));
        for (String banned : new String[]{"kg_vertexPos", "ModelOffset", "kg_uv", "gl_FragCoord", "vUv", "vPos"}) {
            assertFalse(helper, "vertex snippet must not reference '" + banned + "'", vtext.contains(banned));
        }
        assertTrue(helper, "vertex snippet depends on KG_Transforms",
                snippet.uniformBlocks().stream().anyMatch(b -> b.uboName().equals("KG_Transforms")));
        helper.succeed();
    }

    /**
     * The advanced glPosition block owns the vertex stage: when present, the model-space blocks are
     * ignored (no displaced temp), and its unconnected fallback is the standard chain. Also: a
     * FRAGMENT_ONLY node wired into a Position block is a stage-affinity error (the block pass runs
     * in the vertex scope).
     */
    public static void vertexModelLegacyGlPosition(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel posBlock = addBlock(graph, graph.getVertexStageModel(), VertexModelPositionBlock.class);
        NodeModel vec = addNode(graph, Vec3Node.class);
        wire(graph, posBlock.getInputsById().get("position"), vec.getOutputsById().get("out"));
        addBlock(graph, graph.getVertexStageModel(), VertexPositionBlock.class); // legacy joins -> wins
        String vsh = compile(graph).vertexSource();
        assertFalse(helper, "legacy glPosition block suppresses the model blocks",
                vsh.contains("kg_vertexPos"));
        assertTrue(helper, "legacy unconnected fallback is the standard chain", vsh
                .contains("gl_Position = ProjMat * ModelViewMat * vec4((Position + ModelOffset), 1.0);"));

        // Iris side of the legacy block: vanilla-only — the snippet still exists (fragment shading keeps
        // injecting) but carries no vertex parts and flags legacyVertexBlock for the registry's log.
        var legacySnippet = compile(graph).injectionSnippet();
        assertTrue(helper, "legacy graph still has a fragment snippet", legacySnippet != null);
        assertTrue(helper, "legacy graph has no vertex parts", !legacySnippet.hasVertex());
        assertTrue(helper, "legacy graph is flagged", legacySnippet.legacyVertexBlock());

        RenderTypeGraph bad = new RenderTypeGraph();
        NodeModel badBlock = addBlock(bad, bad.getVertexStageModel(), VertexModelPositionBlock.class);
        NodeModel primId = addNode(bad, PrimitiveIdNode.class);
        wire(bad, badBlock.getInputsById().get("position"), primId.getOutputsById().get("out"));
        assertTrue(helper, "FRAGMENT_ONLY node feeding a Position block is a stage error",
                compile(bad).hasStageErrors());

        // Rejection granularity: a displacement that leaks a fragment-only identifier (ScreenPosition ->
        // gl_FragCoord) drops ONLY the vertex parts — the fragment snippet survives (shading kept,
        // displacement dropped; the pack applies its standard transform).
        RenderTypeGraph leak = new RenderTypeGraph();
        NodeModel leakBlock = addBlock(leak, leak.getVertexStageModel(), VertexModelPositionBlock.class);
        NodeModel screenPos = addNode(leak, ScreenPositionNode.class);
        wire(leak, leakBlock.getInputsById().get("position"), screenPos.getOutputsById().get("out"));
        var leakSnippet = compile(leak).injectionSnippet();
        assertTrue(helper, "gl_FragCoord-leaking displacement keeps the fragment snippet", leakSnippet != null);
        assertTrue(helper, "gl_FragCoord-leaking displacement drops the vertex parts", !leakSnippet.hasVertex());
        helper.succeed();
    }

    /** The graph's Iris-injection snippet via the REAL production path ({@code compile()} passes the main
     *  layout so the snippet's KG_Material declaration matches the bound buffer). */
    private static com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet injectionSnippet(RenderTypeGraph graph) {
        return compile(graph).injectionSnippet();
    }

    /** Every GLSL piece of a snippet joined, for contains-assertions. */
    private static String snippetText(com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet s) {
        return String.join("\n", s.declarationUnits()) + "\n" + String.join("\n", s.functions())
                + "\n" + s.body() + "\n" + s.surfaceArgs();
    }

    /**
     * The Normal/Position nodes are injectable in every space: the snippet exists (not rejected), never
     * references the preview-quad varyings ({@code vNormal}/{@code vPos} — the bug where injection implies
     * preview and a hand-rolled isPreview() branch leaked them), and reconstructs from {@code gl_FragCoord}
     * + KG UBOs ({@code kg_recon_*}); only the normal needs the pack's varying ({@code usesGeometry}).
     */
    public static void injectionGeometryNodes(GameTestHelper helper) {
        for (String space : new String[]{"world", "object", "view"}) {
            var normal = injectionSnippet(inputNodeGraph(NormalNode.class, "space", space));
            assertTrue(helper, "normal " + space + " snippet exists", normal != null);
            String nText = snippetText(normal);
            assertFalse(helper, "normal " + space + " has no preview varyings",
                    nText.contains("vNormal") || nText.contains("vPos"));
            assertTrue(helper, "normal " + space + " reconstructs via kg_recon_normal",
                    nText.contains("kg_recon_normal"));
            assertTrue(helper, "normal " + space + " flags usesGeometry", normal.usesGeometry());

            var position = injectionSnippet(inputNodeGraph(PositionNode.class, "space", space));
            assertTrue(helper, "position " + space + " snippet exists", position != null);
            String pText = snippetText(position);
            assertFalse(helper, "position " + space + " has no preview varyings",
                    pText.contains("vNormal") || pText.contains("vPos"));
            assertTrue(helper, "position " + space + " reconstructs via kg_recon_viewPos",
                    pText.contains("kg_recon_viewPos"));
            assertFalse(helper, "position " + space + " does not need the pack normal", position.usesGeometry());
        }

        // Fresnel regression pin ("pure white sphere"): the snippet's OWN UBO dependencies must include
        // KG_Globals — the viewDir reconstruction needs ScreenSize, which the vanilla compile of the same
        // graph never registers. The material binds snippet.uniformBlocks() extras onto the injected
        // program; without them kg_recon_viewPos divides by an unbound (zero) ScreenSize -> NaN viewDir.
        var fresnel = injectionSnippet(inputNodeGraph(FresnelNode.class, null, null));
        assertTrue(helper, "fresnel snippet exists", fresnel != null);
        assertTrue(helper, "fresnel snippet depends on KG_Globals (ScreenSize for viewDir reconstruction)",
                fresnel.uniformBlocks().stream().anyMatch(b -> b.uboName().equals("KG_Globals")));
        assertTrue(helper, "fresnel snippet depends on KG_Transforms",
                fresnel.uniformBlocks().stream().anyMatch(b -> b.uboName().equals("KG_Transforms")));
        helper.succeed();
    }

    /**
     * The unified-UBO policy end-to-end: every formerly-rejecting node family (Fog UBO/values, Lighting,
     * MC Globals, Camera, Projection, Transform, Game Time) now compiles an injection snippet — nodes read
     * KilaGraph blocks (slice-views of Minecraft's buffers), never a fragment {@code #moj_import}. Also
     * pins the vanilla fragment GLSL to be include-free for these graphs (single-source unification), and
     * the Overlay/LightMap neutral degrade.
     */
    public static void injectionAllNodesCompatible(GameTestHelper helper) {
        record Case(String name, RenderTypeGraph graph) {}
        java.util.List<Case> cases = new java.util.ArrayList<>();

        // Fog family: raw fields, total value with UBO defaults, apply_fog with defaults.
        RenderTypeGraph fogUbo = new RenderTypeGraph();
        NodeModel fogEmission = addBlock(fogUbo, fogUbo.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel fog = addNode(fogUbo, FogUboNode.class);
        wire(fogUbo, fogEmission.getInputsById().get("color"), fog.getOutputsById().get("FogColor"));
        cases.add(new Case("fog ubo", fogUbo));

        RenderTypeGraph totalFog = new RenderTypeGraph();
        NodeModel tfAlpha = addBlock(totalFog, totalFog.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel total = addNode(totalFog, TotalFogValueNode.class);
        wire(totalFog, tfAlpha.getInputsById().get("alpha"), total.getOutputsById().get("out"));
        cases.add(new Case("total fog value", totalFog));

        RenderTypeGraph applyFog = new RenderTypeGraph();
        NodeModel afEmission = addBlock(applyFog, applyFog.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel apply = addNode(applyFog, ApplyFogNode.class);
        wire(applyFog, afEmission.getInputsById().get("color"), apply.getOutputsById().get("out"));
        cases.add(new Case("apply fog", applyFog));

        // Lighting: raw light directions.
        RenderTypeGraph lighting = new RenderTypeGraph();
        NodeModel liEmission = addBlock(lighting, lighting.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel light = addNode(lighting, com.lowdragmc.kilagraph.rendertype.nodes.lighting.LightUboNode.class);
        wire(lighting, liEmission.getInputsById().get("color"), light.getOutputsById().get("Light0_Direction"));
        cases.add(new Case("lighting ubo", lighting));

        // MC Globals fields (GlintAlpha has no other mirror).
        RenderTypeGraph globals = new RenderTypeGraph();
        NodeModel glAlpha = addBlock(globals, globals.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel glob = addNode(globals, GlobalsUboNode.class);
        wire(globals, glAlpha.getInputsById().get("alpha"), glob.getOutputsById().get("GlintAlpha"));
        cases.add(new Case("mc globals", globals));

        // Camera: Position (world camera) + Orthographic (ProjMat[3][3]).
        RenderTypeGraph camera = new RenderTypeGraph();
        NodeModel camEmission = addBlock(camera, camera.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel camAlpha = addBlock(camera, camera.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel cam = addNode(camera, CameraNode.class);
        wire(camera, camEmission.getInputsById().get("color"), cam.getOutputsById().get("Position"));
        wire(camera, camAlpha.getInputsById().get("alpha"), cam.getOutputsById().get("Orthographic"));
        cases.add(new Case("camera", camera));

        // Game Time (was minecraft:globals.glsl).
        cases.add(new Case("game time", inputNodeGraph(
                com.lowdragmc.kilagraph.rendertype.nodes.constant.GameTimeNode.class, null, null)));

        // Transform object->world for a position (ModelViewMat + IViewMat + camera pair).
        RenderTypeGraph transform = new RenderTypeGraph();
        NodeModel trEmission = addBlock(transform, transform.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel tr = addNode(transform, TransformNode.class);
        setOption(tr, "from", "object");
        setOption(tr, "to", "world");
        NodeModel vec = addNode(transform, Vec3Node.class);
        wire(transform, tr.getInputsById().get("in"), vec.getOutputsById().get("out"));
        wire(transform, trEmission.getInputsById().get("color"), tr.getOutputsById().get("out"));
        cases.add(new Case("transform", transform));

        for (Case c : cases) {
            var snippet = injectionSnippet(c.graph());
            assertTrue(helper, c.name() + " snippet exists (no rejection)", snippet != null);
            // Unified-UBO policy: the converted families never pull their old Minecraft includes into the
            // fragment. (dynamictransforms can still appear via the default graph's ColorModulator node.)
            String fsh = compile(c.graph()).fragmentSource();
            for (String include : new String[]{"minecraft:fog.glsl", "minecraft:light.glsl",
                    "minecraft:globals.glsl", "minecraft:projection.glsl"}) {
                assertFalse(helper, c.name() + " vanilla fsh does not import " + include,
                        fsh.contains("#moj_import <" + include + ">"));
            }
        }

        // Overlay/LightMap degrade to the all-white neutral sampler under injection (no Sampler1/Sampler2).
        for (var nodeClass : java.util.List.of(OverlayTextureNode.class, LightMapTextureNode.class)) {
            RenderTypeGraph g = new RenderTypeGraph();
            NodeModel emission = addBlock(g, g.getFragmentStageModel(), FragmentEmissionBlock.class);
            NodeModel tex = addNode(g, nodeClass);
            NodeModel sample = addNode(g, SamplerTexture2DNode.class);
            wire(g, sample.getInputsById().get("sampler"), tex.getOutputsById().get("sampler"));
            wire(g, emission.getInputsById().get("color"), sample.getOutputsById().get("color"));
            var snippet = injectionSnippet(g);
            assertTrue(helper, nodeClass.getSimpleName() + " snippet exists", snippet != null);
            String text = snippetText(snippet);
            assertTrue(helper, nodeClass.getSimpleName() + " degrades to the neutral white sampler",
                    text.contains(ShaderGraphCompiler.NEUTRAL_WHITE_SAMPLER));
            assertFalse(helper, nodeClass.getSimpleName() + " references no pipeline sampler",
                    text.contains("Sampler1") || text.contains("Sampler2"));
            // Regression pin (same class as the Fresnel/KG_Globals gap): the neutral sampler is baked ONLY
            // by the injection compile, so the snippet must carry its default for the material to bind it.
            assertTrue(helper, nodeClass.getSimpleName() + " snippet carries the neutral sampler default",
                    snippet.samplerDefaults().containsKey(ShaderGraphCompiler.NEUTRAL_WHITE_SAMPLER));
        }
        helper.succeed();
    }

    /**
     * The injection blacklist gate: a node that leaks a preview-quad varying into the fragment (the
     * VERTEX_ONLY attribute node's preview branch emits {@code vPos} — a latent leak, since injection
     * skips stage errors) is caught structurally and the snippet is rejected (null) instead of shipping
     * undefined GLSL into the shaderpack program.
     */
    public static void injectionBlacklistGate(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel attr = addNode(graph, VertexAttributeInputNode.class); // default = Position -> preview vPos
        wire(graph, emission.getInputsById().get("color"), attr.getOutputsById().get("out"));
        assertTrue(helper, "leaked preview varying rejects the snippet", injectionSnippet(graph) == null);
        helper.succeed();
    }

    /** The formerly fragment-only UV and Vertex Color nodes are now stage-agnostic: pulled into a vertex
     *  varying block they compile (no stage error) and read their raw vertex attributes in the vsh. */
    public static void uvAndVertexColorUsableInVertexStage(GameTestHelper helper) {
        // UV node → vertex varying block (vsh) → consumed in fragment. Previously FRAGMENT_ONLY = a stage error.
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel varying = addBlock(graph, graph.getVertexStageModel(), VaryingCustomVec3Block.class);
        NodeModel uv = addNode(graph, UVNode.class); // default channel uv0
        wire(graph, varying.getInputsById().get("value"), uv.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), varying.getOutputsById().get("value"));
        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "UV node in the vertex stage is not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "vsh reads the raw UV0 attribute", compiled.vertexSource().contains("UV0"));

        // Vertex Color node (raw color mode) → vertex varying block → no stage error, vsh reads the Color attribute.
        RenderTypeGraph graph2 = new RenderTypeGraph();
        NodeModel varying2 = addBlock(graph2, graph2.getVertexStageModel(), VaryingCustomVec3Block.class);
        NodeModel vc = addNode(graph2, VertexColorNode.class);
        setOption(vc, "mode", VertexColorNode.MODE_COLOR);
        wire(graph2, varying2.getInputsById().get("value"), vc.getOutputsById().get("out"));
        NodeModel emission2 = addBlock(graph2, graph2.getFragmentStageModel(), FragmentEmissionBlock.class);
        wire(graph2, emission2.getInputsById().get("color"), varying2.getOutputsById().get("value"));
        CompiledShaderGraph compiled2 = compile(graph2);
        assertFalse(helper, "Vertex Color node in the vertex stage is not a stage error", compiled2.hasStageErrors());
        assertTrue(helper, "vsh reads the raw Color attribute", compiled2.vertexSource().contains("Color"));
        helper.succeed();
    }

    /** A shared upstream node (the texture sample, apply_fog) must be emitted exactly once. */
    public static void sharedSubexpressionCompiledOnce(GameTestHelper helper) {
        CompiledShaderGraph compiled = compile(new RenderTypeGraph());
        String fsh = compiled.fragmentSource();
        assertEq(helper, "texture(...) emitted once", 1, count(fsh, "texture("));
        // apply_fog is now an inline function (unified-UBO policy): one definition + exactly one call.
        assertEq(helper, "apply_fog defined once", 1, count(fsh, "vec4 apply_fog("));
        assertEq(helper, "apply_fog called once (definition + call)", 2, count(fsh, "apply_fog("));
        helper.succeed();
    }

    /** An Emission block contributes an additive term to the base color. */
    public static void emissionBlockAddsToBaseColor(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        NodeModel vec3 = addNode(graph, Vec3Node.class);
        wire(graph, emission.getInputsById().get("color"), vec3.getOutputsById().get("out"));

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "emission adds to base color", fsh.contains("kg_baseColor += "));
        helper.succeed();
    }

    /** An Alpha Discard block emits a clip against the cutoff. */
    public static void alphaDiscardEmitsDiscard(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        addBlock(graph, fragment, FragmentAlphaDiscardBlock.class);

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "alpha discard emits guarded discard",
                fsh.contains("if (kg_alpha < ") && fsh.contains("discard;"));
        helper.succeed();
    }

    /** A custom interpolator becomes a vsh {@code out} and a matching fsh {@code in} of the same name. */
    public static void customInterpolatorCrossesStages(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel vertex = graph.getVertexStageModel();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel custom = addBlock(graph, vertex, VaryingCustomVec3Block.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        // route the custom vertex varying into a fragment consumer so the boundary is exercised
        wire(graph, emission.getInputsById().get("color"), custom.getOutputsById().get("value"));

        CompiledShaderGraph compiled = compile(graph);
        String vsh = compiled.vertexSource();
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "custom varying declared as vsh out", vsh.contains("out vec3 vc_"));
        assertTrue(helper, "custom varying read as fsh in", fsh.contains("in vec3 vc_"));
        // the same generated name on both sides
        String name = extractToken(vsh, "out vec3 ");
        assertTrue(helper, "matching varying name across stages", name != null && fsh.contains("in vec3 " + name + ";"));
        helper.succeed();
    }

    /** A float wired into a vec3 input is broadcast via a vec3(...) constructor. */
    public static void floatBroadcastsToVec3(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        NodeModel fmul = addNode(graph, MultiplyNode.class); // dynamic; float×float → float output
        wire(graph, emission.getInputsById().get("color"), fmul.getOutputsById().get("out"));

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "float broadcast to vec3 via constructor", fsh.contains("kg_baseColor += vec3("));
        helper.succeed();
    }

    /** A vec4 wired into a vec3 input is narrowed via a .xyz swizzle (default graph already does this). */
    public static void vec4SwizzlesToVec3(GameTestHelper helper) {
        // The default entity graph feeds a vec3 base color built from split floats; instead, wire a
        // vec4 (apply_fog output via a fresh sample) straight into a vec3 base-color block.
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel tex = addNode(graph, SamplerTexture2DNode.class); // vec4 output (unconnected sampler → missing)
        wire(graph, baseColor.getInputsById().get("color"), tex.getOutputsById().get("color"));

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "vec4 narrowed to vec3 via .xyz swizzle", fsh.contains(").xyz"));
        helper.succeed();
    }

    /** Sampler-typed values are never copied into a temp (illegal in GLSL). */
    public static void samplerIsNotHoisted(GameTestHelper helper) {
        CompiledShaderGraph compiled = compile(new RenderTypeGraph());
        String fsh = compiled.fragmentSource();
        // The only sampler2D token must be the uniform declaration, never an indented temp copy. The
        // default graph's texture comes from a Sampler2D constant (kg_tex_*).
        assertFalse(helper, "no sampler temp variable", fsh.contains("    sampler2D "));
        assertTrue(helper, "sampler declared as uniform", fsh.contains("uniform sampler2D kg_tex"));
        helper.succeed();
    }

    /** An unconnected sampler input falls back to a dedicated {@code kg_MissingSampler} (MC missing-texture). */
    public static void unconnectedSamplerFallsBackToMissing(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel tex = addNode(graph, SamplerTexture2DNode.class); // sampler input unconnected
        wire(graph, baseColor.getInputsById().get("color"), tex.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        assertTrue(helper, "samples kg_MissingSampler", compiled.fragmentSource().contains("texture(kg_MissingSampler"));
        assertTrue(helper, "kg_MissingSampler declared", compiled.layout().samplers().contains("kg_MissingSampler"));
        assertTrue(helper, "missing sampler has a baked default", compiled.samplerDefaults().containsKey("kg_MissingSampler"));
        helper.succeed();
    }

    /**
     * A {@code TextureNode} is a self-contained texture source: its {@code texture} option compiles to a
     * {@code uniform sampler2D kg_tex_*} (never a literal) whose baked {@link SamplerDefault} carries the
     * configured texture + sampler params (filter / address / mipmap).
     */
    public static void textureNodeBecomesUniform(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        var value = new RenderTypeGraphTypes.Sampler2DValue("minecraft:textures/block/stone.png",
                RenderTypeGraphTypes.SamplerMode.CUSTOM, RenderTypeGraphTypes.SamplerFilter.LINEAR,
                RenderTypeGraphTypes.SamplerAddress.REPEAT, true);
        NodeModel sampler = addNode(graph, TextureNode.class);
        setOption(sampler, "texture", value);
        NodeModel tex = addNode(graph, SamplerTexture2DNode.class);
        wire(graph, tex.getInputsById().get("sampler"), sampler.getOutputsById().get("sampler"));
        wire(graph, baseColor.getInputsById().get("color"), tex.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        // The default graph also has a TextureNode (dirt), so find ours by its stone texture.
        SamplerDefault def = compiled.samplerDefaults().values().stream()
                .filter(d -> d.texture().getPath().contains("stone")).findFirst().orElse(null);
        assertTrue(helper, "stone TextureNode bakes its texture", def != null);
        assertTrue(helper, "texture node is declared as a kg_tex uniform",
                compiled.fragmentSource().contains("uniform sampler2D kg_tex"));
        assertTrue(helper, "texture node keeps filter", def != null && def.filter() == RenderTypeGraphTypes.SamplerFilter.LINEAR);
        assertTrue(helper, "texture node keeps address", def != null && def.address() == RenderTypeGraphTypes.SamplerAddress.REPEAT);
        assertTrue(helper, "texture node keeps mipmap", def != null && def.mipmap());
        helper.succeed();
    }

    /** Overlay/LightMap nodes emit Sampler1/Sampler2 and flag the pipeline (replacing Settings toggles). */
    public static void overlayLightmapNodesFlagPipeline(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        NodeModel overlay = addNode(graph, OverlayTextureNode.class);
        NodeModel lightmap = addNode(graph, LightMapTextureNode.class);
        NodeModel texO = addNode(graph, SamplerTexture2DNode.class);
        NodeModel texL = addNode(graph, SamplerTexture2DNode.class);
        wire(graph, texO.getInputsById().get("sampler"), overlay.getOutputsById().get("sampler"));
        wire(graph, texL.getInputsById().get("sampler"), lightmap.getOutputsById().get("sampler"));
        wire(graph, baseColor.getInputsById().get("color"), texO.getOutputsById().get("color"));
        wire(graph, emission.getInputsById().get("color"), texL.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        assertTrue(helper, "usesOverlay flagged", compiled.usesOverlay());
        assertTrue(helper, "usesLightmap flagged", compiled.usesLightmap());
        assertTrue(helper, "samples Sampler1 (overlay)", compiled.fragmentSource().contains("texture(Sampler1"));
        assertTrue(helper, "samples Sampler2 (lightmap)", compiled.fragmentSource().contains("texture(Sampler2"));
        // Vanilla owns Sampler1/Sampler2 — no baked default registered for them.
        assertFalse(helper, "no baked default for Sampler1", compiled.samplerDefaults().containsKey("Sampler1"));
        assertFalse(helper, "no baked default for Sampler2", compiled.samplerDefaults().containsKey("Sampler2"));
        helper.succeed();
    }

    /** The expanded {@link RenderTypeGraphTypes.Sampler2DValue} round-trips through its codec (so it persists). */
    public static void sampler2DValueCodecRoundTrips(GameTestHelper helper) {
        var value = new RenderTypeGraphTypes.Sampler2DValue("minecraft:textures/block/dirt.png",
                RenderTypeGraphTypes.SamplerMode.ATLAS, RenderTypeGraphTypes.SamplerFilter.LINEAR,
                RenderTypeGraphTypes.SamplerAddress.REPEAT, true);
        var encoded = RenderTypeGraphTypes.SAMPLER2D_CODEC.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        assertTrue(helper, "encodes to NBT", encoded != null);
        var decoded = RenderTypeGraphTypes.SAMPLER2D_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        assertTrue(helper, "decodes equal to original", value.equals(decoded));
        helper.succeed();
    }

    /**
     * A constant Gradient node + Sample Gradient: the {@code KG_Gradient} struct + {@code kg_sampleGradient}
     * helper are declared (the struct before {@code main()}), a per-gradient builder is baked with the keys
     * (Fixed mode → {@code header.x == 1}), and the fragment samples it. GRADIENT is opaque (no temp copy).
     */
    public static void gradientNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel grad = addNode(graph, GradientNode.class);
        setOption(grad, "gradient", new RenderTypeGraphTypes.GradientValue(
                new GradientColor(0xFF000000, 0xFFFFFFFF), RenderTypeGraphTypes.BlendMode.FIXED));
        NodeModel sample = addNode(graph, SampleGradientNode.class);
        wire(graph, sample.getInputsById().get("gradient"), grad.getOutputsById().get("gradient"));
        wire(graph, baseColor.getInputsById().get("color"), sample.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "KG_Gradient struct declared", fsh.contains("struct KG_Gradient"));
        assertTrue(helper, "sample helper declared", fsh.contains("vec4 kg_sampleGradient(KG_Gradient"));
        assertTrue(helper, "per-gradient builder declared", fsh.contains("KG_Gradient kg_grad_"));
        assertTrue(helper, "Fixed mode bakes header.x = 1.0", fsh.contains("header = vec4(1.0,"));
        assertTrue(helper, "fragment samples the gradient", fsh.contains("kg_sampleGradient("));
        assertTrue(helper, "struct declared before main()",
                fsh.indexOf("struct KG_Gradient") < fsh.indexOf("void main"));
        helper.succeed();
    }

    /**
     * An EXPOSED Gradient variable becomes a {@code KG_Gradient} field in the KG_Material UBO — with the
     * struct declared <b>before</b> the UBO block (it references the type) — and its default gradient is
     * std140-packed (header + 8 colour + 8 alpha vec4 = 68 floats) into {@code uniformDefaults}.
     */
    public static void gradientVariableBecomesUboStruct(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        var rampVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Ramp", RenderTypeGraphTypes.GRADIENT,
                RenderTypeGraphTypes.GradientValue.defaultValue(), VariableKind.INPUT);
        rampVar.setScope(VariableScope.EXPOSED);
        var rampNode = graph.graphModel.createVariableNode(rampVar, new Vector2f(0, 0), null, null);
        NodeModel sample = addNode(graph, SampleGradientNode.class);
        wire(graph, sample.getInputsById().get("gradient"), rampNode.getOutputPort());
        wire(graph, baseColor.getInputsById().get("color"), sample.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "gradient var is a KG_Gradient UBO field", fsh.contains("KG_Gradient kg_Ramp;"));
        assertTrue(helper, "struct declared before the KG_Material UBO",
                fsh.indexOf("struct KG_Gradient") < fsh.indexOf("uniform KG_Material"));
        assertTrue(helper, "gradient uniform default recorded", compiled.uniformDefaults().containsKey("kg_Ramp"));
        assertEq(helper, "gradient packs 68 std140 floats", 68, compiled.uniformDefaults().get("kg_Ramp").length);
        assertTrue(helper, "variable name maps to its field for set-by-name",
                compiled.uniformFields().containsKey("Ramp"));
        helper.succeed();
    }

    /** A {@link RenderTypeGraphTypes.GradientValue} round-trips through its codec (so a gradient persists). */
    public static void gradientValueCodecRoundTrips(GameTestHelper helper) {
        var value = new RenderTypeGraphTypes.GradientValue(
                new GradientColor(0xFF112233, 0xFF445566, 0xFF778899), RenderTypeGraphTypes.BlendMode.FIXED);
        var encoded = RenderTypeGraphTypes.GRADIENT_CODEC.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        assertTrue(helper, "encodes to NBT", encoded != null);
        var decoded = RenderTypeGraphTypes.GRADIENT_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        assertTrue(helper, "decodes equal to original", value.equals(decoded));
        helper.succeed();
    }

    /**
     * A constant Curve node + Sample Curve (the CURVE twin of {@link #gradientNodesEmitGlsl}): the
     * {@code KG_Curve} struct + {@code kg_sampleCurve} helper are declared (struct before {@code main()}),
     * a per-curve builder is baked with the segments, and the fragment samples it (opaque — no temp copy).
     */
    public static void curveNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        NodeModel curve = addNode(graph, CurveNode.class);
        setOption(curve, "curve", RenderTypeGraphTypes.CurveValue.defaultValue().withBounds(0f, 2f));
        NodeModel sample = addNode(graph, SampleCurveNode.class);
        wire(graph, sample.getInputsById().get("curve"), curve.getOutputsById().get("curve"));
        wire(graph, baseColor.getInputsById().get("color"), sample.getOutputsById().get("value"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "KG_Curve struct declared", fsh.contains("struct KG_Curve"));
        assertTrue(helper, "sample helper declared", fsh.contains("float kg_sampleCurve(KG_Curve"));
        assertTrue(helper, "per-curve builder declared", fsh.contains("KG_Curve kg_curve_"));
        assertTrue(helper, "bounds baked into header", fsh.contains("c.header = vec4(1.0, 0.0, 2.0, 0.0);"));
        assertTrue(helper, "fragment samples the curve", fsh.contains("kg_sampleCurve("));
        assertTrue(helper, "struct declared before main()",
                fsh.indexOf("struct KG_Curve") < fsh.indexOf("void main"));
        helper.succeed();
    }

    /**
     * An EXPOSED Curve variable becomes a {@code KG_Curve} field in the KG_Material UBO (the CURVE twin of
     * {@link #gradientVariableBecomesUboStruct}) — struct declared before the UBO block — and its default
     * curve is std140-packed (header + 16 segment vec4 = 68 floats) into {@code uniformDefaults}.
     */
    public static void curveVariableBecomesUboStruct(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        var curveVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Fade", RenderTypeGraphTypes.CURVE,
                RenderTypeGraphTypes.CurveValue.defaultValue(), VariableKind.INPUT);
        curveVar.setScope(VariableScope.EXPOSED);
        var curveNode = graph.graphModel.createVariableNode(curveVar, new Vector2f(0, 0), null, null);
        NodeModel sample = addNode(graph, SampleCurveNode.class);
        wire(graph, sample.getInputsById().get("curve"), curveNode.getOutputPort());
        wire(graph, baseColor.getInputsById().get("color"), sample.getOutputsById().get("value"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "curve var is a KG_Curve UBO field", fsh.contains("KG_Curve kg_Fade;"));
        assertTrue(helper, "struct declared before the KG_Material UBO",
                fsh.indexOf("struct KG_Curve") < fsh.indexOf("uniform KG_Material"));
        assertTrue(helper, "curve uniform default recorded", compiled.uniformDefaults().containsKey("kg_Fade"));
        assertEq(helper, "curve packs 68 std140 floats", 68, compiled.uniformDefaults().get("kg_Fade").length);
        assertTrue(helper, "variable name maps to its field for set-by-name",
                compiled.uniformFields().containsKey("Fade"));
        helper.succeed();
    }

    /** A {@link RenderTypeGraphTypes.CurveValue} round-trips through its codec, and its CPU evaluation
     *  holds first/last y outside the key range (the same contract the GLSL sampler implements). */
    public static void curveValueCodecRoundTrips(GameTestHelper helper) {
        var segments = new java.util.ArrayList<ExplicitCubicBezierCurve2>();
        segments.add(new ExplicitCubicBezierCurve2(
                new Vector2f(0.2f, 0.1f), new Vector2f(0.4f, 0.9f),
                new Vector2f(0.6f, 0.9f), new Vector2f(0.8f, 0.3f)));
        var value = new RenderTypeGraphTypes.CurveValue(segments, -1f, 3f);
        var encoded = RenderTypeGraphTypes.CURVE_CODEC.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        assertTrue(helper, "encodes to NBT", encoded != null);
        var decoded = RenderTypeGraphTypes.CURVE_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        assertTrue(helper, "decodes equal to original", value.equals(decoded));
        assertTrue(helper, "before-first holds first y", Math.abs(value.getCurveY(0f) - 0.1f) < 1e-5f);
        assertTrue(helper, "after-last holds last y", Math.abs(value.getCurveY(1f) - 0.3f) < 1e-5f);
        assertTrue(helper, "sample remaps into [lower, upper]",
                Math.abs(value.sample(0f) - (-1f + 4f * 0.1f)) < 1e-4f);
        helper.succeed();
    }

    /**
     * A connection routed through a <b>wire portal</b> (the editor's "convert wire to portal" feature)
     * is still followed by the compiler: the upstream value reaches the consumer. The compiler must
     * resolve the portal (entry↔exit), not stop at the portal node. Without the fix the consumer would
     * read 0 (the portal node isn't a ShaderNode).
     */
    public static void wirePortalRoutesConnection(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        NodeModel vec3 = addNode(graph, Vec3Node.class);
        setInputConstant(vec3, "x", 0.25f);
        setInputConstant(vec3, "y", 0.5f);
        setInputConstant(vec3, "z", 0.75f);

        // Wire vec3 -> emission, then convert that wire into a portal pair (entry on vec3, exit on emission).
        var w = graph.graphModel.createWire(emission.getInputsById().get("color"), vec3.getOutputsById().get("out"));
        graph.graphModel.createPortalsFromWire(w, new Vector2f(0, 0), new Vector2f(0, 0), 12,
                new java.util.HashMap<>(), new java.util.HashMap<>());

        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "portal-routed vec3 value reaches emission (0.25 present)", fsh.contains("0.25"));
        helper.succeed();
    }

    /** A Time node pulls from the engine-globals block (KG_Globals.Time), updated by us each frame. */
    public static void timeNodeUsesEngineGlobals(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel alpha = addBlock(graph, fragment, com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock.class);
        NodeModel time = addNode(graph, TimeNode.class);
        wire(graph, alpha.getInputsById().get("alpha"), time.getOutputsById().get("time"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();

        assertTrue(helper, "graph uses engine globals", usesUniformBlock(compiled, "KG_Globals"));
        assertTrue(helper, "fsh declares KG_Globals block",
                fsh.contains("layout(std140) uniform "
                        + com.lowdragmc.kilagraph.rendertype.runtime.KGEngineUniforms.UBO_NAME));
        assertTrue(helper, "fsh declares Time member", fsh.contains("float Time;"));
        assertTrue(helper, "fsh references kg_globals.Time", fsh.contains("kg_globals.Time"));
        helper.succeed();
    }

    /**
     * compilePreview emits a flat-quad preview shader for an arbitrary output port: mesh uv becomes
     * the quad's {@code vUv}, the subgraph is folded in, and the port value drives {@code fragColor}.
     */
    public static void previewCompilesPortSubgraph(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel uv = addNode(graph, UVNode.class);
        NodeModel tiling = addNode(graph, TilingAndOffsetNode.class);
        NodeModel tex = addNode(graph, SamplerTexture2DNode.class); // unconnected sampler → missing
        wire(graph, tiling.getInputsById().get("uv"), uv.getOutputsById().get("out"));
        wire(graph, tex.getInputsById().get("uv"), tiling.getOutputsById().get("out"));

        // Preview of the texture-sample output: full upstream chain (UV -> Tiling -> sample).
        CompiledShaderGraph preview = new ShaderGraphCompiler(graph)
                .compilePreview(tex.getOutputsById().get("color"));
        String vsh = preview.vertexSource();
        String fsh = preview.fragmentSource();

        assertTrue(helper, "preview vsh passes quad uv", vsh.contains("out vec2 vUv;") && vsh.contains("vUv = UV0;"));
        assertTrue(helper, "preview fsh reads vUv", fsh.contains("in vec2 vUv;"));
        assertTrue(helper, "preview samples missing sampler", fsh.contains("texture(kg_MissingSampler"));
        assertTrue(helper, "preview uv chain uses quad uv", fsh.contains("vUv"));
        assertTrue(helper, "preview writes fragColor", fsh.contains("fragColor = "));
        assertTrue(helper, "preview registers missing sampler", preview.layout().samplers().contains("kg_MissingSampler"));
        assertFalse(helper, "preview has no kg_uv varying (uses vUv)", fsh.contains("kg_uv"));

        // Preview of a vec2 (UV) output is converted to a vec4 colour (the value is hoisted to a temp).
        CompiledShaderGraph uvPreview = new ShaderGraphCompiler(graph)
                .compilePreview(uv.getOutputsById().get("out"));
        String uvFsh = uvPreview.fragmentSource();
        assertTrue(helper, "vec2 preview padded to an opaque vec4", uvFsh.contains("fragColor = vec4(") && uvFsh.contains(", 1.0);"));
        assertTrue(helper, "uv preview sources the quad uv", uvFsh.contains("= vUv;"));
        helper.succeed();
    }

    /**
     * A VERTEX_ONLY node (Normal) pulled into the fragment stage is flagged as a stage error; the same
     * node feeding a vertex varying block is fine.
     */
    public static void stageAffinityFlagsMisuse(GameTestHelper helper) {
        // Misuse: Normal -> fragment base color (fragment stage) → error.
        RenderTypeGraph bad = new RenderTypeGraph();
        NodeModel fragment = bad.getFragmentStageModel();
        NodeModel baseColor = addBlock(bad, fragment, FragmentBaseColorBlock.class);
        NodeModel normal = addNode(bad, VertexAttributeInputNode.class);
        wire(bad, baseColor.getInputsById().get("color"), normal.getOutputsById().get("out"));
        CompiledShaderGraph badCompiled = new ShaderGraphCompiler(bad).compile();
        assertTrue(helper, "vertex attribute in fragment stage is a stage error", badCompiled.hasStageErrors());
        assertTrue(helper, "error names the vertex attribute node",
                badCompiled.stageErrors().stream().anyMatch(e -> e.nodeName().contains("Vertex Attribute")));

        // Correct: Normal -> a custom vec4 varying block input (vertex stage) → no error.
        RenderTypeGraph good = new RenderTypeGraph();
        NodeModel vertex = good.getVertexStageModel();
        NodeModel colorVarying = addBlock(good, vertex, VaryingCustomVec4Block.class);
        NodeModel n2 = addNode(good, VertexAttributeInputNode.class);
        NodeModel toVec4 = addNode(good, MultiplyNode.class);
        // feed Normal (vec3 -> vec4 via convert) into the varying's input (computed in vsh)
        wire(good, toVec4.getInputsById().get("a"), n2.getOutputsById().get("out"));
        wire(good, colorVarying.getInputsById().get("value"), toVec4.getOutputsById().get("out"));
        // also consume the varying in fragment so the vertex subgraph is actually compiled
        NodeModel baseColor2 = addBlock(good, good.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(good, baseColor2.getInputsById().get("color"), colorVarying.getOutputsById().get("value"));
        CompiledShaderGraph goodCompiled = new ShaderGraphCompiler(good).compile();
        assertFalse(helper, "Normal feeding a vertex varying is not a stage error", goodCompiled.hasStageErrors());
        helper.succeed();
    }

    /**
     * A Blackboard variable's scope drives its GLSL: an EXPOSED scalar/vector becomes a KG_Material
     * uniform field (with its declared default baked into {@code uniformDefaults}); a LOCAL one is
     * inlined as a literal (no uniform field).
     */
    public static void variableScopeDrivesUniformVsInline(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        // EXPOSED float "Tint" (default 0.5) -> KG_Material uniform, wired into the alpha block.
        var tintVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Tint", TypeHandles.FLOAT, 0.5f, VariableKind.INPUT);
        tintVar.setScope(VariableScope.EXPOSED);
        var tintNode = graph.graphModel.createVariableNode(tintVar, new Vector2f(0, 0), null, null);
        NodeModel alpha = addBlock(graph, fragment, com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), tintNode.getOutputPort());

        // LOCAL vec3 "Glow" (default (0.1,0.2,0.3)) -> inlined literal, wired into the base color block.
        var glowVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Glow", RenderTypeGraphTypes.VEC3, new org.joml.Vector3f(0.1f, 0.2f, 0.3f),
                VariableKind.INPUT);
        glowVar.setScope(VariableScope.LOCAL);
        var glowNode = graph.graphModel.createVariableNode(glowVar, new Vector2f(0, 200), null, null);
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), glowNode.getOutputPort());

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();

        // EXPOSED -> uniform field kg_Tint in KG_Material, with the default baked in.
        assertTrue(helper, "exposed var declared in KG_Material",
                fsh.contains("layout(std140) uniform "
                        + com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout.UBO_NAME));
        assertTrue(helper, "exposed float uniform field present", fsh.contains("float kg_Tint;"));
        assertTrue(helper, "layout has exactly the one exposed field", compiled.layout().fields().size() == 1);
        assertEq(helper, "exposed field name", "kg_Tint", compiled.layout().fields().getFirst().name());
        assertTrue(helper, "uniform default recorded", compiled.uniformDefaults().containsKey("kg_Tint"));
        assertEq(helper, "uniform default value", 0.5f, compiled.uniformDefaults().get("kg_Tint")[0], 1e-6f);

        // LOCAL -> inlined vec3 literal, no uniform field, not in uniformDefaults.
        assertTrue(helper, "local vec3 inlined as literal", fsh.contains("vec3(0.1"));
        assertFalse(helper, "local var has no uniform field", fsh.contains("kg_Glow"));
        assertEq(helper, "only one uniform default", 1, compiled.uniformDefaults().size());
        helper.succeed();
    }

    /** A Sampler2D variable is ALWAYS a uniform sampler (opaque type), regardless of scope. */
    public static void samplerVariableBecomesUniform(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        // A LOCAL Sampler2D var with a parseable default texture -> still a uniform + a sampler default.
        var texVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Albedo", RenderTypeGraphTypes.SAMPLER2D,
                RenderTypeGraphTypes.Sampler2DValue.defaultValue().withLocation("minecraft:textures/block/stone.png"),
                VariableKind.INPUT);
        texVar.setScope(VariableScope.LOCAL);
        var texNode = graph.graphModel.createVariableNode(texVar, new Vector2f(0, 0), null, null);
        NodeModel sample = addNode(graph, SamplerTexture2DNode.class);
        wire(graph, sample.getInputsById().get("sampler"), texNode.getOutputPort());
        wire(graph, baseColor.getInputsById().get("color"), sample.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "sampler var declared as uniform", fsh.contains("uniform sampler2D kg_Albedo;"));
        assertTrue(helper, "sampler registered in layout", compiled.layout().samplers().contains("kg_Albedo"));
        assertTrue(helper, "sampler value never copied into a temp", !fsh.contains("    sampler2D "));
        assertTrue(helper, "default texture recorded", compiled.samplerDefaults().containsKey("kg_Albedo"));
        helper.succeed();
    }

    /**
     * A {@link com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles#COLOR} variable edits as
     * an ARGB color-picker but compiles to a {@code vec4} (EXPOSED → KG_Material uniform), with the
     * ARGB default unpacked into rgba components.
     */
    public static void colorVariableCompilesToVec4Uniform(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        // ARGB 0x80FF8040: a=128/255, r=255/255, g=128/255, b=64/255.
        int argb = 0x80FF8040;
        var colorVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Accent",
                com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.COLOR, argb,
                VariableKind.INPUT);
        colorVar.setScope(VariableScope.EXPOSED);
        var colorNode = graph.graphModel.createVariableNode(colorVar, new Vector2f(0, 0), null, null);
        wire(graph, baseColor.getInputsById().get("color"), colorNode.getOutputPort());

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();

        assertTrue(helper, "color var is a vec4 uniform field", fsh.contains("vec4 kg_Accent;"));
        assertTrue(helper, "color uniform default recorded", compiled.uniformDefaults().containsKey("kg_Accent"));
        float[] rgba = compiled.uniformDefaults().get("kg_Accent");
        assertEq(helper, "color default has 4 components", 4, rgba.length);
        assertEq(helper, "color default r", 1.0f, rgba[0], 1e-4f);
        assertEq(helper, "color default g", 128f / 255f, rgba[1], 1e-4f);
        assertEq(helper, "color default b", 64f / 255f, rgba[2], 1e-4f);
        assertEq(helper, "color default a", 128f / 255f, rgba[3], 1e-4f);
        helper.succeed();
    }

    /**
     * A {@link com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles#HDR_COLOR} variable also
     * compiles to a {@code vec4} uniform, but its default is baked <em>premultiplied</em>: the intensity
     * folds into rgb (so components may exceed 1) while alpha is left alone. That is the one place the
     * {@link com.lowdragmc.lowdraglib2.math.HDRColor} convention meets the std140 KG_Material packing.
     */
    public static void hdrColorVariableCompilesToPremultipliedVec4Uniform(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);

        // base (1, 0.5, 0.25) @ a=0.5, intensity 4 → rgb premultiplied to (4, 2, 1), alpha untouched.
        var hdr = new com.lowdragmc.lowdraglib2.math.HDRColor(1f, 0.5f, 0.25f, 0.5f, 4f);
        var hdrVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Emission",
                com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.HDR_COLOR, hdr,
                VariableKind.INPUT);
        hdrVar.setScope(VariableScope.EXPOSED);
        var hdrNode = graph.graphModel.createVariableNode(hdrVar, new Vector2f(0, 0), null, null);
        wire(graph, baseColor.getInputsById().get("color"), hdrNode.getOutputPort());

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();

        assertTrue(helper, "hdr color var is a vec4 uniform field", fsh.contains("vec4 kg_Emission;"));
        var field = compiled.uniformFields().get("Emission");
        assertTrue(helper, "hdr color mapped to a uniform field", field != null);
        assertTrue(helper, "hdr color field type is VEC4",
                field.type() == com.lowdragmc.kilagraph.rendertype.compiler.GlslType.VEC4);
        assertTrue(helper, "hdr color uniform default recorded", compiled.uniformDefaults().containsKey("kg_Emission"));
        float[] rgba = compiled.uniformDefaults().get("kg_Emission");
        assertEq(helper, "hdr default has 4 components", 4, rgba.length);
        assertEq(helper, "hdr default r premultiplied", 4.0f, rgba[0], 1e-4f);
        assertEq(helper, "hdr default g premultiplied", 2.0f, rgba[1], 1e-4f);
        assertEq(helper, "hdr default b premultiplied", 1.0f, rgba[2], 1e-4f);
        assertEq(helper, "hdr default a untouched by intensity", 0.5f, rgba[3], 1e-4f);
        helper.succeed();
    }

    /**
     * The compiler exposes a variable display-name → uniform-field / sampler-name mapping on
     * {@link CompiledShaderGraph}, so the runtime can offer set-by-name uniform/texture updates without
     * callers knowing the mangled {@code kg_*} identifiers. EXPOSED scalar/vec/color vars land in
     * {@code uniformFields} (name + GlslType); Sampler2D vars land in {@code variableSamplers}.
     */
    public static void uniformFieldMappingExposesVariableNames(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        // EXPOSED float "Tint" → kg_Tint (FLOAT), wired into alpha.
        var tintVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Tint", TypeHandles.FLOAT, 0.5f, VariableKind.INPUT);
        tintVar.setScope(VariableScope.EXPOSED);
        var tintNode = graph.graphModel.createVariableNode(tintVar, new Vector2f(0, 0), null, null);
        NodeModel alpha = addBlock(graph, fragment, com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), tintNode.getOutputPort());

        // EXPOSED COLOR "Accent" → kg_Accent (VEC4), wired into base color.
        var accentVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Accent", com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.COLOR, 0xFFFFFFFF,
                VariableKind.INPUT);
        accentVar.setScope(VariableScope.EXPOSED);
        var accentNode = graph.graphModel.createVariableNode(accentVar, new Vector2f(0, 120), null, null);
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), accentNode.getOutputPort());

        // Sampler2D "Albedo" → kg_Albedo, wired through a texture sample into emission.
        var texVar = (VariableDeclarationModelBase) graph.graphModel.createVariable(
                "Albedo", RenderTypeGraphTypes.SAMPLER2D,
                RenderTypeGraphTypes.Sampler2DValue.defaultValue().withLocation("minecraft:textures/block/stone.png"),
                VariableKind.INPUT);
        texVar.setScope(VariableScope.LOCAL);
        var texNode = graph.graphModel.createVariableNode(texVar, new Vector2f(0, 240), null, null);
        NodeModel sample = addNode(graph, SamplerTexture2DNode.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, sample.getInputsById().get("sampler"), texNode.getOutputPort());
        wire(graph, emission.getInputsById().get("color"), sample.getOutputsById().get("color"));

        CompiledShaderGraph compiled = compile(graph);

        var tintField = compiled.uniformFields().get("Tint");
        assertTrue(helper, "Tint mapped to a uniform field", tintField != null);
        assertEq(helper, "Tint field name", "kg_Tint", tintField.name());
        assertTrue(helper, "Tint field type is FLOAT",
                tintField.type() == com.lowdragmc.kilagraph.rendertype.compiler.GlslType.FLOAT);

        var accentField = compiled.uniformFields().get("Accent");
        assertTrue(helper, "Accent mapped to a uniform field", accentField != null);
        assertEq(helper, "Accent field name", "kg_Accent", accentField.name());
        assertTrue(helper, "Accent field type is VEC4",
                accentField.type() == com.lowdragmc.kilagraph.rendertype.compiler.GlslType.VEC4);

        assertTrue(helper, "Albedo mapped to a sampler", compiled.variableSamplers().containsKey("Albedo"));
        assertEq(helper, "Albedo sampler name", "kg_Albedo", compiled.variableSamplers().get("Albedo"));
        // A Sampler2D var is not a UBO field.
        assertTrue(helper, "Albedo is not a uniform field", !compiled.uniformFields().containsKey("Albedo"));
        helper.succeed();
    }

    /**
     * The default entity shader is lit out of the box: its (lit) {@code VertexColorNode} resolves through
     * {@code ctx.litVertexColor()} to vanilla per-vertex diffuse lighting ({@code minecraft_mix_light}),
     * importing {@code minecraft:light.glsl} — the same default the old vertex Color block carried.
     */
    public static void unconnectedVertexColorDefaultsToMixLight(GameTestHelper helper) {
        CompiledShaderGraph compiled = compile(new RenderTypeGraph());
        String vsh = compiled.vertexSource();
        assertTrue(helper, "vsh applies minecraft_mix_light by default", vsh.contains("minecraft_mix_light("));
        assertTrue(helper, "vsh imports light.glsl", vsh.contains("#moj_import <minecraft:light.glsl>"));
        helper.succeed();
    }

    /**
     * A chain of scalar math nodes (Abs→Sin→Add(+Length)→Min→Lerp→Pow→Clamp→alpha) compiles each to
     * its GLSL builtin / operator. Confirms the unary/binary/ternary node families wire and emit.
     */
    public static void mathNodesEmitGlslCalls(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        NodeModel abs = addNode(graph, AbsNode.class);
        NodeModel sin = addNode(graph, SinNode.class);
        wire(graph, sin.getInputsById().get("a"), abs.getOutputsById().get("out"));
        NodeModel length = addNode(graph, LengthNode.class);          // vec3 -> float
        NodeModel add = addNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("a"), sin.getOutputsById().get("out"));
        wire(graph, add.getInputsById().get("b"), length.getOutputsById().get("out"));
        NodeModel min = addNode(graph, MinNode.class);
        NodeModel lerp = addNode(graph, LerpNode.class);
        wire(graph, lerp.getInputsById().get("a"), add.getOutputsById().get("out"));
        wire(graph, lerp.getInputsById().get("t"), min.getOutputsById().get("out"));
        NodeModel pow = addNode(graph, PowNode.class);
        wire(graph, pow.getInputsById().get("a"), lerp.getOutputsById().get("out"));
        NodeModel clamp = addNode(graph, ClampNode.class);
        wire(graph, clamp.getInputsById().get("value"), pow.getOutputsById().get("out"));
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), clamp.getOutputsById().get("out"));

        String fsh = compile(graph).fragmentSource();
        for (String fn : new String[]{"abs(", "sin(", "length(", "min(", "mix(", "pow(", "clamp("}) {
            assertTrue(helper, "fsh emits " + fn, fsh.contains(fn));
        }
        assertTrue(helper, "add emits + operator", fsh.contains(" + "));
        helper.succeed();
    }

    /**
     * A Dynamic-type math node infers its output width from the operands: a Multiply fed a float and a
     * vec3 produces a {@code vec3} result (the float is broadcast), while two floats keep a {@code float}
     * result. Confirms the compile-time width inference + single-evaluation hoist.
     */
    public static void dynamicMathInfersWidth(GameTestHelper helper) {
        // float (Sin output) × vec3 (Vec3 node) -> the Multiply result is hoisted as a vec3 temp.
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel mul = addNode(graph, MultiplyNode.class);
        NodeModel sinF = addNode(graph, SinNode.class);                 // float
        NodeModel vec3 = addNode(graph, Vec3Node.class);                // vec3
        wire(graph, mul.getInputsById().get("a"), sinF.getOutputsById().get("out"));
        wire(graph, mul.getInputsById().get("b"), vec3.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), mul.getOutputsById().get("out"));
        String fsh = compile(graph).fragmentSource();
        assertTrue(helper, "float×vec3 multiply hoisted as a vec3 temp", fsh.contains("    vec3 f_"));

        // float × float -> a float result (no vec temp introduced for the product).
        RenderTypeGraph scalar = new RenderTypeGraph();
        NodeModel sFrag = scalar.getFragmentStageModel();
        NodeModel addS = addNode(scalar, AddNode.class);                // both inputs unconnected floats
        NodeModel alpha = addBlock(scalar, sFrag, FragmentAlphaBlock.class);
        wire(scalar, alpha.getInputsById().get("alpha"), addS.getOutputsById().get("out"));
        String sfsh = compile(scalar).fragmentSource();
        assertTrue(helper, "float+float add hoisted as a float temp", sfsh.contains("    float f_"));
        helper.succeed();
    }

    /** Construct→Transform→Split of a mat4 emits {@code mat4(}, a {@code m * v} transform, and column reads. */
    public static void matrixNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel construct = addNode(graph, Mat4ConstructNode.class);  // 4 unconnected vec4 columns
        NodeModel transform = addNode(graph, Mat4TransformNode.class);  // mat4 × vec4 -> vec4
        wire(graph, transform.getInputsById().get("m"), construct.getOutputsById().get("out"));
        NodeModel split = addNode(graph, Mat4SplitNode.class);          // mat4 -> 4 vec4
        wire(graph, split.getInputsById().get("in"), construct.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), transform.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), split.getOutputsById().get("c0"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "matrix graph has no stage errors", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "constructs a mat4", fsh.contains("mat4("));
        assertTrue(helper, "transforms a vec4 by the matrix", fsh.contains(" * "));
        assertTrue(helper, "reads a matrix column", fsh.contains(")[0]"));
        helper.succeed();
    }

    /** A derivative node compiles to its GLSL builtin and, in the fragment stage, raises no stage error. */
    public static void derivativeNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel uv = addNode(graph, UVNode.class); // vec2, fragment-available
        NodeModel ddx = addNode(graph, DDXNode.class);
        wire(graph, ddx.getInputsById().get("a"), uv.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), ddx.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "fragment-stage derivative is not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "emits dFdx", compiled.fragmentSource().contains("dFdx("));
        helper.succeed();
    }

    /**
     * Previewing a node fed by a VERTEX_ONLY {@code VertexAttributeInputNode} must not record a stage
     * error (a preview is a single fragment quad), and the attribute resolves to a fragment-safe default
     * — so the thumbnail compiles instead of going blank. Regression for the preview-recompile bug.
     */
    public static void previewOfVertexAttributeHasNoStageError(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel attr = addNode(graph, VertexAttributeInputNode.class); // default element: position (VERTEX_ONLY)
        NodeModel add = addNode(graph, AddNode.class);
        wire(graph, add.getInputsById().get("a"), attr.getOutputsById().get("out"));

        CompiledShaderGraph preview = new ShaderGraphCompiler(graph).compilePreview(add.getOutputsById().get("out"));
        assertFalse(helper, "preview of a vertex attribute has no stage error", preview.hasStageErrors());
        String fsh = preview.fragmentSource();
        assertTrue(helper, "preview writes fragColor", fsh.contains("fragColor = "));
        assertFalse(helper, "preview fsh does not reference raw Position attribute", fsh.contains("vec4(Position"));
        helper.succeed();
    }

    /** The newly added Unity math nodes (Posterize/Sphere Mask/wave/Rejection) emit their GLSL formulas. */
    public static void extraMathNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel poster = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.PosterizeNode.class);
        NodeModel saw = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.wave.SawtoothWaveNode.class);
        wire(graph, poster.getInputsById().get("in"), saw.getOutputsById().get("out"));
        NodeModel mask = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.vector.SphereMaskNode.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), poster.getOutputsById().get("out"));
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), mask.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "extra math graph has no stage errors", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "posterize emits floor(.../steps", fsh.contains("floor("));
        assertTrue(helper, "sawtooth emits its ramp", fsh.contains(" - floor(0.5 + "));
        assertTrue(helper, "sphere mask emits distance()", fsh.contains("distance("));
        helper.succeed();
    }

    /**
     * A Transform(object→world) of a vec3 sets {@code usesTransforms}, declares the KG_Transforms block,
     * and references the world-space matrices (ModelViewMat in the vsh path, ViewMat/CameraPos from our
     * block) — confirming the precomputed-matrix UBO is wired end to end.
     */
    public static void transformNodeUsesSpaceMatrices(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel transform = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode.class);
        // defaults: from=object, to=world, type=position
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), transform.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "transform graph has no stage errors", compiled.hasStageErrors());
        assertTrue(helper, "graph flags usesTransforms", usesUniformBlock(compiled, "KG_Transforms"));
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "fsh declares KG_Transforms", fsh.contains("uniform KG_Transforms"));
        assertTrue(helper, "fsh references ModelViewMat (object->view)", fsh.contains("ModelViewMat"));
        // object->world = ModelViewMat (to view) then IViewMat + camera position (view to absolute world);
        // the camera position comes from MC's globals.glsl (precision-split), not our KG block.
        assertTrue(helper, "fsh references the inverse camera view matrix", fsh.contains("kg_transforms.IViewMat"));
        assertTrue(helper, "fsh reads the camera position from globals", fsh.contains("CameraBlockPos"));

        // clip→view uses the precomputed inverse projection (kg_transforms.IProjMat), not a per-pixel inverse.
        RenderTypeGraph clipGraph = new RenderTypeGraph();
        NodeModel clipFrag = clipGraph.getFragmentStageModel();
        NodeModel clipT = addNode(clipGraph, com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode.class);
        setOption(clipT, "from", "clip");
        setOption(clipT, "to", "view");
        NodeModel clipEmit = addBlock(clipGraph, clipFrag, FragmentEmissionBlock.class);
        wire(clipGraph, clipEmit.getInputsById().get("color"), clipT.getOutputsById().get("out"));
        String clipFsh = compile(clipGraph).fragmentSource();
        assertTrue(helper, "clip source uses precomputed IProjMat", clipFsh.contains("kg_transforms.IProjMat"));
        assertFalse(helper, "no per-pixel inverse(ProjMat)", clipFsh.contains("inverse(ProjMat)"));

        // clip→world (position) reconstructs a world position from an NDC/depth sample: the inverse
        // projection (IProjMat) does the perspective divide (.xyz / .w), then view→world via IViewMat +
        // the camera position. Without the divide the result would be wrong under perspective.
        RenderTypeGraph clipWorldGraph = new RenderTypeGraph();
        NodeModel cwFrag = clipWorldGraph.getFragmentStageModel();
        NodeModel cwT = addNode(clipWorldGraph, com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode.class);
        setOption(cwT, "from", "clip");
        setOption(cwT, "to", "world"); // type defaults to position
        NodeModel cwEmit = addBlock(clipWorldGraph, cwFrag, FragmentEmissionBlock.class);
        wire(clipWorldGraph, cwEmit.getInputsById().get("color"), cwT.getOutputsById().get("out"));
        String cwFsh = compile(clipWorldGraph).fragmentSource();
        assertTrue(helper, "clip→world inverse-projects via IProjMat", cwFsh.contains("kg_transforms.IProjMat"));
        assertTrue(helper, "clip→world does the perspective divide", cwFsh.contains(".xyz / ") && cwFsh.contains(".w"));
        assertTrue(helper, "clip→world un-rotates via IViewMat", cwFsh.contains("kg_transforms.IViewMat"));
        assertTrue(helper, "clip→world adds the camera position", cwFsh.contains("CameraBlockPos"));
        helper.succeed();
    }

    /** The merged Exponential/Log nodes pick their GLSL variant from a {@code base} option dropdown. */
    public static void expLogBaseOptionDrivesGlsl(GameTestHelper helper) {
        // Default Exponential = exp; switching base to 2 = exp2.
        assertTrue(helper, "default exp emits exp(", expFsh(null).contains("exp("));
        assertTrue(helper, "base-2 exp emits exp2(", expFsh("2").contains("exp2("));
        // Default Log = log; base 2 = log2; base 10 = log(x)/log(10.0).
        assertTrue(helper, "default log emits log(", logFsh(null).contains("log("));
        assertTrue(helper, "base-2 log emits log2(", logFsh("2").contains("log2("));
        assertTrue(helper, "base-10 log divides by log(10.0)", logFsh("10").contains("/ log(10.0)"));
        helper.succeed();
    }

    private static String expFsh(String base) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel exp = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.ExpNode.class);
        if (base != null) setOption(exp, "base", base);
        NodeModel alpha = addBlock(graph, graph.getFragmentStageModel(), FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), exp.getOutputsById().get("out"));
        return compile(graph).fragmentSource();
    }

    private static String logFsh(String base) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel log = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.LogNode.class);
        if (base != null) setOption(log, "base", base);
        NodeModel alpha = addBlock(graph, graph.getFragmentStageModel(), FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), log.getOutputsById().get("out"));
        return compile(graph).fragmentSource();
    }

    /**
     * The world-position-grid Transform graph the {@code /kilagraph_shadertest transform} debug command
     * builds: {@code Position attr → Transform(object,world) → custom vec3 varying}, then fragment
     * {@code fract(world) → base color}. Guards that it compiles with no stage errors (Position is
     * VERTEX_ONLY but crosses to fragment via the varying) and reflects the world matrices, so a client
     * launch isn't wasted on a broken graph.
     */
    public static void worldGridTransformGraphCompiles(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel vertexStage = graph.getVertexStageModel();
        NodeModel posAttr = addNode(graph, VertexAttributeInputNode.class); // default element = position
        NodeModel transform = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.vector.TransformNode.class);
        setOption(transform, "from", "object");
        setOption(transform, "to", "world");
        NodeModel varying = addBlock(graph, vertexStage, com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingCustomVec3Block.class);
        wire(graph, transform.getInputsById().get("in"), posAttr.getOutputsById().get("out"));
        wire(graph, varying.getInputsById().get("value"), transform.getOutputsById().get("out"));
        NodeModel fract = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.range.FractNode.class);
        wire(graph, fract.getInputsById().get("a"), varying.getOutputsById().get("value"));
        NodeModel baseColor = addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), fract.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "world-grid graph has no stage errors", compiled.hasStageErrors());
        assertTrue(helper, "world-grid graph flags usesTransforms", usesUniformBlock(compiled, "KG_Transforms"));
        assertTrue(helper, "vsh transforms to world via IViewMat", compiled.vertexSource().contains("kg_transforms.IViewMat"));
        assertTrue(helper, "fsh fracts the interpolated world position", compiled.fragmentSource().contains("fract("));
        helper.succeed();
    }

    /**
     * The Procedural nodes (noise / shapes / checkerboard) emit their GLSL: the noise/Voronoi/rounded-
     * polygon helpers are declared as global functions (and deduped — two Simple Noise nodes declare
     * {@code kg_valueNoise} once), the shapes antialias with {@code fwidth}, and Checkerboard reads
     * screen-space derivatives ({@code dFdx}). A per-node preview of a fragment-only shape also compiles
     * (its helper-less {@code fwidth} field) and a noise preview carries its helper function.
     */
    public static void proceduralNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        NodeModel simple = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise.SimpleNoiseNode.class);
        NodeModel simple2 = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise.SimpleNoiseNode.class);
        NodeModel gradient = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise.GradientNoiseNode.class);
        NodeModel voronoi = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise.VoronoiNode.class);
        NodeModel ellipse = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.EllipseNode.class);
        NodeModel polygon = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.PolygonNode.class);
        NodeModel rect = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.RectangleNode.class);
        NodeModel rrect = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.RoundedRectangleNode.class);
        NodeModel rpoly = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.RoundedPolygonNode.class);
        NodeModel checker = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.CheckerboardNode.class);

        // Sum every float output (incl. both Voronoi outputs) through an Add chain into the alpha channel.
        java.util.List<com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel> outs = java.util.List.of(
                simple.getOutputsById().get("out"), simple2.getOutputsById().get("out"),
                gradient.getOutputsById().get("out"),
                voronoi.getOutputsById().get("out"), voronoi.getOutputsById().get("cells"),
                ellipse.getOutputsById().get("out"), polygon.getOutputsById().get("out"),
                rect.getOutputsById().get("out"), rrect.getOutputsById().get("out"),
                rpoly.getOutputsById().get("out"));
        var acc = outs.get(0);
        for (int i = 1; i < outs.size(); i++) {
            NodeModel add = addNode(graph, AddNode.class);
            wire(graph, add.getInputsById().get("a"), acc);
            wire(graph, add.getInputsById().get("b"), outs.get(i));
            acc = add.getOutputsById().get("out");
        }
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), acc);
        // Checkerboard is vec3 → additive emission.
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), checker.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "procedural graph has no stage errors", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "emits value-noise helper", fsh.contains("float kg_valueNoise(vec2 uv) {"));
        assertTrue(helper, "emits gradient-noise dir helper", fsh.contains("vec2 kg_gradientDir(vec2 p) {"));
        assertTrue(helper, "emits voronoi random helper", fsh.contains("vec2 kg_voronoiRandom("));
        assertTrue(helper, "emits voronoi search helper", fsh.contains("void kg_voronoi("));
        assertTrue(helper, "emits rounded-polygon sdf helper", fsh.contains("float kg_sdRoundedPolygon("));
        assertTrue(helper, "shapes antialias with fwidth", fsh.contains("fwidth("));
        assertTrue(helper, "checkerboard reads screen-space derivatives", fsh.contains("dFdx("));
        // Dedup: two Simple Noise nodes share ONE kg_valueNoise definition.
        assertEq(helper, "value-noise helper declared exactly once",
                1, countOccurrences(fsh, "float kg_valueNoise(vec2 uv) {"));

        // A fragment-only shape previews fine (single quad), antialiased with fwidth.
        CompiledShaderGraph shapePreview = new ShaderGraphCompiler(new RenderTypeGraph())
                .compilePreview(ellipsePreviewPort());
        assertFalse(helper, "ellipse preview has no stage errors", shapePreview.hasStageErrors());
        assertTrue(helper, "ellipse preview antialiases with fwidth", shapePreview.fragmentSource().contains("fwidth("));
        // A noise preview carries its helper function into the preview fragment shader.
        assertTrue(helper, "noise preview emits its helper function",
                noisePreviewFsh().contains("float kg_valueNoise(vec2 uv) {"));
        helper.succeed();
    }

    /** A standalone Ellipse node's preview output port (separate graph; preview is a flat quad). */
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel ellipsePreviewPort() {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel ellipse = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.shapes.EllipseNode.class);
        return ellipse.getOutputsById().get("out");
    }

    private static String noisePreviewFsh() {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel simple = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.procedural.noise.SimpleNoiseNode.class);
        return new ShaderGraphCompiler(graph).compilePreview(simple.getOutputsById().get("out")).fragmentSource();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    /**
     * The Artistic nodes (adjustment / blend / mask / normal / filter / utility) emit their GLSL and the
     * whole graph compiles with no stage errors — all 18 wired reachable from the fragment outputs (the
     * fragment-only Dither/Normal-From-Height/Normal-From-Texture are pulled into the fragment stage).
     * Also checks the shared HSV helper is name-deduped (Hue + Colorspace both register {@code kg_rgb2hsv}
     * → one definition) and that the Blend mode dropdown drives the emitted formula.
     */
    public static void artisticNodesEmitGlsl(GameTestHelper helper) {
        String pkg = "com.lowdragmc.kilagraph.rendertype.nodes.artistic.";
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        NodeModel sat = addArtistic(graph, pkg + "adjustment.SaturationNode");
        NodeModel contrast = addArtistic(graph, pkg + "adjustment.ContrastNode");
        NodeModel hue = addArtistic(graph, pkg + "adjustment.HueNode");
        NodeModel mixer = addArtistic(graph, pkg + "adjustment.ChannelMixerNode");
        NodeModel invert = addArtistic(graph, pkg + "adjustment.InvertColorsNode");
        NodeModel replace = addArtistic(graph, pkg + "adjustment.ReplaceColorNode");
        NodeModel white = addArtistic(graph, pkg + "adjustment.WhiteBalanceNode");
        NodeModel chanMask = addArtistic(graph, pkg + "mask.ChannelMaskNode");
        NodeModel nUnpack = addArtistic(graph, pkg + "normal.NormalUnpackNode");
        NodeModel nStrength = addArtistic(graph, pkg + "normal.NormalStrengthNode");
        NodeModel blend = addArtistic(graph, pkg + "blend.BlendNode");
        NodeModel colorspace = addArtistic(graph, pkg + "utility.ColorspaceConversionNode");
        NodeModel nBlend = addArtistic(graph, pkg + "normal.NormalBlendNode");
        NodeModel nReconstruct = addArtistic(graph, pkg + "normal.NormalReconstructZNode");
        NodeModel nFromTex = addArtistic(graph, pkg + "normal.NormalFromTextureNode");
        NodeModel nFromHeight = addArtistic(graph, pkg + "normal.NormalFromHeightNode");
        NodeModel colorMask = addArtistic(graph, pkg + "mask.ColorMaskNode");
        NodeModel dither = addArtistic(graph, pkg + "filter.DitherNode");
        setOption(blend, "mode", "overlay");
        setOption(colorspace, "from", "rgb");
        setOption(colorspace, "to", "hsv");

        // vec3 adjustment chain → blend.base → colorspace → emission color.
        wire(graph, contrast.getInputsById().get("in"), sat.getOutputsById().get("out"));
        wire(graph, hue.getInputsById().get("in"), contrast.getOutputsById().get("out"));
        wire(graph, mixer.getInputsById().get("in"), hue.getOutputsById().get("out"));
        wire(graph, invert.getInputsById().get("in"), mixer.getOutputsById().get("out"));
        wire(graph, replace.getInputsById().get("in"), invert.getOutputsById().get("out"));
        wire(graph, white.getInputsById().get("in"), replace.getOutputsById().get("out"));
        wire(graph, chanMask.getInputsById().get("in"), white.getOutputsById().get("out"));
        wire(graph, nUnpack.getInputsById().get("in"), chanMask.getOutputsById().get("out"));
        wire(graph, nStrength.getInputsById().get("in"), nUnpack.getOutputsById().get("out"));
        wire(graph, blend.getInputsById().get("base"), nStrength.getOutputsById().get("out"));
        // normal sub-tree → blend.blend.
        wire(graph, nBlend.getInputsById().get("a"), nReconstruct.getOutputsById().get("out"));
        wire(graph, nBlend.getInputsById().get("b"), nFromTex.getOutputsById().get("out"));
        wire(graph, nReconstruct.getInputsById().get("in"), sat.getOutputsById().get("out")); // vec3 -> vec2
        wire(graph, blend.getInputsById().get("blend"), nBlend.getOutputsById().get("out"));
        wire(graph, replace.getInputsById().get("from"), nFromHeight.getOutputsById().get("out"));
        wire(graph, colorspace.getInputsById().get("in"), blend.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), colorspace.getOutputsById().get("out"));
        // float chain: colorMask → (dither + normalFromHeight.in) → alpha.
        wire(graph, nFromHeight.getInputsById().get("in"), colorMask.getOutputsById().get("out"));
        wire(graph, dither.getInputsById().get("in"), colorMask.getOutputsById().get("out"));
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), dither.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "artistic graph has no stage errors", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "saturation emits the Rec.709 luma", fsh.contains("0.2126729"));
        assertTrue(helper, "hue/colorspace emit the rgb->hsv helper", fsh.contains("kg_rgb2hsv("));
        assertTrue(helper, "hue emits the hsv->rgb helper", fsh.contains("kg_hsv2rgb("));
        assertTrue(helper, "blend overlay mode emits the per-channel step", fsh.contains("step(0.5,"));
        assertTrue(helper, "dither reads gl_FragCoord", fsh.contains("gl_FragCoord"));
        assertTrue(helper, "normal-from-height uses a screen-space derivative", fsh.contains("dFdx("));
        assertTrue(helper, "normal-from-texture samples the texture", fsh.contains("texture("));
        // The HSV helper is shared by Hue + Colorspace but declared exactly once (name-keyed dedup).
        assertEq(helper, "rgb->hsv helper declared exactly once",
                1, countOccurrences(fsh, "vec3 kg_rgb2hsv(vec3 c) {"));
        helper.succeed();
    }

    private static NodeModel addArtistic(RenderTypeGraph graph, String className) {
        try {
            @SuppressWarnings("unchecked")
            var cls = (Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node>)
                    Class.forName(className);
            return addNode(graph, cls);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("missing artistic node " + className, e);
        }
    }

    /** The Camera node reads the Globals block (camera world position + screen size). */
    public static void cameraNodeReadsGlobals(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel camera = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.transform.CameraNode.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), camera.getOutputsById().get("Position"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "camera graph has no stage errors", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "camera node reads CameraBlockPos", fsh.contains("CameraBlockPos"));
        assertTrue(helper, "camera node reads CameraOffset", fsh.contains("CameraOffset"));
        helper.succeed();
    }

    /** The KG_Transforms UBO node exposes our precomputed space matrices (flags + declares the block). */
    public static void kgTransformsUboNodeExposesMatrices(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel ubo = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.transform.KGTransformsUboNode.class);
        // The node's outputs are mat4s; transform a vec4 by ViewMat so the result reaches a color block.
        NodeModel xform = addNode(graph, com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4TransformNode.class);
        wire(graph, xform.getInputsById().get("m"), ubo.getOutputsById().get("ViewMat"));
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), xform.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "KG transforms UBO graph has no stage errors", compiled.hasStageErrors());
        assertTrue(helper, "graph flags usesTransforms", usesUniformBlock(compiled, "KG_Transforms"));
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "fsh declares KG_Transforms", fsh.contains("uniform KG_Transforms"));
        assertTrue(helper, "fsh references kg_transforms.ViewMat", fsh.contains("kg_transforms.ViewMat"));
        helper.succeed();
    }

    /** Vector construct (Vec2/Vec4) and vec3 ops (Cross/Normalize/Dot) compile to their GLSL builtins. */
    public static void vectorNodesEmitGlslCalls(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        // normalize(cross(a, b)) -> base color (vec3).
        NodeModel cross = addNode(graph, CrossNode.class);
        NodeModel normalize = addNode(graph, NormalizeNode.class);
        wire(graph, normalize.getInputsById().get("v"), cross.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), normalize.getOutputsById().get("out"));

        // Vec2 -> texture sample uv -> emission (also exercises a vec2 constructor feeding a sampler).
        NodeModel vec2 = addNode(graph, Vec2Node.class);
        NodeModel tex = addNode(graph, SamplerTexture2DNode.class); // unconnected sampler → missing
        wire(graph, tex.getInputsById().get("uv"), vec2.getOutputsById().get("out"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), tex.getOutputsById().get("color"));

        // dot(cross, vec4.xyz) -> alpha (pulls Vec4 + Dot).
        NodeModel vec4 = addNode(graph, Vec4Node.class);
        NodeModel dot = addNode(graph, DotNode.class);
        wire(graph, dot.getInputsById().get("a"), cross.getOutputsById().get("out"));
        wire(graph, dot.getInputsById().get("b"), vec4.getOutputsById().get("out")); // vec4 -> vec3 swizzle
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), dot.getOutputsById().get("out"));

        String fsh = compile(graph).fragmentSource();
        for (String fn : new String[]{"cross(", "normalize(", "vec2(", "vec4(", "dot("}) {
            assertTrue(helper, "fsh emits " + fn, fsh.contains(fn));
        }
        helper.succeed();
    }

    /**
     * Fresnel's {@code normal}/{@code viewDir} ports have no configurator: left unconnected they fall back
     * to the interpolated world-space mesh normal / view direction. The vsh declares + writes both varyings
     * (object→world via ModelViewMat + kg_transforms.IViewMat), the fsh reads them, and the graph registers
     * the KG_Transforms block.
     */
    public static void fresnelDefaultsToMeshNormalAndViewDir(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fresnel = addNode(graph, FresnelNode.class); // normal/viewDir intentionally unconnected
        NodeModel alpha = addBlock(graph, graph.getFragmentStageModel(), FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), fresnel.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        String vsh = compiled.vertexSource();
        String fsh = compiled.fragmentSource();
        assertFalse(helper, "fresnel defaults are not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "vsh declares kg_worldNormal out", vsh.contains("out vec3 kg_worldNormal;"));
        assertTrue(helper, "vsh declares kg_worldViewDir out", vsh.contains("out vec3 kg_worldViewDir;"));
        assertTrue(helper, "world normal uses ModelViewMat", vsh.contains("mat3(ModelViewMat)"));
        assertTrue(helper, "defaults rotate to world via IViewMat", vsh.contains("kg_transforms.IViewMat"));
        assertTrue(helper, "fsh reads kg_worldNormal in", fsh.contains("in vec3 kg_worldNormal;"));
        assertTrue(helper, "fsh reads kg_worldViewDir in", fsh.contains("in vec3 kg_worldViewDir;"));
        assertTrue(helper, "fsh emits the fresnel pow", fsh.contains("pow(1.0 - clamp(dot("));
        assertTrue(helper, "graph registers KG_Transforms", usesUniformBlock(compiled, "KG_Transforms"));

        // Per-node preview: the normal default is the preview mesh's real interpolated normal (vNormal), so
        // the rim gradient is correct on sphere/cube/custom geometry (Unity-like). A flat +Z default would
        // give dot(+Z,+Z)=1 → pow(0,power)=0 → all black.
        CompiledShaderGraph preview = new ShaderGraphCompiler(graph)
                .compilePreview(fresnel.getOutputsById().get("out"));
        assertTrue(helper, "preview vsh passes the mesh Normal as vNormal",
                preview.vertexSource().contains("in vec3 Normal;") && preview.vertexSource().contains("vNormal = Normal;"));
        assertTrue(helper, "preview fresnel reads the real interpolated normal vNormal",
                preview.fragmentSource().contains("in vec3 vNormal;") && preview.fragmentSource().contains("vNormal"));
        helper.succeed();
    }

    /**
     * Fog distance nodes are VERTEX_ONLY: pulling one into a fragment block is a stage error, but feeding a
     * vsh varying block works and an unconnected {@code pos} defaults to the model-space vertex position.
     */
    public static void fogDistanceNodesAreVertexOnly(GameTestHelper helper) {
        // (a) into a fragment block → stage error keyed to the fog node.
        RenderTypeGraph badGraph = new RenderTypeGraph();
        NodeModel alpha = addBlock(badGraph, badGraph.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel fogBad = addNode(badGraph, FogSphericalDistanceNode.class);
        wire(badGraph, alpha.getInputsById().get("alpha"), fogBad.getOutputsById().get("out"));
        CompiledShaderGraph badCompiled = new ShaderGraphCompiler(badGraph).compile();
        assertTrue(helper, "fog distance in fragment stage is a stage error", badCompiled.hasStageErrors());
        assertTrue(helper, "error names the fog distance node",
                badCompiled.stageErrors().stream().anyMatch(e -> e.nodeName().contains("Fog Spherical Distance")));

        // (b) into a vsh varying block, pos unconnected → default model-space position.
        RenderTypeGraph okGraph = new RenderTypeGraph();
        NodeModel varying = addBlock(okGraph, okGraph.getVertexStageModel(), VaryingCustomFloatBlock.class);
        NodeModel fogOk = addNode(okGraph, FogSphericalDistanceNode.class); // pos intentionally unconnected
        wire(okGraph, varying.getInputsById().get("value"), fogOk.getOutputsById().get("out"));
        CompiledShaderGraph okCompiled = compile(okGraph);
        assertFalse(helper, "fog distance feeding a vsh block is legal", okCompiled.hasStageErrors());
        assertTrue(helper, "pos defaults to fog_spherical_distance of the model position",
                okCompiled.vertexSource().contains("fog_spherical_distance((Position + ModelOffset))"));
        helper.succeed();
    }

    /**
     * ApplyFog / TotalFogValue parameters left unconnected fall back to the Fog UBO fields + the fog-distance
     * varyings, so the node fogs with the current scene settings out of the box.
     */
    public static void fogParamsDefaultToUboAndVaryings(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel emission = addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel apply = addNode(graph, ApplyFogNode.class); // every param unconnected
        // apply.out is vec4 → emission color is vec3 (swizzle).
        wire(graph, emission.getInputsById().get("color"), apply.getOutputsById().get("out"));
        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "fog defaults are not a stage error", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        for (String field : new String[]{"FogColor", "FogEnvironmentalStart", "FogEnvironmentalEnd",
                "FogRenderDistanceStart", "FogRenderDistanceEnd"}) {
            assertTrue(helper, "fsh references " + field, fsh.contains(field));
        }
        assertTrue(helper, "fsh reads the spherical distance varying", fsh.contains("in float sphericalVertexDistance;"));
        assertTrue(helper, "vsh writes the spherical distance default",
                compiled.vertexSource().contains("fog_spherical_distance((Position + ModelOffset))"));

        // TotalFogValue: same field/varying defaults (float output → alpha).
        RenderTypeGraph tot = new RenderTypeGraph();
        NodeModel alpha = addBlock(tot, tot.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel total = addNode(tot, TotalFogValueNode.class);
        wire(tot, alpha.getInputsById().get("alpha"), total.getOutputsById().get("out"));
        String totFsh = compile(tot).fragmentSource();
        assertTrue(helper, "total_fog_value references the Fog UBO", totFsh.contains("FogEnvironmentalStart"));
        assertTrue(helper, "total_fog_value reads the cylindrical varying",
                totFsh.contains("in float cylindricalVertexDistance;"));
        helper.succeed();
    }

    /** SphereMask's coords port has no configurator: unconnected it defaults to the interpolated mesh
     *  model-space position (kg_modelPos varying). */
    public static void sphereMaskCoordsDefaultToMeshPosition(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel alpha = addBlock(graph, graph.getFragmentStageModel(), FragmentAlphaBlock.class);
        NodeModel mask = addNode(graph, SphereMaskNode.class); // coords unconnected
        wire(graph, alpha.getInputsById().get("alpha"), mask.getOutputsById().get("out"));
        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "sphere mask default is not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "vsh writes kg_modelPos = (Position + ModelOffset)",
                compiled.vertexSource().contains("kg_modelPos = (Position + ModelOffset);"));
        assertTrue(helper, "fsh reads kg_modelPos in", compiled.fragmentSource().contains("in vec3 kg_modelPos;"));
        helper.succeed();
    }

    /**
     * Channel nodes: Combine assembles R/G/B/A into vectors, Swizzle remaps channels (GLSL swizzle), Flip
     * mirrors selected channels (Unity's {@code (flip*-2+1)*in+flip}), Split breaks a vector into floats.
     */
    public static void channelNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        // Combine R,G,B,A → rgba/rgb.
        NodeModel combine = addNode(graph, CombineNode.class);
        setInputConstant(combine, "r", 1f);
        setInputConstant(combine, "a", 1f);

        // Swizzle rgba → zyx (vec3) → base color.
        NodeModel swizzle = addNode(graph, SwizzleNode.class);
        setOption(swizzle, "c0", "z");
        setOption(swizzle, "c1", "y");
        setOption(swizzle, "c2", "x");
        setOption(swizzle, "c3", "-");
        wire(graph, swizzle.getInputsById().get("in"), combine.getOutputsById().get("rgba"));
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), swizzle.getOutputsById().get("out"));

        // Flip rgb (red + blue) → emission.
        NodeModel flip = addNode(graph, FlipNode.class);
        setOption(flip, "red", true);
        setOption(flip, "blue", true);
        wire(graph, flip.getInputsById().get("in"), combine.getOutputsById().get("rgb"));
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), flip.getOutputsById().get("out"));

        // Split rgba → r → alpha.
        NodeModel split = addNode(graph, SplitNode.class);
        wire(graph, split.getInputsById().get("in"), combine.getOutputsById().get("rgba"));
        NodeModel alpha = addBlock(graph, fragment, FragmentAlphaBlock.class);
        wire(graph, alpha.getInputsById().get("alpha"), split.getOutputsById().get("r"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertFalse(helper, "channel nodes are not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "combine emits a vec4", fsh.contains("vec4("));
        assertTrue(helper, "swizzle emits the .zyx remap", fsh.contains(".zyx"));
        assertTrue(helper, "flip emits the Unity flip mask (vec3(1.0, 0.0, 1.0))",
                fsh.contains("vec3(1.0, 0.0, 1.0)") && fsh.contains("* -2.0 + 1.0"));
        helper.succeed();
    }

    /**
     * UV nodes: the 6 pure-uv transforms (chained, each unconnected uv auto-resolving to the mesh uv) +
     * Triplanar. Asserts their distinctive GLSL appears and there are no stage errors.
     */
    public static void uvNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();

        // Chain: Rotate -> Twirl -> Spherize -> RadialShear -> Polar -> Flipbook -> base color.
        NodeModel rotate = addNode(graph, RotateNode.class);       // uv unconnected → mesh uv (UV0)
        NodeModel twirl = addNode(graph, TwirlNode.class);
        NodeModel spherize = addNode(graph, SpherizeNode.class);
        NodeModel shear = addNode(graph, RadialShearNode.class);
        NodeModel polar = addNode(graph, PolarCoordinatesNode.class);
        NodeModel flipbook = addNode(graph, FlipbookNode.class);
        wire(graph, twirl.getInputsById().get("uv"), rotate.getOutputsById().get("out"));
        wire(graph, spherize.getInputsById().get("uv"), twirl.getOutputsById().get("out"));
        wire(graph, shear.getInputsById().get("uv"), spherize.getOutputsById().get("out"));
        wire(graph, polar.getInputsById().get("uv"), shear.getOutputsById().get("out"));
        wire(graph, flipbook.getInputsById().get("uv"), polar.getOutputsById().get("out"));
        NodeModel baseColor = addBlock(graph, fragment, FragmentBaseColorBlock.class);
        wire(graph, baseColor.getInputsById().get("color"), flipbook.getOutputsById().get("out"));

        // Triplanar (texture unconnected → missing sampler) → emission.
        NodeModel triplanar = addNode(graph, TriplanarNode.class);
        NodeModel emission = addBlock(graph, fragment, FragmentEmissionBlock.class);
        wire(graph, emission.getInputsById().get("color"), triplanar.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertFalse(helper, "uv nodes are not a stage error", compiled.hasStageErrors());
        for (String marker : new String[]{"cos(", "length(", "atan(", "floor(", "pow(abs(", "texture("}) {
            assertTrue(helper, "uv fsh emits " + marker, fsh.contains(marker));
        }
        // The chained uv default resolved to the UV0 mesh varying.
        assertTrue(helper, "uv chain uses the uv0 varying", fsh.contains("in vec2 uv0;"));
        helper.succeed();
    }

    /**
     * The UV port type: an unconnected uv port reads the channel its configurator picked. UV0 writes the
     * {@code uv0} varying from the {@code UV0} attribute; UV1 writes {@code uv1} from {@code vec2(UV1)}; and
     * when the chosen channel's attribute is absent from the format it falls back to UV0.
     */
    public static void uvTypeResolvesChannel(GameTestHelper helper) {
        // Default (UV0): the ENTITY format has UV0 → uv0 = UV0.
        RenderTypeGraph g0 = new RenderTypeGraph();
        NodeModel tile0 = addNode(g0, TilingAndOffsetNode.class); // uv unconnected, default channel UV0
        wire(g0, addBlock(g0, g0.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                tile0.getOutputsById().get("out"));
        String vsh0 = compile(g0).vertexSource();
        assertTrue(helper, "UV0 default writes uv0 = UV0", vsh0.contains("uv0 = UV0;"));

        // UV1 picked, ENTITY has UV1 (ivec2) → uv1 = vec2(UV1), read in fsh.
        RenderTypeGraph g1 = new RenderTypeGraph();
        NodeModel tile1 = addNode(g1, TilingAndOffsetNode.class);
        setInputConstant(tile1, "uv", RenderTypeGraphTypes.UvChannel.UV1);
        wire(g1, addBlock(g1, g1.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                tile1.getOutputsById().get("out"));
        CompiledShaderGraph c1 = compile(g1);
        assertTrue(helper, "UV1 vsh declares uv1 varying", c1.vertexSource().contains("out vec2 uv1;"));
        assertTrue(helper, "UV1 casts the ivec2 attribute (vec2(UV1))", c1.vertexSource().contains("uv1 = vec2(UV1);"));
        assertTrue(helper, "UV1 fsh reads uv1", c1.fragmentSource().contains("in vec2 uv1;"));

        // UV1 picked but the BLOCK format omits UV1 (has UV0) → fall back to UV0.
        RenderTypeGraph g2 = new RenderTypeGraph();
        var s = g2.getSettings();
        g2.setSettings(new RenderTypeGraph.Settings(VertexFormatPresets.BLOCK, s.vertexFormatMode(), s.blend(),
                s.depthTest(), s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload()));
        NodeModel tile2 = addNode(g2, TilingAndOffsetNode.class);
        setInputConstant(tile2, "uv", RenderTypeGraphTypes.UvChannel.UV1);
        wire(g2, addBlock(g2, g2.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                tile2.getOutputsById().get("out"));
        assertTrue(helper, "missing UV1 falls back to UV0", compile(g2).vertexSource().contains("uv1 = UV0;"));
        helper.succeed();
    }

    /** The UV channel value round-trips through its codec (registered so it survives graph save). */
    public static void uvChannelValueCodecRoundTrips(GameTestHelper helper) {
        var value = RenderTypeGraphTypes.UvChannel.UV2;
        var encoded = RenderTypeGraphTypes.UV_CODEC.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        assertTrue(helper, "uv channel encodes", encoded != null);
        var decoded = RenderTypeGraphTypes.UV_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        assertEq(helper, "uv channel round-trips", value, decoded);
        helper.succeed();
    }

    /**
     * Scene Color / Scene Depth: a Screen Position drives a Scene Color sample and a Scene Depth sample.
     * Asserts the captured-scene samplers + their flags, the gl_FragCoord-derived UV, and that Linear01/Eye
     * reconstruct linear depth via the inverse-projection helper.
     */
    public static void sceneNodesEmitGlsl(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel fragment = graph.getFragmentStageModel();
        NodeModel screenPos = addNode(graph, ScreenPositionNode.class);       // default mode
        NodeModel sceneColor = addNode(graph, SceneColorNode.class);
        wire(graph, sceneColor.getInputsById().get("uv"), screenPos.getOutputsById().get("out"));
        wire(graph, addBlock(graph, fragment, FragmentBaseColorBlock.class).getInputsById().get("color"),
                sceneColor.getOutputsById().get("out"));
        NodeModel sceneDepth = addNode(graph, SceneDepthNode.class);          // linear01 default
        wire(graph, addBlock(graph, fragment, FragmentAlphaBlock.class).getInputsById().get("alpha"),
                sceneDepth.getOutputsById().get("out"));

        CompiledShaderGraph compiled = compile(graph);
        String fsh = compiled.fragmentSource();
        assertFalse(helper, "scene nodes are not a stage error", compiled.hasStageErrors());
        assertTrue(helper, "samples the captured scene colour", fsh.contains("texture(KG_SceneColor"));
        assertTrue(helper, "samples the captured scene depth", fsh.contains("texture(KG_SceneDepth"));
        assertTrue(helper, "screen position derives uv from gl_FragCoord", fsh.contains("gl_FragCoord"));
        assertTrue(helper, "linear01 imports the scene depth helper",
                fsh.contains("#moj_import <kilagraph:kg_scene.glsl>"));
        assertTrue(helper, "linear01 reconstructs via IProjMat",
                fsh.contains("kg_linear01_depth(") && fsh.contains("IProjMat"));
        assertTrue(helper, "flags scene colour use", compiled.usesSceneColor());
        assertTrue(helper, "flags scene depth use", compiled.usesSceneDepth());
        assertTrue(helper, "declares the scene samplers",
                compiled.layout().samplers().contains("KG_SceneColor")
                        && compiled.layout().samplers().contains("KG_SceneDepth"));

        // Eye reconstructs distance; Raw samples directly with no linearise include.
        assertTrue(helper, "eye mode emits kg_eye_depth", compileSceneDepthFsh("eye").contains("kg_eye_depth("));
        String raw = compileSceneDepthFsh("raw");
        assertTrue(helper, "raw mode samples the depth texture", raw.contains("texture(KG_SceneDepth"));
        assertFalse(helper, "raw mode needs no linearise helper", raw.contains("kg_scene.glsl"));
        helper.succeed();
    }

    private static String compileSceneDepthFsh(String mode) {
        RenderTypeGraph g = new RenderTypeGraph();
        NodeModel depth = addNode(g, SceneDepthNode.class);
        setOption(depth, "sampling", mode);
        wire(g, addBlock(g, g.getFragmentStageModel(), FragmentAlphaBlock.class).getInputsById().get("alpha"),
                depth.getOutputsById().get("out"));
        return compile(g).fragmentSource();
    }

    /**
     * The captured scene samplers are declared in the layout but carry <b>no</b> baked default texture (unlike
     * a Sampler2D / the missing-texture fallback) — the runtime binds them live from the capture manager.
     */
    public static void sceneSamplersHaveNoBakedDefault(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel sceneColor = addNode(graph, SceneColorNode.class);
        wire(graph, addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                sceneColor.getOutputsById().get("out"));
        CompiledShaderGraph compiled = compile(graph);
        assertTrue(helper, "scene colour sampler is declared", compiled.layout().samplers().contains("KG_SceneColor"));
        assertFalse(helper, "scene colour sampler has no baked default texture",
                compiled.samplerDefaults().containsKey("KG_SceneColor"));
        helper.succeed();
    }

    /** Each Screen Position mode emits its distinctive GLSL formula. */
    public static void screenPositionModesEmitGlsl(GameTestHelper helper) {
        assertTrue(helper, "pixel mode scales the screen uv to pixels",
                screenPosFsh("pixel").contains("* kg_globals.ScreenSize, 0.0, 0.0)"));
        assertTrue(helper, "center mode remaps to -1..1", screenPosFsh("center").contains("* 2.0 - 1.0"));
        assertTrue(helper, "tiled mode tiles with fract", screenPosFsh("tiled").contains("fract("));
        assertTrue(helper, "default mode normalises by ScreenSize",
                screenPosFsh("default").contains("gl_FragCoord.xy / kg_globals.ScreenSize"));
        assertTrue(helper, "raw mode carries the fragment eye depth in W",
                screenPosFsh("raw").contains("kg_eye_depth(gl_FragCoord.z"));
        helper.succeed();
    }

    /**
     * Screen Position "raw" W must be the fragment's eye-space depth reconstructed from the SAME basis as
     * Scene Depth "eye" (kg_eye_depth + IProjMat), so {@code SceneDepth[eye] - raw.w} cancels the camera —
     * a camera-independent depth fade. Regression for the old {@code w=1.0} stub.
     */
    public static void screenPositionRawCarriesFragmentEyeDepth(GameTestHelper helper) {
        String fsh = screenPosFsh("raw");
        assertTrue(helper, "raw reconstructs eye depth from gl_FragCoord.z", fsh.contains("kg_eye_depth(gl_FragCoord.z"));
        assertTrue(helper, "raw uses the inverse projection (same basis as Scene Depth eye)", fsh.contains("IProjMat"));
        assertTrue(helper, "raw pulls in the scene-depth helper include", fsh.contains("kg_scene.glsl"));
        assertFalse(helper, "raw is no longer the w=1.0 stub", fsh.contains("vec4((gl_FragCoord.xy / ScreenSize), 0.0, 1.0)"));
        helper.succeed();
    }

    /**
     * Screen Position previews must build on the editor-preview screen UV (the quad uv), not raw
     * gl_FragCoord: in a node thumbnail the geometry covers only a screen sub-rect, so true screen
     * coordinates would collapse center/tiled/pixel to a near-constant corner. In-world it keeps
     * gl_FragCoord screen-space.
     */
    public static void screenPositionPreviewMapsToMeshUv(GameTestHelper helper) {
        RenderTypeGraph g = new RenderTypeGraph();
        NodeModel sp = addNode(g, ScreenPositionNode.class); // default mode
        String pfsh = new ShaderGraphCompiler(g).compilePreview(sp.getOutputsById().get("out")).fragmentSource();
        assertFalse(helper, "screen position preview does not use gl_FragCoord", pfsh.contains("gl_FragCoord"));
        assertTrue(helper, "screen position preview maps to the quad uv", pfsh.contains("vUv"));

        // In-world: the real compile keeps true screen-space.
        RenderTypeGraph g2 = new RenderTypeGraph();
        NodeModel sp2 = addNode(g2, ScreenPositionNode.class);
        wire(g2, addBlock(g2, g2.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                sp2.getOutputsById().get("out"));
        assertTrue(helper, "screen position in-world keeps gl_FragCoord",
                new ShaderGraphCompiler(g2).compile().fragmentSource().contains("gl_FragCoord"));
        helper.succeed();
    }

    /**
     * A node preview is composited over the editor GUI by its alpha, so a scalar output must be forced
     * opaque — otherwise a 0 value (alpha 0) would vanish instead of showing black (Unity parity). The
     * preview colour is {@code vec4(<value>.rgb, 1.0)}, never the {@code vec4(f)} broadcast that aliases
     * alpha to the value.
     */
    public static void scalarPreviewForcesOpaqueAlpha(GameTestHelper helper) {
        RenderTypeGraph g = new RenderTypeGraph();
        NodeModel add = addNode(g, AddNode.class); // scalar (dynamic float) output
        setInputConstant(add, "a", 0.0f);
        setInputConstant(add, "b", 0.0f);
        String fsh = new ShaderGraphCompiler(g).compilePreview(add.getOutputsById().get("out")).fragmentSource();
        assertTrue(helper, "scalar preview broadcasts to rgb with opaque alpha",
                fsh.contains("fragColor = vec4(vec3(") && fsh.contains(", 1.0);"));
        helper.succeed();
    }

    private static String screenPosFsh(String mode) {
        RenderTypeGraph g = new RenderTypeGraph();
        NodeModel sp = addNode(g, ScreenPositionNode.class);
        setOption(sp, "mode", mode);
        wire(g, addBlock(g, g.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                sp.getOutputsById().get("out"));
        return compile(g).fragmentSource();
    }

    /**
     * Scene Color's unconnected (screen-space) UV maps the whole capture onto the preview geometry in both
     * editor previews (node thumbnail = compilePreview; whole-graph = editorPreview), but keeps true
     * gl_FragCoord screen-space for in-world rendering — so a preview shows the entire scene, not the panel's
     * screen sub-rect.
     */
    public static void scenePreviewMapsWholeCapture(GameTestHelper helper) {
        // Node thumbnail (compilePreview): screen default becomes the quad uv, not gl_FragCoord.
        RenderTypeGraph g = new RenderTypeGraph();
        NodeModel sceneColor = addNode(g, SceneColorNode.class);
        String pfsh = new ShaderGraphCompiler(g)
                .compilePreview(sceneColor.getOutputsById().get("out")).fragmentSource();
        assertTrue(helper, "node preview still samples the capture", pfsh.contains("texture(KG_SceneColor"));
        assertFalse(helper, "node preview does not use gl_FragCoord", pfsh.contains("gl_FragCoord"));

        // Whole-graph editor preview: same — the screen default maps to the mesh uv.
        RenderTypeGraph g2 = new RenderTypeGraph();
        NodeModel sc2 = addNode(g2, SceneColorNode.class);
        wire(g2, addBlock(g2, g2.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                sc2.getOutputsById().get("out"));
        assertFalse(helper, "editor preview does not use gl_FragCoord",
                new ShaderGraphCompiler(g2).editorPreview().compile().fragmentSource().contains("gl_FragCoord"));

        // In-world: the real compile keeps true screen-space.
        assertTrue(helper, "in-world keeps gl_FragCoord screen-space",
                new ShaderGraphCompiler(g2).compile().fragmentSource().contains("gl_FragCoord"));
        helper.succeed();
    }

    /** The extended Camera node exposes Direction/Up/Right (IViewMat), Near/Far (inverse-projection) and Orthographic. */
    public static void cameraNodeExposesNewOutputs(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel camera = addNode(graph, CameraNode.class);
        wire(graph, addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                camera.getOutputsById().get("Direction"));
        // Route the three float outputs through a Combine so the demand-driven compiler actually emits them.
        NodeModel combine = addNode(graph, CombineNode.class);
        wire(graph, combine.getInputsById().get("r"), camera.getOutputsById().get("NearPlane"));
        wire(graph, combine.getInputsById().get("g"), camera.getOutputsById().get("FarPlane"));
        wire(graph, combine.getInputsById().get("b"), camera.getOutputsById().get("Orthographic"));
        wire(graph, addBlock(graph, graph.getFragmentStageModel(), FragmentEmissionBlock.class).getInputsById().get("color"),
                combine.getOutputsById().get("rgb"));

        CompiledShaderGraph compiled = compile(graph);
        assertFalse(helper, "camera new outputs are not a stage error", compiled.hasStageErrors());
        String fsh = compiled.fragmentSource();
        assertTrue(helper, "Direction uses the inverse view matrix", fsh.contains("IViewMat"));
        assertTrue(helper, "Near/Far reconstruct via the kg_scene helpers",
                fsh.contains("kg_camera_near(") && fsh.contains("kg_camera_far("));
        assertTrue(helper, "Orthographic reads ProjMat[3][3]", fsh.contains("ProjMat[3][3]"));

        // The camera basis vectors (Up/Right) derive from the same inverse view matrix as Direction, each
        // transforming its own view-space axis. Verify Up compiles and rotates the view-space up axis.
        RenderTypeGraph upGraph = new RenderTypeGraph();
        NodeModel upCam = addNode(upGraph, CameraNode.class);
        wire(upGraph, addBlock(upGraph, upGraph.getFragmentStageModel(), FragmentBaseColorBlock.class).getInputsById().get("color"),
                upCam.getOutputsById().get("Up"));
        CompiledShaderGraph upCompiled = compile(upGraph);
        assertFalse(helper, "camera Up is not a stage error", upCompiled.hasStageErrors());
        String upFsh = upCompiled.fragmentSource();
        assertTrue(helper, "camera Up rotates the view-space up axis by IViewMat",
                upFsh.contains("vec4(0.0, 1.0, 0.0, 0.0)") && upFsh.contains("IViewMat"));
        helper.succeed();
    }

    /**
     * Node-preview honesty for uv: (A) a UV node on UV0 previews the quad gradient ({@code vUv}) but on
     * UV1/UV2 previews a flat colour (those channels are constant per draw, not a gradient); (B) a uv driven
     * through a varying block by a fixed value previews that fixed value downstream (not the quad uv) — the
     * varying boundary reuses the block's own preview logic.
     */
    public static void uvPreviewSemantics(GameTestHelper helper) {
        // (A) UV node channel previews.
        RenderTypeGraph g0 = new RenderTypeGraph();
        NodeModel uv0 = addNode(g0, UVNode.class); // default channel uv0
        addBlock(g0, g0.getFragmentStageModel(), FragmentBaseColorBlock.class);
        String uv0Fsh = new ShaderGraphCompiler(g0).compilePreview(uv0.getOutputsById().get("out")).fragmentSource();
        assertTrue(helper, "UV0 preview is the quad gradient (vUv)", uv0Fsh.contains("= vUv;"));

        RenderTypeGraph g1 = new RenderTypeGraph();
        NodeModel uv1 = addNode(g1, UVNode.class);
        setOption(uv1, "channel", "uv1");
        addBlock(g1, g1.getFragmentStageModel(), FragmentBaseColorBlock.class);
        String uv1Fsh = new ShaderGraphCompiler(g1).compilePreview(uv1.getOutputsById().get("out")).fragmentSource();
        assertTrue(helper, "UV1 preview is a flat colour", uv1Fsh.contains("= vec2(0.0);"));
        assertFalse(helper, "UV1 preview is not the quad gradient", uv1Fsh.contains("= vUv;"));

        // (B) A custom vec2 varying block driven by a fixed Vec2 → SamplerTexture2D preview samples that uv.
        RenderTypeGraph g2 = new RenderTypeGraph();
        NodeModel vec2 = addNode(g2, Vec2Node.class);
        setInputConstant(vec2, "x", 0.25f);
        setInputConstant(vec2, "y", 0.75f);
        NodeModel uvBlock = addBlock(g2, g2.getVertexStageModel(), VaryingCustomVec2Block.class);
        wire(g2, uvBlock.getInputsById().get("value"), vec2.getOutputsById().get("out"));
        NodeModel tex = addNode(g2, SamplerTexture2DNode.class); // sampler unconnected → missing
        wire(g2, tex.getInputsById().get("uv"), uvBlock.getOutputsById().get("value"));
        String texFsh = new ShaderGraphCompiler(g2).compilePreview(tex.getOutputsById().get("color")).fragmentSource();
        assertTrue(helper, "driven-varying preview samples the fixed uv (0.25)", texFsh.contains("0.25"));
        assertTrue(helper, "driven-varying preview still samples a texture", texFsh.contains("texture("));
        helper.succeed();
    }

    /** A VertexFormat input (raw vsh attribute) is VERTEX_ONLY: pulling it into fragment is a stage error. */
    public static void vertexFormatInputsAreVertexOnly(GameTestHelper helper) {
        RenderTypeGraph graph = new RenderTypeGraph();
        NodeModel baseColor = addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        NodeModel position = addNode(graph, VertexAttributeInputNode.class);
        wire(graph, baseColor.getInputsById().get("color"), position.getOutputsById().get("out"));

        CompiledShaderGraph compiled = new ShaderGraphCompiler(graph).compile();
        assertTrue(helper, "vertex attribute in fragment stage is a stage error", compiled.hasStageErrors());
        assertTrue(helper, "error names the vertex attribute node",
                compiled.stageErrors().stream().anyMatch(e -> e.nodeName().contains("Vertex Attribute")));
        helper.succeed();
    }

    /**
     * A standalone FragmentInput node reads a fixed interpolated varying, ensuring the vsh declares and
     * writes it with the block's default (no vertex block placed). uv0 → UV0; vertexColor → light mix.
     */
    public static void fragmentInputsEmitVaryings(GameTestHelper helper) {
        // TexCoord input → base color: uv0 varying with UV0 default, no kg_uv anywhere.
        RenderTypeGraph uvGraph = new RenderTypeGraph();
        NodeModel baseColor = addBlock(uvGraph, uvGraph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        NodeModel texcoord = addNode(uvGraph, UVNode.class);
        wire(uvGraph, baseColor.getInputsById().get("color"), texcoord.getOutputsById().get("out"));
        CompiledShaderGraph uvCompiled = new ShaderGraphCompiler(uvGraph).compile();
        assertTrue(helper, "vsh declares uv0 out", uvCompiled.vertexSource().contains("out vec2 uv0;"));
        assertTrue(helper, "vsh writes uv0 = UV0", uvCompiled.vertexSource().contains("uv0 = UV0;"));
        assertTrue(helper, "fsh reads uv0 in", uvCompiled.fragmentSource().contains("in vec2 uv0;"));
        assertFalse(helper, "no kg_uv anywhere (unified to uv0)",
                uvCompiled.vertexSource().contains("kg_uv") || uvCompiled.fragmentSource().contains("kg_uv"));

        // A (lit) VertexColorNode reads the vertexColor varying across the stage boundary (declared as a vsh
        // out + read as an fsh in); its vsh default is per-vertex mix_light lighting.
        RenderTypeGraph colGraph = new RenderTypeGraph();
        NodeModel emission = addBlock(colGraph, colGraph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel vcolor = addNode(colGraph, VertexColorNode.class);
        wire(colGraph, emission.getInputsById().get("color"), vcolor.getOutputsById().get("out"));
        CompiledShaderGraph colCompiled = new ShaderGraphCompiler(colGraph).compile();
        assertTrue(helper, "vsh declares vertexColor out", colCompiled.vertexSource().contains("out vec4 vertexColor;"));
        assertTrue(helper, "fsh reads vertexColor in", colCompiled.fragmentSource().contains("in vec4 vertexColor;"));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static int count(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }

    /** Extract the identifier following the first occurrence of {@code prefix}, up to ';'. */
    private static String extractToken(String s, String prefix) {
        int i = s.indexOf(prefix);
        if (i < 0) return null;
        int start = i + prefix.length();
        int end = s.indexOf(';', start);
        return end < 0 ? null : s.substring(start, end).trim();
    }
}
