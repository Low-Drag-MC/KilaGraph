package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.BlendMode;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import org.joml.Vector2fc;
import org.joml.Vector4fc;

import java.util.List;

/**
 * GLSL emission + std140 packing for the {@code GRADIENT} wire type (a Unity-style gradient). The shader
 * representation is a {@code KG_Gradient} struct (all-float so the float-based material UBO writer can pack
 * it directly): {@code colors[i] = (position, r, g, b)} and {@code alphas[i] = (position, alpha, 0, 0)},
 * matching LDLib2 {@link GradientColor}'s {@code rgbP}/{@code aP} layout. Colour and alpha keys are
 * interpolated independently (Unity); {@code type} selects Blend (lerp) vs Fixed (step).
 */
public final class GradientGlsl {
    private GradientGlsl() {}

    /** Max colour/alpha keys (matches Unity's gradient editor); fixed array size keeps std140 packing simple. */
    public static final int MAX_KEYS = 8;

    /** Floats per packed gradient: header(4) + colors(8*4) + alphas(8*4). */
    public static final int PACKED_FLOATS = 4 + MAX_KEYS * 4 + MAX_KEYS * 4;

    /** The dedup key + name for the shared sample/default helper functions. */
    public static final String HELPER_KEY = "kg_gradient";

    /**
     * The {@code KG_Gradient} struct declaration. Emitted in a stage's prelude <em>before</em> the
     * {@code KG_Material} UBO (which may contain a gradient field) and the helper functions — both
     * reference the type. The header is a {@code vec4} so the whole struct is vec4-aligned (clean
     * std140: 17 vec4s).
     */
    public static final String STRUCT = """
            struct KG_Gradient {
                vec4 header;     // x = type (0 blend, 1 fixed), y = colorsLength, z = alphasLength
                vec4 colors[8];  // (position, r, g, b)
                vec4 alphas[8];  // (position, alpha, 0, 0)
            };""";

    /** {@code kg_sampleGradient} + {@code kg_gradientDefault}, registered once by {@link #HELPER_KEY}.
     *  Assumes {@link #STRUCT} is already declared (the compiler emits it in the stage prelude). */
    public static final String HELPER = """
            vec4 kg_sampleGradient(KG_Gradient g, float t) {
                float type = g.header.x;
                vec3 color = g.colors[0].yzw;
                for (int c = 1; c < 8; c++) {
                    float p = clamp((t - g.colors[c - 1].x) / max(g.colors[c].x - g.colors[c - 1].x, 1e-6),
                                    0.0, 1.0) * step(float(c), g.header.y - 1.0);
                    color = mix(color, g.colors[c].yzw, mix(p, step(0.01, p), type));
                }
                float alpha = g.alphas[0].y;
                for (int a = 1; a < 8; a++) {
                    float p = clamp((t - g.alphas[a - 1].x) / max(g.alphas[a].x - g.alphas[a - 1].x, 1e-6),
                                    0.0, 1.0) * step(float(a), g.header.z - 1.0);
                    alpha = mix(alpha, g.alphas[a].y, mix(p, step(0.01, p), type));
                }
                return vec4(color, alpha);
            }
            KG_Gradient kg_gradientDefault() {
                KG_Gradient g;
                g.header = vec4(0.0, 2.0, 2.0, 0.0);
                for (int i = 0; i < 8; i++) {
                    g.colors[i] = vec4(1.0, 1.0, 1.0, 1.0);
                    g.alphas[i] = vec4(1.0, 1.0, 0.0, 0.0);
                }
                g.colors[0] = vec4(0.0, 0.0, 0.0, 0.0);
                g.colors[1] = vec4(1.0, 1.0, 1.0, 1.0);
                g.alphas[0] = vec4(0.0, 1.0, 0.0, 0.0);
                g.alphas[1] = vec4(1.0, 1.0, 0.0, 0.0);
                return g;
            }""";

    /** A GLSL function {@code KG_Gradient <fnName>() { ... }} that builds the given constant gradient. */
    public static String builderFunction(String fnName, GradientValue value) {
        StringBuilder sb = new StringBuilder();
        sb.append("KG_Gradient ").append(fnName).append("() {\n");
        sb.append("    KG_Gradient g;\n");
        appendBuild(sb, "    g", value);
        sb.append("    return g;\n");
        sb.append("}");
        return sb.toString();
    }

    /** Emit the per-field assignments that populate a {@code KG_Gradient} variable {@code var}. */
    private static void appendBuild(StringBuilder sb, String var, GradientValue value) {
        GradientColor g = value.gradient();
        List<Vector4fc> rgb = g.getRgbP();
        List<Vector2fc> a = g.getAP();
        int nc = Math.max(1, Math.min(rgb.size(), MAX_KEYS));
        int na = Math.max(1, Math.min(a.size(), MAX_KEYS));
        sb.append(var).append(".header = vec4(")
                .append(value.mode() == BlendMode.FIXED ? "1.0" : "0.0").append(", ")
                .append(GlslFormat.f(nc)).append(", ").append(GlslFormat.f(na)).append(", 0.0);\n");
        for (int i = 0; i < MAX_KEYS; i++) {
            Vector4fc c = rgb.isEmpty() ? null : rgb.get(Math.min(i, rgb.size() - 1));
            float pos = c != null ? c.x() : (i == 0 ? 0f : 1f);
            float r = c != null ? c.y() : 1f, gg = c != null ? c.z() : 1f, b = c != null ? c.w() : 1f;
            sb.append(var).append(".colors[").append(i).append("] = vec4(")
                    .append(GlslFormat.f(pos)).append(", ").append(GlslFormat.f(r)).append(", ")
                    .append(GlslFormat.f(gg)).append(", ").append(GlslFormat.f(b)).append(");\n");
        }
        for (int i = 0; i < MAX_KEYS; i++) {
            Vector2fc al = a.isEmpty() ? null : a.get(Math.min(i, a.size() - 1));
            float pos = al != null ? al.x() : (i == 0 ? 0f : 1f);
            float alpha = al != null ? al.y() : 1f;
            sb.append(var).append(".alphas[").append(i).append("] = vec4(")
                    .append(GlslFormat.f(pos)).append(", ").append(GlslFormat.f(alpha)).append(", 0.0, 0.0);\n");
        }
    }

    /** std140 float layout of a gradient for the material UBO (header + 8 colour vec4 + 8 alpha vec4). */
    public static float[] pack(GradientValue value) {
        GradientColor g = value.gradient();
        List<Vector4fc> rgb = g.getRgbP();
        List<Vector2fc> a = g.getAP();
        int nc = Math.max(1, Math.min(rgb.size(), MAX_KEYS));
        int na = Math.max(1, Math.min(a.size(), MAX_KEYS));
        float[] out = new float[PACKED_FLOATS];
        out[0] = value.mode() == BlendMode.FIXED ? 1f : 0f;
        out[1] = nc;
        out[2] = na;
        out[3] = 0f;
        for (int i = 0; i < MAX_KEYS; i++) {
            Vector4fc c = rgb.isEmpty() ? null : rgb.get(Math.min(i, rgb.size() - 1));
            int base = 4 + i * 4;
            out[base] = c != null ? c.x() : (i == 0 ? 0f : 1f);
            out[base + 1] = c != null ? c.y() : 1f;
            out[base + 2] = c != null ? c.z() : 1f;
            out[base + 3] = c != null ? c.w() : 1f;
        }
        for (int i = 0; i < MAX_KEYS; i++) {
            Vector2fc al = a.isEmpty() ? null : a.get(Math.min(i, a.size() - 1));
            int base = 4 + MAX_KEYS * 4 + i * 4;
            out[base] = al != null ? al.x() : (i == 0 ? 0f : 1f);
            out[base + 1] = al != null ? al.y() : 1f;
            out[base + 2] = 0f;
            out[base + 3] = 0f;
        }
        return out;
    }
}
