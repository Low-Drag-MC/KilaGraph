package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * A typed fragment of GLSL produced during compilation: a {@link #code} string (usually a single
 * identifier referencing a hoisted temp, or a literal/builtin reference) together with its
 * {@link #type}. Nodes read inputs as {@code ShaderExpr} and emit outputs as {@code ShaderExpr};
 * {@link ShaderCompileContext} handles implicit conversions between types.
 */
public record ShaderExpr(String code, GlslType type) {

    public static ShaderExpr of(String code, GlslType type) {
        return new ShaderExpr(code, type);
    }

    /** A float literal expression, e.g. {@code 1.0}. */
    public static ShaderExpr literal(float value) {
        return new ShaderExpr(GlslFormat.f(value), GlslType.FLOAT);
    }

    /** An int literal expression. */
    public static ShaderExpr literal(int value) {
        return new ShaderExpr(Integer.toString(value), GlslType.INT);
    }

    @Override
    public String toString() {
        return code;
    }
}
