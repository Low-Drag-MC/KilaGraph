package com.lowdragmc.kilagraph.rendertype.compiler;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;

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

    /** Register a UBO field (idempotent by name). Returns the GLSL accessor for the field. */
    public String addField(String name, GlslType type) {
        fields.putIfAbsent(name, new Field(name, type));
        return UBO_INSTANCE + "." + name;
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
            }
        }
        return calc.get();
    }

    /** Emit the GLSL declaration of the UBO block + sampler uniforms (empty string if none). */
    public String declareGlsl() {
        StringBuilder sb = new StringBuilder();
        if (!fields.isEmpty()) {
            sb.append("layout(std140) uniform ").append(UBO_NAME).append(" {\n");
            for (Field f : fields.values()) {
                sb.append("    ").append(f.type().glsl()).append(' ').append(f.name()).append(";\n");
            }
            sb.append("} ").append(UBO_INSTANCE).append(";\n");
        }
        for (String s : samplers) {
            sb.append("uniform sampler2D ").append(s).append(";\n");
        }
        return sb.toString();
    }
}
