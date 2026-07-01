package com.lowdragmc.kilagraph.mixin.client;

/**
 * TODO(1.21-backport milestone 2): DISABLED for the compile-only milestone (removed from
 * {@code kilagraph.mixins.json}).
 *
 * <p>The 26.1 mixin bound a KilaGraph material's custom {@code KG_Material} UBO during
 * {@code RenderType.draw(MeshData)} by capturing the active {@code com.mojang.blaze3d.systems.RenderPass}
 * — an API set (per-{@code RenderType} {@code draw}, {@code MeshData}, {@code RenderPass},
 * {@code RenderTypeGraphMaterial.bindCustomUniforms}) introduced in the 1.21.5+ blaze3d rewrite and absent
 * in 1.21.1. Reimplement per-material uniform binding against the 1.21.1 {@code ShaderInstance} /
 * {@code RenderStateShard} model (e.g. a {@code RenderStateShard.setupRenderState} seam), then re-enable
 * this mixin.</p>
 */
public class RenderTypeMixin {
}
