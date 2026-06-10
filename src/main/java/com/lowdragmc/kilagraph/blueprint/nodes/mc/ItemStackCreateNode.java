package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds an {@link ItemStack} from an {@link Item} and a count. */
@NodeAttribute(name = "mc_item_stack_create", group = "mc", graphTypes = BlueprintGraph.class)
public class ItemStackCreateNode extends AnnotatedNode {

    @InputPort public Item item = Items.AIR;
    @InputPort public int count = 1;
    @OutputPort public ItemStack out;

    @Override
    public void evaluate(EvalContext ctx) {
        Item i = ctx.getInput("item", Item.class, Items.AIR);
        int c = ctx.getInput("count", Integer.class, 1);
        ctx.setOutput("out", new ItemStack(i, c));
    }
}
