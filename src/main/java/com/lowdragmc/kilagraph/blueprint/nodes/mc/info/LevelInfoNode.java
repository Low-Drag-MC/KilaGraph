package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.level.Level;

/** Reads properties off a {@link Level} (dayTime, gameTime, isRaining, isThundering, …). */
@NodeAttribute(name = "info_level", group = "mc_info", graphTypes = BlueprintGraph.class)
public class LevelInfoNode extends InfoContextNode<Level> {
    @Override
    protected Class<Level> targetClass() {
        return Level.class;
    }
}
