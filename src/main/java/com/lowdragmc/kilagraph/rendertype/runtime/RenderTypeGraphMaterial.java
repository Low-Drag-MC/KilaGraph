package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.Map;

/**
 * A compiled, ready-to-draw material for the 1.21.1 backport: a vanilla {@link ShaderInstance} (built from the
 * graph's generated GLSL+manifest via {@link KGShaderResourceProvider#createShaderInstance}) plus the per-instance
 * uniform values and sampler bindings. Everything is individual uniforms — no UBO (see
 * {@code ShaderGraphCompiler.useBuiltinUniform} / {@link MaterialUniformLayout}).
 *
 * <p>Draw flow (the preview / any renderer): with the {@link ShaderInstance} active
 * ({@code RenderSystem.setShader(() -> material.shader())}), call {@link #applyUniforms()} to stage the KG-managed
 * ({@link KGBuiltinUniforms}) and EXPOSED-variable uniforms + samplers, then draw geometry in {@link #format()} /
 * {@link #mode()} through {@code BufferUploader.drawWithShader} — which sets the vanilla builtins
 * ({@code ModelViewMat}/{@code ProjMat}/{@code Fog*}/…) via {@code ShaderInstance.setDefaultUniforms} and uploads
 * everything in {@code ShaderInstance.apply()}.</p>
 *
 * <p>The material can be drawn either immediately (the editor preview: {@link #applyRenderState()} +
 * {@code drawWithShader}) or through its vanilla {@link #renderType()} on a {@code MultiBufferSource} (in-world).
 * GRADIENT struct uniforms (member-wise, {@link #setGradient}) and Scene Color/Depth samplers
 * ({@link SceneCaptureManager}) are applied here; lightmap/overlay samplers (Sampler1/Sampler2) are bound by the
 * RenderType's {@code LIGHTMAP}/{@code OVERLAY} shards on the in-world path, not by the immediate preview draw.</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private final LDShaderInstance shader;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final RenderTypeGraph.Settings settings;
    private final String contentHash;
    /** builtin / KG-managed uniforms the GLSL declares (name -> type), staged each draw via {@link KGBuiltinUniforms}. */
    private final Map<String, GlslType> builtinUniforms;
    /** The EXPOSED-variable uniform values + sampler bindings (set-by-name store, staged each draw). */
    private final KGMaterialValues values;
    /** Whether an Overlay/LightMap node referenced Sampler1/Sampler2 — drives the RenderType's overlay/lightmap shards. */
    private final boolean usesOverlay;
    private final boolean usesLightmap;
    /** Whether a Scene Color/Depth node referenced KG_SceneColor/KG_SceneDepth — drives the scene capture
     *  ({@link SceneCaptureManager}, acquired for this material's lifetime) + binds those samplers at draw. */
    private final boolean usesScene;
    /** Lazily-built vanilla RenderType bound to {@link #shader} (for in-world rendering / export). */
    @org.jetbrains.annotations.Nullable
    private RenderType renderType;
    private boolean closed = false;

    public RenderTypeGraphMaterial(LDShaderInstance shader, CompiledShaderGraph compiled) {
        this.shader = shader;
        this.format = RenderTypeFactory.vertexFormat(compiled.settings().vertexFormatElements());
        this.mode = RenderTypeFactory.vertexMode(compiled.settings().vertexFormatMode());
        this.settings = compiled.settings();
        this.contentHash = compiled.contentHash();
        this.builtinUniforms = new HashMap<>(compiled.builtinUniforms());
        // Bakes EXPOSED-variable default values + default sampler textures.
        this.values = new KGMaterialValues(compiled);
        this.usesOverlay = compiled.usesOverlay();
        this.usesLightmap = compiled.usesLightmap();
        this.usesScene = compiled.usesSceneColor() || compiled.usesSceneDepth();
        // A scene-using material keeps the opaque-scene capture alive for its lifetime (balanced in close()).
        if (usesScene) SceneCaptureManager.INSTANCE.acquire();
    }

    public VertexFormat format() { return format; }
    public VertexFormat.Mode mode() { return mode; }
    public RenderTypeGraph.Settings settings() { return settings; }
    public String contentHash() { return contentHash; }
    public LDShaderInstance shader() { return shader; }
    /** Whether the shader samples the vanilla lightmap ({@code Sampler2}) — an immediate-mode caller (the
     *  editor preview) must then enable the light layer itself; the RenderType path binds it via its shard. */
    public boolean usesLightmap() { return usesLightmap; }

    /** Re-bake baked defaults from a freshly compiled graph with the same content hash (value-only edit). */
    public void refreshDefaults(CompiledShaderGraph compiled) {
        values.bakeDefaults(compiled);
    }

    // ---- uniform / texture setters (by EXPOSED variable display name) -------------------------

    /** The per-instance value store, for callers that manage values directly (see {@link KGMaterialValues}). */
    public KGMaterialValues values() { return values; }

    public void setUniformField(String fieldName, float... components) { values.setUniformField(fieldName, components); }

    public boolean setUniform(String variableName, float value) { return values.setUniform(variableName, value); }
    public boolean setUniform(String variableName, Vector2fc v) { return values.setUniform(variableName, v); }
    public boolean setUniform(String variableName, Vector3fc v) { return values.setUniform(variableName, v); }
    public boolean setUniform(String variableName, Vector4fc v) { return values.setUniform(variableName, v); }
    public boolean setUniform(String variableName, Matrix4fc m) { return values.setUniform(variableName, m); }

    /** Set a vec4 (e.g. a Color variable) from an ARGB int — unpacked to rgba in 0..1. */
    public boolean setColorUniform(String variableName, int argb) { return values.setColorUniform(variableName, argb); }

    /** Set a vec4 (an HDR Color variable) from an {@link HDRColor} — intensity premultiplied into rgb. */
    public boolean setHDRColorUniform(String variableName, HDRColor color) {
        return values.setHDRColorUniform(variableName, color);
    }

    /** Set a Gradient variable by display name (packed to the {@code KG_Gradient} member layout). */
    public boolean setGradient(String variableName, RenderTypeGraphTypes.GradientValue value) {
        return values.setGradient(variableName, value);
    }

    /** Set a Curve variable by display name (packed to the {@code KG_Curve} member layout). */
    public boolean setCurve(String variableName, RenderTypeGraphTypes.CurveValue value) {
        return values.setCurve(variableName, value);
    }

    /** Bind a texture to a Sampler2D variable by display name (or raw sampler name). Returns false if unknown. */
    public boolean setTexture(String name, ResourceLocation texture) {
        return values.setTexture(name, texture);
    }

    // ---- draw-time binding -------------------------------------------------------------------

    /**
     * Apply the graph {@code Settings}' blend / cull / depth to {@code RenderSystem} for an immediate-mode draw
     * (the preview). Mirrors what a vanilla {@code RenderType}'s {@code RenderStateShard}s would set up; call before
     * {@link #applyUniforms()} + the draw. Depth/cull/blend come from the SAME {@link #blendFactors} /
     * {@code depthFunc} mapping the {@link #renderType()} export uses, so an immediate draw and a batched
     * RenderType draw of the same graph render identically.
     */
    public void applyRenderState() {
        // Depth test + write.
        if (settings.depthTest() == RenderTypeGraph.Settings.DepthTest.NONE) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(switch (settings.depthTest()) {
                case LESS -> GL11.GL_LESS;
                case EQUAL -> GL11.GL_EQUAL;
                case ALWAYS -> GL11.GL_ALWAYS;
                case LEQUAL, NONE -> GL11.GL_LEQUAL;
            });
        }
        RenderSystem.depthMask(settings.depthWrite());

        // Back-face culling.
        if (settings.cull()) RenderSystem.enableCull(); else RenderSystem.disableCull();

        // Blend / transparency (blendFactors is shared with the RenderType path so both honour every mode).
        Blend b = blendFactors(settings.blend());
        if (b == null) {
            RenderSystem.disableBlend();
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(b.src(), b.dst(), b.srcAlpha(), b.dstAlpha());
    }

    /** The separable blend factors for a {@code BlendMode} ({@code null} = opaque / no blend). The single source of
     *  truth shared by the immediate-draw path ({@link #applyRenderState()}) and the RenderType path
     *  ({@link #transparencyOf}), so every mode is honoured identically by both — including the ones with no vanilla
     *  {@code TransparencyStateShard} constant (premultiplied-alpha / entity-outline-blit / invert), which used to
     *  silently collapse to plain translucent on the RenderType path. */
    @org.jetbrains.annotations.Nullable
    private static Blend blendFactors(RenderTypeGraph.Settings.BlendMode blend) {
        var A = GlStateManager.SourceFactor.SRC_ALPHA;
        var OMSA = GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA;
        var ONE_S = GlStateManager.SourceFactor.ONE;
        var ONE_D = GlStateManager.DestFactor.ONE;
        return switch (blend) {
            case OPAQUE -> null;
            case TRANSLUCENT, ENTITY_OUTLINE_BLIT -> new Blend(A, OMSA, ONE_S, OMSA);
            case TRANSLUCENT_PREMULTIPLIED_ALPHA -> new Blend(ONE_S, OMSA, ONE_S, OMSA);
            case ADDITIVE, LIGHTNING, OVERLAY -> new Blend(A, ONE_D, A, ONE_D);
            case GLINT -> new Blend(GlStateManager.SourceFactor.SRC_COLOR, ONE_D,
                    GlStateManager.SourceFactor.ZERO, ONE_D);
            case INVERT -> new Blend(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, ONE_S, GlStateManager.DestFactor.ZERO);
        };
    }

    /** A separable blend function (colour src/dst + alpha src/dst). */
    private record Blend(GlStateManager.SourceFactor src, GlStateManager.DestFactor dst,
                         GlStateManager.SourceFactor srcAlpha, GlStateManager.DestFactor dstAlpha) {}

    // ---- RenderType export -------------------------------------------------------------------

    /**
     * A vanilla {@link RenderType} bound to this material's {@code ShaderInstance} (built lazily, cached), for
     * in-world rendering / export. Its {@code CompositeState} maps the graph {@code Settings} to
     * {@code RenderStateShard}s: our shader via {@link RenderStateShard.ShaderStateShard}, plus
     * transparency/depth/cull/write-mask/output/lightmap/overlay. A {@link RenderStateShard.TexturingStateShard}
     * is (ab)used purely as an AT-free hook to run {@link #applyUniforms()} during {@code setupRenderState} — so
     * the KG-managed + EXPOSED uniforms/samplers are applied even on a vanilla batched draw (which calls
     * {@code shader.apply()} but not this material). All shards used are public 1.21.1 API — no AccessTransformer.
     *
     * <p>TODO(1.21-backport milestone 2): main-texture (Sampler0) state — a graph sampling a block/entity atlas
     * needs a {@code TextureStateShard} for it; currently only the graph's own EXPOSED samplers (set by name) and
     * lightmap/overlay are bound.</p>
     *
     * <p><b>Lifecycle constraint (read before building a runtime consumption API).</b> The returned RenderType
     * captures {@code this::shader}, and {@link #close()} closes that {@code ShaderInstance}. A material is a
     * <em>single content-hash snapshot</em> — the editor/consumer rebuilds a fresh material (and closes the old
     * one) on every graph change — so a RenderType held across an edit would draw with a CLOSED shader (GL error).
     * Callers must therefore treat the RenderType as valid only for this material's lifetime: <b>own materials in
     * a refcounted cache keyed by graph id and re-fetch the RenderType each frame</b> rather than caching the
     * object. (Today only the previews build materials, and they draw immediately without ever calling this — so
     * there is no live consumer of the RenderType yet; this note guards the future one.)</p>
     */
    public RenderType renderType() {
        if (renderType == null) renderType = buildRenderType();
        return renderType;
    }

    private RenderType buildRenderType() {
        var uniformHook = new RenderStateShard.TexturingStateShard(
                Kilagraph.MODID + "_kg_uniforms", this::applyUniforms, () -> {});
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(this::shader))
                .setTexturingState(uniformHook)
                .setTransparencyState(transparencyOf(settings.blend()))
                .setDepthTestState(depthTestOf(settings.depthTest()))
                .setCullState(settings.cull() ? RenderStateShard.CULL : RenderStateShard.NO_CULL)
                .setWriteMaskState(settings.depthWrite() ? RenderStateShard.COLOR_DEPTH_WRITE : RenderStateShard.COLOR_WRITE)
                .setLightmapState(usesLightmap ? RenderStateShard.LIGHTMAP : RenderStateShard.NO_LIGHTMAP)
                .setOverlayState(usesOverlay ? RenderStateShard.OVERLAY : RenderStateShard.NO_OVERLAY)
                .setOutputState(outputOf(settings.outputTarget()))
                .createCompositeState(settings.affectsOutline());
        return RenderType.create(Kilagraph.MODID + ":generated/" + contentHash, format, mode, 256,
                false, settings.sortOnUpload(), state);
    }

    private static RenderStateShard.TransparencyStateShard transparencyOf(RenderTypeGraph.Settings.BlendMode blend) {
        Blend b = blendFactors(blend);
        if (b == null) return RenderStateShard.NO_TRANSPARENCY;
        // A custom shard built from the shared blendFactors — the public (name, setup, clear) constructor lets us
        // honour every mode exactly (incl. those with no vanilla constant), and matches applyRenderState 1:1.
        return new RenderStateShard.TransparencyStateShard("kg_" + blend.name().toLowerCase(java.util.Locale.ROOT),
                () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(b.src(), b.dst(), b.srcAlpha(), b.dstAlpha());
                },
                () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });
    }

    /** {@code LESS} has no public vanilla shard constant, but {@code DepthTestStateShard(name, glDepthFunc)} is
     *  public — so we honour {@code GL_LESS} exactly rather than downgrading to {@code LEQUAL}. */
    private static final RenderStateShard.DepthTestStateShard LESS_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("less", GL11.GL_LESS);

    private static RenderStateShard.DepthTestStateShard depthTestOf(RenderTypeGraph.Settings.DepthTest depth) {
        return switch (depth) {
            case NONE, ALWAYS -> RenderStateShard.NO_DEPTH_TEST;
            case EQUAL -> RenderStateShard.EQUAL_DEPTH_TEST;
            case LEQUAL -> RenderStateShard.LEQUAL_DEPTH_TEST;
            case LESS -> LESS_DEPTH_TEST;
        };
    }

    private static RenderStateShard.OutputStateShard outputOf(RenderTypeGraph.Settings.OutputTarget target) {
        return switch (target) {
            case MAIN -> RenderStateShard.MAIN_TARGET;
            case TRANSLUCENT -> RenderStateShard.TRANSLUCENT_TARGET;
            case PARTICLES -> RenderStateShard.PARTICLES_TARGET;
            case WEATHER -> RenderStateShard.WEATHER_TARGET;
            case ITEM_ENTITY -> RenderStateShard.ITEM_ENTITY_TARGET;
        };
    }

    /**
     * Stage the KG-managed + EXPOSED uniforms and the custom samplers into the {@link ShaderInstance}. Call on the
     * render thread with the shader active, right before {@code BufferUploader.drawWithShader} (which then sets the
     * vanilla builtins and uploads everything). Vanilla builtins are NOT set here (see {@link KGBuiltinUniforms}).
     */
    public void applyUniforms() {
        if (closed) return;
        KGBuiltinUniforms.bind(shader, builtinUniforms);
        values.apply(shader);
        // Scene Color/Depth: bind the opaque-scene capture's texture ids (as raw GL ids — ShaderInstance.apply
        // accepts Integer samplers, cf. RenderTarget._blitToScreen). A sampler the shader didn't declare is a
        // harmless no-op (apply only binds names in the manifest), so bind both when the material uses the scene.
        if (usesScene) {
            shader.setSampler(ShaderGraphCompiler.SCENE_COLOR_SAMPLER, SceneCaptureManager.INSTANCE.colorTextureId());
            shader.setSampler(ShaderGraphCompiler.SCENE_DEPTH_SAMPLER, SceneCaptureManager.INSTANCE.depthTextureId());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (usesScene) SceneCaptureManager.INSTANCE.release(); // balance the acquire() in the ctor
        shader.close();
    }
}
