package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;

/** Remove {@code key} from {@code tag}, returning the (mutated) tag. */
@NodeAttribute(name = "mc_nbt_remove", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtRemoveNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_remove.tooltip");
    }

    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
    @OutputPort public CompoundTag out;

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        if (t == null) { ctx.setOutput("out", new CompoundTag()); return; }
        String k = ctx.getInput("key", String.class, "");
        if (!k.isEmpty()) t.remove(k);
        ctx.setOutput("out", t);
    }
}
