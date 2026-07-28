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

    /**
     * The GLSL a custom varying compiles to, for the description panel — shared by the Custom
     * Float/Vec2/Vec3/Vec4 blocks, which differ only in the declared type. The real name is derived from
     * the block's uid ({@code vc_<hash>}); {@code vc_1f3a} here stands in for it.
     */
    static String varyingGlslExample(String glslType) {
        return "// vertex shader\nout " + glslType + " vc_1f3a;\nvc_1f3a = value;\n\n"
                + "// fragment shader\nin " + glslType + " vc_1f3a;";
    }
}
