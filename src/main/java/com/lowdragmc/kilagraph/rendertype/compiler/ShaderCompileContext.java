package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-node compilation context, mirroring {@code EvalContext} but emitting GLSL instead of values.
 * A node's {@link ShaderNode#compile(ShaderCompileContext)} reads inputs with {@link #input(String)}
 * and publishes outputs with {@link #output(String, ShaderExpr)}; side declarations (includes,
 * uniforms, samplers, raw statements) go through the helpers here. All emission targets the
 * compiler's <em>current stage</em>.
 */
public final class ShaderCompileContext {

    private final ShaderGraphCompiler compiler;
    private final NodeModel node;
    final Map<String, ShaderExpr> outputs = new HashMap<>();

    ShaderCompileContext(ShaderGraphCompiler compiler, NodeModel node) {
        this.compiler = compiler;
        this.node = node;
    }

    // ---- input reads -------------------------------------------------------------------------

    /** Pull an input port as a GLSL expression, converted to the port's declared type. */
    public ShaderExpr input(String portId) {
        PortModel pm = node.getInputsById().get(portId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + portId + "' on " + node.getUid());
        GlslType expected = GlslType.of(pm.getDataTypeHandle());
        return compiler.pullInput(pm, expected);
    }

    /**
     * Pull an input port, but if it is unconnected return {@code builtinDefault} instead of reading
     * an embedded constant. Used by varying blocks whose unconnected inputs fall back to a builtin
     * vertex attribute/expression.
     */
    public ShaderExpr inputOr(String portId, ShaderExpr builtinDefault) {
        PortModel pm = node.getInputsById().get(portId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + portId + "' on " + node.getUid());
        if (!pm.isConnected()) return builtinDefault;
        GlslType expected = GlslType.of(pm.getDataTypeHandle());
        return compiler.pullInput(pm, expected);
    }

    /** Whether the given input port has a wire. */
    public boolean isConnected(String portId) {
        PortModel pm = node.getInputsById().get(portId);
        return pm != null && pm.isConnected();
    }

    /**
     * Pull an input port at its <em>natural</em> GLSL type — no resize to the port's declared type
     * (only int/bool→float normalisation). Used by Dynamic math/vector nodes, which infer their
     * output width from the actual operand types instead of a fixed port type. An unconnected port
     * yields its embedded {@code float} default as a literal.
     */
    public ShaderExpr inputDynamic(String portId) {
        PortModel pm = node.getInputsById().get(portId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + portId + "' on " + node.getUid());
        return compiler.pullInputNatural(pm);
    }

    /** Whether this is a per-node preview compile (single fragment quad; no real vertex stage). */
    public boolean isPreview() {
        return compiler.isPreview();
    }

    // ---- vertex attributes -------------------------------------------------------------------

    /**
     * A raw vertex-attribute reference (e.g. {@code Color}) when its element is in the active vertex
     * format, else {@code fallback} — so a node default referencing an attribute the user removed degrades
     * to a safe constant instead of producing undefined-variable GLSL. The substitution is recorded and
     * surfaced as an editor warning.
     */
    public ShaderExpr attribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElement element,
                                GlslType type, ShaderExpr fallback) {
        return compiler.attribute(element, type, fallback);
    }

    /** Whether the given vertex element is declared in the active vertex format. */
    public boolean hasAttribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElement element) {
        return compiler.hasAttribute(element);
    }

    /** Record that a referenced attribute is absent from the format (for callers building the ref themselves). */
    public void markMissingAttribute(String attribName) {
        compiler.markMissingAttribute(attribName);
    }

    // ---- output writes -----------------------------------------------------------------------

    /** Publish an output port's GLSL expression (converted/hoisted by the compiler after compile). */
    public void output(String portId, ShaderExpr expr) {
        outputs.put(portId, expr);
    }

    // ---- option reads ------------------------------------------------------------------------

    public <T> T option(String optionId, Class<T> type, T defaultIfMissing) {
        INodeOption opt = node.getNodeOptionById(optionId);
        if (opt == null) return defaultIfMissing;
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        if (type.isInstance(raw)) return type.cast(raw);
        if (raw instanceof Number n) {
            if (type == Integer.class) return type.cast(n.intValue());
            if (type == Float.class) return type.cast(n.floatValue());
            if (type == Double.class) return type.cast(n.doubleValue());
            if (type == Long.class) return type.cast(n.longValue());
        }
        if (type == String.class && raw != null) return type.cast(raw.toString());
        return defaultIfMissing;
    }

    // ---- emission helpers --------------------------------------------------------------------

    /** Register a {@code #moj_import <path>} include in the current stage. */
    public void include(String path) {
        compiler.addInclude(path);
    }

    /**
     * Declare use of a Minecraft builtin uniform block (e.g. {@code Fog}, {@code Projection},
     * {@code Lighting}) backed by an include that defines its std140 layout. Registers the include in
     * the current stage and records the UBO so the runtime pipeline declares the matching binding.
     */
    public void useMinecraftUniform(String uboName, String includePath) {
        compiler.addInclude(includePath);
    }

    /**
     * Declare a vanilla / KG-managed builtin uniform in the current stage (1.21.1: individual {@code uniform}s —
     * there is no {@code dynamictransforms.glsl}/{@code globals.glsl}, so ModelViewMat / ProjMat / ColorModulator /
     * TextureMat / ScreenSize / GameTime etc. must be declared directly). Returns the GLSL accessor (the bare
     * name), so a node emits {@code ctx.useBuiltinUniform("ProjMat", GlslType.MAT4)} instead of a raw string.
     * Vanilla builtins are auto-set by {@code ShaderInstance.setDefaultUniforms}; KG-managed by KGBuiltinUniforms.
     */
    public String useBuiltinUniform(String name, GlslType type) {
        return compiler.useBuiltinUniform(name, type);
    }

    /**
     * Register a material UBO field and return a {@link ShaderExpr} referencing it. The field is
     * exposed to users as a per-material uniform.
     */
    public ShaderExpr uniform(String name, GlslType type) {
        String accessor = compiler.layout().addField(name, type);
        return new ShaderExpr(accessor, type);
    }

    /** Register a sampler (builtin or material) and return a sampler-typed expression for it. */
    public ShaderExpr sampler(String name) {
        compiler.layout().addSampler(name);
        return new ShaderExpr(name, GlslType.SAMPLER2D);
    }

    /**
     * Allocate a per-node {@code uniform sampler2D} for a texture value and bake its texture + sampler
     * params as the material default. Used by {@code TextureNode}; the value is a
     * {@code RenderTypeGraphTypes.Sampler2DValue}.
     */
    public ShaderExpr textureSampler(Object value) {
        return compiler.textureSampler(node, value);
    }

    /** The fallback sampler for an unconnected Sampler2D input — bound to the MC missing-texture. */
    public ShaderExpr missingSampler() {
        return compiler.missingSampler();
    }

    /**
     * Build a constant {@code KG_Gradient} value (a Unity-style gradient) for {@code value}, registering the
     * shared sample helper + a per-gradient builder, and return a GRADIENT-typed expression. Used by the
     * Gradient node. A {@code SampleGradient} node turns the result + a float position into a {@code vec4}.
     */
    public ShaderExpr constantGradient(com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue value) {
        return compiler.constantGradient(value);
    }

    /** A default black&rarr;white {@code KG_Gradient} (registers the helper) — for an unconnected gradient. */
    public ShaderExpr defaultGradient() {
        return compiler.defaultGradient();
    }

    /** Declare that this node references {@code KG_Gradient} (emits its struct decl + sample helpers). Call
     *  before using {@code kg_sampleGradient(...)} — e.g. a Sample Gradient node. */
    public void useGradient() {
        compiler.useGradient();
    }

    /**
     * Build a constant {@code KG_Curve} value (a Unity-style float curve) for {@code value}, registering the
     * shared sample helper + a per-curve builder, and return a CURVE-typed expression. Used by the Curve
     * node. A {@code SampleCurve} node turns the result + a float position into a {@code float}.
     */
    public ShaderExpr constantCurve(com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue value) {
        return compiler.constantCurve(value);
    }

    /** A default linear 0&rarr;1 {@code KG_Curve} (registers the helper) — for an unconnected curve. */
    public ShaderExpr defaultCurve() {
        return compiler.defaultCurve();
    }

    /** Declare that this node references {@code KG_Curve} (emits its struct decl + sample helpers). Call
     *  before using {@code kg_sampleCurve(...)} — e.g. a Sample Curve node. */
    public void useCurve() {
        compiler.useCurve();
    }

    /** Vanilla overlay sampler ({@code Sampler1}); flags the pipeline to enable overlay binding. */
    public ShaderExpr overlaySampler() {
        return compiler.overlaySampler();
    }

    /** Vanilla lightmap sampler ({@code Sampler2}); flags the pipeline to enable lightmap binding. */
    public ShaderExpr lightmapSampler() {
        return compiler.lightmapSampler();
    }

    /** Screen-space UV {@code gl_FragCoord.xy / ScreenSize} (vec2) — the default UV for Scene Color/Depth. */
    public ShaderExpr screenUv() {
        return compiler.screenUv();
    }

    /** Sample the captured opaque scene colour (vec3) at {@code uv} — Unity's Scene Color node. */
    public ShaderExpr sampleSceneColor(ShaderExpr uv) {
        return compiler.sampleSceneColor(uv);
    }

    /** Raw hardware scene depth {@code [0,1]} at {@code uv} (Unity Scene Depth "Raw"). */
    public ShaderExpr sampleSceneDepthRaw(ShaderExpr uv) {
        return compiler.sampleSceneDepthRaw(uv);
    }

    /** Eye-space scene depth in world units at {@code uv} (Unity Scene Depth "Eye"). */
    public ShaderExpr sampleSceneDepthEye(ShaderExpr uv) {
        return compiler.sampleSceneDepthEye(uv);
    }

    /** Linearised scene depth {@code 0}(near)..{@code 1}(far) at {@code uv} (Unity Scene Depth "Linear 01"). */
    public ShaderExpr sampleSceneDepthLinear01(ShaderExpr uv) {
        return compiler.sampleSceneDepthLinear01(uv);
    }

    /** Eye-space distance of the current fragment from the camera (world units) — Unity's ScreenPosition raw
     *  {@code .w}. Reconstructed from {@code gl_FragCoord.z} with the same {@code IProjMat} basis as
     *  {@link #sampleSceneDepthEye}, so subtracting the two cancels the camera (depth fade). Fragment-only. */
    public ShaderExpr fragmentEyeDepth() {
        return compiler.fragmentEyeDepth();
    }

    /** Camera near-plane distance (world units), reconstructed from {@code IProjMat}. */
    public ShaderExpr cameraNear() {
        return compiler.cameraNear();
    }

    /** Camera far-plane distance (world units), reconstructed from {@code IProjMat}. */
    public ShaderExpr cameraFar() {
        return compiler.cameraFar();
    }

    /** Absolute world camera position ({@code kg_CameraBlockPos - kg_CameraOffset}) — Minecraft's precision-split
     *  form, bound from the double camera position so world-position math stays jitter-free far from the origin. */
    public ShaderExpr cameraWorldPos() {
        return compiler.cameraWorldPos();
    }

    /** Allocate a temp variable in the current stage holding {@code expr}, returning a reference. */
    public ShaderExpr temp(GlslType type, String code) {
        return compiler.hoist(type, code);
    }

    /** The interpolated mesh uv (vec2). In a per-node preview this is the preview quad's uv. */
    public ShaderExpr meshUv() {
        return compiler.meshUv();
    }

    /** The interpolated mesh uv for a specific channel (UV0/UV1/UV2). UV0 is the texture uv; UV1/UV2 are
     *  the overlay/lightmap coords (cast to vec2). See {@link #meshUv()}. */
    public ShaderExpr meshUv(com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.UvChannel channel) {
        return compiler.meshUv(channel);
    }

    /** The interpolated lit vertex colour (vanilla per-vertex {@code minecraft_mix_light} by default). */
    public ShaderExpr litVertexColor() {
        return compiler.litVertexColor();
    }

    /** The interpolated raw (unlit) vertex {@code Color} attribute. */
    public ShaderExpr meshColor() {
        return compiler.meshColor();
    }

    /** Block-style vertex colour: {@code Color * sample_lightmap(Sampler2, UV2)} (vanilla {@code block.vsh}),
     *  no Normal needed. */
    public ShaderExpr blockVertexColor() {
        return compiler.blockVertexColor();
    }

    /**
     * The interpolated world-space surface normal (vec3) — the default fallback for an unconnected
     * {@code normal} port (Unity's Normal Vector node defaults to world space). Not unit-length after
     * interpolation, so renormalize before use. In a per-node preview the quad faces the camera ({@code +Z}).
     */
    public ShaderExpr meshNormal() {
        return compiler.meshNormal();
    }

    /**
     * The interpolated world-space view direction, surface&rarr;camera (vec3) — the default fallback for an
     * unconnected {@code viewDir} port (Unity's View Direction node defaults to world space). Renormalize
     * before use. In a per-node preview this is {@code +Z} (looking straight at the quad).
     */
    public ShaderExpr meshViewDir() {
        return compiler.meshViewDir();
    }

    /**
     * The interpolated <b>model-space</b> vertex position {@code (Position + ModelOffset)} (vec3) — the
     * default fallback for an unconnected {@code position}/{@code coords} port. Fragment-safe. Wire a
     * Transform node for world space.
     */
    public ShaderExpr meshPosition() {
        return compiler.meshPosition();
    }

    /** The interpolated {@code sphericalVertexDistance} varying (float) — vanilla's spherical fog distance,
     *  the default for an unconnected fog distance port. */
    public ShaderExpr sphericalVertexDistance() {
        return compiler.sphericalVertexDistance();
    }

    /** The interpolated {@code cylindricalVertexDistance} varying (float) — vanilla's cylindrical fog
     *  distance, the default for an unconnected fog distance port. */
    public ShaderExpr cylindricalVertexDistance() {
        return compiler.cylindricalVertexDistance();
    }

    /** A raw Minecraft {@code Fog} UBO field accessor (e.g. {@code FogColor}, {@code FogEnvironmentalStart}) —
     *  the default for an unconnected fog parameter port. Registers the fog include + UBO binding. */
    public ShaderExpr fogField(String name, GlslType type) {
        return compiler.fogField(name, type);
    }

    /**
     * The model-space vertex position MC transforms: {@code (Position + ModelOffset)} (vec3). ModelOffset is
     * a per-draw DynamicTransforms uniform — zero unless set (block/terrain rendering sets it), so this is
     * the correct, harmless default for position / fog distance. Registers the dynamictransforms import.
     */
    public ShaderExpr modelPosition() {
        return compiler.modelPosition();
    }

    /**
     * Read a fixed interpolated varying in the fragment stage, ensuring the vsh writes it with
     * {@code vshDefault} (unless a vertex varying block already produced it). In a per-node preview
     * (no vertex stage) returns {@code previewDefault}. Used by {@code FragmentInputNode}s.
     */
    public ShaderExpr varyingInput(String name, GlslType type,
                                   java.util.function.Supplier<ShaderExpr> vshDefault, ShaderExpr previewDefault) {
        return compiler.varyingInput(name, type, vshDefault, previewDefault);
    }

    /** World time in seconds, from KilaGraph's engine-globals block (we update it each frame). */
    public ShaderExpr engineTime() {
        return compiler.engineTime();
    }

    /**
     * A {@code KG_Transforms} field accessor (precomputed space matrices + camera, e.g.
     * {@code "ViewMat"}/{@code "IModelViewMat"}/{@code "CameraPos"}), flagging the pipeline to declare +
     * bind the block. {@code type} is the field's GLSL type. Used by the Transform node.
     */
    public ShaderExpr transformField(String field, GlslType type) {
        return compiler.transformField(field, type);
    }

    /** Minecraft's builtin {@code Globals.GameTime} (normalised day fraction, wraps every MC day). */
    public ShaderExpr mcGameTime() {
        return compiler.mcGameTime();
    }

    /** Append a raw statement to the current stage's main() body. */
    public void line(String statement) {
        compiler.line(statement);
    }

    /**
     * Declare a global-scope GLSL helper function {@code glsl} (its full definition) under {@code name}
     * in the current stage, emitted once before {@code main()}. Keyed by name (a re-registration with the
     * same name is ignored — no redefinition); register a callee before its caller. For procedural nodes
     * whose math reads best as a reusable function (noise hashes, the Voronoi cell loop).
     */
    public void function(String name, String glsl) {
        compiler.addFunction(name, glsl);
    }

    /** Convert an expression to a target GLSL type using the standard float/vector rules. */
    public ShaderExpr convert(ShaderExpr expr, GlslType target) {
        return compiler.convert(expr, target);
    }

    public NodeModel node() {
        return node;
    }

    @Nullable
    public NodeModel getNodeModel() {
        return node;
    }
}
