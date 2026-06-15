package com.lowdragmc.kilagraph.rendertype.runtime;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client hook that drives {@link SceneCaptureManager#capture()} at the opaque&rarr;translucent boundary.
 * {@link RenderLevelStageEvent.AfterOpaqueFeatures} is posted in {@code LevelRenderer.addMainPass} right
 * after {@code renderSolidFeatures()} (and before the depth copy to the translucent targets), so the main
 * render target holds the full opaque scene with no translucent content yet — exactly Unity's "opaque
 * texture". The capture itself is gated (no-op unless a live material needs it).
 */
public final class SceneCaptureHandler {

    private SceneCaptureHandler() {}

    /** Register the capture listener on the game event bus (client only). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(SceneCaptureHandler::onAfterOpaqueFeatures);
    }

    private static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        SceneCaptureManager.INSTANCE.capture();
    }
}
