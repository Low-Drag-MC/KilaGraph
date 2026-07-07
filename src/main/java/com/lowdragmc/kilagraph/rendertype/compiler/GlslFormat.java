package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;

import java.util.Locale;

/** Helpers for emitting GLSL literals with a stable, locale-independent format. */
public final class GlslFormat {

    private GlslFormat() {}

    /**
     * Format a float as a GLSL float literal, always with a decimal point and never in scientific
     * notation (e.g. {@code 1.0}, {@code 0.00010000}). Avoiding {@code Float.toString}'s exponent
     * form ({@code 1.0E-4}) keeps the literal portable across GLSL compilers.
     */
    public static String f(float value) {
        if (Float.isNaN(value)) return "0.0";
        if (Float.isInfinite(value)) return value > 0 ? "1.0e38" : "-1.0e38";
        if (value == 0f) return "0.0";
        // %.8f never uses exponent notation; trim trailing zeros but keep one digit after the dot.
        String s = String.format(Locale.ROOT, "%.8f", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s + "0";
        }
        return s;
    }

    /** GLSL literal for a graph constant value, given its target GLSL type. Falls back to a zero. */
    public static String literal(Object value, GlslType type) {
        return switch (type) {
            case FLOAT -> f(toFloat(value, 0f));
            case INT -> Integer.toString((int) toFloat(value, 0f));
            case BOOL -> Boolean.toString(toBool(value));
            case VEC2 -> {
                if (value instanceof org.joml.Vector2fc v) yield "vec2(" + f(v.x()) + ", " + f(v.y()) + ")";
                yield "vec2(" + f(toFloat(value, 0f)) + ")";
            }
            case VEC3 -> {
                if (value instanceof org.joml.Vector3fc v) {
                    yield "vec3(" + f(v.x()) + ", " + f(v.y()) + ", " + f(v.z()) + ")";
                }
                yield "vec3(" + f(toFloat(value, 0f)) + ")";
            }
            case VEC4 -> {
                if (value instanceof org.joml.Vector4fc v) {
                    yield "vec4(" + f(v.x()) + ", " + f(v.y()) + ", " + f(v.z()) + ", " + f(v.w()) + ")";
                }
                // A bare Integer with a vec4 target is a COLOR-typed value (ARGB) — unpack to rgba.
                if (value instanceof Integer argb) {
                    float[] c = argbToRgba(argb);
                    yield "vec4(" + f(c[0]) + ", " + f(c[1]) + ", " + f(c[2]) + ", " + f(c[3]) + ")";
                }
                yield "vec4(" + f(toFloat(value, 0f)) + ")";
            }
            case MAT4 -> "mat4(1.0)";
            case SAMPLER2D -> ShaderGraphCompiler.MISSING_SAMPLER; // opaque; never a real literal
            // GRADIENT constants are emitted by GradientNode's own builder; unconnected/LOCAL gradients are
            // routed through the compiler's defaultGradient()/builder paths, so this is only a safe fallback.
            case GRADIENT -> "kg_gradientDefault()";
            // CURVE mirrors GRADIENT: real constants go through the compiler's constantCurve() builder.
            case CURVE -> "kg_curveDefault()";
        };
    }

    /**
     * The std140 float components for a graph value, given its target GLSL type — used to bake an
     * EXPOSED variable's declared default into a material uniform via {@code MaterialUniformBuffer.set}.
     * Mirrors {@link #literal}'s value handling. {@code MAT4} yields a 16-float column-major identity
     * (the {@code Mat4Value} record currently carries no components). {@code SAMPLER2D} is opaque and
     * has no UBO representation — returns an empty array.
     */
    public static float[] components(Object value, GlslType type) {
        return switch (type) {
            case FLOAT, INT -> new float[]{toFloat(value, 0f)};
            case BOOL -> new float[]{toBool(value) ? 1f : 0f};
            case VEC2 -> value instanceof org.joml.Vector2fc v
                    ? new float[]{v.x(), v.y()} : new float[]{toFloat(value, 0f), 0f};
            case VEC3 -> value instanceof org.joml.Vector3fc v
                    ? new float[]{v.x(), v.y(), v.z()} : new float[]{toFloat(value, 0f), 0f, 0f};
            case VEC4 -> value instanceof org.joml.Vector4fc v
                    ? new float[]{v.x(), v.y(), v.z(), v.w()}
                    : value instanceof Integer argb // COLOR-typed (ARGB) default → rgba components
                    ? argbToRgba(argb) : new float[]{toFloat(value, 0f), 0f, 0f, 0f};
            case MAT4 -> new float[]{
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f};
            case SAMPLER2D -> new float[0];
            // EXPOSED gradient default: the std140-packed gradient (header + 8 colour + 8 alpha vec4).
            case GRADIENT -> value instanceof RenderTypeGraphTypes.GradientValue gv
                    ? GradientGlsl.pack(gv)
                    : GradientGlsl.pack(RenderTypeGraphTypes.GradientValue.defaultValue());
            // EXPOSED curve default: the std140-packed curve (header + 16 segment vec4).
            case CURVE -> value instanceof RenderTypeGraphTypes.CurveValue cv
                    ? CurveGlsl.pack(cv)
                    : CurveGlsl.pack(RenderTypeGraphTypes.CurveValue.defaultValue());
        };
    }

    /** Unpack an ARGB int (as produced by the COLOR color-picker) into {@code [r, g, b, a]} in 0..1. */
    private static float[] argbToRgba(int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    private static float toFloat(Object value, float fallback) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof Boolean b) return b ? 1f : 0f;
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        return false;
    }
}
