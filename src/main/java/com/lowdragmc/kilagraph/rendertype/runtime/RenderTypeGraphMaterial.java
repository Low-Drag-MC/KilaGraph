package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.lowdraglib2.client.shader.management.ShaderProgram;
import com.lowdragmc.lowdraglib2.client.shader.uniform.UniformCache;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A compiled, ready-to-draw material for the 1.21.1 backport: a linked LDLib2 {@link ShaderProgram} (built
 * from the graph's generated GLSL) plus the per-instance uniform values and sampler bindings. Everything is
 * individual uniforms — no UBO (see {@code ShaderGraphCompiler.useBuiltinUniform} / {@link MaterialUniformLayout}).
 *
 * <p>Draw flow (the preview / any renderer): call {@link #apply()} to bind the program, its builtin/KG-managed
 * uniforms ({@link KGBuiltinUniforms}) and the EXPOSED-variable uniforms, then draw geometry in {@link #format()}
 * / {@link #mode()} while the program is bound.</p>
 *
 * <p>TODO(1.21-backport milestone 2): GRADIENT struct uniforms and scene/lightmap/overlay dynamic samplers are
 * not yet applied (their node groups are deferred); vanilla RenderType integration for in-world rendering is a
 * later increment (this material is currently driven by the editor preview's manual draw).</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private final ShaderProgram program;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final String contentHash;
    /** builtin / KG-managed uniforms the GLSL declares (name -> type), bound each draw via {@link KGBuiltinUniforms}. */
    private final Map<String, GlslType> builtinUniforms;
    /** EXPOSED-variable uniform fields (name -> type), set each draw from {@link #values}. */
    private final List<MaterialUniformLayout.Field> materialFields;
    /** EXPOSED variable display name -> its uniform field, for set-by-name updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Sampler2D variable display name -> sampler uniform name, so setTexture accepts the friendly name. */
    private final Map<String, String> variableSamplers;
    /** uniform name -> current components. */
    private final Map<String, float[]> values = new HashMap<>();
    private boolean closed = false;

    public RenderTypeGraphMaterial(ShaderProgram program, CompiledShaderGraph compiled) {
        this.program = program;
        this.format = RenderTypeFactory.vertexFormat(compiled.settings().vertexFormatElements());
        this.mode = RenderTypeFactory.vertexMode(compiled.settings().vertexFormatMode());
        this.contentHash = compiled.contentHash();
        this.builtinUniforms = new HashMap<>(compiled.builtinUniforms());
        this.materialFields = compiled.layout().fields();
        this.uniformFields = new HashMap<>(compiled.uniformFields());
        this.variableSamplers = new HashMap<>(compiled.variableSamplers());
        // Bake EXPOSED-variable default values + default sampler textures.
        this.values.putAll(compiled.uniformDefaults());
        for (Map.Entry<String, SamplerDefault> e : compiled.samplerDefaults().entrySet()) {
            program.bindTexture(e.getKey(), e.getValue().texture());
        }
    }

    public VertexFormat format() { return format; }
    public VertexFormat.Mode mode() { return mode; }
    public String contentHash() { return contentHash; }
    public ShaderProgram program() { return program; }

    /** Re-bake baked defaults from a freshly compiled graph with the same content hash (value-only edit). */
    public void refreshDefaults(CompiledShaderGraph compiled) {
        values.putAll(compiled.uniformDefaults());
        for (Map.Entry<String, SamplerDefault> e : compiled.samplerDefaults().entrySet()) {
            program.bindTexture(e.getKey(), e.getValue().texture());
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
        program.bindTexture(sampler, texture);
        return true;
    }

    // ---- draw-time binding -------------------------------------------------------------------

    /** Bind the program + all uniforms (builtin/KG-managed + EXPOSED). Call before drawing geometry. */
    public void apply() {
        if (closed) return;
        program.use();
        UniformCache uc = program.uniformCache;
        KGBuiltinUniforms.bind(uc, builtinUniforms);
        for (MaterialUniformLayout.Field f : materialFields) {
            setUniform(uc, f.name(), f.type(), values.get(f.name()));
        }
    }

    private static void setUniform(UniformCache uc, String name, GlslType type, float[] v) {
        switch (type) {
            case FLOAT -> uc.glUniform1F(name, at(v, 0));
            case INT, BOOL -> uc.glUniform1I(name, (int) at(v, 0));
            case VEC2 -> uc.glUniform2F(name, at(v, 0), at(v, 1));
            case VEC3 -> uc.glUniform3F(name, at(v, 0), at(v, 1), at(v, 2));
            case VEC4 -> uc.glUniform4F(name, at(v, 0), at(v, 1), at(v, 2), at(v, 3));
            case MAT4 -> uc.glUniformMatrix4F(name, new org.joml.Matrix4f().set(v == null ? new float[16] : v));
            // TODO(1.21-backport milestone 2): GRADIENT struct uniform; SAMPLER2D bound via bindTexture.
            case SAMPLER2D, GRADIENT -> { }
        }
    }

    private static float at(float[] v, int i) { return v != null && i < v.length ? v[i] : 0f; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        program.delete();
    }
}
