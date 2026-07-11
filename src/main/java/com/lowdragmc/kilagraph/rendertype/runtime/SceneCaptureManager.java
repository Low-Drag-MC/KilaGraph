package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.mojang.blaze3d.opengl.GlTexture;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
    // Colour and depth are sized independently: under an Iris shaderpack the colour is copied from Iris's
    // colortex0 (whose size follows Iris's render-quality scaling), while depth still copies MC's main
    // depth (window-sized) — Iris's depthtex0 wraps that same attachment, so it stays live under a pack.
    private int colorWidth, colorHeight, depthWidth, depthHeight;
    @Nullable private TextureFormat colorFormat, depthFormat;
    @Nullable private GpuTexture colorTexture, depthTexture;
    @Nullable private GpuTextureView colorView, depthView;
    @Nullable private GpuSampler sampler;
    /** Lazily created FBO pair for the raw-GL colour blit from Iris's colortex0 (freed in {@link #destroy()}). */
    private int blitReadFbo, blitDrawFbo;

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
        // Colour: under an Iris shaderpack the world is rendered into Iris's colortex0 — MC's main colour
        // target is stale until Iris's end-of-frame blit — so copy from there (raw-GL FBO blit; the ids are
        // re-fetched every frame, never cached — they go stale on pack reload/resize/dimension change).
        // Depth stays on the vanilla copy either way: Iris's depthtex0 wraps MC's main depth attachment.
        // Any Iris-path failure falls back to the vanilla copy (worst case: stale colour, never a crash).
        int[] iris = IrisCompat.isShaderPackInUse() ? IrisCompat.irisColorTargetInfo() : null;
        boolean irisColorDone = iris != null && blitIrisColor(iris[0], iris[1], iris[2]);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (!irisColorDone) {
            ensureColor(w, h, mainColor.getFormat());
            encoder.copyTextureToTexture(mainColor, colorTexture, 0, 0, 0, 0, 0, w, h);
        }
        ensureDepth(w, h, mainDepth.getFormat());
        encoder.copyTextureToTexture(mainDepth, depthTexture, 0, 0, 0, 0, 0, w, h);
    }

    /**
     * Copy Iris's colortex0 (GL id {@code srcTex}, {@code w}×{@code h}) into our capture texture via a
     * framebuffer blit — a blit converts formats (packs configure colortex0 as RGBA16F/R11F_G11F_B10F/...,
     * which {@code glCopyImageSubData}/copyTextureToTexture would reject), so our capture stays a plain
     * RGBA8 the graph samples like any texture. Raw GL with save/restore of both framebuffer bindings
     * ({@code GlStateManager} doesn't track FBO bindings the way it tracks texture units, but restoring is
     * cheap and keeps us robust). @return false on any failure — caller falls back to the vanilla copy.
     */
    private boolean blitIrisColor(int srcTex, int w, int h) {
        if (srcTex == 0 || w <= 0 || h <= 0) return false;
        try {
            ensureColor(w, h, TextureFormat.RGBA8);
            int dstTex = ((GlTexture) colorTexture).glId();
            if (blitReadFbo == 0) blitReadFbo = GL30.glGenFramebuffers();
            if (blitDrawFbo == 0) blitDrawFbo = GL30.glGenFramebuffers();
            int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, blitReadFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, srcTex, 0);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blitDrawFbo);
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, dstTex, 0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                        || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    return false;
                }
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                return true;
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            }
        } catch (Throwable t) {
            return false;
        }
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

    private void ensureColor(int w, int h, TextureFormat cf) {
        if (colorTexture != null && w == colorWidth && h == colorHeight && cf == colorFormat) return;
        if (colorView != null) { colorView.close(); colorView = null; }
        if (colorTexture != null) { colorTexture.close(); colorTexture = null; }
        var device = RenderSystem.getDevice();
        colorWidth = w;
        colorHeight = h;
        colorFormat = cf;
        colorTexture = device.createTexture(() -> "KilaGraph/SceneColor", CAPTURE_USAGE, cf, w, h, 1, 1);
        colorView = device.createTextureView(colorTexture);
    }

    private void ensureDepth(int w, int h, TextureFormat df) {
        if (depthTexture != null && w == depthWidth && h == depthHeight && df == depthFormat) return;
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depthTexture != null) { depthTexture.close(); depthTexture = null; }
        var device = RenderSystem.getDevice();
        depthWidth = w;
        depthHeight = h;
        depthFormat = df;
        depthTexture = device.createTexture(() -> "KilaGraph/SceneDepth", CAPTURE_USAGE, df, w, h, 1, 1);
        depthView = device.createTextureView(depthTexture);
    }

    /** Free the owned textures + blit FBOs (when no material needs the capture anymore). */
    public void destroy() {
        destroyTextures();
        if (blitReadFbo != 0) { GL30.glDeleteFramebuffers(blitReadFbo); blitReadFbo = 0; }
        if (blitDrawFbo != 0) { GL30.glDeleteFramebuffers(blitDrawFbo); blitDrawFbo = 0; }
    }

    private void destroyTextures() {
        if (colorView != null) { colorView.close(); colorView = null; }
        if (colorTexture != null) { colorTexture.close(); colorTexture = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depthTexture != null) { depthTexture.close(); depthTexture = null; }
        colorWidth = colorHeight = depthWidth = depthHeight = 0;
        colorFormat = depthFormat = null;
    }
}
