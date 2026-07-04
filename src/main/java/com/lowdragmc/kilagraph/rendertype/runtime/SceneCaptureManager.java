package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30;

/**
 * Owns a copy of the opaque scene's colour + depth, for the Scene Color / Scene Depth nodes
 * ({@code KG_SceneColor} / {@code KG_SceneDepth}). A material that uses either sampler {@link #acquire()}s on
 * build and {@link #release()}s on close; while any material needs it, {@link SceneCaptureHandler} calls
 * {@link #capture()} once per frame at the opaque&rarr;translucent boundary, and {@link RenderTypeGraphMaterial}
 * binds {@link #colorTextureId()}/{@link #depthTextureId()} into the shader's scene samplers at draw.
 *
 * <p>The capture is a vanilla {@link TextureTarget} (RGBA8 colour + a sampleable depth texture) blitted from the
 * main render target — the 1.21.1 render-target model (the 26.1 original used the 1.21.5+ blaze3d
 * {@code GpuTexture} API, absent here). A plain {@code TextureTarget} (not LDLib2's {@code HDRTarget}) is
 * deliberate: RGBA8 matches the main colour buffer (no wasted RGBA16F bandwidth), and — because it shares
 * {@code RenderTarget.createBuffers} with the main target — its <b>depth format matches</b>, so the
 * {@code glBlitFramebuffer} depth copy is valid (a mismatched format silently fails the depth blit).</p>
 */
public final class SceneCaptureManager {

    public static final SceneCaptureManager INSTANCE = new SceneCaptureManager();

    private int users;
    /** The owned colour+depth copy of the opaque scene; created on first {@link #capture()}, freed at zero users. */
    @Nullable
    private TextureTarget target;

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

    /** Whether any live material needs the capture (so {@link SceneCaptureHandler} should run it). */
    public boolean isNeeded() {
        return users > 0;
    }

    /**
     * Copy the main render target's colour + depth into the owned {@link TextureTarget} (created/resized to match),
     * then restore the main target as the active framebuffer so the rest of the level render is unaffected. Render
     * thread only — {@link SceneCaptureHandler} calls this at {@code AFTER_BLOCK_ENTITIES} (opaque scene complete,
     * before translucent) when {@link #isNeeded()}.
     */
    public void capture() {
        RenderSystem.assertOnRenderThreadOrInit();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        // Blit colour + depth from the main framebuffer into our copy (same size, so NEAREST 1:1). The main and the
        // TextureTarget share RenderTarget.createBuffers, so their depth formats match — required for a depth blit.
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, target.width, target.height,
                GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT, GL30.GL_NEAREST);
        main.bindWrite(true); // restore the main target for the remaining (translucent+) level rendering
    }

    /** GL id of the captured opaque-scene colour texture, or {@code 0} (no texture &rarr; samples black) before the
     *  first capture — e.g. in the editor preview, where no level render happens. */
    public int colorTextureId() {
        return target == null ? 0 : target.getColorTextureId();
    }

    /** GL id of the captured opaque-scene depth texture, or {@code 0} before the first capture. */
    public int depthTextureId() {
        return target == null ? 0 : target.getDepthTextureId();
    }

    /** Free the owned textures (when no material needs the capture). */
    public void destroy() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
