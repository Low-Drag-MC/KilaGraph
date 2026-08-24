package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What a compound tag contains: its keys, how many, and whether it is empty.
 *
 * <p>The piece the rest of the {@code mc_nbt} group could not express. {@code mc_nbt_get} /
 * {@code mc_nbt_has} answer questions about a key you already know; this is how a graph walks a tag it
 * did not write — feed {@code keys} into a For Each and {@code mc_nbt_get} the values.
 *
 * <p>The key list is sorted, which {@code CompoundTag.getAllKeys()} does not promise: it is backed by a
 * {@code HashMap}, so iteration order is stable for one tag but arbitrary between two tags with the same
 * contents. A graph that renders a tag, or compares two lists of keys, would otherwise see spurious
 * differences. Sorting costs nothing at these sizes and makes the output a function of the value.
 */
@NodeAttribute(name = "mc_nbt_keys", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtKeysNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_keys.tooltip");
    }

    @InputPort public CompoundTag in;
    @OutputPort public List<?> keys;
    @OutputPort public int size;
    @OutputPort public boolean empty;

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag tag = ctx.getInput("in", CompoundTag.class, null);
        List<String> names = new ArrayList<>(tag == null ? List.of() : tag.getAllKeys());
        names.sort(null);
        ctx.setOutput("keys", names);
        ctx.setOutput("size", names.size());
        ctx.setOutput("empty", names.isEmpty());
    }
}
