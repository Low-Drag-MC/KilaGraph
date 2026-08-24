package com.lowdragmc.kilagraph.blueprint.nodes.mc.tag;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Whether an {@link Item} belongs to the item tag named by {@code tag} (e.g.
 * {@code minecraft:planks}). Malformed tag id → false. Full {@code TagKey} typing is deferred — for
 * now the tag is a plain string id.
 */
@NodeAttribute(name = "mc_item_in_tag", group = "mc/tag", graphTypes = BlueprintGraph.class)
public class ItemInTagNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_item_in_tag.tooltip");
    }


    @InputPort public Item item = Items.AIR;
    @InputPort public String tag = "";
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        Item i = ctx.getInput("item", Item.class, Items.AIR);
        String id = ctx.getInput("tag", String.class, "");
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (i == null || rl == null) {
            ctx.setOutput("out", false);
            return;
        }
        TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
        ctx.setOutput("out", i.builtInRegistryHolder().is(key));
    }
}
