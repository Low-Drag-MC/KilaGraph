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

    /** A single UBO field. */
    public record Field(String name, GlslType type) {}

    public static final String UBO_NAME = "KG_Material";
    public static final String UBO_INSTANCE = "kg_material";

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
     *  declared (in the stage prelude) before this UBO block, since the block references the type. */
    public boolean hasGradientField() {
        return fields.values().stream().anyMatch(f -> f.type() == GlslType.GRADIENT);
    }

    /**
     * Minimal std140 size calculator. Replaces {@code com.mojang.blaze3d.buffers.Std140SizeCalculator}
     * (absent in 1.21.1): each put aligns the running offset to the member's base alignment then adds
     * the member's base size; {@link #get()} rounds the total up to a 16-byte (vec4) boundary.
     */
    private static final class Std140SizeCalculator {
        private int offset = 0;

        private void align(int alignment) {
            offset = ((offset + alignment - 1) / alignment) * alignment;
        }

        Std140SizeCalculator putFloat() { align(4); offset += 4; return this; }
        Std140SizeCalculator putVec2() { align(8); offset += 8; return this; }
        Std140SizeCalculator putVec3() { align(16); offset += 12; return this; }
        Std140SizeCalculator putVec4() { align(16); offset += 16; return this; }
        Std140SizeCalculator putMat4f() { align(16); offset += 64; return this; }

        int get() { return ((offset + 15) / 16) * 16; }
    }

    /** Compute the std140 byte size of the UBO (0 when there are no fields). */
    public int std140Size() {
        if (fields.isEmpty()) return 0;
        var calc = new Std140SizeCalculator();
        for (Field f : fields.values()) {
            switch (f.type()) {
                case FLOAT, INT, BOOL -> calc.putFloat();
                case VEC2 -> calc.putVec2();
                case VEC3 -> calc.putVec3();
                case VEC4 -> calc.putVec4();
                case MAT4 -> calc.putMat4f();
                case SAMPLER2D -> { /* samplers are not UBO members */ }
                // KG_Gradient struct: vec4 header + vec4 colors[8] + vec4 alphas[8] = 17 vec4 (all 16-aligned).
                case GRADIENT -> { for (int i = 0; i < 1 + GradientGlsl.MAX_KEYS * 2; i++) calc.putVec4(); }
            }
        }
        return calc.get();
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
