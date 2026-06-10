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

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

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
    private final boolean usesEngineGlobals;
    private final String contentHash;
    /** Variable display name -> KG_Material field (name + type), for set-by-name uniform updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Variable display name -> sampler uniform name, so setTexture accepts the friendly name too. */
    private final Map<String, String> variableSamplers;
    /** Sampler uniform name -> bound texture + params. (Re)bound each draw so setTexture is dynamic. */
    private final Map<String, SamplerDefault> textures;
    /** Sampler uniform name -> resolved view+sampler, refreshed in {@link #prepareUniforms} (pre-pass). */
    private final Map<String, ResolvedSampler> resolvedTextures = new HashMap<>();

    private record ResolvedSampler(GpuTextureView view, GpuSampler sampler) {}

    public RenderTypeGraphMaterial(RenderType renderType, MaterialUniformLayout layout,
                                   boolean usesEngineGlobals, String contentHash,
                                   Map<String, MaterialUniformLayout.Field> uniformFields,
                                   Map<String, String> variableSamplers,
                                   Map<String, SamplerDefault> textures) {
        this.renderType = renderType;
        this.uniforms = new MaterialUniformBuffer(layout);
        this.usesEngineGlobals = usesEngineGlobals;
        this.contentHash = contentHash;
        this.uniformFields = new HashMap<>(uniformFields);
        this.variableSamplers = new HashMap<>(variableSamplers);
        this.textures = new HashMap<>(textures);
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

    // ---- draw-time binding -------------------------------------------------------------------

    /**
     * Called from the draw mixin at {@code RenderType.draw} HEAD, before the render pass opens:
     * (re)upload the UBO if values changed and resolve the dynamic textures. Both must happen here, not
     * inside the pass — {@code writeToBuffer} and a lazy texture load ({@code getTexture} →
     * {@code registerAndLoad} → {@code writeToTexture}) are illegal once a render pass is active.
     */
    public void prepareUniforms() {
        uniforms.prepareUpload();
        if (usesEngineGlobals) KGEngineUniforms.prepareUpload();
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
        if (usesEngineGlobals) {
            GpuBufferSlice globals = KGEngineUniforms.slice();
            if (globals != null) renderPass.setUniform(KGEngineUniforms.UBO_NAME, globals);
        }
        // Dynamic samplers: (re)bind the textures resolved in prepareUniforms, so setTexture takes effect
        // without rebuilding the RenderType. bindTexture is a pure pass command (no GPU upload), and the
        // GL backend dedups the actual glBindTexture, so a no-op rebind is cheap.
        for (Map.Entry<String, ResolvedSampler> e : resolvedTextures.entrySet()) {
            ResolvedSampler r = e.getValue();
            renderPass.bindTexture(e.getKey(), r.view(), r.sampler());
        }
    }

    @Override
    public void close() {
        BY_RENDER_TYPE.remove(renderType);
        uniforms.close();
        RenderTypeFactory.release(contentHash);
    }
}
