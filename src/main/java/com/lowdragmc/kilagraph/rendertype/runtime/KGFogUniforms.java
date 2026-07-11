package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

/**
 * A KilaGraph-named <b>view</b> of Minecraft's own {@code Fog} uniform buffer: {@link #slice()} returns
 * {@code RenderSystem.getShaderFog()} — the very slice the vanilla renderer uploads each frame — bound
 * under the block name {@code KG_Fog}. No CPU mirroring, no second upload; the values are identical to
 * the {@code #moj_import <minecraft:fog.glsl>} block by construction.
 *
 * <p>Why a rename instead of the include: an injected Iris shaderpack program can't resolve
 * {@code #moj_import} (a Minecraft include in the fragment stage rejects the whole graph from injection),
 * and the pack's own GLSL owns Minecraft's block names. Declaring the same std140 layout under our own
 * name and binding Minecraft's slice to it works on both the vanilla pipeline (the generic
 * {@link ShaderUniformBlock} bind path) and the injected program ({@code IrisSurfaceUniform} binds
 * material blocks by name at draw). The fog <em>functions</em> ({@code linear_fog_value} etc.) are
 * mirrored as {@code addFunction} helpers in {@code FogGlsl}.</p>
 */
public final class KGFogUniforms {

    public static final String UBO_NAME = "KG_Fog";
    public static final String UBO_INSTANCE = "kg_fog";

    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGFogUniforms.declareGlsl(); }
        @Override public void prepareUpload() { /* vanilla uploads the underlying buffer */ }
        @Override @Nullable public GpuBufferSlice slice() { return RenderSystem.getShaderFog(); }
    };

    private KGFogUniforms() {}

    /** Must match Minecraft's {@code Fog} block layout exactly ({@code fog.glsl}) — we bind MC's buffer. */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    vec4 FogColor;\n"
                + "    float FogEnvironmentalStart;\n"
                + "    float FogEnvironmentalEnd;\n"
                + "    float FogRenderDistanceStart;\n"
                + "    float FogRenderDistanceEnd;\n"
                + "    float FogSkyEnd;\n"
                + "    float FogCloudsEnd;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor, e.g. {@code kg_fog.FogColor}. */
    public static String accessor(String field) {
        return UBO_INSTANCE + "." + field;
    }
}
