package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.*;

/**
 * A compiled, ready-to-render material: a Minecraft {@link RenderType} (sharing a cached pipeline)
 * plus its per-instance {@code KG_Material} uniform buffer and dynamic sampler bindings. Renderers get
 * {@link #renderType()} for {@code submitCustomGeometry}; {@code RenderTypeMixin} calls
 * {@link #bindCustomUniforms} during draw to bind both the custom UBO and the per-instance textures that
 * vanilla's {@code RenderSetup} path doesn't update.
 *
 * <p><b>Setting values.</b> EXPOSED graph variables are addressed by their <em>display name</em> (not
 * the mangled {@code kg_*} GLSL identifier): {@link #setUniform(String, float)} and its typed overloads
 * resolve the variable name to its UBO field and pack std140 for you; {@link #setColorUniform} takes an
 * ARGB int. Sampler2D variables are updated with {@link #setTexture} — dynamically, without rebuilding
 * the RenderType (the binding is re-applied each draw in {@link #bindCustomUniforms}). The low-level
 * {@link #setUniformField} (by GLSL field name) remains for baking compiled defaults.</p>
 *
 * <p>Overlay ({@code Sampler1}) / lightmap ({@code Sampler2}) stay owned by vanilla and are not part of
 * this material's dynamic texture map.</p>
 *
 * <p>Instances register themselves in a static identity side-table keyed by {@link RenderType} so the
 * draw mixin can find the owning material without adding fields to the vanilla class.</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private static final Map<RenderType, RenderTypeGraphMaterial> BY_RENDER_TYPE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final RenderType renderType;
    private final MaterialUniformBuffer uniforms;
    /** KilaGraph-managed UBOs this material's graph uses — uploaded each draw (HEAD) + bound in the pass. */
    private final java.util.List<ShaderUniformBlock> uniformBlocks;
    private final String contentHash;
    /** Variable display name -> KG_Material field (name + type), for set-by-name uniform updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Variable display name -> sampler uniform name, so setTexture accepts the friendly name too. */
    private final Map<String, String> variableSamplers;
    /** Sampler uniform name -> bound texture + params. (Re)bound each draw so setTexture is dynamic. */
    private final Map<String, SamplerDefault> textures;
    /** Sampler uniform name -> resolved view+sampler, refreshed in {@link #prepareUniforms} (pre-pass). */
    private final Map<String, ResolvedSampler> resolvedTextures = new HashMap<>();
    /** Sampler uniform name -> externally-supplied raw view+sampler (e.g. another mod's live {@code GpuTexture}),
     *  bound in preference to the Identifier/TextureManager binding. Lets a host feed a texture that has no
     *  registered {@link Identifier} (like SlideShow's decoded slide) straight into a graph sampler. */
    private final Map<String, ResolvedSampler> externalViews = new HashMap<>();
    /** Whether the graph samples the captured scene colour/depth — bound from {@link SceneCaptureManager}. */
    private final boolean usesSceneColor;
    private final boolean usesSceneDepth;
    /** Non-zero discriminator written to the shaderpack program's injected {@code kg_surface_id} uniform while
     *  this material draws (see {@code IrisShaderInjector}). 0 = no Iris injection. Set by the factory when a
     *  shaderpack-compatible variant is active. */
    private int irisSurfaceId = 0;

    private record ResolvedSampler(GpuTextureView view, GpuSampler sampler) {}

    public RenderTypeGraphMaterial(RenderType renderType, MaterialUniformLayout layout,
                                   java.util.List<ShaderUniformBlock> uniformBlocks, String contentHash,
                                   Map<String, MaterialUniformLayout.Field> uniformFields,
                                   Map<String, String> variableSamplers,
                                   Map<String, SamplerDefault> textures,
                                   boolean usesSceneColor, boolean usesSceneDepth) {
        this.renderType = renderType;
        this.uniforms = new MaterialUniformBuffer(layout);
        this.uniformBlocks = java.util.List.copyOf(uniformBlocks);
        this.contentHash = contentHash;
        this.uniformFields = new HashMap<>(uniformFields);
        this.variableSamplers = new HashMap<>(variableSamplers);
        this.textures = new HashMap<>(textures);
        this.usesSceneColor = usesSceneColor;
        this.usesSceneDepth = usesSceneDepth;
        BY_RENDER_TYPE.put(renderType, this);
    }

    /** Lookup the material owning a render type, or {@code null} if it isn't a KilaGraph material. */
    @Nullable
    public static RenderTypeGraphMaterial of(RenderType renderType) {
        return BY_RENDER_TYPE.get(renderType);
    }

    public RenderType renderType() {
        return renderType;
    }

    public String contentHash() {
        return contentHash;
    }

    /** The {@code kg_surface_id} written to the active shaderpack program while this material draws; 0 = none. */
    public int irisSurfaceId() {
        return irisSurfaceId;
    }

    public void setIrisSurfaceId(int id) {
        this.irisSurfaceId = id;
    }

    /** Whether the graph samples the captured scene colour ({@code KG_SceneColor}) — under a shaderpack the
     *  injected program's sampler is bound from {@code SceneCaptureManager} by {@code IrisSurfaceUniform}. */
    public boolean usesSceneColor() {
        return usesSceneColor;
    }

    /** The uploaded {@code KG_Material} buffer slice, for binding onto an Iris shaderpack program at draw
     *  (see {@code IrisSurfaceUniform}); null when this material has no uniform fields or before upload. */
    @Nullable
    public GpuBufferSlice irisMaterialSlice() {
        return uniforms.slice();
    }

    /** The KilaGraph-managed UBOs ({@code KG_Globals}/{@code KG_Transforms}/...) this material's graph uses,
     *  for binding onto an Iris shaderpack program at draw. */
    public java.util.List<ShaderUniformBlock> irisUniformBlocks() {
        return uniformBlocks;
    }

    /** Receives each of this material's dynamic samplers (uniform name + resolved view/params) for binding
     *  onto an Iris shaderpack program at draw. */
    public interface IrisSamplerBinder {
        void bind(String samplerName, GpuTextureView view, GpuSampler sampler);
    }

    /** Feed each resolved dynamic sampler (with any external-view override applied) to {@code binder}, so
     *  {@code IrisSurfaceUniform} can bind them onto the shaderpack program. Call after {@link #prepareUniforms}
     *  (which fills {@link #resolvedTextures}). Excludes overlay/lightmap/scene (not in the dynamic map). */
    public void bindIrisSamplers(IrisSamplerBinder binder) {
        for (Map.Entry<String, ResolvedSampler> e : resolvedTextures.entrySet()) {
            ResolvedSampler r = externalViews.getOrDefault(e.getKey(), e.getValue());
            binder.bind(e.getKey(), r.view(), r.sampler());
        }
    }

    /**
     * Re-bake the per-instance baked defaults (EXPOSED uniform values + sampler textures/params) from a
     * freshly compiled graph with the <b>same content hash</b>. A value-only edit (a Color/uniform
     * default, a Sampler2D texture or its filter/address/mipmap) changes these but not the GLSL, so the
     * pipeline is reused (no rebuild) — but the material must still refresh, or the edit wouldn't reach
     * the GPU until an unrelated structural change forced a rebuild. Cheap (no RenderType/pipeline work).
     */
    public void refreshDefaults(CompiledShaderGraph compiled) {
        for (Map.Entry<String, float[]> e : compiled.uniformDefaults().entrySet()) {
            setUniformField(e.getKey(), e.getValue());
        }
        textures.clear();
        textures.putAll(RenderTypeFactory.buildMaterialTextures(compiled));
    }

    // ---- uniform value setters ---------------------------------------------------------------

    /** Set a material uniform field value directly by its GLSL field name (1-4 std140 components). */
    public void setUniformField(String fieldName, float... components) {
        uniforms.set(fieldName, components);
    }

    /** Set an EXPOSED variable's value by its display name. Returns false if no such uniform variable. */
    public boolean setUniform(String variableName, float value) {
        return setByVariable(variableName, value);
    }

    public boolean setUniform(String variableName, Vector2fc v) {
        return setByVariable(variableName, v.x(), v.y());
    }

    public boolean setUniform(String variableName, Vector3fc v) {
        return setByVariable(variableName, v.x(), v.y(), v.z());
    }

    public boolean setUniform(String variableName, Vector4fc v) {
        return setByVariable(variableName, v.x(), v.y(), v.z(), v.w());
    }

    /** Set a vec4 (e.g. a Color variable) from an ARGB int — unpacked to rgba in 0..1. */
    public boolean setColorUniform(String variableName, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return setByVariable(variableName, r, g, b, a);
    }

    public boolean setUniform(String variableName, Matrix4fc m) {
        float[] arr = new float[16];
        m.get(arr); // column-major, matching std140 mat4 packing
        return setByVariable(variableName, arr);
    }

    /** Set an EXPOSED Gradient variable from a {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue}
     *  — std140-packed (header + 8 colour + 8 alpha vec4) into its {@code KG_Gradient} UBO field. */
    public boolean setGradient(String variableName, com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue value) {
        return setByVariable(variableName,
                com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl.pack(value));
    }

    /** Set an EXPOSED Curve variable from a {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue}
     *  — std140-packed (header + 16 segment vec4) into its {@code KG_Curve} UBO field. */
    public boolean setCurve(String variableName, com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue value) {
        return setByVariable(variableName,
                com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl.pack(value));
    }

    private boolean setByVariable(String variableName, float... components) {
        MaterialUniformLayout.Field field = uniformFields.get(variableName);
        if (field == null) return false;
        uniforms.set(field.name(), components);
        return true;
    }

    // ---- texture setters ---------------------------------------------------------------------

    /**
     * Bind a texture to a sampler dynamically (no RenderType rebuild). {@code name} may be a Sampler2D
     * variable's display name or the raw sampler uniform name. Returns false if this material doesn't
     * manage that sampler (e.g. overlay/lightmap, owned by vanilla). Takes effect on the next draw.
     */
    public boolean setTexture(String name, Identifier texture) {
        String sampler = variableSamplers.getOrDefault(name, name);
        SamplerDefault current = textures.get(sampler);
        if (current == null || texture == null) return false;
        // Swap the texture, keep the sampler params (filter/address/mipmap) the graph configured.
        textures.put(sampler, new SamplerDefault(texture, current.filter(), current.address(), current.mipmap()));
        return true;
    }

    /**
     * Bind a raw {@link GpuTextureView} (not a registered {@link Identifier}) to a sampler dynamically,
     * taking precedence over {@link #setTexture}'s TextureManager binding. {@code name} may be a Sampler2D
     * variable's display name or the raw sampler uniform name. Pass a {@code null} view/sampler to clear the
     * override (falling back to the Identifier binding). Returns false if this material doesn't manage that
     * sampler (e.g. overlay/lightmap/scene, not in the dynamic texture map). Takes effect on the next draw.
     */
    public boolean setTextureView(String name, @Nullable GpuTextureView view, @Nullable GpuSampler sampler) {
        String s = variableSamplers.getOrDefault(name, name);
        if (!textures.containsKey(s)) return false;
        if (view == null || sampler == null) {
            externalViews.remove(s);
        } else {
            externalViews.put(s, new ResolvedSampler(view, sampler));
        }
        return true;
    }

    /** The custom sampler uniform names this material manages (excludes overlay/lightmap/scene, which are
     *  bound elsewhere). Lets a host rebind every custom sampler — e.g. feed one external texture into all
     *  of a graph's samplers — without knowing their generated names. */
    public Set<String> managedSamplerNames() {
        return Collections.unmodifiableSet(textures.keySet());
    }

    // ---- draw-time binding -------------------------------------------------------------------

    /**
     * Called from the draw mixin at {@code RenderType.draw} HEAD, before the render pass opens:
     * (re)upload the UBO if values changed and resolve the dynamic textures. Both must happen here, not
     * inside the pass — {@code writeToBuffer} and a lazy texture load ({@code getTexture} →
     * {@code registerAndLoad} → {@code writeToTexture}) are illegal once a render pass is active.
     */
    public void prepareUniforms() {
        uniforms.prepareUpload();
        for (ShaderUniformBlock block : uniformBlocks) block.prepareUpload();
        // Resolve (loading if needed) each bound texture now, and build its GpuSampler from the params —
        // the actual bindTexture in the pass then only reads the already-uploaded view/sampler.
        if (!textures.isEmpty()) {
            var textureManager = Minecraft.getInstance().getTextureManager();
            resolvedTextures.clear();
            for (Map.Entry<String, SamplerDefault> e : textures.entrySet()) {
                SamplerDefault def = e.getValue();
                AbstractTexture tex = textureManager.getTexture(def.texture());
                resolvedTextures.put(e.getKey(), new ResolvedSampler(tex.getTextureView(), RenderTypeFactory.gpuSampler(def)));
            }
        }
    }

    /** Called from the draw mixin inside the active pass: bind the custom UBO + engine UBO + textures. */
    public void bindCustomUniforms(RenderPass renderPass) {
        if (!uniforms.isEmpty()) {
            GpuBufferSlice slice = uniforms.slice();
            if (slice != null) renderPass.setUniform(MaterialUniformLayout.UBO_NAME, slice);
        }
        for (ShaderUniformBlock block : uniformBlocks) {
            GpuBufferSlice s = block.slice();
            if (s != null) renderPass.setUniform(block.uboName(), s);
        }
        // Dynamic samplers: (re)bind the textures resolved in prepareUniforms, so setTexture takes effect
        // without rebuilding the RenderType. bindTexture is a pure pass command (no GPU upload), and the
        // GL backend dedups the actual glBindTexture, so a no-op rebind is cheap.
        for (Map.Entry<String, ResolvedSampler> e : resolvedTextures.entrySet()) {
            // An external raw view (e.g. SlideShow's live slide texture) wins over the Identifier binding.
            ResolvedSampler r = externalViews.getOrDefault(e.getKey(), e.getValue());
            renderPass.bindTexture(e.getKey(), r.view(), r.sampler());
        }
        // Captured scene colour/depth: bound from the live SceneCaptureManager (not TextureManager). Before
        // the first capture the view is null — bind the missing-texture as a placeholder so the encoder's
        // "every declared sampler must be bound" check passes.
        if (usesSceneColor) {
            GpuTextureView view = SceneCaptureManager.INSTANCE.colorView();
            renderPass.bindTexture(com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler.SCENE_COLOR_SAMPLER,
                    view != null ? view : missingView(), SceneCaptureManager.INSTANCE.sampler());
        }
        if (usesSceneDepth) {
            GpuTextureView view = SceneCaptureManager.INSTANCE.depthView();
            renderPass.bindTexture(com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler.SCENE_DEPTH_SAMPLER,
                    view != null ? view : missingView(), SceneCaptureManager.INSTANCE.sampler());
        }
    }

    /** The MC missing-texture view, a safe placeholder for a scene sampler before the first capture.
     *  Public so {@code IrisSurfaceUniform} binds the same placeholder on injected shaderpack programs. */
    public static GpuTextureView missingView() {
        return Minecraft.getInstance().getTextureManager()
                .getTexture(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())
                .getTextureView();
    }

    @Override
    public void close() {
        BY_RENDER_TYPE.remove(renderType);
        uniforms.close();
        if (usesSceneColor || usesSceneDepth) SceneCaptureManager.INSTANCE.release();
        RenderTypeFactory.release(contentHash);
    }
}
