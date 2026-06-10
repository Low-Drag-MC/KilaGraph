package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;

/** Reads properties off a {@link Direction} (opposite, axis, stepX, stepY, stepZ, …). */
@NodeAttribute(name = "info_direction", group = "mc_info", graphTypes = BlueprintGraph.class)
public class DirectionInfoNode extends InfoContextNode<Direction> {
    @Override
    protected Class<Direction> targetClass() {
        return Direction.class;
    }
}
