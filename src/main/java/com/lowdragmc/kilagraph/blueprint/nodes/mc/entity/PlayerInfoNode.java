package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoContextNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.entity.player.Player;

/**
 * Holds a {@link Player} for the blocks in {@link PlayerInfoBlocks} to read — hunger, experience, held
 * items, game mode, posture.
 *
 * <p>It also accepts every block from {@link EntityInfoBlocks}, because a player is an entity and those
 * blocks are scoped to both contexts. So this is the entity context plus the player-only properties, and
 * a graph never has to choose between them.
 *
 * <p>{@code mc_entity_as_player} is how an {@code Entity} becomes a {@code Player} to feed this.
 */
@NodeAttribute(name = "mc_player_info", group = "mc_entity", graphTypes = BlueprintGraph.class)
public class PlayerInfoNode extends InfoContextNode<Player> {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_player_info.tooltip");
    }

    @Override
    protected Class<Player> targetClass() {
        return Player.class;
    }
}
