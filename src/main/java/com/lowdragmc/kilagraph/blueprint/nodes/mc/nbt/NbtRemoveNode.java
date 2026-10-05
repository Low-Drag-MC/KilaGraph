package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;

/**
 * Remove {@code key} from {@code tag}, returning the tag — in place, as {@code mc_nbt_set} writes, or from a copy
 * with {@code copy}.
 */
@NodeAttribute(name = "mc_nbt_remove", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtRemoveNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_remove.tooltip");
    }

    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
    /** Remove from a copy of {@code tag}, leaving it as it was, rather than from the tag itself. */
    @InputPort public boolean copy = false;
    @OutputPort public CompoundTag out;

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        if (t == null) { ctx.setOutput("out", new CompoundTag()); return; }
        if (ctx.getBool("copy", false)) t = t.copy();
        String k = ctx.getInput("key", String.class, "");
        if (!k.isEmpty()) t.remove(k);
        ctx.setOutput("out", t);
    }
}
