package com.lowdragmc.kilagraph.rendertype.runtime;

/**
 * A compiled, ready-to-render material: a Minecraft {@code RenderType} (sharing a cached pipeline) plus its
 * per-instance {@code KG_Material} uniform buffer and dynamic sampler bindings.
 *
 * <p>TODO(1.21-backport milestone 2): the entire material relies on the 1.21.5+ blaze3d GPU/render API —
 * {@code net.minecraft.client.renderer.rendertype.RenderType} (identity side-table key),
 * {@code com.mojang.blaze3d.systems.RenderPass} (draw-time {@code setUniform}/{@code bindTexture}),
 * {@code GpuBufferSlice}/{@code GpuTextureView}/{@code GpuSampler}, and {@code AbstractTexture.getTextureView()}
 * — none of which exist in 1.21.1. The class was reduced to an empty shell for the compile-only milestone
 * (it has no kept-code callers: it was only reached through {@code RenderTypeMixin} and the preview UI, both
 * isolated here). Reimplement the material — uniform value store, dynamic samplers, and draw-time binding —
 * against the 1.21.1 {@code ShaderInstance}/{@code VertexBuffer}/{@code RenderStateShard} model.</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private RenderTypeGraphMaterial() {
        // TODO(1.21-backport milestone 2): no instances are created during the compile-only milestone
        // (RenderTypeFactory.createMaterial returns null).
    }

    @Override
    public void close() {
        // TODO(1.21-backport milestone 2): release the generated pipeline reference + scene-capture refcount.
    }
}
