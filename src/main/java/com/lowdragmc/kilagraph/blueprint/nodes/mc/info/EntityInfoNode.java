package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.entity.Entity;

/** Reads properties off an {@link Entity} (x, y, z, blockPosition, name, isAlive, …). */
@NodeAttribute(name = "info_entity", group = "mc_info", graphTypes = BlueprintGraph.class)
public class EntityInfoNode extends InfoContextNode<Entity> {
    @Override
    protected Class<Entity> targetClass() {
        return Entity.class;
    }
}
