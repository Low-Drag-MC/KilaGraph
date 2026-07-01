package com.lowdragmc.kilagraph.rendertype.runtime;

/**
 * Client hook that drives {@link SceneCaptureManager#capture()} at the opaque&rarr;translucent boundary.
 *
 * <p>TODO(1.21-backport milestone 2): the original listened for
 * {@code net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterOpaqueFeatures} — that per-stage
 * nested event does not exist in 1.21.1 NeoForge (which exposes {@code RenderLevelStageEvent.Stage} via
 * {@code getStage()} instead), and {@link SceneCaptureManager#capture()} depends on the absent blaze3d GPU
 * texture API. {@link #init()} was reduced to a no-op for the compile-only milestone; re-wire the capture
 * listener + scene-capture textures against the 1.21.1 rendering model.</p>
 */
public final class SceneCaptureHandler {

    private SceneCaptureHandler() {}

    /** Register the capture listener on the game event bus (client only). No-op until milestone 2. */
    public static void init() {
        // TODO(1.21-backport milestone 2): register the opaque->translucent capture listener.
    }
}
