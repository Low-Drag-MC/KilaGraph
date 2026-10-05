package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;

/** A deep copy of {@code tag}, since the setters write in place. A null input yields a fresh compound. */
@NodeAttribute(name = "mc_nbt_copy", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtCopyNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_copy.tooltip");
    }

    @InputPort public CompoundTag tag;
    @OutputPort public CompoundTag out;

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        ctx.setOutput("out", t == null ? new CompoundTag() : t.copy());
    }
}
