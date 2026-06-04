package com.lowdragmc.kilagraph;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Kilagraph.MODID)
public class Kilagraph {
    public static final String MODID = "kilagraph";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Kilagraph(IEventBus modEventBus, ModContainer modContainer) {
        // Custom TypeHandles (LIST etc.) must exist before any node class is scanned, because the
        // node registry instantiates each Node to harvest its declared port types.
        KGTypeHandles.init();
        // Touch the registry to trigger annotation scanning; classes annotated with @NodeAttribute
        // bound to BlueprintGraph self-register.
        LOGGER.info("KilaGraph blueprint nodes loaded: {}", BlueprintGraph.NODE_REGISTRY.getNodeClasses().size());
        // Register all KG GameTests (each group adds itself in KGGameTests.init).
        KGGameTests.init(modEventBus);
    }
}
