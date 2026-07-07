package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue;
import com.lowdragmc.lowdraglib2.math.curve.ExplicitCubicBezierCurve2;

import java.util.List;

/**
 * GLSL emission + std140 packing for the {@code CURVE} wire type (a Unity-style float curve). The shader
 * representation is a {@code KG_Curve} struct (all-float so the float-based material UBO writer can pack it
 * directly): each explicit cubic bezier segment occupies two {@code vec4}s —
 * {@code segments[2i] = (p0.x, p0.y, c0.x, c0.y)} and {@code segments[2i+1] = (c1.x, c1.y, p1.x, p1.y)} —
 * matching LDLib2 {@link ExplicitCubicBezierCurve2}'s {@code p0/c0/c1/p1} layout. The curve's normalized
 * 0..1 y is remapped to {@code [lower, upper]} on sample.
 *
 * <p>Mirrors {@link GradientGlsl} exactly: struct + shared sample/default helpers, a baked builder
 * function for LOCAL/constant curves, and a packed float layout for EXPOSED (uniform) curves.</p>
 */
public final class CurveGlsl {
    private CurveGlsl() {}

    /** Max bezier segments; fixed array size keeps the std140 struct member list finite. */
    public static final int MAX_SEGMENTS = 8;

    /** Floats per packed curve: header(4) + segments(2 * MAX_SEGMENTS * 4). */
    public static final int PACKED_FLOATS = 4 + MAX_SEGMENTS * 8;

    /** The dedup key + name for the shared sample/default helper functions. */
    public static final String HELPER_KEY = "kg_curve";

    /**
     * The {@code KG_Curve} struct declaration. Emitted in a stage's prelude <em>before</em> the material
     * uniforms (which may contain a curve field) and the helper functions — both reference the type.
     */
    public static final String STRUCT = """
            struct KG_Curve {
                vec4 header;        // x = segmentCount, y = lower bound, z = upper bound
                vec4 segments[16];  // [2i] = (p0.x, p0.y, c0.x, c0.y), [2i+1] = (c1.x, c1.y, p1.x, p1.y)
            };""";

    /**
     * {@code kg_sampleCurve} + {@code kg_curveDefault}, registered once by {@link #HELPER_KEY}.
     * Assumes {@link #STRUCT} is already declared (the compiler emits it in the stage prelude).
     *
     * <p>Evaluation mirrors {@code CurveValue.getCurveY} (an explicit cubic bezier: x is linear in t, y is
     * the cubic bezier of the four control-point ys): branchless per-segment masking like
     * {@code kg_sampleGradient} — for each segment with {@code x >= p0.x} the clamped-t bezier value wins
     * (t clamps to 1 past the segment, so "after last key" holds the last y; before the first key the
     * initial {@code segments[0].y} fallback holds).</p>
     */
    public static final String HELPER = """
            float kg_sampleCurve(KG_Curve c, float x) {
                float y = c.segments[0].y;
                for (int i = 0; i < 8; i++) {
                    vec4 a = c.segments[2 * i];
                    vec4 b = c.segments[2 * i + 1];
                    float use = step(a.x, x) * step(float(i), c.header.x - 1.0);
                    float t = clamp((x - a.x) / max(b.z - a.x, 1e-6), 0.0, 1.0);
                    float it = 1.0 - t;
                    float by = it * it * it * a.y + 3.0 * it * it * t * a.w + 3.0 * it * t * t * b.y + t * t * t * b.w;
                    y = mix(y, by, use);
                }
                return mix(c.header.y, c.header.z, y);
            }
            KG_Curve kg_curveDefault() {
                KG_Curve c;
                c.header = vec4(1.0, 0.0, 1.0, 0.0);
                for (int i = 0; i < 16; i++) c.segments[i] = vec4(0.0);
                c.segments[0] = vec4(0.0, 0.0, 0.25, 0.25);
                c.segments[1] = vec4(0.75, 0.75, 1.0, 1.0);
                return c;
            }""";

    /** A GLSL function {@code KG_Curve <fnName>() { ... }} that builds the given constant curve. */
    public static String builderFunction(String fnName, CurveValue value) {
        StringBuilder sb = new StringBuilder();
        sb.append("KG_Curve ").append(fnName).append("() {\n");
        sb.append("    KG_Curve c;\n");
        float[] packed = pack(value);
        sb.append("    c.header = vec4(").append(GlslFormat.f(packed[0])).append(", ")
                .append(GlslFormat.f(packed[1])).append(", ")
                .append(GlslFormat.f(packed[2])).append(", 0.0);\n");
        for (int i = 0; i < MAX_SEGMENTS * 2; i++) {
            int base = 4 + i * 4;
            sb.append("    c.segments[").append(i).append("] = vec4(")
                    .append(GlslFormat.f(packed[base])).append(", ")
                    .append(GlslFormat.f(packed[base + 1])).append(", ")
                    .append(GlslFormat.f(packed[base + 2])).append(", ")
                    .append(GlslFormat.f(packed[base + 3])).append(");\n");
        }
        sb.append("    return c;\n");
        sb.append("}");
        return sb.toString();
    }

    /** std140 float layout of a curve for the material UBO (header + 2*MAX_SEGMENTS segment vec4s). */
    public static float[] pack(CurveValue value) {
        List<ExplicitCubicBezierCurve2> segments = value.segments();
        int n = Math.max(1, Math.min(segments.size(), MAX_SEGMENTS));
        float[] out = new float[PACKED_FLOATS];
        out[0] = n;
        out[1] = value.lower();
        out[2] = value.upper();
        out[3] = 0f;
        for (int i = 0; i < n; i++) {
            // An empty list degrades to a flat 0.5 line (the ECBCurves default), never an NPE.
            ExplicitCubicBezierCurve2 s = segments.isEmpty() ? FLAT : segments.get(Math.min(i, segments.size() - 1));
            int base = 4 + i * 8;
            out[base] = s.p0.x();
            out[base + 1] = s.p0.y();
            out[base + 2] = s.c0.x();
            out[base + 3] = s.c0.y();
            out[base + 4] = s.c1.x();
            out[base + 5] = s.c1.y();
            out[base + 6] = s.p1.x();
            out[base + 7] = s.p1.y();
        }
        return out;
    }

    private static final ExplicitCubicBezierCurve2 FLAT = new ExplicitCubicBezierCurve2(
            new org.joml.Vector2f(0f, 0.5f), new org.joml.Vector2f(0.25f, 0.5f),
            new org.joml.Vector2f(0.75f, 0.5f), new org.joml.Vector2f(1f, 0.5f));
}
