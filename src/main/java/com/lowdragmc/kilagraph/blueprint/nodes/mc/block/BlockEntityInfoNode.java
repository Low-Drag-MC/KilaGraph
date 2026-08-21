package com.lowdragmc.kilagraph.blueprint.nodes.mc.block;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoContextNode;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Holds a {@link BlockEntity} for the blocks in {@link BlockEntityInfoBlocks} to read — its position,
 * its block state, its level and its type.
 *
 * <p>Its <em>contents</em> are not here and cannot be: a chest's items and a furnace's progress live on
 * subclasses that no shared block could reach. Those go through {@code mc_nbt_block_entity} and the NBT
 * nodes.
 */
@NodeAttribute(name = "info_block_entity", group = "mc_block", graphTypes = BlueprintGraph.class)
public class BlockEntityInfoNode extends InfoContextNode<BlockEntity> {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.info_block_entity.tooltip");
    }

    @Override
    protected Class<BlockEntity> targetClass() {
        return BlockEntity.class;
    }
}
