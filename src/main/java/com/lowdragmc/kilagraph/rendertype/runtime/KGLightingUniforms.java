package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

/**
 * A KilaGraph-named <b>view</b> of Minecraft's own {@code Lighting} uniform buffer:
 * {@link #slice()} returns {@code RenderSystem.getShaderLights()} bound under {@code KG_Lighting}.
 * See {@link KGFogUniforms} for the rename-instead-of-include rationale (Iris injectability); the
 * {@code minecraft_mix_light} functions are mirrored in {@code LightGlsl}.
 */
public final class KGLightingUniforms {

    public static final String UBO_NAME = "KG_Lighting";
    public static final String UBO_INSTANCE = "kg_lighting";

    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGLightingUniforms.declareGlsl(); }
        @Override public void prepareUpload() { /* vanilla uploads the underlying buffer */ }
        @Override @Nullable public GpuBufferSlice slice() { return RenderSystem.getShaderLights(); }
    };

    private KGLightingUniforms() {}

    /** Must match Minecraft's {@code Lighting} block layout exactly ({@code light.glsl}). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    vec3 Light0_Direction;\n"
                + "    vec3 Light1_Direction;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor, e.g. {@code kg_lighting.Light0_Direction}. */
    public static String accessor(String field) {
        return UBO_INSTANCE + "." + field;
    }
}
