package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Emits a constant {@link Item} chosen from the inspector picker (registry accessor). */
@NodeAttribute(name = "mc_item_const", group = "mc", graphTypes = BlueprintGraph.class)
public class ItemConstNode extends AnnotatedNode {

    @Option public Item value = Items.AIR;
    @OutputPort public Item out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption("value", Item.class, Items.AIR));
    }
}
