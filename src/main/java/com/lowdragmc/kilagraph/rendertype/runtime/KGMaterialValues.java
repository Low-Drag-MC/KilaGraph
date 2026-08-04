package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.lowdraglib2.client.shader.ILDShaderInstance;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-instance value store of a compiled shader graph: the EXPOSED-variable uniform components and
 * sampler texture bindings, with a set-by-display-name API, plus {@link #apply(ShaderInstance)} to stage
 * everything onto a {@code ShaderInstance} before a draw. Extracted from {@link RenderTypeGraphMaterial}
 * so a downstream material system that shares one shader across many materials with independent values
 * can reuse the value plumbing without the RenderType / scene-capture machinery.
 *
 * <p>Because uniforms are program state, two materials sharing one {@code ShaderInstance} must each
 * {@link #apply} their full value set before their own draw — which this does (every field, every
 * sampler), so values never leak between materials.</p>
 */
public final class KGMaterialValues {

    /** EXPOSED-variable uniform fields (name -> type), set each draw from {@link #values}. */
    private final List<MaterialUniformLayout.Field> materialFields;
    /** EXPOSED variable display name -> its uniform field, for set-by-name updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Sampler2D variable display name -> sampler uniform name, so setTexture accepts the friendly name. */
    private final Map<String, String> variableSamplers;
    /** uniform name -> current components. */
    private final Map<String, float[]> values = new HashMap<>();
    /** sampler uniform name -> bound texture + GPU sampler params (filter/address/mipmap): the texture is
     *  resolved to an AbstractTexture and the params staged for a GL sampler object at {@link #apply}. */
    private final Map<String, SamplerDefault> samplerBindings = new HashMap<>();

    public KGMaterialValues(CompiledShaderGraph compiled) {
        this.materialFields = compiled.layout().fields();
        this.uniformFields = new HashMap<>(compiled.uniformFields());
        this.variableSamplers = new HashMap<>(compiled.variableSamplers());
        bakeDefaults(compiled);
    }

    /** (Re-)bake the EXPOSED-variable default values + default sampler textures/params (a value-only graph edit). */
    public void bakeDefaults(CompiledShaderGraph compiled) {
        values.putAll(compiled.uniformDefaults());
        samplerBindings.putAll(compiled.samplerDefaults());
    }

    /** The EXPOSED variable display name -> uniform field mapping (for building editor surfaces). */
    public Map<String, MaterialUniformLayout.Field> uniformFields() {
        return uniformFields;
    }

    /** The Sampler2D variable display name -> sampler uniform name mapping. */
    public Map<String, String> variableSamplers() {
        return variableSamplers;
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

    /**
     * Set a vec4 (an HDR Color variable) from an {@link HDRColor} — the intensity is premultiplied into
     * rgb, so components may exceed 1. See {@link HDRColor#toVector4f()} for the convention.
     */
    public boolean setHDRColorUniform(String variableName, HDRColor color) {
        var v = color.toVector4f();
        return setByVariable(variableName, v.x, v.y, v.z, v.w);
    }

    /** Set a Gradient variable by display name (packed to the {@code KG_Gradient} member layout). */
    public boolean setGradient(String variableName, RenderTypeGraphTypes.GradientValue value) {
        return setByVariable(variableName, GradientGlsl.pack(value));
    }

    /** Set a Curve variable by display name (packed to the {@code KG_Curve} member layout). */
    public boolean setCurve(String variableName, RenderTypeGraphTypes.CurveValue value) {
        return setByVariable(variableName, CurveGlsl.pack(value));
    }

    /** Set any EXPOSED variable by display name from raw components. Returns false if unknown. */
    public boolean setByVariable(String variableName, float... components) {
        MaterialUniformLayout.Field field = uniformFields.get(variableName);
        if (field == null) return false;
        values.put(field.name(), components);
        return true;
    }

    /** Bind a texture to a Sampler2D variable by display name (or raw sampler name), keeping its sampler params. */
    public boolean setTexture(String name, ResourceLocation texture) {
        String sampler = variableSamplers.getOrDefault(name, name);
        SamplerDefault current = samplerBindings.get(sampler);
        // Swap the texture, keep the filter/address/mipmap the graph configured for this sampler.
        samplerBindings.put(sampler, current != null
                ? new SamplerDefault(texture, current.filter(), current.address(), current.mipmap())
                : new SamplerDefault(texture, RenderTypeGraphTypes.SamplerFilter.NEAREST,
                        RenderTypeGraphTypes.SamplerAddress.CLAMP, false));
        return true;
    }

    // ---- draw-time staging ---------------------------------------------------------------------

    /**
     * Stage every EXPOSED uniform + sampler texture onto {@code shader} (render thread, shader active,
     * before the draw's {@code ShaderInstance.apply()} uploads them). Vanilla builtins and KG-managed
     * uniforms are NOT set here — see {@link KGBuiltinUniforms}.
     */
    public void apply(ShaderInstance shader) {
        for (MaterialUniformLayout.Field f : materialFields) {
            setUniform(shader, f.name(), f.type(), values.get(f.name()));
        }
        var textureManager = Minecraft.getInstance().getTextureManager();
        samplerBindings.forEach((name, def) -> shader.setSampler(name, textureManager.getTexture(def.texture())));
        // Stage this material's sampler params (filter/wrap); ShaderInstanceMixin binds a GL sampler object onto
        // each sampler's texture unit in the shader's apply() and unbinds it in clear(), so the override rides the
        // draw lifecycle and never leaks to a later draw. Only an LDShaderInstance exposes the sampler-name list
        // (via LDLib2's accessor) that maps a sampler to its unit — which is always what a KG material uses.
        if (shader instanceof ILDShaderInstance ld) {
            KGSamplerBinder.stage(shader,
                    KGSamplerGl.resolveBindings(ld.getShaderInstanceAccessor().getSamplerNames(), samplerBindings));
        }
    }

    private void setUniform(ShaderInstance shader, String name, GlslType type, float[] v) {
        Uniform u = shader.getUniform(name);
        if (u == null && type != GlslType.GRADIENT && type != GlslType.CURVE) return;
        switch (type) {
            case FLOAT -> u.set(at(v, 0));
            case INT, BOOL -> u.set((int) at(v, 0));
            case VEC2 -> u.set(at(v, 0), at(v, 1));
            case VEC3 -> u.set(at(v, 0), at(v, 1), at(v, 2));
            case VEC4 -> u.set(at(v, 0), at(v, 1), at(v, 2), at(v, 3));
            case MAT4 -> u.set(new Matrix4f().set(v == null ? new float[16] : v));
            // A GRADIENT/CURVE field is a struct uniform, uploaded member-by-member (the whole-struct
            // name has no GL location; the members do). SAMPLER2D is bound via setSampler in apply().
            case GRADIENT -> setGradientMembers(shader, name, v);
            case CURVE -> setCurveMembers(shader, name, v);
            case SAMPLER2D -> { }
        }
    }

    /**
     * Upload a packed gradient (the {@code GradientGlsl.pack} 68-float layout: header + 8 colour + 8 alpha
     * vec4) into the {@code KG_Gradient} struct uniform {@code name}'s members — the manifest declares each
     * as a vec4 (see {@code KGShaderManifest.appendUniforms}).
     */
    private static void setGradientMembers(ShaderInstance shader, String name, float[] v) {
        if (v == null) return;
        setVec4(shader, name + ".header", v, 0);
        for (int i = 0; i < GradientGlsl.MAX_KEYS; i++) {
            setVec4(shader, name + ".colors[" + i + "]", v, 4 + i * 4);
            setVec4(shader, name + ".alphas[" + i + "]", v, 4 + GradientGlsl.MAX_KEYS * 4 + i * 4);
        }
    }

    /** Upload a packed curve (the {@code CurveGlsl.pack} layout: header + 16 segment vec4s) into the
     *  {@code KG_Curve} struct uniform {@code name}'s members. */
    private static void setCurveMembers(ShaderInstance shader, String name, float[] v) {
        if (v == null) return;
        setVec4(shader, name + ".header", v, 0);
        for (int i = 0; i < CurveGlsl.MAX_SEGMENTS * 2; i++) {
            setVec4(shader, name + ".segments[" + i + "]", v, 4 + i * 4);
        }
    }

    private static void setVec4(ShaderInstance shader, String uniformName, float[] v, int base) {
        Uniform u = shader.getUniform(uniformName);
        if (u == null) return;
        u.set(at(v, base), at(v, base + 1), at(v, base + 2), at(v, base + 3));
    }

    private static float at(float[] v, int i) { return v != null && i < v.length ? v[i] : 0f; }
}
