package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** True if {@code tag} contains {@code key}. */
@NodeAttribute(name = "mc_nbt_has", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class NbtHasNode extends AnnotatedNode {
    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
    @OutputPort public boolean out;

    @Override public Component getDisplayName() { return Component.literal("Nbt Has"); }

    @Override public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        String k = ctx.getInput("key", String.class, "");
        ctx.setOutput("out", t != null && !k.isEmpty() && t.contains(k));
    }
}
