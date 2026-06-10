package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.item.ItemStack;

/** Reads properties off an {@link ItemStack} (count, item, maxStackSize, isEmpty, …). */
@NodeAttribute(name = "info_item_stack", group = "mc_info", graphTypes = BlueprintGraph.class)
public class ItemStackInfoNode extends InfoContextNode<ItemStack> {
    @Override
    protected Class<ItemStack> targetClass() {
        return ItemStack.class;
    }
}
