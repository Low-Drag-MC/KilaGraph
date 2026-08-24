package com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Write a value into a {@link CompoundTag} by path, e.g. {@code display.Name}.
 *
 * <p>Missing intermediate compounds are created on the way down, so writing {@code a.b.c} into an empty
 * tag gives you all three — the same thing {@code /data modify set} does. What cannot be created is a list
 * slot that does not exist: {@code Items[4]} on a three-element list writes nothing and reports
 * {@code count = 0}.
 *
 * <p>Like {@code mc_nbt_set}, this mutates the tag it is given and hands the same object back rather than
 * copying. Tags in this graph are shared references, so a second reader downstream sees the write; that is
 * the existing behaviour of the key-based node and the two must not disagree about it.
 *
 * <p>{@code count} is how many matches were written, since a wildcard path can hit several at once.
 * {@code ok} is false for an unparseable path and for a path that reached nothing writable, which are the
 * two ways a graph can believe it stored something it did not.</p>
 */
// valueType MUST stay an option — see NbtGetNode: it drives the dynamic port's type, decided at
// defineNode time, before any wire has a value.
@NodeAttribute(name = "mc_nbt_path_set", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtPathSetNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_path_set.tooltip");
    }

    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String path = "";
    @OutputPort public CompoundTag out;
    @OutputPort public int count;
    @OutputPort public boolean ok;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        if (t == null) t = new CompoundTag();
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        NbtPathArgument.NbtPath p = NbtPaths.parse(ctx.getInput("path", String.class, ""));

        int written = 0;
        if (p != null) {
            try {
                written = p.set(t, valueTag(ctx, vt));
            } catch (CommandSyntaxException e) {
                // The path led somewhere that cannot hold a value — into a list by name, say. The throw
                // happens before anything is assigned, so written stays 0 and that is the reported answer:
                // a mistake in the path text rather than a broken graph.
            }
        }
        ctx.setOutput("out", t);
        ctx.setOutput("count", written);
        ctx.setOutput("ok", written > 0);
    }

    /** The {@code value} port read as {@code vt} and boxed into the matching NBT tag. */
    private static Tag valueTag(EvalContext ctx, NbtValueType vt) {
        return switch (vt) {
            case INT -> IntTag.valueOf(ctx.getInt("value", 0));
            case LONG -> LongTag.valueOf(ctx.getLong("value", 0L));
            case FLOAT -> FloatTag.valueOf(ctx.getFloat("value", 0f));
            case DOUBLE -> DoubleTag.valueOf(ctx.getDouble("value", 0d));
            case BOOL -> ByteTag.valueOf(ctx.getBool("value", false));
            case COMPOUND -> {
                CompoundTag c = ctx.getInput("value", CompoundTag.class, null);
                yield c == null ? new CompoundTag() : c;
            }
            default -> StringTag.valueOf(ctx.getInput("value", String.class, ""));
        };
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "valueType".equals(optionId) ? NbtValueType.CHOICES : List.of();
    }
}
