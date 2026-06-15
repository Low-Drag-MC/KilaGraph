package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Owns copies of the opaque scene's colour + depth, for the Scene Color / Scene Depth nodes. You cannot
 * sample the main render target while it is the active attachment (feedback loop), so {@link #capture()}
 * copies the main colour + depth into textures we own — taken at the opaque&rarr;translucent boundary
 * (see {@code LevelRendererMixin}) so translucent materials sample "the opaque scene behind them", exactly
 * like Unity's opaque texture.
 *
 * <p><b>Gated.</b> Capture only runs while at least one live {@link RenderTypeGraphMaterial} needs it:
 * such materials {@link #acquire()} on build and {@link #release()} on close. When the count drops to
 * zero the textures are freed. All methods run on the render thread.</p>
 *
 * <p>The depth texture is created with {@code TEXTURE_BINDING} so it can be sampled as a {@code sampler2D}
 * (returning hardware depth in {@code .r}) — unlike a vanilla depth attachment.</p>
 */
public final class SceneCaptureManager {

    public static final SceneCaptureManager INSTANCE = new SceneCaptureManager();

    // Usage = COPY_DST(1) | TEXTURE_BINDING(4): we only copy into these and sample them (never render to them).
    private static final int CAPTURE_USAGE = 1 | 4;

    private int users;
    private int width, height;
    @Nullable private TextureFormat colorFormat, depthFormat;
    @Nullable private GpuTexture colorTexture, depthTexture;
    @Nullable private GpuTextureView colorView, depthView;
    @Nullable private GpuSampler sampler;

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

    /**
     * Copy the main render target's colour + depth into our owned textures. No-op if nobody needs it or the
     * main target isn't ready. Called from {@code LevelRendererMixin} after opaque geometry, before translucent.
     */
    public void capture() {
        if (users <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        GpuTexture mainColor = main.getColorTexture();
        GpuTexture mainDepth = main.getDepthTexture();
        if (mainColor == null || mainDepth == null) return;
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0) return;
        ensure(w, h, mainColor.getFormat(), mainDepth.getFormat());
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainColor, colorTexture, 0, 0, 0, 0, 0, w, h);
        encoder.copyTextureToTexture(mainDepth, depthTexture, 0, 0, 0, 0, 0, w, h);
    }

    /** The captured colour view, or {@code null} before the first capture (binders fall back to a placeholder). */
    @Nullable
    public GpuTextureView colorView() {
        return colorView;
    }

    /** The captured depth view, or {@code null} before the first capture. */
    @Nullable
    public GpuTextureView depthView() {
        return depthView;
    }

    /** A clamp-to-edge, nearest sampler for reading the captured textures (point-sampled screen lookups). */
    public GpuSampler sampler() {
        if (sampler == null) {
            sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, false);
        }
        return sampler;
    }

    private void ensure(int w, int h, TextureFormat cf, TextureFormat df) {
        if (colorTexture != null && w == width && h == height && cf == colorFormat && df == depthFormat) return;
        destroyTextures();
        var device = RenderSystem.getDevice();
        width = w;
        height = h;
        colorFormat = cf;
        depthFormat = df;
        colorTexture = device.createTexture(() -> "KilaGraph/SceneColor", CAPTURE_USAGE, cf, w, h, 1, 1);
        colorView = device.createTextureView(colorTexture);
        depthTexture = device.createTexture(() -> "KilaGraph/SceneDepth", CAPTURE_USAGE, df, w, h, 1, 1);
        depthView = device.createTextureView(depthTexture);
    }

    /** Free the owned textures (on resize/format change, or when no material needs the capture). */
    public void destroy() {
        destroyTextures();
    }

    private void destroyTextures() {
        if (colorView != null) { colorView.close(); colorView = null; }
        if (colorTexture != null) { colorTexture.close(); colorTexture = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depthTexture != null) { depthTexture.close(); depthTexture = null; }
        width = height = 0;
        colorFormat = depthFormat = null;
    }
}
