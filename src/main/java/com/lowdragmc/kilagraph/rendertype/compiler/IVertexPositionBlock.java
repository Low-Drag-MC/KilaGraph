package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Implemented by the vertex-stage block that writes {@code gl_Position}. Compiled in the vertex
 * shader scope; the returned expression (clip-space vec4) is assigned to {@code gl_Position}.
 */
public interface IVertexPositionBlock {

    /** Compute the clip-space position (vec4) in the vertex shader scope. */
    ShaderExpr compilePosition(ShaderCompileContext ctx);
}
