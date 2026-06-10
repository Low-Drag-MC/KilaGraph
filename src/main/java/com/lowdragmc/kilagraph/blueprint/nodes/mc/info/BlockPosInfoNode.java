package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;

/** Reads properties off a {@link BlockPos} (x, y, z, …). */
@NodeAttribute(name = "info_block_pos", group = "mc_info", graphTypes = BlueprintGraph.class)
public class BlockPosInfoNode extends InfoContextNode<BlockPos> {
    @Override
    protected Class<BlockPos> targetClass() {
        return BlockPos.class;
    }
}
