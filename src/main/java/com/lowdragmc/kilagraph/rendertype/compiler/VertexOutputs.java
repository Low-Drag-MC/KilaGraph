package com.lowdragmc.kilagraph.rendertype.compiler;

import org.jetbrains.annotations.Nullable;

/**
 * Accumulates the vertex stage's semantic outputs as the vertex blocks compile (the Unity-like
 * Position/Normal blocks — see {@link IVertexOutputBlock}). A null field means "untouched": the mesh's
 * own value flows through and the emitted GLSL is byte-identical to a graph without the block, which is
 * also what keeps the graph's Iris vertex injection a no-op (packs untouched).
 */
public final class VertexOutputs {
    /** The full <b>model-space</b> vertex position (vec3) the standard {@code ProjMat * ModelViewMat}
     *  chain then transforms. Null = identity (the mesh's {@code Position + ModelOffset}). */
    @Nullable public ShaderExpr position;
    /** The <b>model-space</b> normal (vec3) replacing the {@code Normal} attribute for every consumer
     *  (lit vertex colour, world-normal varying, Normal node). Null = identity. */
    @Nullable public ShaderExpr normal;
}
