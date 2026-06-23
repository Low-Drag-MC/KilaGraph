package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.level.block.state.BlockState;

/** Reads properties off a {@link BlockState} (block, isAir, lightEmission, …). */
@NodeAttribute(name = "info_block_state", group = "mc_info", graphTypes = BlueprintGraph.class)
public class BlockStateInfoNode extends InfoContextNode<BlockState> {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.info_block_state.tooltip");
    }

    @Override
    protected Class<BlockState> targetClass() {
        return BlockState.class;
    }
}
