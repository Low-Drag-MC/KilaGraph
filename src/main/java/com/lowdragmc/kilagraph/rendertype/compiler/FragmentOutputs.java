package com.lowdragmc.kilagraph.rendertype.compiler;

import org.jetbrains.annotations.Nullable;

/**
 * Accumulates the fragment stage's semantic outputs as the fragment blocks compile. The compiler
 * assembles the final {@code fragColor} / discard logic from these.
 */
public final class FragmentOutputs {
    /** Base color rgb (vec3). Null means "untouched" (defaults to white). */
    @Nullable public ShaderExpr baseColor;
    /** Alpha (float). Null means "untouched" (defaults to 1.0). */
    @Nullable public ShaderExpr alpha;
    /** Emission rgb (vec3), added on top of base color. Null means none. */
    @Nullable public ShaderExpr emission;
    /** Alpha discard cutoff (float). When set, {@code if (alpha < cutoff) discard;} is emitted. */
    @Nullable public ShaderExpr alphaDiscardCutoff;
}
