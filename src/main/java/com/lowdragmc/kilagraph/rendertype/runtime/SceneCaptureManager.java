package com.lowdragmc.kilagraph.rendertype.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Owns copies of the opaque scene's colour + depth, for the Scene Color / Scene Depth nodes.
 *
 * <p>TODO(1.21-backport milestone 2): the whole capture path relies on the 1.21.5+ blaze3d GPU API
 * ({@code GpuTexture}/{@code GpuTextureView}/{@code GpuSampler}/{@code TextureFormat} and
 * {@code RenderTarget.getColorTexture()} returning a {@code GpuTexture}), none of which exist in 1.21.1
 * (where render targets expose raw GL texture ids). All method bodies were reduced to stubs for the
 * compile-only milestone and the GPU-typed accessors ({@link #colorView()}/{@link #depthView()}/
 * {@link #sampler()}) retyped to {@code Object}. Reimplement scene capture against the 1.21.1 render
 * target / texture model.</p>
 */
public final class SceneCaptureManager {

    public static final SceneCaptureManager INSTANCE = new SceneCaptureManager();

    private int users;

    private SceneCaptureManager() {}

    /** A material that needs scene colour/depth registers here so {@link #capture()} starts running. */
    public void acquire() {
        users++;
    }

    /** Balance {@link #acquire()}; when no material needs the capture anymore, free the textures. */
    public void release() {
        if (users > 0) users--;
        if (users == 0) destroy();
    }

    /** Whether any live material needs the capture (so the mixin should run it). */
    public boolean isNeeded() {
        return users > 0;
    }

    // TODO(1.21-backport milestone 2): copy the main render target's colour + depth into owned textures.
    public void capture() {
        throw new UnsupportedOperationException("scene capture: 1.21.1 backport pending (milestone 2)");
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.textures.GpuTextureView.
    @Nullable
    public Object colorView() {
        return null;
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.textures.GpuTextureView.
    @Nullable
    public Object depthView() {
        return null;
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.textures.GpuSampler.
    public Object sampler() {
        throw new UnsupportedOperationException("scene capture sampler: 1.21.1 backport pending (milestone 2)");
    }

    /** Free the owned textures (on resize/format change, or when no material needs the capture). */
    public void destroy() {
        // TODO(1.21-backport milestone 2): free owned GPU textures.
    }
}
