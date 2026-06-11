package com.lowdragmc.kilagraph.test.gametest.rendertypegraph;

import com.lowdragmc.kilagraph.test.gametest.KGGameTests;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.CrossNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.DotNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaDiscardBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentEmissionBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.LengthNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.MinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.NormalizeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.PowNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.LightMapTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.OverlayTextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.fragment.VertexColorFragmentInputNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.fragment.TexCoordFragmentInputNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.VertexAttributeInputNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.ShaderFloatAddNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.ShaderFloatMultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.SinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.uv.TilingAndOffsetNode;
import com.lowdragmc.kilagraph.rendertype.nodes.constant.TimeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec2Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec3Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vector.Vec4Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VaryingVertexColorBlock;
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
    }

    private static CompiledShaderGraph compile(RenderTypeGraph graph) {
        return new ShaderGraphCompiler(graph).compile();
    }

    /** A shared upstream node (the texture sample, apply_fog) must be emitted exactly once. */
    public static void sharedSubexpressionCompiledOnce(GameTestHelper helper) {
        CompiledShaderGraph compiled = compile(new RenderTypeGraph());
        String fsh = compiled.fragmentSource();
        assertEq(helper, "texture(...) emitted once", 1, count(fsh, "texture("));
        assertEq(helper, "apply_fog(...) emitted once", 1, count(fsh, "apply_fog("));
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
        NodeModel fmul = addNode(graph, ShaderFloatMultiplyNode.class); // float output
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

        assertTrue(helper, "graph uses engine globals", compiled.usesEngineGlobals());
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
        NodeModel uv = addNode(graph, TexCoordFragmentInputNode.class);
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
        assertTrue(helper, "vec2 preview padded to vec4", uvFsh.contains("fragColor = vec4(") && uvFsh.contains("0.0, 1.0)"));
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

        // Correct: Normal -> vertex Color varying block input (vertex stage) → no error.
        RenderTypeGraph good = new RenderTypeGraph();
        NodeModel vertex = good.getVertexStageModel();
        NodeModel colorVarying = addBlock(good, vertex, VaryingVertexColorBlock.class);
        NodeModel n2 = addNode(good, VertexAttributeInputNode.class);
        NodeModel toVec4 = addNode(good, com.lowdragmc.kilagraph.rendertype.nodes.vector.ShaderVec4MultiplyNode.class);
        // feed Normal (vec3 -> vec4 via convert) into the color varying's input (computed in vsh)
        wire(good, toVec4.getInputsById().get("a"), n2.getOutputsById().get("out"));
        wire(good, colorVarying.getInputsById().get("color"), toVec4.getOutputsById().get("out"));
        // also consume the varying in fragment so the vertex subgraph is actually compiled
        NodeModel baseColor2 = addBlock(good, good.getFragmentStageModel(), FragmentBaseColorBlock.class);
        wire(good, baseColor2.getInputsById().get("color"), colorVarying.getOutputsById().get("color"));
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
     * An unconnected vertex Color block defaults to vanilla per-vertex diffuse lighting
     * ({@code minecraft_mix_light}), importing {@code minecraft:light.glsl} — mirroring how the distance
     * blocks default to {@code fog_*_distance}. The default entity shader exercises exactly this.
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
        NodeModel add = addNode(graph, ShaderFloatAddNode.class);
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
     * writes it with the block's default (no vertex block placed). texCoord0 → UV0; vertexColor → light mix.
     */
    public static void fragmentInputsEmitVaryings(GameTestHelper helper) {
        // TexCoord input → base color: texCoord0 varying with UV0 default, no kg_uv anywhere.
        RenderTypeGraph uvGraph = new RenderTypeGraph();
        NodeModel baseColor = addBlock(uvGraph, uvGraph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        NodeModel texcoord = addNode(uvGraph, TexCoordFragmentInputNode.class);
        wire(uvGraph, baseColor.getInputsById().get("color"), texcoord.getOutputsById().get("out"));
        CompiledShaderGraph uvCompiled = new ShaderGraphCompiler(uvGraph).compile();
        assertTrue(helper, "vsh declares texCoord0 out", uvCompiled.vertexSource().contains("out vec2 texCoord0;"));
        assertTrue(helper, "vsh writes texCoord0 = UV0", uvCompiled.vertexSource().contains("texCoord0 = UV0;"));
        assertTrue(helper, "fsh reads texCoord0 in", uvCompiled.fragmentSource().contains("in vec2 texCoord0;"));
        assertFalse(helper, "no kg_uv anywhere (unified to texCoord0)",
                uvCompiled.vertexSource().contains("kg_uv") || uvCompiled.fragmentSource().contains("kg_uv"));

        // FragmentColorInput reads the vertexColor varying across the stage boundary (declared as a vsh
        // out + read as an fsh in). The varying's value is whatever drives it (the default graph's Color
        // block here); the node itself bakes no vsh lighting.
        RenderTypeGraph colGraph = new RenderTypeGraph();
        NodeModel emission = addBlock(colGraph, colGraph.getFragmentStageModel(), FragmentEmissionBlock.class);
        NodeModel vcolor = addNode(colGraph, VertexColorFragmentInputNode.class);
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
