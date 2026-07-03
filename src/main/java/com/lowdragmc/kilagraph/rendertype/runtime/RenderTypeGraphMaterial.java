package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.List;
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
 * {@code drawWithShader}) or through its vanilla {@link #renderType()} on a {@code MultiBufferSource} (in-world —
 * verified). GRADIENT struct uniforms ARE applied (member-wise — see {@link #setGradient}). TODO(1.21-backport
 * milestone 2): scene/lightmap/overlay dynamic samplers are not yet applied (their node groups are deferred).</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private final LDShaderInstance shader;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final RenderTypeGraph.Settings settings;
    private final String contentHash;
    /** builtin / KG-managed uniforms the GLSL declares (name -> type), staged each draw via {@link KGBuiltinUniforms}. */
    private final Map<String, GlslType> builtinUniforms;
    /** EXPOSED-variable uniform fields (name -> type), set each draw from {@link #values}. */
    private final List<MaterialUniformLayout.Field> materialFields;
    /** EXPOSED variable display name -> its uniform field, for set-by-name updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Sampler2D variable display name -> sampler uniform name, so setTexture accepts the friendly name. */
    private final Map<String, String> variableSamplers;
    /** uniform name -> current components. */
    private final Map<String, float[]> values = new HashMap<>();
    /** sampler uniform name -> bound texture, resolved to an AbstractTexture at {@link #applyUniforms()}. */
    private final Map<String, ResourceLocation> samplerTextures = new HashMap<>();
    /** Whether an Overlay/LightMap node referenced Sampler1/Sampler2 — drives the RenderType's overlay/lightmap shards. */
    private final boolean usesOverlay;
    private final boolean usesLightmap;
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
        this.materialFields = compiled.layout().fields();
        this.uniformFields = new HashMap<>(compiled.uniformFields());
        this.variableSamplers = new HashMap<>(compiled.variableSamplers());
        this.usesOverlay = compiled.usesOverlay();
        this.usesLightmap = compiled.usesLightmap();
        // Bake EXPOSED-variable default values + default sampler textures.
        this.values.putAll(compiled.uniformDefaults());
        for (Map.Entry<String, SamplerDefault> e : compiled.samplerDefaults().entrySet()) {
            samplerTextures.put(e.getKey(), e.getValue().texture());
        }
    }

    public VertexFormat format() { return format; }
    public VertexFormat.Mode mode() { return mode; }
    public RenderTypeGraph.Settings settings() { return settings; }
    public String contentHash() { return contentHash; }
    public LDShaderInstance shader() { return shader; }

    /** Re-bake baked defaults from a freshly compiled graph with the same content hash (value-only edit). */
    public void refreshDefaults(CompiledShaderGraph compiled) {
        values.putAll(compiled.uniformDefaults());
        for (Map.Entry<String, SamplerDefault> e : compiled.samplerDefaults().entrySet()) {
            samplerTextures.put(e.getKey(), e.getValue().texture());
        }
    }

    // ---- uniform / texture setters (by EXPOSED variable display name) -------------------------

    public void setUniformField(String fieldName, float... components) { values.put(fieldName, components.clone()); }

    public boolean setUniform(String variableName, float value) { return setByVariable(variableName, value); }
    public boolean setUniform(String variableName, Vector2fc v) { return setByVariable(variableName, v.x(), v.y()); }
    public boolean setUniform(String variableName, Vector3fc v) { return setByVariable(variableName, v.x(), v.y(), v.z()); }
    public boolean setUniform(String variableName, Vector4fc v) { return setByVariable(variableName, v.x(), v.y(), v.z(), v.w()); }

    public boolean setUniform(String variableName, Matrix4fc m) {
        float[] arr = new float[16];
        m.get(arr);
        return setByVariable(variableName, arr);
    }

    /** Set a vec4 (e.g. a Color variable) from an ARGB int — unpacked to rgba in 0..1. */
    public boolean setColorUniform(String variableName, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        return setByVariable(variableName, r, g, b, a);
    }

    private boolean setByVariable(String variableName, float... components) {
        MaterialUniformLayout.Field field = uniformFields.get(variableName);
        if (field == null) return false;
        values.put(field.name(), components);
        return true;
    }

    /** Bind a texture to a Sampler2D variable by display name (or raw sampler name). Returns false if unknown. */
    public boolean setTexture(String name, ResourceLocation texture) {
        String sampler = variableSamplers.getOrDefault(name, name);
        samplerTextures.put(sampler, texture);
        return true;
    }

    // ---- draw-time binding -------------------------------------------------------------------

    /**
     * Apply the graph {@code Settings}' blend / cull / depth to {@code RenderSystem} for an immediate-mode draw
     * (the preview). Mirrors what a vanilla {@code RenderType}'s {@code RenderStateShard}s would set up; call before
     * {@link #applyUniforms()} + the draw. (The eventual {@code RenderType} export encodes the same intent as shards
     * instead — this is the immediate-draw equivalent, and doesn't need the AT-gated {@code RenderStateShard}
     * constants.) Blend funcs mirror the corresponding vanilla transparency shards; exotic modes are approximated.
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

        // Blend / transparency.
        if (settings.blend() == RenderTypeGraph.Settings.BlendMode.OPAQUE) {
            RenderSystem.disableBlend();
            return;
        }
        RenderSystem.enableBlend();
        var src = GlStateManager.SourceFactor.SRC_ALPHA;
        var dst = GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA;
        var srcA = GlStateManager.SourceFactor.ONE;
        var dstA = GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA;
        switch (settings.blend()) {
            case TRANSLUCENT, ENTITY_OUTLINE_BLIT -> { /* defaults above (translucent) */ }
            case TRANSLUCENT_PREMULTIPLIED_ALPHA -> { src = GlStateManager.SourceFactor.ONE; }
            case ADDITIVE, LIGHTNING, OVERLAY -> {
                src = GlStateManager.SourceFactor.SRC_ALPHA; dst = GlStateManager.DestFactor.ONE;
                srcA = GlStateManager.SourceFactor.SRC_ALPHA; dstA = GlStateManager.DestFactor.ONE;
            }
            case GLINT -> {
                src = GlStateManager.SourceFactor.SRC_COLOR; dst = GlStateManager.DestFactor.ONE;
                srcA = GlStateManager.SourceFactor.ZERO; dstA = GlStateManager.DestFactor.ONE;
            }
            case INVERT -> {
                src = GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR; dst = GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR;
                srcA = GlStateManager.SourceFactor.ONE; dstA = GlStateManager.DestFactor.ZERO;
            }
            case OPAQUE -> { /* handled above */ }
        }
        RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
    }

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
     * lightmap/overlay are bound, and {@code Settings.DepthTest.LESS} maps to {@code LEQUAL} (no public LESS shard).</p>
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
        return switch (blend) {
            case OPAQUE -> RenderStateShard.NO_TRANSPARENCY;
            case ADDITIVE, OVERLAY -> RenderStateShard.ADDITIVE_TRANSPARENCY;
            case LIGHTNING -> RenderStateShard.LIGHTNING_TRANSPARENCY;
            case GLINT -> RenderStateShard.GLINT_TRANSPARENCY;
            // No vanilla shard for premultiplied / entity-outline-blit / invert — closest is translucent.
            case TRANSLUCENT, TRANSLUCENT_PREMULTIPLIED_ALPHA, ENTITY_OUTLINE_BLIT, INVERT ->
                    RenderStateShard.TRANSLUCENT_TRANSPARENCY;
        };
    }

    private static RenderStateShard.DepthTestStateShard depthTestOf(RenderTypeGraph.Settings.DepthTest depth) {
        return switch (depth) {
            case NONE, ALWAYS -> RenderStateShard.NO_DEPTH_TEST;
            case EQUAL -> RenderStateShard.EQUAL_DEPTH_TEST;
            case LEQUAL, LESS -> RenderStateShard.LEQUAL_DEPTH_TEST; // no public LESS shard — LEQUAL is closest
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
        for (MaterialUniformLayout.Field f : materialFields) {
            setUniform(f.name(), f.type(), values.get(f.name()));
        }
        var textureManager = Minecraft.getInstance().getTextureManager();
        samplerTextures.forEach((name, loc) -> shader.setSampler(name, textureManager.getTexture(loc)));
    }

    private void setUniform(String name, GlslType type, float[] v) {
        Uniform u = shader.getUniform(name);
        if (u == null) return;
        switch (type) {
            case FLOAT -> u.set(at(v, 0));
            case INT, BOOL -> u.set((int) at(v, 0));
            case VEC2 -> u.set(at(v, 0), at(v, 1));
            case VEC3 -> u.set(at(v, 0), at(v, 1), at(v, 2));
            case VEC4 -> u.set(at(v, 0), at(v, 1), at(v, 2), at(v, 3));
            case MAT4 -> u.set(new Matrix4f().set(v == null ? new float[16] : v));
            // A GRADIENT field is a KG_Gradient struct uniform, uploaded member-by-member (setUniform's `u` — the
            // whole-struct name — has no GL location; the members do). SAMPLER2D is bound via setSampler above.
            case GRADIENT -> setGradient(name, v);
            case SAMPLER2D -> { }
        }
    }

    /**
     * Upload a packed gradient (the {@code GradientGlsl.pack} 68-float layout: header + 8 colour + 8 alpha vec4)
     * into the {@code KG_Gradient} struct uniform {@code name}'s members ({@code name.header},
     * {@code name.colors[i]}, {@code name.alphas[i]}) — the manifest declares each as a vec4 (see
     * {@code KGShaderManifest.appendUniforms}).
     */
    private void setGradient(String name, float[] v) {
        if (v == null) return;
        setVec4(name + ".header", v, 0);
        for (int i = 0; i < GradientGlsl.MAX_KEYS; i++) {
            setVec4(name + ".colors[" + i + "]", v, 4 + i * 4);
            setVec4(name + ".alphas[" + i + "]", v, 4 + GradientGlsl.MAX_KEYS * 4 + i * 4);
        }
    }

    private void setVec4(String uniformName, float[] v, int base) {
        Uniform u = shader.getUniform(uniformName);
        if (u == null) return;
        u.set(at(v, base), at(v, base + 1), at(v, base + 2), at(v, base + 3));
    }

    private static float at(float[] v, int i) { return v != null && i < v.length ? v[i] : 0f; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shader.close();
    }
}
