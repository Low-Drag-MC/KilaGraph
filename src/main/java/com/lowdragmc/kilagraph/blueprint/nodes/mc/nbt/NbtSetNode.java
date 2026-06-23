package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.nbt.CompoundTag;

/**
 * Put a value into a {@link CompoundTag} under {@code key}, returning the (mutated) tag. A null
 * input tag yields a fresh compound. The {@link NbtValueType} option types the {@code value} port.
 */
@NodeAttribute(name = "mc_nbt_set", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class NbtSetNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_set.tooltip");
    }


    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
    @OutputPort public CompoundTag out;
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        if (t == null) t = new CompoundTag();
        String k = ctx.getInput("key", String.class, "");
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        if (!k.isEmpty()) {
            switch (vt) {
                case INT -> t.putInt(k, ctx.getInput("value", Integer.class, 0));
                case LONG -> t.putLong(k, ctx.getInput("value", Long.class, 0L));
                case FLOAT -> t.putFloat(k, ctx.getInput("value", Float.class, 0f));
                case DOUBLE -> t.putDouble(k, ctx.getInput("value", Double.class, 0d));
                case BOOL -> t.putBoolean(k, ctx.getInput("value", Boolean.class, false));
                case COMPOUND -> {
                    CompoundTag c = ctx.getInput("value", CompoundTag.class, null);
                    if (c != null) t.put(k, c);
                }
                default -> t.putString(k, ctx.getInput("value", String.class, ""));
            }
        }
        ctx.setOutput("out", t);
    }
}
