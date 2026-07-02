package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
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

        // uniforms: builtin/KG-managed + EXPOSED fields (samplers/gradient excluded)
        List<String> uniforms = new ArrayList<>();
        compiled.builtinUniforms().forEach((name, type) -> {
            String u = uniform(name, type);
            if (u != null) uniforms.add(u);
        });
        for (MaterialUniformLayout.Field f : compiled.layout().fields()) {
            String u = uniform(f.name(), f.type());
            if (u != null) uniforms.add(u);
        }
        sb.append("  \"uniforms\": [\n");
        sb.append(String.join(",\n", uniforms));
        sb.append(uniforms.isEmpty() ? "  ]\n}" : "\n  ]\n}");
        return sb.toString();
    }

    private static String uniform(String name, GlslType type) {
        return switch (type) {
            case FLOAT -> entry(name, "float", 1, "0.0");
            case VEC2 -> entry(name, "float", 2, "0.0, 0.0");
            case VEC3 -> entry(name, "float", 3, "0.0, 0.0, 0.0");
            case VEC4 -> entry(name, "float", 4, "0.0, 0.0, 0.0, 0.0");
            case INT, BOOL -> entry(name, "int", 1, "0");
            case MAT4 -> entry(name, "matrix4x4", 16,
                    "1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0");
            // Samplers are declared in the "samplers" list; GRADIENT struct uniforms are milestone-2 deferred.
            case SAMPLER2D, GRADIENT -> null;
        };
    }

    private static String entry(String name, String type, int count, String values) {
        return "    { \"name\": \"" + name + "\", \"type\": \"" + type + "\", \"count\": " + count
                + ", \"values\": [ " + values + " ] }";
    }
}
