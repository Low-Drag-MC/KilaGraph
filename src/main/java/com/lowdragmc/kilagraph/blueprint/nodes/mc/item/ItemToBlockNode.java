package com.lowdragmc.kilagraph.blueprint.nodes.mc.item;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The block form of an {@link Item} ({@link Block#byItem(Item)}); AIR block for non-block items. */
@NodeAttribute(name = "mc_item_to_block", group = "mc/item", graphTypes = BlueprintGraph.class)
public class ItemToBlockNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_item_to_block.tooltip");
    }


    @InputPort public Item in = Items.STONE;
    @OutputPort public Block out;

    @Override
    public void evaluate(EvalContext ctx) {
        Item i = ctx.getInput("in", Item.class, Items.AIR);
        ctx.setOutput("out", i == null ? Blocks.AIR : Block.byItem(i));
    }
}
