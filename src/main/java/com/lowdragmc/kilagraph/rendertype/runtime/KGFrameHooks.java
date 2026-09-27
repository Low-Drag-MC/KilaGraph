package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Per-frame housekeeping of the render type runtime, run once the frame has rendered (no render pass open). */
@EventBusSubscriber(modid = Kilagraph.MODID, value = Dist.CLIENT)
public final class KGFrameHooks {

    private KGFrameHooks() {}

    @SubscribeEvent
    public static void onFrameRendered(RenderFrameEvent.Post event) {
        KGUploadBuffer.endFrame();
        RenderTypeGraphMaterial.endFrame();
    }
}
