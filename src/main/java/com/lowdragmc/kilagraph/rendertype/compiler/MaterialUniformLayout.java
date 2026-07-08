package com.lowdragmc.kilagraph.rendertype.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The std140 layout of a compiled graph's per-material uniform block ({@code KG_Material}). Holds
 * the ordered scalar/vector fields exposed to the shader, plus the list of sampler names. The
 * runtime uses this to size and write the UBO and to declare the {@code RenderPipeline} uniforms.
 *
 * <p>Built incrementally during compilation via {@link #addField(String, GlslType)} /
 * {@link #addSampler(String)} (both idempotent by name). Field declaration order is preserved and
 * matches the std140 packing order used when writing the UBO.</p>
 */
public final class MaterialUniformLayout {

    /** A single material uniform field. */
    public record Field(String name, GlslType type) {}

    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final List<String> samplers = new ArrayList<>();

    /**
     * Register a material field (idempotent by name). Returns the GLSL accessor for the field — which, on
     * 1.21.1, is just the bare uniform name: the backport uses individual {@code uniform}s (no UBO — vanilla
     * 1.21.1 shaders have no uniform-block support), so an EXPOSED variable is a plain {@code uniform <T> name}
     * set per-draw by the material.
     */
    public String addField(String name, GlslType type) {
        fields.putIfAbsent(name, new Field(name, type));
        return name;
    }

    /** Register a sampler (idempotent by name). Returns the sampler name. */
    public String addSampler(String name) {
        if (!samplers.contains(name)) samplers.add(name);
        return name;
    }

    public List<Field> fields() {
        return new ArrayList<>(fields.values());
    }

    public List<String> samplers() {
        return new ArrayList<>(samplers);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** Whether any field is a {@link GlslType#GRADIENT} — the {@code KG_Gradient} struct must then be
     *  declared (in the stage prelude) before the field that references the type. */
    public boolean hasGradientField() {
        return fields.values().stream().anyMatch(f -> f.type() == GlslType.GRADIENT);
    }

    /** Whether any field is a {@link GlslType#CURVE} — the {@code KG_Curve} struct must then be
     *  declared (in the stage prelude) before the field that references the type. */
    public boolean hasCurveField() {
        return fields.values().stream().anyMatch(f -> f.type() == GlslType.CURVE);
    }

    /**
     * Emit the GLSL declaration of the material's individual field + sampler uniforms (empty string if none).
     * 1.21.1 backport: each field is a plain {@code uniform <T> name;} (no {@code layout(std140) uniform} block —
     * see {@link #addField}); a GRADIENT field is a {@code uniform KG_Gradient name;} struct uniform.
     */
    public String declareGlsl() {
        StringBuilder sb = new StringBuilder();
        for (Field f : fields.values()) {
            sb.append("uniform ").append(f.type().glsl()).append(' ').append(f.name()).append(";\n");
        }
        for (String s : samplers) {
            sb.append("uniform sampler2D ").append(s).append(";\n");
        }
        return sb.toString();
    }
}
