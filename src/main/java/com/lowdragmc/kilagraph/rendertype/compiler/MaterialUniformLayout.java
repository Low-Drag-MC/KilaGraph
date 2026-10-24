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

    /** Whether any field is a {@link GlslType#GRADIENT} — the {@code KG_Gradient} struct must then be
     *  declared (in the stage prelude) before this UBO block, since the block references the type. */
    public boolean hasGradientField() {
        return fields.values().stream().anyMatch(f -> f.type() == GlslType.GRADIENT);
    }

    /** Whether any field is a {@link GlslType#CURVE} — the {@code KG_Curve} struct must then be
     *  declared (in the stage prelude) before this UBO block, since the block references the type. */
    public boolean hasCurveField() {
        return fields.values().stream().anyMatch(f -> f.type() == GlslType.CURVE);
    }

    /** Compute the std140 byte size of the UBO (0 when there are no fields). */
    public int std140Size() {
        if (fields.isEmpty()) return 0;
        int offset = 0;
        for (Field f : fields.values()) {
            if (f.type() == GlslType.SAMPLER2D) continue;
            offset = align(offset, f.type()) + std140SizeOf(f.type());
        }
        // rounded up to a vec4, the block's largest alignment
        return (offset + 15) / 16 * 16;
    }

    /**
     * Where each field sits in the block, in {@link #fields()} order ({@code -1} for a sampler). Computed here rather
     * than through Minecraft's {@code Std140Builder}, so the layout is checkable without the game and doesn't move
     * with its versions: up to 26.2 its {@code putVec3} advanced 16 bytes, a slot late.
     */
    public int[] std140Offsets() {
        var list = fields();
        var offsets = new int[list.size()];
        int offset = 0;
        for (int i = 0; i < list.size(); i++) {
            var type = list.get(i).type();
            if (type == GlslType.SAMPLER2D) {
                offsets[i] = -1;
                continue;
            }
            offset = align(offset, type);
            offsets[i] = offset;
            offset += std140SizeOf(type);
        }
        return offsets;
    }

    /** The std140 base alignment of a member of this type. */
    static int std140AlignOf(GlslType type) {
        return switch (type) {
            case FLOAT, INT, BOOL -> 4;
            case VEC2 -> 8;
            // vec3/vec4/mat4 columns, and the KG_Gradient / KG_Curve structs (vec4 members only)
            default -> 16;
        };
    }

    /** The std140 size of a member of this type, in bytes. */
    static int std140SizeOf(GlslType type) {
        return switch (type) {
            case FLOAT, INT, BOOL -> 4;
            case VEC2 -> 8;
            case VEC3 -> 12;
            case VEC4 -> 16;
            case MAT4 -> 64;
            case SAMPLER2D -> 0;
            // KG_Gradient struct: vec4 header + vec4 colors[8] + vec4 alphas[8] = 17 vec4.
            case GRADIENT -> (1 + GradientGlsl.MAX_KEYS * 2) * 16;
            // KG_Curve struct: vec4 header + vec4 segments[16] = 17 vec4.
            case CURVE -> (1 + CurveGlsl.MAX_SEGMENTS * 2) * 16;
            default -> throw new IllegalArgumentException("not a uniform block member: " + type);
        };
    }

    private static int align(int offset, GlslType type) {
        int alignment = std140AlignOf(type);
        return (offset + alignment - 1) / alignment * alignment;
    }

    /** Emit the GLSL declaration of the UBO block + sampler uniforms (empty string if none). */
    public String declareGlsl() {
        StringBuilder sb = new StringBuilder(blockGlsl());
        for (String s : samplers) {
            sb.append("uniform sampler2D ").append(s).append(";\n");
        }
        return sb.toString();
    }

    /** Emit just the std140 {@code KG_Material} block declaration (no sampler lines); empty when no fields.
     *  Used by the Iris injection path, which emits the block and each sampler as separate dedup units. */
    public String blockGlsl() {
        if (fields.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("layout(std140) uniform ").append(UBO_NAME).append(" {\n");
        for (Field f : fields.values()) {
            sb.append("    ").append(f.type().glsl()).append(' ').append(f.name()).append(";\n");
        }
        sb.append("} ").append(UBO_INSTANCE).append(";\n");
        return sb.toString();
    }
}
