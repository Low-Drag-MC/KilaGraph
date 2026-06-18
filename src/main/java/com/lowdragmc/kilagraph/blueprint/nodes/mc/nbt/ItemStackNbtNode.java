package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Reads the {@code custom_data} NBT compound off an {@link ItemStack} (a copy — safe to mutate). */
@NodeAttribute(name = "mc_nbt_item_stack", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class ItemStackNbtNode extends AnnotatedNode {
    @InputPort public ItemStack stack;
    @OutputPort public CompoundTag out;
@Override public void evaluate(EvalContext ctx) {
        ItemStack s = ctx.getInput("stack", ItemStack.class, null);
        if (s == null || s.isEmpty()) { ctx.setOutput("out", new CompoundTag()); return; }
        ctx.setOutput("out", s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }
}
