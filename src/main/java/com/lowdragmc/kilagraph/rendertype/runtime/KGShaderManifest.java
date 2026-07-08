package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the vanilla core-shader {@code .json} manifest for a {@link CompiledShaderGraph}, so the generated
 * GLSL can be loaded as a {@code net.minecraft.client.renderer.ShaderInstance} (the 1.21.1 path that both a
 * {@code RenderType} — via a {@code ShaderStateShard} — and a standalone ShaderInstance export need).
 *
 * <p>Declares every builtin / KG-managed uniform (from {@link CompiledShaderGraph#builtinUniforms()}) and every
 * EXPOSED-variable field (from the {@link MaterialUniformLayout}) as an individual {@code uniform}, plus the
 * samplers. ShaderInstance auto-updates the ones whose names match its known builtins (ModelViewMat / ProjMat /
 * FogColor / …) from {@code RenderSystem}; KG-managed ({@code kg_*}) + EXPOSED uniforms are set by the material.</p>
 */
public final class KGShaderManifest {

    private KGShaderManifest() {}

    /** The vanilla {@code shaders/core/<shaderName>.json} manifest (vertex+fragment both point at {@code shaderName}). */
    public static String json(CompiledShaderGraph compiled, String shaderName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"vertex\": \"").append(shaderName).append("\",\n");
        sb.append("  \"fragment\": \"").append(shaderName).append("\",\n");

        // samplers
        sb.append("  \"samplers\": [");
        List<String> samplers = compiled.layout().samplers();
        for (int i = 0; i < samplers.size(); i++) {
            sb.append(i > 0 ? ", " : " ").append("{ \"name\": \"").append(samplers.get(i)).append("\" }");
        }
        sb.append(samplers.isEmpty() ? "],\n" : " ],\n");

        // uniforms: builtin/KG-managed + EXPOSED fields (samplers excluded; a GRADIENT field expands to its
        // 17 vec4 struct members — see appendUniforms).
        List<String> uniforms = new ArrayList<>();
        compiled.builtinUniforms().forEach((name, type) -> appendUniforms(uniforms, name, type));
        for (MaterialUniformLayout.Field f : compiled.layout().fields()) {
            appendUniforms(uniforms, f.name(), f.type());
        }
        sb.append("  \"uniforms\": [\n");
        sb.append(String.join(",\n", uniforms));
        sb.append(uniforms.isEmpty() ? "  ]\n}" : "\n  ]\n}");
        return sb.toString();
    }

    /**
     * Append the manifest {@code uniform} entries for one field. Scalars/vectors/matrices are a single entry;
     * a {@link GlslType#GRADIENT} {@code uniform KG_Gradient <name>;} is a <em>struct</em>, which GLSL/GL address
     * per member — so it expands to its 17 {@code vec4} members ({@code <name>.header}, {@code <name>.colors[i]},
     * {@code <name>.alphas[i]}), matching {@link GradientGlsl}'s packing and the runtime's member-wise upload in
     * {@code RenderTypeGraphMaterial}. A {@code Uniform} name is passed verbatim to {@code glGetUniformLocation},
     * which resolves struct/array member names. Samplers are declared in the {@code "samplers"} list, not here.
     */
    private static void appendUniforms(List<String> out, String name, GlslType type) {
        if (type == GlslType.SAMPLER2D) return;
        if (type == GlslType.GRADIENT) {
            out.add(entry(name + ".header", "float", 4, "0.0, 0.0, 0.0, 0.0"));
            for (int i = 0; i < GradientGlsl.MAX_KEYS; i++) {
                out.add(entry(name + ".colors[" + i + "]", "float", 4, "0.0, 0.0, 0.0, 0.0"));
            }
            for (int i = 0; i < GradientGlsl.MAX_KEYS; i++) {
                out.add(entry(name + ".alphas[" + i + "]", "float", 4, "0.0, 0.0, 0.0, 0.0"));
            }
            return;
        }
        // A CURVE struct uniform expands the same way: header + 2*MAX_SEGMENTS segment vec4 members.
        if (type == GlslType.CURVE) {
            out.add(entry(name + ".header", "float", 4, "0.0, 0.0, 0.0, 0.0"));
            for (int i = 0; i < CurveGlsl.MAX_SEGMENTS * 2; i++) {
                out.add(entry(name + ".segments[" + i + "]", "float", 4, "0.0, 0.0, 0.0, 0.0"));
            }
            return;
        }
        out.add(switch (type) {
            case FLOAT -> entry(name, "float", 1, "0.0");
            case VEC2 -> entry(name, "float", 2, "0.0, 0.0");
            case VEC3 -> entry(name, "float", 3, "0.0, 0.0, 0.0");
            case VEC4 -> entry(name, "float", 4, "0.0, 0.0, 0.0, 0.0");
            case INT, BOOL -> entry(name, "int", 1, "0");
            case MAT4 -> entry(name, "matrix4x4", 16,
                    "1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0");
            case SAMPLER2D, GRADIENT, CURVE -> throw new IllegalStateException("handled above");
        });
    }

    private static String entry(String name, String type, int count, String values) {
        return "    { \"name\": \"" + name + "\", \"type\": \"" + type + "\", \"count\": " + count
                + ", \"values\": [ " + values + " ] }";
    }
}
