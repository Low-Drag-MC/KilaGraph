package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.nbt.CompoundTag;

/**
 * Read a value out of a {@link CompoundTag} by key. The {@link NbtValueType} option both selects
 * how the value is read and types the {@code out} port. Missing key / null tag → type default.
 */
@NodeAttribute(name = "mc_nbt_get", group = "mc_nbt", graphTypes = BlueprintGraph.class)
public class NbtGetNode extends AnnotatedNode {

    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String key = "";
@Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addOutputPort("out", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        String k = ctx.getInput("key", String.class, "");
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        if (t == null || k.isEmpty() || !t.contains(k)) {
            ctx.setOutput("out", defaultFor(vt));
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

    private static Object defaultFor(NbtValueType vt) {
        return switch (vt) {
            case INT -> 0;
            case LONG -> 0L;
            case FLOAT -> 0f;
            case DOUBLE -> 0d;
            case BOOL -> false;
            case COMPOUND -> new CompoundTag();
            default -> "";
        };
    }
}
