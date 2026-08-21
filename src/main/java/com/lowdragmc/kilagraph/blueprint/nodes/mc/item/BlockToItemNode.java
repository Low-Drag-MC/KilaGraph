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

/** The item form of a {@link Block} ({@link Block#asItem()}); AIR for blocks with no item. */
@NodeAttribute(name = "mc_block_to_item", group = "mc_item", graphTypes = BlueprintGraph.class)
public class BlockToItemNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_block_to_item.tooltip");
    }


    @InputPort public Block in = Blocks.STONE;
    @OutputPort public Item out;

    @Override
    public void evaluate(EvalContext ctx) {
        Block b = ctx.getInput("in", Block.class, Blocks.AIR);
        ctx.setOutput("out", b == null ? Items.AIR : b.asItem());
    }
}
