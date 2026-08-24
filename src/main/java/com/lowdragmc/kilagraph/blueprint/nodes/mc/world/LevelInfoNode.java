package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoContextNode;
import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.level.Level;

/**
 * Holds a {@link Level} for the blocks in {@link LevelInfoBlocks} to read — weather, time, dimension,
 * build bounds, difficulty.
 *
 * <p>The level is never injected by the framework. It comes in on the {@code target} port, from a graph
 * variable or from {@code mc_block_entity_level}.
 */
@NodeAttribute(name = "mc_level_info", group = "mc/world", graphTypes = BlueprintGraph.class)
public class LevelInfoNode extends InfoContextNode<Level> {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_level_info.tooltip");
    }

    @Override
    protected Class<Level> targetClass() {
        return Level.class;
    }
}
