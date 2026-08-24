package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * Read a value out of a {@link CompoundTag} by key. The {@link NbtValueType} option both selects
 * how the value is read and types the {@code out} port. Missing key / null tag → type default.
 */
// valueType MUST stay an option, not a port: it decides the dynamic output port's TypeHandle, and
// onDefineDynamicPorts can read an option (optionValue) but cannot know what a wire will carry.
@NodeAttribute(name = "mc_nbt_get", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtGetNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_get.tooltip");
    }


    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String key = "";

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addOutputPort("out", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        String k = ctx.getInput("key", String.class, "");
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        if (t == null || k.isEmpty() || !t.contains(k)) {
            ctx.setOutput("out", vt.defaultValue());
            return;
        }
        Object v = switch (vt) {
            case INT -> t.getIntOr(k, 0);
            case LONG -> t.getLongOr(k, 0L);
            case FLOAT -> t.getFloatOr(k, 0f);
            case DOUBLE -> t.getDoubleOr(k, 0d);
            case BOOL -> t.getBooleanOr(k, false);
            case COMPOUND -> t.getCompoundOrEmpty(k);
            default -> t.getStringOr(k, "");
        };
        ctx.setOutput("out", v);
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "valueType".equals(optionId) ? NbtValueType.CHOICES : List.of();
    }
}
