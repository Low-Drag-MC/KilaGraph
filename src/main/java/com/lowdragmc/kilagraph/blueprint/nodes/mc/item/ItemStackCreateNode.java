package com.lowdragmc.kilagraph.blueprint.nodes.mc.item;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Builds an {@link ItemStack} from an {@link Item} and a count.
 *
 * <h2>What this is for, and what it is not</h2>
 * <b>Runtime composition only.</b> To author a literal stack — a specific item, count and set of
 * components, picked in the editor — drop a plain {@code Item Stack} constant node instead: its
 * configurator edits all of that and serialises with the graph, which this node cannot do.
 *
 * <p>This node exists for the case a constant cannot cover: an item that is not known until the graph
 * runs. An item looked up from an id, chosen by a Select, or read off another stack, combined with a
 * count that was computed. Neither input can be baked in, so neither can be a constant.
 *
 * <p>Components are deliberately not inputs here. Adding them would be a second, worse way to do what
 * {@code Set Component} already does, and it would have to take them as one opaque blob rather than
 * one at a time. Build the stack here and pipe it through {@code Set Component} for each one.
 */
@NodeAttribute(name = "mc_item_stack_create", group = "mc_item", graphTypes = BlueprintGraph.class)
public class ItemStackCreateNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_item_stack_create.tooltip");
    }

    @InputPort public Item item = Items.AIR;
    @InputPort public int count = 1;
    @OutputPort public ItemStack out;

    @Override
    public void evaluate(EvalContext ctx) {
        Item i = ctx.getInput("item", Item.class, Items.AIR);
        int c = Math.max(0, ctx.getInt("count", 1));
        // An air item or a zero count both mean nothing, and new ItemStack would otherwise build a
        // stack that reports itself empty while carrying a count.
        ctx.setOutput("out", i == null || i == Items.AIR || c == 0
                ? ItemStack.EMPTY
                : new ItemStack(i, c));
    }
}
