package com.lowdragmc.kilagraph;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Kilagraph.MODID)
public class Kilagraph {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "kilagraph";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Kilagraph(IEventBus modEventBus, ModContainer modContainer) {

    }

}
