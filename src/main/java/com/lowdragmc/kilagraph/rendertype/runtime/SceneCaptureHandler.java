package com.lowdragmc.kilagraph.rendertype.runtime;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client hook that drives {@link SceneCaptureManager#capture()} at the opaque&rarr;translucent boundary.
 *
 * <p>Listens for {@link RenderLevelStageEvent} and, on {@link RenderLevelStageEvent.Stage#AFTER_BLOCK_ENTITIES}
 * (opaque terrain + entities + block entities drawn, before translucent), captures the scene colour/depth when
 * any material needs it ({@link SceneCaptureManager#isNeeded()}). 1.21.1 exposes the per-stage boundary via
 * {@code getStage()} (the 26.1 {@code RenderLevelStageEvent.AfterOpaqueFeatures} nested event does not exist).</p>
 */
public final class SceneCaptureHandler {

    private SceneCaptureHandler() {}

    /** Register the capture listener on the game event bus (client only). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(SceneCaptureHandler::onRenderLevelStage);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                && SceneCaptureManager.INSTANCE.isNeeded()) {
            SceneCaptureManager.INSTANCE.capture();
        }
    }
}
