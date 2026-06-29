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

    // ---- PBR (LabPBR) channels — consumed only by the Iris injection path (buildInjectionSnippet);
    //      our own pipeline / editor preview ignore them. See the M2 spec. ----

    /** Tangent-space surface normal (vec3). Null = flat (0,0,1). LabPBR _n.rg. */
    @Nullable public ShaderExpr normalTS;
    /** Perceptual smoothness 0..1 (float). Null = 0. LabPBR _s.r. */
    @Nullable public ShaderExpr smoothness;
    /** Metallic 0..1 (float). Null = 0 (dielectric). LabPBR _s.g. */
    @Nullable public ShaderExpr metallic;
    /** Ambient occlusion 0..1 (float). Null = 1. LabPBR _n.b. */
    @Nullable public ShaderExpr ao;
    /** Height 0..1 (float), for POM. Null = 1. LabPBR _n.a. */
    @Nullable public ShaderExpr height;
    /** Porosity 0..1 (float). Null = 0. LabPBR _s.b low range. */
    @Nullable public ShaderExpr porosity;
    /** Subsurface scattering 0..1 (float). Null = 0. LabPBR _s.b high range (wins over porosity). */
    @Nullable public ShaderExpr sss;
}
