package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

/**
 * A KilaGraph-named <b>view</b> of Minecraft's own {@code Globals} uniform buffer
 * ({@code RenderSystem.getGlobalSettingsUniform()}, uploaded by {@code GlobalSettingsUniform} each frame),
 * bound under {@code KG_McGlobals}. Used by the Globals-exposing node so every field — including
 * {@code GlintAlpha}/{@code MenuBlurRadius}/{@code UseRgss}, which have no other KilaGraph mirror — stays
 * available under an Iris shaderpack. See {@link KGFogUniforms} for the rename-instead-of-include
 * rationale. (The frequently-used fields also exist in KilaGraph's own blocks: {@code ScreenSize}/
 * {@code GameTime} in {@code KG_Globals}, the camera pair in {@code KG_Transforms} — those remain the
 * preferred accessors; this block is the completeness fallback.)
 */
public final class KGMcGlobalsUniforms {

    public static final String UBO_NAME = "KG_McGlobals";
    public static final String UBO_INSTANCE = "kg_mcglobals";

    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGMcGlobalsUniforms.declareGlsl(); }
        @Override public void prepareUpload() { /* vanilla uploads the underlying buffer */ }
        @Override @Nullable public GpuBufferSlice slice() {
            GpuBuffer buffer = RenderSystem.getGlobalSettingsUniform();
            return buffer == null ? null : buffer.slice();
        }
    };

    private KGMcGlobalsUniforms() {}

    /** Must match Minecraft's {@code Globals} block layout exactly ({@code globals.glsl}). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    ivec3 CameraBlockPos;\n"
                + "    vec3 CameraOffset;\n"
                + "    vec2 ScreenSize;\n"
                + "    float GlintAlpha;\n"
                + "    float GameTime;\n"
                + "    int MenuBlurRadius;\n"
                + "    int UseRgss;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor, e.g. {@code kg_mcglobals.GlintAlpha}. */
    public static String accessor(String field) {
        return UBO_INSTANCE + "." + field;
    }
}
