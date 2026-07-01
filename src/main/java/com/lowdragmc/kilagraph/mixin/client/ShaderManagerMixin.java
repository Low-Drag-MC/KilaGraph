package com.lowdragmc.kilagraph.mixin.client;

/**
 * TODO(1.21-backport milestone 2): DISABLED for the compile-only milestone (removed from
 * {@code kilagraph.mixins.json}).
 *
 * <p>The 26.1 mixin routed KilaGraph-generated shader ids ({@code kilagraph:generated/...}) to
 * {@link com.lowdragmc.kilagraph.rendertype.runtime.DynamicShaderSourceRegistry} by patching
 * {@code net.minecraft.client.renderer.ShaderManager.getShader(Identifier, ShaderType)} — the source seam
 * the GL device uses for lazy/explicit pipeline compilation. Neither {@code ShaderManager} nor
 * {@code com.mojang.blaze3d.shaders.ShaderType} exist in 1.21.1, where shaders load through
 * {@code ShaderInstance} / {@code Program} / {@code GameRenderer}. Reimplement the generated-source seam
 * against the 1.21.1 shader-loading pipeline, then re-enable this mixin.</p>
 */
public class ShaderManagerMixin {
}
