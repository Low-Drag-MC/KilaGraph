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
 * Put a value into a {@link CompoundTag} under {@code key}, returning the tag. A null input tag yields a
 * fresh compound. The {@link NbtValueType} option types the {@code value} port.
 *
 * <p>In place by default: the tag it is given is the one written and handed back. {@code copy} writes into a
 * copy instead. ⚠️ A pure node is worked out again for each exec node that reads it, so an in-place write
 * computed from the tag itself (a count read out of it plus one) lands once per reader.</p>
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
    /** Write into a copy of {@code tag}, leaving it as it was, rather than into the tag itself. */
    @InputPort public boolean copy = false;
    @OutputPort public CompoundTag out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag in = ctx.getInput("tag", CompoundTag.class, null);
        CompoundTag t = in == null ? new CompoundTag() : ctx.getBool("copy", false) ? in.copy() : in;
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
                    if (c != null) t.put(k, c);
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
