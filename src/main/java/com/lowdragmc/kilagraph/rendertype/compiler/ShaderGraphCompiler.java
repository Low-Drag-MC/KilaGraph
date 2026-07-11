package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.kilagraph.rendertype.runtime.KGEngineUniforms;
import com.lowdragmc.kilagraph.rendertype.runtime.KGTransformUniforms;
import com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IVariableNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.IVariable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.BlockNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ContextNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.SubgraphNodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compiles a {@link RenderTypeGraph} into GLSL vertex/fragment sources plus the material uniform
 * layout. Demand-driven, mirroring {@code GraphExecutor}: starting from the fragment stage's
 * semantic blocks, the compiler pulls backward through the data graph, emitting hoisted temp
 * variables in dependency order.
 *
 * <p>Two stage scopes are maintained (vertex, fragment), each with its own body, temp counter and
 * memo cache. When fragment compilation reaches a vertex {@link IVaryingBlock}'s output, the
 * compiler treats it as a stage boundary: the varying is built once in the vertex scope and the
 * fragment scope receives a reference to the interpolated {@code in} variable.</p>
 */
public class ShaderGraphCompiler {

    private static final String GLSL_VERSION = "#version 330";

    /** Per-stage emission state. */
    private static final class StageScope {
        final String tempPrefix;
        final StringBuilder body = new StringBuilder();
        final Set<String> includes = new LinkedHashSet<>();
        /** Global-scope helper function definitions for this stage, keyed by function name (first
         *  registration wins, so a same-named helper can't be redefined) and emitted in insertion order
         *  (a dependency registered first is declared first) before main(). */
        final Map<String, String> functions = new LinkedHashMap<>();
        final Map<PortModel, ShaderExpr> cache = new IdentityHashMap<>();
        final Set<AbstractNodeModel> visiting = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int tempCounter;
        /** Whether this stage references the {@code KG_Gradient} struct (so its decl is emitted in the prelude). */
        boolean usesGradient;
        /** Whether this stage references the {@code KG_Curve} struct (so its decl is emitted in the prelude). */
        boolean usesCurve;

        StageScope(String tempPrefix) {
            this.tempPrefix = tempPrefix;
        }
    }

    private final RenderTypeGraph graph;
    private final StageScope vertex = new StageScope("v");
    private final StageScope fragment = new StageScope("f");
    private final MaterialUniformLayout layout = new MaterialUniformLayout();
    private final Set<String> builtinUbos = new LinkedHashSet<>();
    /** name -> type of varyings already built in the vertex shader. */
    private final Map<String, GlslType> varyings = new java.util.LinkedHashMap<>();
    /** Baked default values for EXPOSED variable uniforms: uniform field name -> std140 components. */
    private final Map<String, float[]> uniformDefaults = new LinkedHashMap<>();
    /** Baked default textures+params for Sampler2D samplers: sampler name -> {@link SamplerDefault}. */
    private final Map<String, SamplerDefault> samplerDefaults = new LinkedHashMap<>();
    /** EXPOSED variable display name -> its KG_Material field (name + type), for set-by-name uniform updates. */
    private final Map<String, MaterialUniformLayout.Field> variableUniformFields = new LinkedHashMap<>();
    /** Sampler2D variable display name -> its sampler uniform name, for set-by-name texture updates. */
    private final Map<String, String> variableSamplerNames = new LinkedHashMap<>();
    /** Variable declaration uid -> the sanitized, unique GLSL identifier chosen for it. */
    private final Map<UUID, String> variableNames = new HashMap<>();
    /** Texture node uid -> its allocated sampler uniform name. */
    private final Map<UUID, String> nodeSamplerNames = new HashMap<>();
    /** All GLSL identifiers already handed out to variables/constants, to keep them unique. */
    private final Set<String> usedVariableNames = new LinkedHashSet<>();
    /** Attribute names a node/block default referenced that aren't in the active vertex format (a safe
     *  constant was substituted) — surfaced as editor warnings so the user knows a default degraded. */
    private final Set<String> missingAttributes = new LinkedHashSet<>();

    /** Sampler name for an unconnected Sampler2D fallback — bound to the MC missing-texture. */
    public static final String MISSING_SAMPLER = "kg_MissingSampler";
    /** Whether an OverlayTextureNode referenced {@code Sampler1} (so the pipeline must enable overlay). */
    private boolean usesOverlay;
    /** Whether a LightMapTextureNode referenced {@code Sampler2} (so the pipeline must enable lightmap). */
    private boolean usesLightmap;
    /** Whether a SceneColorNode referenced {@code KG_SceneColor} (the runtime must capture+bind scene colour). */
    private boolean usesSceneColor;
    /** Whether a SceneDepthNode referenced {@code KG_SceneDepth} (the runtime must capture+bind scene depth). */
    private boolean usesSceneDepth;

    private StageScope current;
    /** Preview mode: compile a single port onto a flat quad, substituting stage inputs with defaults. */
    private boolean preview;
    /** Injection mode ({@link #buildInjectionSnippet()}): compile the fragment surface into a
     *  {@code kg_surface(vec2 kg_uv)} function for an Iris shaderpack program. Implies {@link #preview}
     *  (varyings degrade to preview defaults), but the primary mesh uv resolves to the function parameter
     *  {@code kg_uv} rather than the preview quad's {@code vUv}. */
    private boolean injection;
    /** Editor whole-graph preview ({@code ShaderPreviewTool}): compiles the real graph, but screen-space
     *  defaults (see {@link #screenUv()}) map the whole capture onto the preview geometry rather than the
     *  panel's screen sub-rect, so the preview shows the entire scene. In-world rendering is unaffected. */
    private boolean editorPreview;
    /** Set in injection mode when a node read the mesh normal / view direction / position
     *  ({@link #meshNormal()}/{@link #meshViewDir()}/{@link #meshPosition()}), so the surface references the
     *  {@code kg_normal}/{@code kg_viewDir}/{@code kg_localPos} varyings. {@code IrisShaderInjector} then
     *  injects a tiny vertex stage that computes them (see {@link InjectionSnippet#usesGeometry()}). */
    private boolean usesGeometryVarying;
    /** KilaGraph-managed UBOs any node referenced (engine globals, transforms, or a mod's own block).
     *  Each is declared in the GLSL + bound on the pipeline + uploaded each frame — the single, generic
     *  extension point that replaced per-block {@code usesX} flags. */
    private final Set<com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock> uniformBlocks = new LinkedHashSet<>();
    /** Stage-affinity violations found during traversal, keyed by node uid (first conflict per node). */
    private final Map<UUID, StageError> stageErrors = new LinkedHashMap<>();
    /**
     * Active subgraph-inlining bindings: one frame per nested {@link SubgraphNodeModel} we're inside,
     * mapping an inner READ-variable's declaration uid → the outer input expression bound to it.
     * The {@link IVariableNode} branch consults this so an inner READ variable resolves to its caller's
     * argument instead of the top-level const/uniform path.
     */
    private final Deque<Map<UUID, ShaderExpr>> bindingStack = new ArrayDeque<>();

    /** Fixed render state for per-node previews: opaque, depth-tested, no cull (so the quad always shows). */
    public static final RenderTypeGraph.Settings PREVIEW_SETTINGS = new RenderTypeGraph.Settings(
            // Includes Normal so node previews carry a real surface normal (the preview meshes supply it),
            // letting Fresnel / normal nodes read real geometry like Unity — see meshNormal()'s preview branch.
            com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.POSITION_COLOR_TEX_NORMAL,
            RenderTypeGraph.Settings.VertexFormatMode.QUADS,
            RenderTypeGraph.Settings.BlendMode.OPAQUE,
            RenderTypeGraph.Settings.DepthTest.LEQUAL,
            true, false,
            RenderTypeGraph.Settings.OutputTarget.MAIN,
            false, false);

    public ShaderGraphCompiler(RenderTypeGraph graph) {
        this.graph = graph;
        // DynamicTransforms is always bound by RenderType.draw, so the pipeline must declare it.
        builtinUbos.add("DynamicTransforms");
    }

    // ---- public entry ------------------------------------------------------------------------

    /**
     * Mark this as an editor whole-graph preview compile (the {@link com.lowdragmc.kilagraph.rendertype.gui.ShaderPreviewTool}
     * cube/sphere). Screen-space defaults ({@link #screenUv()}, the unconnected UV of Scene Color/Depth) then map the
     * whole captured frame across the preview geometry's uv instead of the panel's screen sub-rect — otherwise the
     * preview would sample only the small on-screen rectangle it occupies. No effect on graphs that don't use a
     * screen-space default. Fluent: call before {@link #compile()}.
     */
    public ShaderGraphCompiler editorPreview() {
        this.editorPreview = true;
        return this;
    }

    /** Whether this is an editor whole-graph preview compile (see {@link #editorPreview()}). */
    protected boolean isEditorPreview() {
        return editorPreview;
    }

    public CompiledShaderGraph compile() {
        ContextNodeModel vertexStage = asContext(graph.getVertexStageModel(), "vertex");
        ContextNodeModel fragmentStage = asContext(graph.getFragmentStageModel(), "fragment");

        // 1) Fragment stage: pull from each fragment semantic block. This lazily builds the
        //    varyings it depends on in the vertex scope.
        current = fragment;
        FragmentOutputs out = new FragmentOutputs();
        for (BlockNodeModel block : fragmentStage.getBlocks()) {
            Node node = nodeOf(block);
            if (node instanceof IFragmentOutputBlock fb) {
                var ctx = new ShaderCompileContext(this, block);
                fb.emitFragment(ctx, out);
            }
        }

        // 2) Vertex position (gl_Position).
        current = vertex;
        ShaderExpr position = null;
        for (BlockNodeModel block : vertexStage.getBlocks()) {
            Node node = nodeOf(block);
            if (node instanceof IVertexPositionBlock pb) {
                var ctx = new ShaderCompileContext(this, block);
                position = convert(pb.compilePosition(ctx), GlslType.VEC4);
                break;
            }
        }
        if (position == null) {
            // No explicit position block — fall back to the standard MVP transform (matching block.vsh,
            // which transforms Position + ModelOffset).
            addInclude("minecraft:projection.glsl");
            position = new ShaderExpr("ProjMat * ModelViewMat * vec4(" + modelPosition().code() + ", 1.0)", GlslType.VEC4);
        }
        line("gl_Position = " + position.code() + ";");

        String vsh = assembleVertex();
        String fsh = assembleFragment(out);
        // Also compile an Iris-injection snippet (a fresh compiler, fragment-only, injection mode) so a
        // material drawn under a shaderpack can run its real surface shading inside the pack's gbuffers
        // program. Skipped for the editor whole-graph preview (rendered in GUI/PIP, which Iris never
        // overrides — so it must not register / trigger Iris reloads). Optional: a failure here never
        // breaks the (non-Iris) material.
        InjectionSnippet injectionSnippet = null;
        if (!editorPreview) {
            try {
                injectionSnippet = new ShaderGraphCompiler(graph).buildInjectionSnippet();
            } catch (RuntimeException e) {
                // Not injection-compatible (or a transient model issue) — fall back to passthrough under Iris.
                Kilagraph.LOGGER.warn("[KilaGraph][Iris] injection snippet compile failed -> passthrough", e);
            }
        }
        return new CompiledShaderGraph(vsh, fsh, layout, new ArrayList<>(builtinUbos), new ArrayList<>(uniformBlocks),
                new ArrayList<>(stageErrors.values()), graph.getSettings(),
                new LinkedHashMap<>(uniformDefaults), new LinkedHashMap<>(samplerDefaults),
                new LinkedHashMap<>(variableUniformFields), new LinkedHashMap<>(variableSamplerNames),
                usesOverlay, usesLightmap, usesSceneColor, usesSceneDepth, new ArrayList<>(missingAttributes),
                injectionSnippet);
    }

    /**
     * Compile a single output port into a preview shader: a flat quad whose fragment colour is the
     * port's value (converted to vec4). Stage inputs that don't exist on a quad — vertex varyings,
     * mesh uv — are substituted with preview defaults; samplers and Minecraft UBOs work normally
     * because the preview is drawn through a real {@code RenderType} (so {@code bindDefaultUniforms}
     * applies). Used by per-node previews in the editor.
     */
    public CompiledShaderGraph compilePreview(PortModel outputPort) {
        preview = true;
        current = fragment;
        // The preview vsh provides Position + UV0 and passes uv through as vUv.
        builtinUbos.add("Projection"); // vsh uses ProjMat * ModelViewMat
        ShaderExpr value = previewValueOf(outputPort);
        if (value == null) value = new ShaderExpr("vec4(0.0)", GlslType.VEC4);
        // The preview quad is composited over the editor GUI by its alpha, so a value's alpha would control
        // visibility — a scalar broadcast (vec4(f)) makes alpha == the value, so a 0 vanishes instead of
        // showing black. Force opaque: take the value's rgb (scalars broadcast to grey) and append alpha 1,
        // matching Unity (a 0 previews as black). See NodeShaderPreview / ShaderPreviewTool.
        // A real vec4 (a texture sample / colour with alpha) is premultiplied (rgb * a) first: particle
        // textures carry their shape in alpha over a flat rgb (often pure white), so an opaque rgb-only
        // preview would show a featureless square — premultiplying shows the shape over black instead.
        ShaderExpr color;
        if (value.type() == GlslType.VEC4) {
            ShaderExpr v = hoist(GlslType.VEC4, value.code());
            color = new ShaderExpr("vec4(" + v.code() + ".rgb * " + v.code() + ".a, 1.0)", GlslType.VEC4);
        } else {
            color = new ShaderExpr("vec4(" + convert(value, GlslType.VEC3).code() + ", 1.0)", GlslType.VEC4);
        }

        String vsh = assemblePreviewVertex();
        String fsh = assemblePreviewFragment(color);
        // Node previews render in the editor GUI (offscreen PIP), which Iris never overrides — no injection.
        return new CompiledShaderGraph(vsh, fsh, layout, new ArrayList<>(builtinUbos), new ArrayList<>(uniformBlocks),
                new ArrayList<>(stageErrors.values()), PREVIEW_SETTINGS,
                new LinkedHashMap<>(uniformDefaults), new LinkedHashMap<>(samplerDefaults),
                new LinkedHashMap<>(variableUniformFields), new LinkedHashMap<>(variableSamplerNames),
                usesOverlay, usesLightmap, usesSceneColor, usesSceneDepth, new ArrayList<>(missingAttributes),
                null);
    }

    /**
     * Compile the fragment surface into an {@link InjectionSnippet} for an Iris shaderpack's shared gbuffers
     * program (see {@code IrisShaderInjector}). Runs only the fragment stage (no {@code gl_Position}) in
     * injection mode: mesh uv resolves to the {@code kg_surface(vec2 kg_uv)} parameter and vertex varyings
     * degrade to preview defaults, so the body references only the {@code KG_Material} UBO, the graph's own
     * samplers, and KilaGraph-managed UBOs — all bound onto the shaderpack program at draw.
     *
     * <p>Returns {@code null} when the graph is not injection-compatible: its fragment needs a Minecraft
     * engine include ({@code #moj_import}, i.e. Fog/Lighting/Projection) or the captured scene
     * ({@code KG_SceneColor/Depth}), or a stage-affinity error was found. Such a material falls back to the
     * M0 passthrough under Iris. Call on a fresh compiler instance (it mutates stage scope state).</p>
     */
    @Nullable
    public InjectionSnippet buildInjectionSnippet() {
        injection = true;
        preview = true; // reuse varying substitution (vertexColor->white, normal->+Y, viewDir->+Z, ...)
        current = fragment;
        ContextNodeModel fragmentStage = asContext(graph.getFragmentStageModel(), "fragment");
        FragmentOutputs out = new FragmentOutputs();
        for (BlockNodeModel block : fragmentStage.getBlocks()) {
            Node node = nodeOf(block);
            if (node instanceof IFragmentOutputBlock fb) {
                var ctx = new ShaderCompileContext(this, block);
                fb.emitFragment(ctx, out);
            }
        }
        // Unsupported subset: a Minecraft engine include (Fog/Lighting/...) or the captured scene can't be
        // satisfied inside the shaderpack program; a stage error means the GLSL references unavailable data.
        if (!fragment.includes.isEmpty() || usesSceneColor || usesSceneDepth || !stageErrors.isEmpty()) {
            return null;
        }

        List<String> decls = new ArrayList<>();
        if (layout.hasGradientField() || fragment.usesGradient) decls.add(GradientGlsl.STRUCT);
        if (!layout.isEmpty()) decls.add(layout.blockGlsl());
        for (String s : layout.samplers()) decls.add("uniform sampler2D " + s + ";");
        uniformBlocks.stream()
                .sorted(java.util.Comparator.comparing(
                        com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock::uboName))
                .forEach(b -> decls.add(b.declareGlsl()));

        StringBuilder body = new StringBuilder(fragment.body);
        // LabPBR emission (_s.a) is a scalar glow intensity (glow colour = albedo). The dedicated float
        // Emissiveness block drives it directly; else we fall back to the max channel of the vec3 Emission
        // colour block (hoisted so the struct arg references it once). (Under a shaderpack the pack does its
        // own alpha test, so the M1 alpha-discard is intentionally dropped here.)
        String emissionArg = "0.0";
        if (out.emissiveness != null) {
            emissionArg = out.emissiveness.code();
        } else if (out.emission != null) {
            String e = out.emission.code();
            body.append("    float kg_emission_i = max(max((").append(e).append(").r, (")
                    .append(e).append(").g), (").append(e).append(").b);\n");
            emissionArg = "kg_emission_i";
        }
        String args = String.join(", ",
                out.baseColor != null ? out.baseColor.code() : "vec3(1.0)",                 // albedo
                out.alpha != null ? out.alpha.code() : "1.0",                               // alpha
                out.normalTS != null ? out.normalTS.code() : "vec3(0.0, 0.0, 1.0)",         // normalTS
                out.smoothness != null ? out.smoothness.code() : "0.0",                     // smoothness
                out.metallic != null ? out.metallic.code() : "0.0",                         // metallic
                emissionArg,                                                                // emission
                out.ao != null ? out.ao.code() : "1.0",                                     // ao
                out.height != null ? out.height.code() : "1.0",                             // height
                out.porosity != null ? out.porosity.code() : "0.0",                         // porosity
                out.sss != null ? out.sss.code() : "0.0");                                  // sss
        return new InjectionSnippet(decls, new ArrayList<>(fragment.functions.values()),
                body.toString(), args, usesGeometryVarying);
    }

    /**
     * Resolve a port's value for preview. A varying-block output (e.g. TexCoord) has no ShaderNode to
     * evaluate; instead its preview value is its connected input compiled in the fragment scope, or —
     * when unconnected — the preview default for that varying (texcoord → quad uv).
     */
    @Nullable
    private ShaderExpr previewValueOf(PortModel outputPort) {
        Node owner = nodeOf(outputPort);
        if (owner instanceof IVaryingBlock vb
                && outputPort.getNodeModel() instanceof NodeModel nm) {
            // varying blocks expose a single input feeding the varying (same id family as the output)
            var inputs = nm.getInputsByDisplayOrder();
            if (!inputs.isEmpty() && inputs.getFirst().isConnected()) {
                return pullInput(inputs.getFirst(), vb.varyingType());
            }
            return previewVaryingDefault(vb.varyingName(), vb.varyingType());
        }
        return evaluateOutput(outputPort);
    }

    /** Default value for a vertex varying when previewing without a vertex stage. */
    private ShaderExpr previewVaryingDefault(String varyingName, GlslType type) {
        if ("uv0".equals(varyingName)) return new ShaderExpr(injection ? "kg_uv" : "vUv", GlslType.VEC2);
        if ("vertexColor".equals(varyingName)) return new ShaderExpr("vec4(1.0)", GlslType.VEC4);
        return zero(type);
    }

    private static ShaderExpr zero(GlslType type) {
        return switch (type) {
            case FLOAT, INT, BOOL -> new ShaderExpr("0.0", type);
            case VEC2 -> new ShaderExpr("vec2(0.0)", GlslType.VEC2);
            case VEC3 -> new ShaderExpr("vec3(0.0)", GlslType.VEC3);
            case VEC4 -> new ShaderExpr("vec4(0.0)", GlslType.VEC4);
            case MAT4 -> new ShaderExpr("mat4(1.0)", GlslType.MAT4);
            case SAMPLER2D -> new ShaderExpr(MISSING_SAMPLER, GlslType.SAMPLER2D);
            // Defensive: GRADIENT/CURVE are opaque and never flow as varyings, so these are unreachable in practice.
            case GRADIENT -> new ShaderExpr("kg_gradientDefault()", GlslType.GRADIENT);
            case CURVE -> new ShaderExpr("kg_curveDefault()", GlslType.CURVE);
        };
    }

    /** The interpolated primary mesh uv (UV0). See {@link #meshUv(RenderTypeGraphTypes.UvChannel)}. */
    protected ShaderExpr meshUv() {
        return meshUv(RenderTypeGraphTypes.UvChannel.UV0);
    }

    /**
     * The interpolated mesh uv for a given channel, routed through the {@code uv0}/{@code uv1}/{@code uv2}
     * varying (vsh default = that channel's vertex attribute). UV0 is a {@code vec2} attribute; UV1/UV2 are
     * Minecraft's overlay/lightmap {@code ivec2} coords, cast to {@code vec2}. If the chosen channel's
     * element isn't in the active format it falls back to UV0, then to {@code vec2(0.0)} (the "missing uv"
     * case). Preview (no vertex stage): UV0 is the quad's gradient uv ({@code vUv}); UV1/UV2 don't exist on
     * the preview mesh and are constant per draw, so they preview as a flat {@code vec2(0.0)} (a solid colour,
     * not a misleading gradient). Shared first-writer-wins with the matching varying block / fragment input.
     */
    protected ShaderExpr meshUv(RenderTypeGraphTypes.UvChannel channel) {
        // Injection: UV0 is the kg_surface() function parameter; preview: the quad's gradient uv.
        ShaderExpr quadUv = new ShaderExpr(injection ? "kg_uv" : "vUv", GlslType.VEC2);
        ShaderExpr flatUv = new ShaderExpr("vec2(0.0)", GlslType.VEC2);
        return switch (channel) {
            case UV0 -> varyingInput("uv0", GlslType.VEC2, () -> uvAttr(KGVertexElements.UV0, false), quadUv);
            case UV1 -> varyingInput("uv1", GlslType.VEC2, () -> uvAttr(KGVertexElements.UV1, true), flatUv);
            case UV2 -> varyingInput("uv2", GlslType.VEC2, () -> uvAttr(KGVertexElements.UV2, true), flatUv);
        };
    }

    /**
     * The interpolated <b>lit</b> vertex colour, routed through the {@code vertexColor} varying — the vsh
     * default applies vanilla per-vertex diffuse lighting ({@code minecraft_mix_light} of the {@code Normal}
     * + {@code Color} attributes), so an entity is lit out of the box with no lighting block placed. (Mix
     * light is vertex-only, hence a varying.) Missing Normal/Color degrade to up / white. Preview: white.
     */
    protected ShaderExpr litVertexColor() {
        return varyingInput("vertexColor", GlslType.VEC4,
                () -> {
                    addInclude("minecraft:light.glsl");
                    useBuiltinUbo("Lighting");
                    ShaderExpr normal = attribute(KGVertexElements.NORMAL, GlslType.VEC3,
                            new ShaderExpr("vec3(0.0, 1.0, 0.0)", GlslType.VEC3));
                    ShaderExpr color = attribute(KGVertexElements.COLOR, GlslType.VEC4,
                            new ShaderExpr("vec4(1.0)", GlslType.VEC4));
                    return new ShaderExpr("minecraft_mix_light(Light0_Direction, Light1_Direction, "
                            + normal.code() + ", " + color.code() + ")", GlslType.VEC4);
                },
                new ShaderExpr("vec4(1.0)", GlslType.VEC4));
    }

    /** The interpolated <b>raw</b> (unlit) vertex colour — the {@code Color} attribute through the
     *  {@code rawColor} varying. Missing Color degrades to white. Preview: white. */
    protected ShaderExpr meshColor() {
        return varyingInput("rawColor", GlslType.VEC4,
                () -> attribute(KGVertexElements.COLOR, GlslType.VEC4, new ShaderExpr("vec4(1.0)", GlslType.VEC4)),
                new ShaderExpr("vec4(1.0)", GlslType.VEC4));
    }

    /**
     * Block-style vertex colour: {@code Color * sample_lightmap(Sampler2, UV2)} computed in the vertex stage
     * (the {@code blockColor} varying), exactly like vanilla {@code block.vsh}. Needs no Normal — the lighting
     * comes from the baked lightmap (UV2) rather than per-vertex diffuse. Declares {@code Sampler2} + flags
     * {@code usesLightmap} so the runtime binds the vanilla lightmap. Missing UV2 degrades to the raw Color
     * (unlit); missing Color degrades to white. Preview: white.
     */
    protected ShaderExpr blockVertexColor() {
        return varyingInput("blockColor", GlslType.VEC4,
                () -> {
                    ShaderExpr color = attribute(KGVertexElements.COLOR, GlslType.VEC4,
                            new ShaderExpr("vec4(1.0)", GlslType.VEC4));
                    if (!hasAttribute(KGVertexElements.UV2)) {
                        markMissingAttribute(KGVertexElements.UV2.attribName());
                        return color; // no lightmap coords -> unlit colour
                    }
                    addInclude("minecraft:sample_lightmap.glsl");
                    layout.addSampler("Sampler2"); // declare `uniform sampler2D Sampler2`
                    usesLightmap = true;           // bind the vanilla lightmap, skip the placeholder
                    return new ShaderExpr(color.code() + " * sample_lightmap(Sampler2, "
                            + attributeRef(KGVertexElements.UV2) + ")", GlslType.VEC4);
                },
                new ShaderExpr("vec4(1.0)", GlslType.VEC4));
    }

    /** A uv channel's vsh value: the attribute (cast {@code vec2(...)} for the ivec2 UV1/UV2), else a
     *  fallback to UV0, else {@code vec2(0.0)} — recording the missing attribute for editor warnings. */
    protected ShaderExpr uvAttr(KGVertexElement element, boolean integer) {
        if (hasAttribute(element)) {
            String ref = integer ? "vec2(" + attributeRef(element) + ")" : attributeRef(element);
            return new ShaderExpr(ref, GlslType.VEC2);
        }
        markMissingAttribute(element.attribName());
        if (element != KGVertexElements.UV0 && hasAttribute(KGVertexElements.UV0)) {
            return new ShaderExpr(attributeRef(KGVertexElements.UV0), GlslType.VEC2);
        }
        return new ShaderExpr("vec2(0.0)", GlslType.VEC2);
    }

    /**
     * The interpolated world-space surface normal, routed through the {@code kg_worldNormal} varying — the
     * default Unity-like fallback for an unconnected {@code normal} port. The vsh writes the {@code Normal}
     * attribute transformed object&rarr;world ({@code mat3(IViewMat · ModelViewMat) · Normal}); a missing
     * {@code Normal} element degrades to object +Y. Interpolation isn't unit-length, so consumers should
     * renormalize. Preview: the real interpolated {@code vNormal} (the preview mesh carries a Normal
     * attribute, see {@code PREVIEW_SETTINGS}) — exact on every preview geometry, like Unity's sphere
     * preview. Registers DynamicTransforms + KG_Transforms.
     */
    protected ShaderExpr meshNormal() {
        if (injection) return injectionNormal();
        return varyingInput("kg_worldNormal", GlslType.VEC3,
                () -> {
                    addInclude("minecraft:dynamictransforms.glsl"); // ModelViewMat
                    ShaderExpr n = attribute(KGVertexElements.NORMAL, GlslType.VEC3,
                            new ShaderExpr("vec3(0.0, 1.0, 0.0)", GlslType.VEC3));
                    ShaderExpr iView = transformField("IViewMat", GlslType.MAT4); // view -> world (rotation)
                    return new ShaderExpr("normalize(mat3(" + iView.code() + ") * mat3(ModelViewMat) * "
                            + n.code() + ")", GlslType.VEC3);
                },
                // The preview mesh's own (object-space) normal, interpolated — the preview camera looks down
                // an axis so object space ≈ view space here, and meshViewDir's +Z preview default is the
                // matching view direction — so dot(normal, viewDir) is correct on sphere/cube/custom alike.
                new ShaderExpr("vNormal", GlslType.VEC3));
    }

    /**
     * The interpolated world-space view direction (surface&rarr;camera), routed through the
     * {@code kg_worldViewDir} varying — the default Unity-like fallback for an unconnected {@code viewDir}
     * port. In view space the camera sits at the origin, so the direction is {@code -viewPos}; rotating that
     * into world via {@code IViewMat} suffices because the camera's world translation cancels in
     * {@code (cameraPos - worldPos)}. Consumers should renormalize after interpolation. Preview: {@code +Z}
     * (looking straight at the quad). Registers DynamicTransforms + KG_Transforms.
     */
    protected ShaderExpr meshViewDir() {
        if (injection) return injectionViewDir();
        return varyingInput("kg_worldViewDir", GlslType.VEC3,
                () -> {
                    ShaderExpr iView = transformField("IViewMat", GlslType.MAT4); // view -> world (rotation)
                    String viewPos = "(ModelViewMat * vec4(" + modelPosition().code() + ", 1.0)).xyz";
                    return new ShaderExpr("normalize(mat3(" + iView.code() + ") * (-" + viewPos + "))",
                            GlslType.VEC3);
                },
                // Preview: +Z (looking straight at the quad).
                new ShaderExpr("vec3(0.0, 0.0, 1.0)", GlslType.VEC3));
    }

    /**
     * The interpolated <b>model-space</b> vertex position {@code (Position + ModelOffset)}, routed through
     * the {@code kg_modelPos} varying — the default fallback for an unconnected {@code position}/{@code coords}
     * port. Fragment-safe (vsh writes it, fsh reads the interpolated value). Wire a Transform node for world
     * space. Preview: {@code vec3(0.0)}.
     */
    protected ShaderExpr meshPosition() {
        if (injection) return injectionWorldPos();
        return varyingInput("kg_modelPos", GlslType.VEC3, this::modelPosition,
                new ShaderExpr("vec3(0.0)", GlslType.VEC3)); // preview: 0
    }

    // ---- Injection-mode fragment reconstruction (see IrisShaderInjector) --------------------------------
    // Under a shaderpack we never touch the pack's vertex shader (any undefined reference there disables the
    // whole pack). Instead the geometry/screen inputs are reconstructed in the fragment from gl_FragCoord and
    // KilaGraph's own bound UBOs (KG_Globals.ScreenSize + KG_Transforms.IProjMat/IViewMat) — names we control,
    // so this can never break a pack. Only the smooth surface normal needs the pack: the injector fills a
    // kg_normalView global from the pack's own view-space normal varying when it can detect one. All outputs
    // are world-space, matching the non-injection meshNormal/meshViewDir so previews and in-world agree.

    /** The framebuffer size ({@code kg_globals.ScreenSize}) from our own KG_Globals UBO — bound on both the
     *  vanilla pipeline and the injected shaderpack program, so screen-space nodes work in both without a
     *  Minecraft include (which would make a graph non-injectable). */
    protected ShaderExpr screenSize() {
        useUniformBlock(KGEngineUniforms.BLOCK);
        return new ShaderExpr(KGEngineUniforms.screenSizeAccessor(), GlslType.VEC2);
    }

    /** Injection-only: the reconstructed <b>view-space</b> position of this fragment (see the private helper);
     *  {@code -viewPos} is the surface&rarr;camera direction with length = distance. Used by ViewDirection. */
    protected ShaderExpr reconstructedViewPos() {
        return injectionViewPos();
    }

    /** View-space position of this fragment from {@code gl_FragCoord} + {@code IProjMat} + {@code ScreenSize}. */
    private ShaderExpr injectionViewPos() {
        useUniformBlock(KGEngineUniforms.BLOCK);      // ScreenSize
        useUniformBlock(KGTransformUniforms.BLOCK);   // IProjMat (clip -> view)
        addFunction("kg_recon_viewPos",
                "vec3 kg_recon_viewPos() {\n"
              + "    vec2 kg_ndc = gl_FragCoord.xy / " + KGEngineUniforms.screenSizeAccessor() + " * 2.0 - 1.0;\n"
              + "    vec4 kg_vp = " + KGTransformUniforms.accessor("IProjMat")
                    + " * vec4(kg_ndc, gl_FragCoord.z * 2.0 - 1.0, 1.0);\n"
              + "    return kg_vp.xyz / kg_vp.w;\n}\n");
        return new ShaderExpr("kg_recon_viewPos()", GlslType.VEC3);
    }

    /** World-space surface normal: the pack's own view-space normal ({@code kg_normalView}, filled by the
     *  injector) rotated into world via {@code IViewMat}. */
    private ShaderExpr injectionNormal() {
        usesGeometryVarying = true; // the injector must declare + fill the kg_normalView global
        useUniformBlock(KGTransformUniforms.BLOCK);
        addFunction("kg_recon_normal",
                "vec3 kg_recon_normal() { return normalize(mat3("
                        + KGTransformUniforms.accessor("IViewMat") + ") * kg_normalView); }\n");
        return new ShaderExpr("kg_recon_normal()", GlslType.VEC3);
    }

    /** World-space surface&rarr;camera view direction from the reconstructed view position. */
    private ShaderExpr injectionViewDir() {
        injectionViewPos();
        addFunction("kg_recon_viewDir",
                "vec3 kg_recon_viewDir() { return normalize(mat3("
                        + KGTransformUniforms.accessor("IViewMat") + ") * (-kg_recon_viewPos())); }\n");
        return new ShaderExpr("kg_recon_viewDir()", GlslType.VEC3);
    }

    /** Camera-relative world position from the reconstructed view position ({@code IViewMat} is rotation-only,
     *  so this is world-space relative to the camera — good enough for world-ish position effects). */
    private ShaderExpr injectionWorldPos() {
        injectionViewPos();
        addFunction("kg_recon_worldPos",
                "vec3 kg_recon_worldPos() { return mat3("
                        + KGTransformUniforms.accessor("IViewMat") + ") * kg_recon_viewPos(); }\n");
        return new ShaderExpr("kg_recon_worldPos()", GlslType.VEC3);
    }

    /**
     * The interpolated {@code sphericalVertexDistance} varying — vanilla's spherical fog distance of the
     * model position. The vsh default ({@code fog_spherical_distance((Position + ModelOffset))}) is only used
     * if no varying block already wrote {@code sphericalVertexDistance} (first-writer-wins).
     */
    protected ShaderExpr sphericalVertexDistance() {
        return varyingInput("sphericalVertexDistance", GlslType.FLOAT,
                () -> {
                    addInclude("minecraft:fog.glsl");
                    return new ShaderExpr("fog_spherical_distance(" + modelPosition().code() + ")", GlslType.FLOAT);
                },
                new ShaderExpr("0.0", GlslType.FLOAT));
    }

    /** The interpolated {@code cylindricalVertexDistance} varying (vanilla's cylindrical fog distance). See
     *  {@link #sphericalVertexDistance()}. */
    protected ShaderExpr cylindricalVertexDistance() {
        return varyingInput("cylindricalVertexDistance", GlslType.FLOAT,
                () -> {
                    addInclude("minecraft:fog.glsl");
                    return new ShaderExpr("fog_cylindrical_distance(" + modelPosition().code() + ")", GlslType.FLOAT);
                },
                new ShaderExpr("0.0", GlslType.FLOAT));
    }

    /** A raw Minecraft {@code Fog} UBO field accessor (e.g. {@code FogColor}, {@code FogEnvironmentalStart}),
     *  registering the fog include + builtin UBO so the field resolves. Mirrors {@code FogUboNode}. */
    ShaderExpr fogField(String name, GlslType type) {
        addInclude("minecraft:fog.glsl");
        useBuiltinUbo("Fog");
        return new ShaderExpr(name, type);
    }

    /**
     * The model-space vertex position Minecraft actually transforms: {@code Position + ModelOffset}. The
     * offset is a per-draw {@code DynamicTransforms} uniform — zero unless set, but block/terrain rendering
     * sets it to the section offset (see vanilla {@code block.vsh}). Including it matches vanilla and is
     * harmless when unused, so it's the correct default for {@code gl_Position} and fog distances. Adds the
     * {@code dynamictransforms.glsl} import to the current (vertex) stage.
     */
    protected ShaderExpr modelPosition() {
        addInclude("minecraft:dynamictransforms.glsl");
        return new ShaderExpr("(" + attributeRef(KGVertexElements.POSITION) + " + ModelOffset)", GlslType.VEC3);
    }

    /** The element keys actually declared as {@code in} attributes in the current compile: the graph's
     *  composed vertex format, or — in preview — the fixed preview vsh's {@code Position}+{@code UV0}. */
    private Set<String> availableAttributes() {
        if (preview) return Set.of(KGVertexElements.POSITION.key(), KGVertexElements.UV0.key());
        return new java.util.HashSet<>(graph.getSettings().vertexFormatElements());
    }

    /** Whether this is a per-node preview compile (single fragment quad; no real vertex stage). */
    protected boolean isPreview() {
        return preview;
    }

    /** Whether this is an Iris-injection compile ({@link #buildInjectionSnippet()}). Nodes that depend on
     *  Minecraft engine state the shaderpack program can't provide (e.g. fog — the pack applies its own in
     *  its composite pass) should degrade to a no-op/pass-through here so the graph stays injectable. */
    boolean isInjection() {
        return injection;
    }

    /** Whether the given vertex element is declared in the active vertex format (so its raw {@code in}
     *  attribute can be referenced without producing an undefined-variable shader). */
    protected boolean hasAttribute(KGVertexElement element) {
        return availableAttributes().contains(element.key());
    }

    /**
     * The GLSL expression that reads a raw vertex attribute in the vertex stage. Default: the element's
     * {@code in} attribute name. A subclass whose vertex inputs come from somewhere other than raw
     * attributes (e.g. an instanced/struct layout declared by an include) overrides this to route every
     * attribute read through its own source.
     */
    protected String attributeRef(KGVertexElement element) {
        return element.attribName();
    }

    /**
     * A raw vertex-attribute reference (e.g. {@code Color}) when its element is in the active vertex
     * format, else {@code fallback} — so a node/block <em>default</em> that references an attribute the
     * user removed degrades to a safe constant instead of emitting undefined-variable GLSL (which the GPU
     * rejects). Records the substituted attribute so the editor can warn about the degraded default.
     */
    protected ShaderExpr attribute(KGVertexElement element, GlslType type, ShaderExpr fallback) {
        if (hasAttribute(element)) return new ShaderExpr(attributeRef(element), type);
        missingAttributes.add(element.attribName());
        return fallback;
    }

    /** Record that a referenced attribute is absent from the format (for callers that build the ref
     *  themselves, e.g. an explicit attribute node that casts {@code ivec2 → vec2}). */
    protected void markMissingAttribute(String attribName) {
        missingAttributes.add(attribName);
    }

    /**
     * Read a fixed interpolated varying whose vsh source is {@code vshDefault}. In preview (no vertex stage)
     * returns {@code previewDefault}. In the <b>vertex</b> stage the value is the raw source itself — this is
     * what lets stage-agnostic input nodes (UV/Color/Position/Normal/…) read raw vertex attributes directly in
     * the vsh instead of round-tripping through a varying, subsuming {@code VertexAttributeInputNode}. In the
     * <b>fragment</b> stage it declares + assigns the varying with {@code vshDefault} (unless a vertex varying
     * block already built it — first writer wins) and returns a reference to it. Used by
     * {@code FragmentInputNode}s and {@link #meshUv()}.
     */
    protected ShaderExpr varyingInput(String name, GlslType type,
                            java.util.function.Supplier<ShaderExpr> vshDefault, ShaderExpr previewDefault) {
        if (preview) return previewDefault;
        if (current == vertex) return convert(vshDefault.get(), type);
        ensureVaryingWithDefault(name, type, vshDefault);
        return new ShaderExpr(name, type);
    }

    private void ensureVaryingWithDefault(String name, GlslType type,
                                          java.util.function.Supplier<ShaderExpr> vshDefault) {
        if (varyings.containsKey(name)) return; // already built (by a block or a prior reader)
        varyings.put(name, type);
        StageScope saved = current;
        current = vertex;
        try {
            ShaderExpr value = convert(vshDefault.get(), type);
            line(name + " = " + value.code() + ";");
        } finally {
            current = saved;
        }
    }

    /** Register a KilaGraph-managed UBO (engine globals / transforms / a mod's own) so the GLSL declares it,
     *  the pipeline binds it, and the material uploads it each frame. Idempotent (a Set). */
    void useUniformBlock(ShaderUniformBlock block) {
        uniformBlocks.add(block);
    }

    /** World time in seconds from the {@code KG_Globals} engine block (updated by us each frame). */
    ShaderExpr engineTime() {
        useUniformBlock(KGEngineUniforms.BLOCK);
        return new ShaderExpr(KGEngineUniforms.timeAccessor(), GlslType.FLOAT);
    }

    /**
     * A {@code KG_Transforms} field accessor (e.g. {@code kg_transforms.ViewMat}), flagging the pipeline to
     * declare + bind the block. Used by the Transform node for World-space / reverse-direction matrices that
     * Minecraft's {@code DynamicTransforms}/{@code Projection} blocks don't expose. The {@code type} is the
     * accessor's GLSL type ({@code MAT4} for the matrices, {@code VEC3} for {@code CameraPos}).
     */
    protected ShaderExpr transformField(String field, GlslType type) {
        useUniformBlock(KGTransformUniforms.BLOCK);
        return new ShaderExpr(KGTransformUniforms.accessor(field), type);
    }

    /** Minecraft's builtin {@code Globals.GameTime} (day fraction). Bound by {@code bindDefaultUniforms}. */
    ShaderExpr mcGameTime() {
        addInclude("minecraft:globals.glsl");
        return new ShaderExpr("GameTime", GlslType.FLOAT);
    }

    /** The fallback sampler for an unconnected Sampler2D — declares it + bakes the MC missing-texture. */
    ShaderExpr missingSampler() {
        layout.addSampler(MISSING_SAMPLER);
        samplerDefaults.putIfAbsent(MISSING_SAMPLER, SamplerDefault.missing());
        return new ShaderExpr(MISSING_SAMPLER, GlslType.SAMPLER2D);
    }

    /** Mark the current stage as referencing {@code KG_Gradient} so its struct decl is emitted in the prelude
     *  (before the UBO + helper functions). Also registers the shared sample/default functions. */
    void useGradient() {
        current.usesGradient = true;
        addFunction(GradientGlsl.HELPER_KEY, GradientGlsl.HELPER);
    }

    /** The fallback gradient for an unconnected GRADIENT input — registers the helper, a black->white ramp. */
    ShaderExpr defaultGradient() {
        useGradient();
        return new ShaderExpr("kg_gradientDefault()", GlslType.GRADIENT);
    }

    private int gradientCounter = 0;

    /**
     * A constant gradient value: registers the shared {@code KG_Gradient} helper plus a uniquely-named
     * builder function for these keys, and returns a {@code KG_Gradient}-typed call expression. Used by the
     * Gradient node and by an unconnected GRADIENT port carrying an inline gradient editor.
     */
    ShaderExpr constantGradient(RenderTypeGraphTypes.GradientValue value) {
        useGradient();
        String fn = "kg_grad_" + (gradientCounter++);
        addFunction(fn, GradientGlsl.builderFunction(fn, value));
        return new ShaderExpr(fn + "()", GlslType.GRADIENT);
    }

    /** Mark the current stage as referencing {@code KG_Curve} so its struct decl is emitted in the prelude
     *  (before the UBO + helper functions). Also registers the shared sample/default functions. */
    void useCurve() {
        current.usesCurve = true;
        addFunction(CurveGlsl.HELPER_KEY, CurveGlsl.HELPER);
    }

    /** The fallback curve for an unconnected CURVE input — registers the helper, a linear 0->1 ramp. */
    ShaderExpr defaultCurve() {
        useCurve();
        return new ShaderExpr("kg_curveDefault()", GlslType.CURVE);
    }

    private int curveCounter = 0;

    /**
     * A constant curve value: registers the shared {@code KG_Curve} helper plus a uniquely-named builder
     * function for these segments, and returns a {@code KG_Curve}-typed call expression. Used by the
     * Curve node and by an unconnected CURVE port carrying an inline curve editor.
     */
    ShaderExpr constantCurve(RenderTypeGraphTypes.CurveValue value) {
        useCurve();
        String fn = "kg_curve_" + (curveCounter++);
        addFunction(fn, CurveGlsl.builderFunction(fn, value));
        return new ShaderExpr(fn + "()", GlslType.CURVE);
    }

    /** Vanilla overlay sampler ({@code Sampler1}); flags the pipeline to enable overlay binding. */
    ShaderExpr overlaySampler() {
        usesOverlay = true;
        return new ShaderExpr("Sampler1", GlslType.SAMPLER2D);
    }

    /** Vanilla lightmap sampler ({@code Sampler2}); flags the pipeline to enable lightmap binding. */
    ShaderExpr lightmapSampler() {
        usesLightmap = true;
        return new ShaderExpr("Sampler2", GlslType.SAMPLER2D);
    }

    /** Sampler name for the captured opaque scene colour (bound at draw from {@code SceneCaptureManager}). */
    public static final String SCENE_COLOR_SAMPLER = "KG_SceneColor";
    /** Sampler name for the captured opaque scene depth (bound at draw from {@code SceneCaptureManager}). */
    public static final String SCENE_DEPTH_SAMPLER = "KG_SceneDepth";

    /**
     * Sampler for the captured opaque scene colour (Unity's Scene Color). Declares {@code KG_SceneColor}
     * in the layout (so it's emitted in the GLSL + declared on the pipeline) and flags the runtime to
     * capture + bind it. Unlike a Sampler2D it gets no baked missing-texture default — the runtime binds
     * the live capture (see {@code RenderTypeFactory}/{@code RenderTypeGraphMaterial}).
     */
    protected ShaderExpr sceneColorSampler() {
        usesSceneColor = true;
        layout.addSampler(sceneColorSamplerName());
        return new ShaderExpr(sceneColorSamplerName(), GlslType.SAMPLER2D);
    }

    /** Sampler for the captured opaque scene depth (Unity's Scene Depth). See {@link #sceneColorSampler()}. */
    protected ShaderExpr sceneDepthSampler() {
        usesSceneDepth = true;
        layout.addSampler(sceneDepthSamplerName());
        return new ShaderExpr(sceneDepthSamplerName(), GlslType.SAMPLER2D);
    }

    /**
     * The sampler uniform the Scene Color node reads. Default: {@code KG_SceneColor}, bound at draw from
     * {@code SceneCaptureManager}. A subclass whose runtime owns its own scene capture overrides these
     * names so its material binds them instead — the {@code usesSceneColor}/{@code usesSceneDepth} flags
     * still signal the demand.
     */
    protected String sceneColorSamplerName() {
        return SCENE_COLOR_SAMPLER;
    }

    /** The sampler uniform the Scene Depth node reads. See {@link #sceneColorSamplerName()}. */
    protected String sceneDepthSamplerName() {
        return SCENE_DEPTH_SAMPLER;
    }

    /** Screen-space UV {@code gl_FragCoord.xy / ScreenSize} (vec2) — the default UV for Scene Color/Depth
     *  (Unity defaults their UV to screen position). Fragment-only; reads {@code Globals.ScreenSize}.
     *  <p>In an editor preview the geometry only covers a small screen sub-rect, so true screen coordinates
     *  would sample just that corner of the full-screen capture; there we map the whole captured frame across
     *  the preview geometry's uv (mesh/quad uv) so the preview shows the entire scene.</p> */
    protected ShaderExpr screenUv() {
        if (preview || editorPreview) return meshUv();
        // Framebuffer size from our own KG_Globals UBO (bound on both the vanilla pipeline and — crucially —
        // the injected shaderpack program), so screen-space nodes need no Minecraft include and stay injectable.
        useUniformBlock(KGEngineUniforms.BLOCK);
        return new ShaderExpr("(gl_FragCoord.xy / " + KGEngineUniforms.screenSizeAccessor() + ")", GlslType.VEC2);
    }

    /** Sample the captured opaque scene colour (vec3) at {@code uv} — Unity's Scene Color. */
    ShaderExpr sampleSceneColor(ShaderExpr uv) {
        ShaderExpr s = sceneColorSampler();
        return new ShaderExpr("texture(" + s.code() + ", " + convert(uv, GlslType.VEC2).code() + ").rgb", GlslType.VEC3);
    }

    /** Raw hardware depth in {@code [0,1]} (Unity's Scene Depth "Raw"). */
    ShaderExpr sampleSceneDepthRaw(ShaderExpr uv) {
        ShaderExpr s = sceneDepthSampler();
        return new ShaderExpr("texture(" + s.code() + ", " + convert(uv, GlslType.VEC2).code() + ").r", GlslType.FLOAT);
    }

    /** Eye-space distance from the camera in world units (Unity's Scene Depth "Eye"), reconstructed via {@code IProjMat}. */
    ShaderExpr sampleSceneDepthEye(ShaderExpr uv) {
        addInclude("kilagraph:kg_scene.glsl");
        ShaderExpr raw = sampleSceneDepthRaw(uv);
        ShaderExpr iproj = transformField("IProjMat", GlslType.MAT4);
        return new ShaderExpr("kg_eye_depth(" + raw.code() + ", " + iproj.code() + ")", GlslType.FLOAT);
    }

    /** Linearised depth {@code 0}(near)..{@code 1}(far) (Unity's Scene Depth "Linear 01"), reconstructed via {@code IProjMat}. */
    ShaderExpr sampleSceneDepthLinear01(ShaderExpr uv) {
        addInclude("kilagraph:kg_scene.glsl");
        ShaderExpr raw = sampleSceneDepthRaw(uv);
        ShaderExpr iproj = transformField("IProjMat", GlslType.MAT4);
        return new ShaderExpr("kg_linear01_depth(" + raw.code() + ", " + iproj.code() + ")", GlslType.FLOAT);
    }

    /** Eye-space distance of THIS fragment from the camera (world units), reconstructed from
     *  {@code gl_FragCoord.z} via {@code IProjMat} — the same basis as {@link #sampleSceneDepthEye}, so
     *  {@code sampleSceneDepthEye(uv) - fragmentEyeDepth()} cancels the camera (Unity's ScreenPosition raw
     *  {@code .w} / soft-particle depth fade). Fragment-only. */
    ShaderExpr fragmentEyeDepth() {
        addInclude("kilagraph:kg_scene.glsl");
        ShaderExpr iproj = transformField("IProjMat", GlslType.MAT4);
        return new ShaderExpr("kg_eye_depth(gl_FragCoord.z, " + iproj.code() + ")", GlslType.FLOAT);
    }

    /** Camera near-plane distance (world units), reconstructed from {@code IProjMat}. */
    ShaderExpr cameraNear() {
        addInclude("kilagraph:kg_scene.glsl");
        ShaderExpr iproj = transformField("IProjMat", GlslType.MAT4);
        return new ShaderExpr("kg_camera_near(" + iproj.code() + ")", GlslType.FLOAT);
    }

    /** Camera far-plane distance (world units), reconstructed from {@code IProjMat}. */
    ShaderExpr cameraFar() {
        addInclude("kilagraph:kg_scene.glsl");
        ShaderExpr iproj = transformField("IProjMat", GlslType.MAT4);
        return new ShaderExpr("kg_camera_far(" + iproj.code() + ")", GlslType.FLOAT);
    }

    // ---- traversal ---------------------------------------------------------------------------

    /** Pull an input port's value as a GLSL expression converted to {@code expected}. */
    ShaderExpr pullInput(PortModel inputPort, @Nullable GlslType expected) {
        GlslType target = expected != null ? expected : GlslType.of(inputPort.getDataTypeHandle());
        if (!inputPort.isConnected()) {
            // An unconnected UV port reads the mesh uv of the channel its configurator picked (not a literal).
            if (inputPort.getDataTypeHandle().equals(RenderTypeGraphTypes.UV)) {
                Object c = readConstant(inputPort);
                RenderTypeGraphTypes.UvChannel ch = c instanceof RenderTypeGraphTypes.UvChannel u
                        ? u : RenderTypeGraphTypes.UvChannel.UV0;
                return convert(meshUv(ch), target);
            }
            GlslType declared = GlslType.of(inputPort.getDataTypeHandle());
            // An unconnected sampler can't be a literal — fall back to the missing-texture sampler.
            if (declared == GlslType.SAMPLER2D) return missingSampler();
            // An unconnected gradient: a constant gradient editor sits on the port — build it (else default).
            if (declared == GlslType.GRADIENT) {
                Object c = readConstant(inputPort);
                return c instanceof RenderTypeGraphTypes.GradientValue gv ? constantGradient(gv) : defaultGradient();
            }
            // An unconnected curve: a constant curve editor sits on the port — build it (else default).
            if (declared == GlslType.CURVE) {
                Object c = readConstant(inputPort);
                return c instanceof RenderTypeGraphTypes.CurveValue cv ? constantCurve(cv) : defaultCurve();
            }
            Object constant = readConstant(inputPort);
            String code = declared != null
                    ? GlslFormat.literal(constant, declared)
                    : "0.0";
            ShaderExpr lit = new ShaderExpr(code, declared != null ? declared : GlslType.FLOAT);
            return convert(lit, target);
        }
        // Resolve the real upstream output, FOLLOWING wire portals (an input wired through a portal is
        // physically connected to the portal's exit, not the source). getFirstConnectedPort() walks the
        // WirePortalModel entry/exit links; the raw getConnectedPorts() would stop at the portal node.
        PortModel outputPort = inputPort.getFirstConnectedPort() instanceof PortModel pm ? pm : null;
        if (outputPort == null) {
            return convert(new ShaderExpr("0.0", GlslType.FLOAT), target);
        }
        Node ownerNode = nodeOf(outputPort);

        // Varying boundary: a vertex varying block consumed from the fragment stage.
        if (current == fragment && ownerNode instanceof IVaryingBlock vb) {
            if (preview) {
                // No vertex stage in preview — compute what the varying would carry. If the block's input is
                // driven (e.g. a fixed value), that value is uniform across the quad, so use it; otherwise a
                // sensible default. Same logic as the block's own preview (previewValueOf).
                return convert(previewValueOf(outputPort), target);
            }
            ensureVaryingBuilt(outputPort, vb);
            ShaderExpr ref = new ShaderExpr(vb.varyingName(), vb.varyingType());
            return convert(ref, target);
        }

        ShaderExpr value = evaluateOutput(outputPort);
        if (value == null) value = new ShaderExpr("0.0", GlslType.FLOAT);
        return convert(value, target);
    }

    /**
     * Pull an input port's value at its <em>natural</em> GLSL type, with no resize to the port's declared
     * type — used by Dynamic math/vector nodes that infer their result width from the operands. Int/bool
     * are normalised to float (so arithmetic builtins apply); float-vectors pass through untouched. An
     * unconnected port yields its embedded {@code float} default (the DYNAMIC port editor) as a literal.
     */
    ShaderExpr pullInputNatural(PortModel inputPort) {
        if (!inputPort.isConnected()) {
            Object constant = readConstant(inputPort);
            float f = constant instanceof Number n ? n.floatValue() : 0f;
            return new ShaderExpr(GlslFormat.f(f), GlslType.FLOAT);
        }
        PortModel outputPort = inputPort.getFirstConnectedPort() instanceof PortModel pm ? pm : null;
        if (outputPort == null) return new ShaderExpr("0.0", GlslType.FLOAT);
        Node ownerNode = nodeOf(outputPort);

        // Varying boundary (same handling as pullInput): a vertex varying consumed from the fragment stage.
        if (current == fragment && ownerNode instanceof IVaryingBlock vb) {
            if (preview) return normaliseToFloat(previewValueOf(outputPort));
            ensureVaryingBuilt(outputPort, vb);
            return new ShaderExpr(vb.varyingName(), vb.varyingType());
        }
        ShaderExpr value = evaluateOutput(outputPort);
        if (value == null) return new ShaderExpr("0.0", GlslType.FLOAT);
        return normaliseToFloat(value);
    }

    /** Normalise an int/bool expression to float (leaving float-vectors / opaque types unchanged). */
    private static ShaderExpr normaliseToFloat(ShaderExpr e) {
        return (e.type() == GlslType.INT || e.type() == GlslType.BOOL) ? convert(e, GlslType.FLOAT) : e;
    }

    @Nullable
    private ShaderExpr evaluateOutput(PortModel outputPort) {
        ShaderExpr cached = current.cache.get(outputPort);
        if (cached != null) return cached;
        AbstractNodeModel owner = outputPort.getNodeModel() instanceof AbstractNodeModel a ? a : null;
        if (owner == null) return null;
        if (!current.visiting.add(owner)) {
            throw new ShaderCompileException("Cycle detected while compiling node " + owner.getUid());
        }
        try {
            evaluateNode(owner);
        } finally {
            current.visiting.remove(owner);
        }
        return current.cache.get(outputPort);
    }

    private void evaluateNode(AbstractNodeModel owner) {
        if (!(owner instanceof NodeModel nm)) return;
        // NGT built-in constant node (the generic "Constant" you drag a value into): not a ShaderNode,
        // so read its value and emit it as a GLSL literal. Mirrors GraphExecutor's IConstantNode case.
        if (owner instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.IConstantNode constant) {
            Object value = constant.tryGetValue(constant.getDataType()).result().orElse(null);
            for (PortModel outp : nm.getOutputsByDisplayOrder()) {
                GlslType decl = GlslType.of(outp.getDataTypeHandle());
                if (decl == null) continue;
                // SAMPLER2D/GRADIENT/CURVE are opaque (not a constant scalar) — guard defensively.
                current.cache.put(outp, decl == GlslType.SAMPLER2D ? missingSampler()
                        : decl == GlslType.GRADIENT
                        ? (value instanceof RenderTypeGraphTypes.GradientValue gv ? constantGradient(gv) : defaultGradient())
                        : decl == GlslType.CURVE
                        ? (value instanceof RenderTypeGraphTypes.CurveValue cv ? constantCurve(cv) : defaultCurve())
                        : hoist(decl, GlslFormat.literal(value, decl)));
            }
            return;
        }
        // NGT variable node (a Blackboard variable dragged into the canvas). A variable is one
        // shader-basic type; how it compiles depends on its scope:
        //   LOCAL/UNKNOWN -> bake the declared value inline (like a constant);
        //   EXPOSED       -> a KG_Material uniform field (default value baked at material build);
        //   Sampler2D     -> ALWAYS a uniform (opaque type cannot be a literal), with a default texture.
        if (owner instanceof IVariableNode varNode) {
            compileVariableNode(nm, varNode);
            return;
        }
        // Subgraph node: inline the inner ShaderFunctionGraph (mirrors GraphExecutor.evaluateSubgraph,
        // emitting GLSL instead of values). Inner READ vars bind to the outer input expressions; inner
        // WRITE vars become the outer output ports.
        if (owner instanceof SubgraphNodeModel sub) {
            compileSubgraphNode(sub, nm);
            return;
        }
        if (!(owner instanceof ICustomNodeModel cnm)) return;
        Node userNode = cnm.getNode();
        if (!(userNode instanceof ShaderNode sn)) return; // non-shader nodes contribute nothing
        // Stage inference: this node is being used in the current stage. Flag if its affinity forbids it.
        ShaderStage stage = current == vertex ? ShaderStage.VERTEX : ShaderStage.FRAGMENT;
        StageAffinity affinity = sn.stageAffinity();
        // A per-node preview compiles the whole upstream chain into the fragment scope (single quad), so
        // stage affinity is meaningless there — a VERTEX_ONLY attribute reader is substituted with a
        // fragment-safe default (see VertexAttributeInputNode#compile). Only flag stage errors for a real
        // two-stage compile.
        if (!preview && !affinity.allows(stage)) {
            stageErrors.putIfAbsent(nm.getUid(),
                    new StageError(nm.getUid(), sn.getDisplayName().getString(), stage, affinity));
        }
        var ctx = new ShaderCompileContext(this, nm);
        sn.compile(ctx);
        for (PortModel outp : nm.getOutputsByDisplayOrder()) {
            ShaderExpr raw = ctx.outputs.get(outp.getPortId());
            if (raw == null) continue;
            GlslType decl = GlslType.of(outp.getDataTypeHandle());
            if (decl == null) {
                // A DYNAMIC (or otherwise unmapped) output: keep the node's own inferred type, but still
                // hoist a concrete float-vector result into a temp so a value consumed N times is emitted
                // once. Opaque/no-type exprs are cached inline (can't be copied into a temp).
                current.cache.put(outp, raw.type() != null && raw.type().isFloatVector()
                        ? hoist(raw.type(), raw.code()) : raw);
                continue;
            }
            ShaderExpr conv = convert(raw, decl);
            if (decl == GlslType.SAMPLER2D || decl == GlslType.GRADIENT || decl == GlslType.CURVE) {
                current.cache.put(outp, conv); // opaque — cannot/should not copy into a temp
            } else {
                current.cache.put(outp, hoist(decl, conv.code()));
            }
        }
    }

    /** Emit GLSL for each output port of a variable node (see scope rules at the call site). */
    private void compileVariableNode(NodeModel nm, IVariableNode varNode) {
        IVariable variable = varNode.getVariable();
        if (variable == null) return;
        // Inside an inlined subgraph: a READ variable is a function input — emit the bound outer
        // expression instead of a const/uniform. (WRITE/local vars of the subgraph fall through.)
        if (variable instanceof VariableDeclarationModelBase vd) {
            ShaderExpr bound = lookupBinding(vd.getUid());
            if (bound != null) {
                for (PortModel outp : nm.getOutputsByDisplayOrder()) {
                    GlslType decl = GlslType.of(outp.getDataTypeHandle());
                    current.cache.put(outp, decl != null ? convert(bound, decl) : bound);
                }
                return;
            }
        }
        VariableScope scope = (variable instanceof VariableDeclarationModelBase vd)
                ? vd.getScope() : VariableScope.LOCAL;
        Object defaultValue = variable.tryGetDefaultValue(variable.getDataType()).result().orElse(null);
        for (PortModel outp : nm.getOutputsByDisplayOrder()) {
            GlslType decl = GlslType.of(outp.getDataTypeHandle());
            if (decl == null) continue;
            if (decl == GlslType.SAMPLER2D) {
                String name = variableUniformName(variable);
                layout.addSampler(name);
                SamplerDefault def = SamplerDefault.of(defaultValue);
                if (def != null) samplerDefaults.putIfAbsent(name, def);
                variableSamplerNames.putIfAbsent(variable.getName(), name);
                current.cache.put(outp, new ShaderExpr(name, decl));
            } else if (scope == VariableScope.EXPOSED) {
                String name = variableUniformName(variable);
                String accessor = layout.addField(name, decl);
                uniformDefaults.putIfAbsent(name, GlslFormat.components(defaultValue, decl));
                variableUniformFields.putIfAbsent(variable.getName(), new MaterialUniformLayout.Field(name, decl));
                current.cache.put(outp, new ShaderExpr(accessor, decl));
            } else if (decl == GlslType.GRADIENT) {
                // LOCAL gradient: bake the actual keys as a builder (opaque — don't hoist into a temp).
                current.cache.put(outp, defaultValue instanceof RenderTypeGraphTypes.GradientValue gv
                        ? constantGradient(gv) : defaultGradient());
            } else if (decl == GlslType.CURVE) {
                // LOCAL curve: bake the actual segments as a builder (opaque — don't hoist into a temp).
                current.cache.put(outp, defaultValue instanceof RenderTypeGraphTypes.CurveValue cv
                        ? constantCurve(cv) : defaultCurve());
            } else {
                // LOCAL / UNKNOWN: bake the declared value inline (mirrors the IConstantNode branch).
                current.cache.put(outp, hoist(decl, GlslFormat.literal(defaultValue, decl)));
            }
        }
    }

    /**
     * Inline a subgraph node by emitting the inner {@code ShaderFunctionGraph}'s logic into the current
     * stage. Binds each inner READ variable to the outer input port's expression (pushed as a binding
     * frame), then computes each outer output port from the inner WRITE variable's source. Mirrors
     * {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor}'s {@code evaluateSubgraph}.
     */
    private void compileSubgraphNode(SubgraphNodeModel sub, NodeModel nm) {
        if (!(sub.getSubgraphModel() instanceof CustomGraphModelImpl inner)) return; // unresolved → outputs null
        if (inner == graph.graphModel) return; // trivial self-reference guard

        Map<UUID, ShaderExpr> frame = new HashMap<>();
        for (var v : inner.getGraphVariableModels()) {
            if (v == null) continue;
            var mods = v.getModifiers();
            if (mods == null || !mods.hasFlag(ModifierFlags.READ)) continue;
            PortModel outerInput = lookupSubgraphPort(sub, v, true, mods);
            if (outerInput == null) continue;
            frame.put(v.getUid(), pullInput(outerInput, GlslType.of(outerInput.getDataTypeHandle())));
        }
        bindingStack.push(frame);
        try {
            for (var v : inner.getGraphVariableModels()) {
                if (v == null) continue;
                var mods = v.getModifiers();
                if (mods == null || !mods.hasFlag(ModifierFlags.WRITE)) continue;
                PortModel outerOutput = lookupSubgraphPort(sub, v, false, mods);
                if (outerOutput == null) continue;
                GlslType decl = GlslType.of(outerOutput.getDataTypeHandle());
                current.cache.put(outerOutput, compileInnerWriteVar(v, inner, decl));
            }
        } finally {
            bindingStack.pop();
        }
    }

    /** Compile the value assigned to an inner WRITE variable (its "set" writer node's input source). */
    private ShaderExpr compileInnerWriteVar(VariableDeclarationModelBase v, CustomGraphModelImpl inner, @Nullable GlslType decl) {
        GlslType target = decl != null ? decl : GlslType.FLOAT;
        for (var innerNm : inner.getNodeModels()) {
            if (!(innerNm instanceof NodeModel n)) continue;
            IVariableNode vn = null;
            if (innerNm instanceof IVariableNode direct) {
                vn = direct;
            } else if (innerNm instanceof ICustomNodeModel cnm && cnm.getNode() instanceof IVariableNode wrapped) {
                vn = wrapped;
            }
            if (vn == null) continue;
            IVariable ref = vn.getVariable();
            if (ref == null || !java.util.Objects.equals(ref.getName(), v.getName())) continue;
            var inputs = n.getInputsById();
            if (inputs.isEmpty()) continue; // not the "set" form
            return pullInput(inputs.values().iterator().next(), target);
        }
        // No writer node — fall back to the variable's declared default (baked literal), else zero.
        Object def = v.tryGetDefaultValue(v.getDataType()).result().orElse(null);
        return decl != null ? hoist(decl, GlslFormat.literal(def, decl)) : new ShaderExpr("0.0", GlslType.FLOAT);
    }

    /** The outer subgraph-node port mirroring an inner variable (port id = uid, or uid+"-in"/"-out"). */
    @Nullable
    private PortModel lookupSubgraphPort(SubgraphNodeModel sub, VariableDeclarationModelBase v,
                                         boolean wantInput, ModifierFlags mods) {
        String suffix = (mods == ModifierFlags.READ_WRITE) ? (wantInput ? "-in" : "-out") : "";
        String portId = v.getUid().toString() + suffix;
        return wantInput ? sub.getInputsById().get(portId) : sub.getOutputsById().get(portId);
    }

    /** The bound outer expression for an inner READ variable, or null if not inside that subgraph. */
    @Nullable
    private ShaderExpr lookupBinding(UUID uid) {
        for (var frame : bindingStack) { // head = innermost subgraph; uids are globally unique
            ShaderExpr e = frame.get(uid);
            if (e != null) return e;
        }
        return null;
    }

    /**
     * The unique GLSL identifier (uniform field / sampler name) for a variable, namespaced with a
     * {@code kg_} prefix so it can never collide with builtin samplers (Sampler0/1/2) or UBO names.
     * Stable per variable declaration uid; distinct sanitized collisions get a numeric suffix.
     */
    private String variableUniformName(IVariable variable) {
        UUID uid = (variable instanceof VariableDeclarationModelBase vd) ? vd.getUid() : null;
        if (uid != null) {
            String existing = variableNames.get(uid);
            if (existing != null) return existing;
        }
        String base = ("kg_" + sanitizeIdentifier(variable.getName())).replaceAll("_+", "_");
        String name = base;
        for (int i = 1; usedVariableNames.contains(name); i++) {
            name = base + "_" + i;
        }
        usedVariableNames.add(name);
        if (uid != null) variableNames.put(uid, name);
        return name;
    }

    /**
     * Reduce an arbitrary variable name to a legal GLSL identifier body ([A-Za-z0-9_]). Runs of
     * non-identifier chars (and any resulting consecutive underscores) collapse to a single {@code _} —
     * GLSL reserves identifiers containing {@code __}, so a doubled underscore fails to compile. Callers
     * always prefix with {@code kg_*}, so a leading digit is fine (the result never starts with one).
     */
    private static String sanitizeIdentifier(String raw) {
        if (raw == null || raw.isEmpty()) return "var";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append((c < 128 && (Character.isLetterOrDigit(c) || c == '_')) ? c : '_');
        }
        String s = sb.toString().replaceAll("_+", "_");
        return s.isEmpty() ? "var" : s;
    }

    /**
     * Allocate a per-node {@code uniform sampler2D kg_tex_*} for a {@code TextureNode}'s value and bake
     * its texture + sampler params as the material default. The sampler name is stable per node uid.
     */
    ShaderExpr textureSampler(NodeModel nm, Object value) {
        String name = nodeSamplerName(nm);
        layout.addSampler(name);
        SamplerDefault def = SamplerDefault.of(value);
        samplerDefaults.putIfAbsent(name, def != null ? def : SamplerDefault.missing());
        return new ShaderExpr(name, GlslType.SAMPLER2D);
    }

    /** The stable, unique {@code kg_tex_*} sampler name for a texture node (keyed by node uid). */
    private String nodeSamplerName(NodeModel nm) {
        return nodeSamplerNames.computeIfAbsent(nm.getUid(), uid -> {
            // collapse so the uid's dashes-turned-underscores can't double the prefix's trailing '_'.
            String base = ("kg_tex_" + sanitizeIdentifier(uid.toString())).replaceAll("_+", "_");
            String name = base;
            for (int i = 1; usedVariableNames.contains(name); i++) name = base + "_" + i;
            usedVariableNames.add(name);
            return name;
        });
    }

    private void ensureVaryingBuilt(PortModel blockOutput, IVaryingBlock vb) {
        String name = vb.varyingName();
        if (varyings.containsKey(name)) return;
        varyings.put(name, vb.varyingType());
        AbstractNodeModel owner = blockOutput.getNodeModel() instanceof AbstractNodeModel a ? a : null;
        if (!(owner instanceof NodeModel nm)) return;
        StageScope saved = current;
        current = vertex;
        try {
            var ctx = new ShaderCompileContext(this, nm);
            ShaderExpr value = convert(vb.compileVarying(ctx), vb.varyingType());
            line(name + " = " + value.code() + ";");
        } finally {
            current = saved;
        }
    }

    // ---- emission helpers (called via ShaderCompileContext) ----------------------------------

    protected ShaderExpr hoist(GlslType type, String code) {
        String name = current.tempPrefix + "_" + (current.tempCounter++);
        current.body.append("    ").append(type.glsl()).append(' ').append(name)
                .append(" = ").append(code).append(";\n");
        return new ShaderExpr(name, type);
    }

    protected void line(String statement) {
        current.body.append("    ").append(statement).append('\n');
    }

    /**
     * Register a global-scope GLSL helper function (the full definition {@code glsl}) under {@code name}
     * in the current stage, declared once before {@code main()}. Keyed by name — the same helper used by
     * many node instances appears once, and a re-registration with the same name is ignored (so a helper
     * can't be redefined). A node that needs helper B to call helper A must register A first (insertion
     * order is preserved). Used by procedural nodes (noise / Voronoi) whose math is clearest as a reusable
     * function rather than an inlined expression.
     */
    void addFunction(String name, String glsl) {
        current.functions.putIfAbsent(name, glsl);
    }

    /** Emit the {@code KG_Gradient} / {@code KG_Curve} struct declarations when this stage needs them —
     *  before the UBO + functions (both reference the types). Needed if the material UBO has a
     *  gradient/curve field or a node samples a gradient/curve. */
    private void appendStructDecls(StringBuilder sb, StageScope scope) {
        if (layout.hasGradientField() || scope.usesGradient) {
            sb.append('\n').append(GradientGlsl.STRUCT).append('\n');
        }
        if (layout.hasCurveField() || scope.usesCurve) {
            sb.append('\n').append(CurveGlsl.STRUCT).append('\n');
        }
    }

    /** Emit a stage's helper function definitions (if any), each on its own line, after a blank line. */
    private static void appendFunctions(StringBuilder sb, StageScope scope) {
        if (scope.functions.isEmpty()) return;
        sb.append('\n');
        for (String fn : scope.functions.values()) sb.append(fn).append('\n');
    }

    /** Append each used KG-managed UBO's std140 declaration, sorted by name so the generated source (and
     *  therefore the content hash) is stable regardless of node-traversal order. */
    private void appendUniformBlocks(StringBuilder sb, boolean leadingNewline) {
        uniformBlocks.stream()
                .sorted(java.util.Comparator.comparing(
                        com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock::uboName))
                .forEach(b -> {
                    if (leadingNewline) sb.append('\n');
                    sb.append(b.declareGlsl());
                });
    }

    /**
     * Minecraft include files that unconditionally declare a {@code layout(std140) uniform} block.
     * Importing one means the generated GLSL contains that block, so the pipeline must declare the
     * matching uniform — registered automatically here to keep shader and pipeline in lockstep.
     */
    private static final Map<String, String> INCLUDE_UBOS = Map.of(
            "minecraft:fog.glsl", "Fog",
            "minecraft:light.glsl", "Lighting",
            "minecraft:dynamictransforms.glsl", "DynamicTransforms",
            "minecraft:projection.glsl", "Projection",
            "minecraft:globals.glsl", "Globals"
    );

    protected void addInclude(String path) {
        current.includes.add(path);
        String ubo = INCLUDE_UBOS.get(path);
        if (ubo != null) builtinUbos.add(ubo);
    }

    void useBuiltinUbo(String name) {
        builtinUbos.add(name);
    }

    MaterialUniformLayout layout() {
        return layout;
    }

    // ---- type conversion ---------------------------------------------------------------------

    protected static ShaderExpr convert(ShaderExpr expr, @Nullable GlslType target) {
        if (expr == null || target == null || expr.type() == target) return expr;
        GlslType from = expr.type();
        String code = expr.code();
        if (from == GlslType.SAMPLER2D || target == GlslType.SAMPLER2D
                || from == GlslType.MAT4 || target == GlslType.MAT4) {
            return new ShaderExpr(code, target); // not convertible; keep code, retag
        }
        // Normalise int/bool to float for arithmetic targets.
        if ((from == GlslType.INT || from == GlslType.BOOL) && target.isFloatVector()) {
            from = GlslType.FLOAT;
            code = "float(" + code + ")";
        }
        if (from == GlslType.FLOAT && target == GlslType.INT) {
            return new ShaderExpr("int(" + code + ")", GlslType.INT);
        }
        if (from == GlslType.INT && target == GlslType.FLOAT) {
            return new ShaderExpr("float(" + code + ")", GlslType.FLOAT);
        }
        if (!from.isFloatVector() || !target.isFloatVector()) {
            return new ShaderExpr(code, target);
        }
        if (from == GlslType.FLOAT) {
            // scalar broadcast
            return new ShaderExpr(target == GlslType.FLOAT ? code : target.glsl() + "(" + code + ")", target);
        }
        if (target == GlslType.FLOAT) {
            return new ShaderExpr("(" + code + ").x", GlslType.FLOAT);
        }
        int fc = from.components();
        int tc = target.components();
        if (tc <= fc) {
            return new ShaderExpr("(" + code + ")." + "xyzw".substring(0, tc), target);
        }
        // pad missing components: 0.0, with w forced to 1.0 for vec4 targets
        StringBuilder pad = new StringBuilder();
        for (int i = fc; i < tc; i++) {
            pad.append(", ");
            pad.append((target == GlslType.VEC4 && i == tc - 1) ? "1.0" : "0.0");
        }
        return new ShaderExpr(target.glsl() + "(" + code + pad + ")", target);
    }

    // ---- assembly ----------------------------------------------------------------------------

    private String assembleVertex() {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : vertex.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!vertex.includes.isEmpty()) sb.append('\n');
        sb.append(vertexInputsBlock());
        appendStructDecls(sb, vertex);
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append('\n').append(uniforms);
        appendUniformBlocks(sb, true);
        if (!varyings.isEmpty()) {
            sb.append('\n');
            for (var e : varyings.entrySet()) {
                if (e.getValue().requiresFlat()) sb.append("flat ");
                sb.append("out ").append(e.getValue().glsl()).append(' ').append(e.getKey()).append(";\n");
            }
        }
        appendFunctions(sb, vertex);
        sb.append("\nvoid main() {\n");
        sb.append(vertexPrologue());
        sb.append(vertex.body).append("}\n");
        return sb.toString();
    }

    /**
     * The vertex-stage input declarations, emitted after the includes/builtin uniforms. Default: one
     * {@code in <type> <name>;} per element of the graph's composed vertex format. A subclass whose inputs
     * come from an include (declaring the attribute layouts itself) overrides this to emit its import instead.
     */
    protected String vertexInputsBlock() {
        return vertexAttributes(graph.getSettings().vertexFormatElements());
    }

    /**
     * Statements emitted at the very top of the vertex {@code main()}, before any generated body statement.
     * Default: none. A subclass overrides this to set up its input state (e.g. a struct pulled from an
     * include) so {@link #attributeRef} expressions resolve.
     */
    protected String vertexPrologue() {
        return "";
    }

    private String assemblePreviewVertex() {
        return GLSL_VERSION + "\n\n"
                + "#moj_import <minecraft:dynamictransforms.glsl>\n"
                + "#moj_import <minecraft:projection.glsl>\n\n"
                + "in vec3 Position;\nin vec2 UV0;\nin vec3 Normal;\n\nout vec2 vUv;\nout vec3 vPos;\nout vec3 vNormal;\n\n"
                + "void main() {\n"
                + "    vUv = UV0;\n"
                + "    vPos = Position;\n"
                + "    vNormal = Normal;\n"
                + "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n"
                + "}\n";
    }

    private String assemblePreviewFragment(ShaderExpr color) {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : fragment.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!fragment.includes.isEmpty()) sb.append('\n');
        appendStructDecls(sb, fragment);
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append(uniforms);
        appendUniformBlocks(sb, false);
        sb.append("\nin vec2 vUv;\nin vec3 vPos;\nin vec3 vNormal;\n\nout vec4 fragColor;\n");
        appendFunctions(sb, fragment);
        sb.append("\nvoid main() {\n").append(fragment.body);
        sb.append("    fragColor = ").append(color.code()).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String assembleFragment(FragmentOutputs out) {
        StringBuilder sb = new StringBuilder();
        sb.append(GLSL_VERSION).append("\n\n");
        for (String inc : fragment.includes) sb.append("#moj_import <").append(inc).append(">\n");
        if (!fragment.includes.isEmpty()) sb.append('\n');
        appendStructDecls(sb, fragment);
        String uniforms = layout.declareGlsl();
        if (!uniforms.isEmpty()) sb.append(uniforms);
        appendUniformBlocks(sb, false);
        if (!varyings.isEmpty()) {
            for (var e : varyings.entrySet()) {
                if (e.getValue().requiresFlat()) sb.append("flat ");
                sb.append("in ").append(e.getValue().glsl()).append(' ').append(e.getKey()).append(";\n");
            }
        }
        sb.append("\nout vec4 fragColor;\n");
        appendFunctions(sb, fragment);
        sb.append("\nvoid main() {\n").append(fragment.body);
        String baseColor = out.baseColor != null ? out.baseColor.code() : "vec3(1.0)";
        String alpha = out.alpha != null ? out.alpha.code() : "1.0";
        sb.append("    vec3 kg_baseColor = ").append(baseColor).append(";\n");
        sb.append("    float kg_alpha = ").append(alpha).append(";\n");
        if (out.emission != null) {
            sb.append("    kg_baseColor += ").append(out.emission.code()).append(";\n");
        }
        if (out.alphaDiscardCutoff != null) {
            sb.append("    if (kg_alpha < ").append(out.alphaDiscardCutoff.code()).append(") discard;\n");
        }
        sb.append("    fragColor = vec4(kg_baseColor, kg_alpha);\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * The {@code in} vertex attribute declarations for the graph's composed vertex format. Each declared
     * element contributes one {@code in <glslType> <attribName>;} line; the {@code attribName} is exactly
     * the name {@link com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat} binds the element under, so
     * the shader's inputs line up with the pipeline layout. Unknown keys are skipped.
     */
    private static String vertexAttributes(java.util.List<String> elementKeys) {
        StringBuilder sb = new StringBuilder();
        var seen = new java.util.HashSet<String>();
        for (String key : elementKeys) {
            var e = com.lowdragmc.kilagraph.rendertype.format.KGVertexElements.get(key);
            if (e == null) continue;
            if (!seen.add(e.attribName())) continue; // never declare the same `in` twice
            sb.append("in ").append(e.glslType()).append(' ').append(e.attribName()).append(";\n");
        }
        return sb.toString();
    }

    // ---- helpers -----------------------------------------------------------------------------

    @Nullable
    private static Node nodeOf(AbstractNodeModel model) {
        return model instanceof ICustomNodeModel cnm ? cnm.getNode() : null;
    }

    @Nullable
    private static Node nodeOf(PortModel port) {
        return port.getNodeModel() instanceof AbstractNodeModel a ? nodeOf(a) : null;
    }

    @Nullable
    private static Object readConstant(PortModel inputPort) {
        try {
            return inputPort.tryGetValue(Object.class).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ContextNodeModel asContext(NodeModel model, String which) {
        if (model instanceof ContextNodeModel cnm) return cnm;
        throw new ShaderCompileException("RenderTypeGraph is missing its " + which + " stage");
    }
}
