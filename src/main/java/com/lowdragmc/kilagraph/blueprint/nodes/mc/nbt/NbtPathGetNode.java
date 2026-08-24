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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Read a value out of a {@link CompoundTag} by path, e.g. {@code Inventory[0].id}.
 *
 * <p>What {@code mc_nbt_get} cannot do: that one takes a single key, so reaching anything nested means a
 * chain of compound reads, and reaching into a list means nothing at all. The path syntax is the game's
 * own — see {@link NbtPaths} — so anything {@code /data get} accepts works here.
 *
 * <p>A path can match more than one tag ({@code Inventory[].id} matches every slot). {@code out} is the
 * first match and {@code count} says how many there were, which is how a graph notices it wrote a
 * wildcard it did not mean. The {@link NbtValueType} option types {@code out} exactly as it does on
 * {@code mc_nbt_get}, and a value that is not of that kind reads as the kind's zero.
 *
 * <p>{@code ok} is about the path text, {@code found} is about the data: an unparseable path is
 * {@code ok = false}, and a good path that matched nothing is {@code ok = true, found = false}.</p>
 */
// valueType MUST stay an option, not a port: it decides the dynamic output port's TypeHandle, and
// onDefineDynamicPorts can read an option (optionValue) but cannot know what a wire will carry.
@NodeAttribute(name = "mc_nbt_path_get", group = "mc/nbt", graphTypes = BlueprintGraph.class)
public class NbtPathGetNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_nbt_path_get.tooltip");
    }

    @Option public NbtValueType valueType = NbtValueType.STRING;
    @InputPort public CompoundTag tag;
    @InputPort public String path = "";
    @OutputPort public boolean found;
    @OutputPort public int count;
    @OutputPort public boolean ok;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addOutputPort("out", optionValue("valueType", NbtValueType.class, valueType).portType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        CompoundTag t = ctx.getInput("tag", CompoundTag.class, null);
        NbtValueType vt = ctx.getOption("valueType", NbtValueType.class, NbtValueType.STRING);
        NbtPathArgument.NbtPath p = NbtPaths.parse(ctx.getInput("path", String.class, ""));
        ctx.setOutput("ok", p != null);

        List<Tag> matches = List.of();
        if (t != null && p != null) {
            try {
                matches = p.get(t);
            } catch (CommandSyntaxException e) {
                // The game throws rather than returning empty when a path matches nothing, so this is the
                // ordinary "no such entry" case, not a malformed path — that was caught during parsing.
                matches = List.of();
            }
        }
        ctx.setOutput("out", vt.fromTag(matches.isEmpty() ? null : matches.getFirst()));
        ctx.setOutput("count", matches.size());
        ctx.setOutput("found", !matches.isEmpty());
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "valueType".equals(optionId) ? NbtValueType.CHOICES : List.of();
    }
}
