package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Implemented by vertex-stage blocks that define a varying — a value computed in the vertex shader
 * and interpolated into the fragment shader. When the fragment compiler pulls this block's output
 * port, the compiler ensures the varying is declared ({@code out} in vsh / {@code in} in fsh) and
 * assigned in the vertex shader via {@link #compileVarying(ShaderCompileContext)}, then returns a
 * reference to the fragment-side {@code in} variable.
 */
public interface IVaryingBlock {

    /** The varying variable name (also the vsh {@code out} / fsh {@code in} identifier). */
    String varyingName();

    /** The varying's GLSL type. */
    GlslType varyingType();

    /**
     * Compute the varying's value in the <em>vertex</em> shader scope. The returned expression is
     * assigned to the varying {@code out}. Read the block's input via {@code ctx}; when the input is
     * unconnected, fall back to the appropriate builtin vertex attribute/expression.
     */
    ShaderExpr compileVarying(ShaderCompileContext ctx);
}
