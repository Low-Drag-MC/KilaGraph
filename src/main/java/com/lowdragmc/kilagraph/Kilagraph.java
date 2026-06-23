package com.lowdragmc.kilagraph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.runtime.SceneCaptureHandler;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Kilagraph.MODID)
public class Kilagraph {
    public static final String MODID = "kilagraph";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Kilagraph(IEventBus modEventBus, ModContainer modContainer) {
        // Custom TypeHandles (LIST etc.) must exist before any node class is scanned, because the
        // node registry instantiates each Node to harvest its declared port types.
        KGTypeHandles.init();
        // Sampler2DValue is a custom-object constant/variable value; register its codec so it round-trips
        // in graph NBT (customType does not auto-register one — without this the value silently drops).
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(RenderTypeGraphTypes.Sampler2DValue.class)
                .codec(RenderTypeGraphTypes.SAMPLER2D_CODEC)
                .streamCodec(RenderTypeGraphTypes.SAMPLER2D_STREAM_CODEC)
                .copyMark(v -> v) // immutable record — the captured instance compares by value
                .build(), 1000);
        // Same for the UV channel picker (the value of an unconnected UV port) — else it drops on save.
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(RenderTypeGraphTypes.UvChannel.class)
                .codec(RenderTypeGraphTypes.UV_CODEC)
                .streamCodec(RenderTypeGraphTypes.UV_STREAM_CODEC)
                .copyMark(v -> v)
                .build(), 1000);
        // The Gradient value (a GradientNode option / Gradient variable). GradientColor is mutable, so the
        // accessor must deep-copy (copyMark) or undo/instancing would share & mutate one gradient.
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(RenderTypeGraphTypes.GradientValue.class)
                .codec(RenderTypeGraphTypes.GRADIENT_CODEC)
                .streamCodec(RenderTypeGraphTypes.GRADIENT_STREAM_CODEC)
                .copyMark(RenderTypeGraphTypes.GradientValue::copy)
                .build(), 1000);
        // Touch the registry to trigger annotation scanning; classes annotated with @NodeAttribute
        // bound to BlueprintGraph self-register.
        LOGGER.info("KilaGraph blueprint nodes loaded: {}", BlueprintGraph.NODE_REGISTRY.getNodeClasses().size());
        LOGGER.info("KilaGraph rendertype nodes loaded: {}", RenderTypeGraph.NODE_REGISTRY.getNodeClasses().size());
        // Register all KG GameTests (each group adds itself in KGGameTests.init).
        KGGameTests.init(modEventBus);
        // Client-only debug command + in-world draw for validating the RenderType pipeline end-to-end.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            // Capture the opaque scene colour/depth (for Scene Color/Depth nodes), gated by demand.
            SceneCaptureHandler.init();
        }
    }
}
