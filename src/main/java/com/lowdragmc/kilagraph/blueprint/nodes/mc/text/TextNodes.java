package com.lowdragmc.kilagraph.blueprint.nodes.mc.text;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/**
 * Chat components — the game's rich text.
 *
 * <h2>Why {@code Text} is a type and not just a String</h2>
 * A {@code Component} carries translation keys, colour and formatting, and every place the game shows
 * text to a player wants one. Flattening to a String at the point of construction loses the client-side
 * translation, which is the whole point of {@code translatable}.
 *
 * <p>{@code mc_text_to_string} is therefore the one deliberate exception to the rule that this graph
 * needs no {@code *_to_string} nodes: any type wired to a String pin already coerces via
 * {@code toString()}, but a {@code Component}'s {@code toString()} is a debug dump
 * ({@code literal{hello}}), not its text. {@code getString()} is the flattening that a user means.
 */
public final class TextNodes {

    private static final String GROUP = "mc_text";

    private TextNodes() {
    }

    /** Text exactly as written, with no translation. */
    @NodeAttribute(name = "mc_text_literal", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Literal extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_literal.tooltip");
        }

        @InputPort public String text = "";
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", Component.literal(ctx.getInput("text", String.class, "")));
        }
    }

    /**
     * Text looked up in the player's language, with the arguments substituted into it.
     *
     * <p>Translation happens on the client, so this is what to use for anything a player reads.
     * Arguments are passed through as-is; a Component argument keeps its own formatting.</p>
     */
    @NodeAttribute(name = "mc_text_translatable", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Translatable extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_translatable.tooltip");
        }

        @InputPort public String key = "";
        @InputPort public List<?> args = List.of();
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            String key = ctx.getInput("key", String.class, "");
            Object raw = ctx.getInputRaw("args");
            List<?> args = raw instanceof List<?> l ? l : List.of();
            ctx.setOutput("out", args.isEmpty()
                    ? Component.translatable(key)
                    : Component.translatable(key, args.toArray()));
        }
    }

    /** One component followed by another. */
    @NodeAttribute(name = "mc_text_append", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Append extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_append.tooltip");
        }

        @InputPort public Component a;
        @InputPort public Component b;
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            // copy() first: append mutates, and the input may be shared with another branch of this run
            MutableComponent joined = text(ctx, "a").copy();
            ctx.setOutput("out", joined.append(text(ctx, "b")));
        }
    }

    /**
     * The component's text with formatting and translation resolved.
     *
     * <p>On a server this resolves a translation key against the <em>server's</em> language, which is
     * why a component should be kept as a component all the way to the player where possible.</p>
     */
    @NodeAttribute(name = "mc_text_to_string", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToString extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_to_string.tooltip");
        }

        @InputPort public Component in;
        @OutputPort public String out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", text(ctx, "in").getString());
        }
    }

    /**
     * Applies a colour and the boolean styles to a component.
     *
     * <p>Colour is an ARGB int so it can come from the same places every other colour in the graph does;
     * only the RGB part is used, since chat text has no alpha. A colour of {@code -1} leaves the existing
     * colour alone, which is what makes this composable with a component that is already coloured.</p>
     */
    @NodeAttribute(name = "mc_text_styled", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Styled extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_styled.tooltip");
        }

        @InputPort public Component in;
        @InputPort public int color = -1;
        // An input port rather than an option: the type is already a pin type, so a port gets the same
        // inline dropdown AND can be driven by a wire. An option is a no-connector port — it can
        // never be computed, so it is only right when the type is not one the graph carries.
        @InputPort public boolean bold = false;
        @InputPort public boolean italic = false;
        @InputPort public boolean underlined = false;
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            Style style = Style.EMPTY
                    .withBold(ctx.getInput("bold", Boolean.class, false))
                    .withItalic(ctx.getInput("italic", Boolean.class, false))
                    .withUnderlined(ctx.getInput("underlined", Boolean.class, false));
            int color = ctx.getInt("color", -1);
            if (color != -1) {
                style = style.withColor(TextColor.fromRgb(color & 0xFFFFFF));
            }
            ctx.setOutput("out", text(ctx, "in").copy().withStyle(style));
        }
    }

    /** Named colours, for the common case where an ARGB int is more than a user wants to think about. */
    @NodeAttribute(name = "mc_text_colored", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Colored extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_text_colored.tooltip");
        }

        @Option public ChatFormatting color = ChatFormatting.WHITE;
        @InputPort public Component in;
        @OutputPort public Component out;

        @Override
        public void evaluate(EvalContext ctx) {
            ChatFormatting c = ctx.getOption("color", ChatFormatting.class, ChatFormatting.WHITE);
            ctx.setOutput("out", text(ctx, "in").copy()
                    .withStyle(c == null ? ChatFormatting.WHITE : c));
        }
    }

    private static Component text(EvalContext ctx, String id) {
        Component c = ctx.getInput(id, Component.class, null);
        return c == null ? Component.empty() : c;
    }
}
