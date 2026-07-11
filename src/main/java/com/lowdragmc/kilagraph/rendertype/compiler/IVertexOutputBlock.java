package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Implemented by the Unity-like vertex-stage blocks (Position/Normal). When the vertex stage is compiled,
 * each block reads its input (via {@code ctx}, in the vertex scope) and writes its contribution into the
 * shared {@link VertexOutputs}. Unlike {@link IVertexPositionBlock} (the advanced clip-space glPosition
 * block, vanilla-only under shaderpacks), these outputs are model-space and feed the standard transform
 * chain — which is what makes them injectable into an Iris shaderpack's vertex shader.
 */
public interface IVertexOutputBlock {

    /** Read inputs and contribute to the vertex stage's semantic outputs. */
    void emitVertex(ShaderCompileContext ctx, VertexOutputs out);
}
