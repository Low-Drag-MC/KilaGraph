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

import java.util.List;

/**
 * A copy of {@link CompoundTag} {@code tag} with a value put under {@code key}. A null input tag yields a
 * fresh compound. The {@link NbtValueType} option types the {@code value} port.
 *
 * <p>Tags are values here, as stacks and lists are: the input is never written. It may be a variable's,
 * an entity's, or one another branch has read — and a pure node is worked out again for each exec node
 * that reads it, so a write into the input would land once per reader. Setters chain:
 * {@code Set(Set(tag, a), b)} holds both.</p>
 */
// valueType MUST stay an option — see NbtGetNode: it drives the dynamic port's type, decided at
// defineNode time, before any wire has a value.
@NodeAttribute(name = "mc_nbt_set", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtSetNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_set.tooltip");
    }


    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
    @OutputPort public CompoundTag out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag in = ctx.getInput("tag", CompoundTag.class, null);
        CompoundTag t = in == null ? new CompoundTag() : in.copy();
        String k = ctx.getInput("key", String.class, "");
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        if (!k.isEmpty()) {
            switch (vt) {
                case INT -> t.putInt(k, ctx.getInt("value", 0));
                case LONG -> t.putLong(k, ctx.getLong("value", 0L));
                case FLOAT -> t.putFloat(k, ctx.getFloat("value", 0f));
                case DOUBLE -> t.putDouble(k, ctx.getDouble("value", 0d));
                case BOOL -> t.putBoolean(k, ctx.getBool("value", false));
                case COMPOUND -> {
                    CompoundTag c = ctx.getInput("value", CompoundTag.class, null);
                    if (c != null) t.put(k, c.copy());
                }
                default -> t.putString(k, ctx.getInput("value", String.class, ""));
            }
        }
        ctx.setOutput("out", t);
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "valueType".equals(optionId) ? NbtValueType.CHOICES : List.of();
    }
}
