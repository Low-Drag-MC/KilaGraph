package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.entity.player.Player;

/** Reads properties off a {@link Player} (health, foodLevel, experienceLevel, isCreative, …). */
@NodeAttribute(name = "info_player", group = "mc_info", graphTypes = BlueprintGraph.class)
public class PlayerInfoNode extends InfoContextNode<Player> {
    @Override
    protected Class<Player> targetClass() {
        return Player.class;
    }
}
