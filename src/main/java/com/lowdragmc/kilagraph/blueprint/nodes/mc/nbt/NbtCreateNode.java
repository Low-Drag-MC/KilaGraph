package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;

/** Produces a fresh empty {@link CompoundTag}. */
@NodeAttribute(name = "mc_nbt_create", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtCreateNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_create.tooltip");
    }

    @OutputPort public CompoundTag out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", new CompoundTag());
    }
}
