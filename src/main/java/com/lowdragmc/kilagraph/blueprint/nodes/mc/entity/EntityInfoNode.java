package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoContextNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.entity.Entity;

/**
 * Holds an {@link Entity} for the blocks in {@link EntityInfoBlocks} to read — position, look direction,
 * hitbox, identity, state, health.
 *
 * <p>Wire the entity once here and stack as many property blocks inside as the graph needs, rather than
 * running the same wire into a node per property. For a player, use {@link PlayerInfoNode} instead: it
 * accepts every block this one does, plus the player-only ones.
 */
@NodeAttribute(name = "info_entity", group = "mc_entity", graphTypes = BlueprintGraph.class)
public class EntityInfoNode extends InfoContextNode<Entity> {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.info_entity.tooltip");
    }

    @Override
    protected Class<Entity> targetClass() {
        return Entity.class;
    }
}
