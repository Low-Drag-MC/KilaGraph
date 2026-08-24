package com.lowdragmc.kilagraph.blueprint.nodes.ui.style;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.ui.UISearchConfigurators;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.Property;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleSlot;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleValue;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.properties.ColorProperty;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Styling, entirely through LSS.
 *
 * <h2>There are no typed style setters here, on purpose</h2>
 * No {@code set_button_base_texture}, no {@code set_text_color}. LDLib2 already has one complete,
 * documented vocabulary for appearance — the LSS property names — and every element's own
 * {@code Style} inner class is just a typed façade over it. A second vocabulary made of graph nodes
 * would have to be kept in step with the first forever, and the first is the one a designer's
 * {@code .lss} file is already written in.
 *
 * <p>So the nodes here differ from each other not in <em>what</em> they can set, but in <b>which
 * pipeline the value enters</b>. That choice is the real decision, and LDLib2 makes it matter: every
 * value carries a {@link StyleOrigin}, and origins beat each other in a fixed order —
 * {@code DEFAULT} &lt; {@code STYLESHEET} &lt; {@code INLINE} &lt; {@code ANIMATION} &lt;
 * {@code IMPORTANT}.</p>
 *
 * <table border="1">
 *   <caption>The pipelines</caption>
 *   <tr><th>Node</th><th>Pipeline</th><th>Use when</th></tr>
 *   <tr><td>{@code ldlib2_ui_class}</td><td>class names → stylesheet rules</td>
 *       <td><b>Usually this one.</b> Add {@code .selected} and let the sheet say what selected looks
 *       like. Reversible, themeable, and the styling stays where a designer can find it.</td></tr>
 *   <tr><td>{@code ldlib2_ui_lss_set} / {@code lss_block}</td><td>one element's own style bag</td>
 *       <td>A value only the graph can know — a bar width computed from a number.</td></tr>
 *   <tr><td>{@code ldlib2_ui_local_stylesheet_add}</td><td>a sheet scoped to one subtree</td>
 *       <td>A rule that should apply to a whole panel and its descendants.</td></tr>
 *   <tr><td>{@code ldlib2_ui_global_stylesheet_add}</td><td>the whole {@code ModularUI}</td>
 *       <td>Theming a UI at runtime. The only way to add a sheet after construction, because
 *       {@code UI} is immutable.</td></tr>
 *   <tr><td>{@code ldlib2_ui_animate}</td><td>the animation origin, over time</td>
 *       <td>Transitions. Outranks inline, and restores to {@code INLINE} when it finishes.</td></tr>
 * </table>
 *
 * <h2>What the server does with all of this</h2>
 * {@code lss()} and the class list are plain data and work identically on both sides — which is what
 * makes them assertable in a game test. What does <em>not</em> happen on the server is the cascade:
 * {@code StyleEngine} only exists on a client {@code ModularUI}, so a local or global stylesheet is
 * stored but never resolved into anything. That is correct — the server has nothing to draw — and it
 * is why {@code ldlib2_ui_apply_stylesheet} exists: it performs the match-and-apply step by hand.
 */
public final class UIStyleNodes {

    private static final String GROUP = "ui/style";

    /**
     * The specificity/order pair every programmatic value is written with.
     *
     * <p>Matches what {@link UIElement#lss} uses, and it has to: {@code lss(prop, null, origin)}
     * removes candidates by matching exactly these two numbers, so a value written by
     * {@code lss_block} with any other pair would be impossible to remove with
     * {@code ldlib2_ui_lss_remove}. 999 also puts these above any realistic selector's specificity,
     * which is the intent — a value the graph set explicitly should win within its origin.</p>
     */
    private static final int PROGRAMMATIC_SPECIFICITY = 999;

    private UIStyleNodes() {
    }

    // ---- one property ------------------------------------------------------------------------

    /**
     * Sets one LSS property on one element.
     *
     * <p>The value is written the way LSS writes it, because it is parsed by the very same parser:
     * {@code #ff5555}, {@code 50%}, {@code 12}, {@code center}. A number wired in is formatted
     * plainly; an integer wired into a colour property is formatted as {@code #AARRGGBB}, since an
     * ARGB int is how the rest of this graph carries a colour and {@code -43691} is not something the
     * colour parser accepts.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_lss_set", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LssSet extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_lss_set.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public StyleOrigin origin = StyleOrigin.INLINE;
        @InputPort public UIElement element;
        /**
         * Typed {@code String} rather than {@code UNKNOWN}, and the difference is not cosmetic: an
         * {@code UNKNOWN} pin has no registered accessor, so it gets no embedded constant and no
         * inline editor — the value could then <em>only</em> arrive over a wire. Since the graph's
         * wire rules already let anything assign to a String, a String pin accepts exactly the same
         * connections and can also just be typed into, which is how this node is used most of the
         * time.
         *
         * <p>Read through {@code getInputRaw} rather than as a String so a wired integer still
         * reaches {@link UIStyleNodes#lssText} as a number and can be formatted as a colour.</p>
         */
        @InputPort public String value = "";
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("property", String.class)
                    .withDefaultValue("")
                    .withConfigurable(UISearchConfigurators.lssPropertyOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            Property<?> property = PropertyRegistry.byName(ctx.getOption("property", String.class, ""));
            if (element == null || property == null) {
                UIActions.done(ctx, false);
                return;
            }
            String text = lssText(property, ctx.getInputRaw("value"));
            if (text == null) {
                UIActions.done(ctx, false);
                return;
            }
            element.lss(property.name, text, origin(ctx));
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /**
     * Removes a property this graph set, letting whatever was underneath show through again.
     *
     * <p>Only removes the graph's own candidate at the given origin — a stylesheet rule setting the
     * same property is untouched, which is the point: this is how a hover effect is undone without
     * erasing the element's normal appearance.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_lss_remove", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LssRemove extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_lss_remove.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public StyleOrigin origin = StyleOrigin.INLINE;
        @InputPort public UIElement element;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("property", String.class)
                    .withDefaultValue("")
                    .withConfigurable(UISearchConfigurators.lssPropertyOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            Property<?> property = PropertyRegistry.byName(ctx.getOption("property", String.class, ""));
            if (element == null || property == null) {
                UIActions.done(ctx, false);
                return;
            }
            element.lss(property.name, null, origin(ctx));
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /**
     * A whole LSS declaration block at once: {@code color: #ff5555; opacity: 0.5;}
     *
     * <p>Exactly what the {@code style="…"} attribute of a UI xml file means, and parsed by the same
     * code. Unknown property names are skipped with a log line rather than failing the block, which
     * is what the xml path does too.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_lss_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LssBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_lss_block.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public StyleOrigin origin = StyleOrigin.INLINE;
        @InputPort public UIElement element;
        @InputPort public String declarations = "";
        @OutputPort public UIElement out;
        @OutputPort public int applied;
        @OutputPort public boolean ok;

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            String declarations = ctx.getInput("declarations", String.class, "");
            if (element == null || declarations == null || declarations.isBlank()) {
                ctx.setOutput("applied", 0);
                UIActions.done(ctx, false);
                return;
            }
            StyleOrigin origin = origin(ctx);
            int applied = 0;
            for (var entry : Stylesheet.parseStyleValues(declarations).entrySet()) {
                Property p = entry.getKey();
                StyleValue v = entry.getValue();
                element.getStyleBag().replaceOrPutCandidate(p, StyleSlot.of(p, origin,
                        PROGRAMMATIC_SPECIFICITY, PROGRAMMATIC_SPECIFICITY, v.compute()));
                applied++;
            }
            ctx.setOutput("applied", applied);
            UIActions.done(ctx, applied > 0);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /**
     * The value an element's style resolves to right now, after the whole cascade.
     *
     * <p>Answers the resolved question, not "what did this graph set" — a colour coming from a
     * stylesheet rule reads here just as one set inline does. That makes it the node to check a style
     * with, and a poor one to store a graph's own state in.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_lss_get", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LssGet extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_lss_get.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public String text;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("property", String.class)
                    .withDefaultValue("")
                    .withConfigurable(UISearchConfigurators.lssPropertyOption())
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addOutputPort("value", TypeHandles.UNKNOWN);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            Property<?> property = PropertyRegistry.byName(ctx.getOption("property", String.class, ""));
            Object value = element == null || property == null ? null
                    : element.getStyleBag().computeCandidate(property);
            ctx.setOutput("value", value);
            ctx.setOutput("text", value == null ? "" : String.valueOf(value));
            ctx.setOutput("ok", value != null);
        }
    }

    // ---- classes -----------------------------------------------------------------------------

    /** What {@code ldlib2_ui_class} does with the names it is given. */
    public enum ClassOp implements net.minecraft.util.StringRepresentable {
        ADD("add"),
        REMOVE("remove"),
        /** Replaces the whole class list with these names. */
        SET("set"),
        /** Each name is added if absent, removed if present. */
        TOGGLE("toggle");

        private final String name;

        ClassOp(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Adds, removes, replaces or toggles class names — the pipeline most interaction should use.
     *
     * <p>Names are space-separated, exactly as in a {@code class="a b"} attribute.</p>
     *
     * <p>Preferred over {@code lss_set} for anything a stylesheet could describe. A handler that adds
     * {@code .selected} leaves the question of what "selected" looks like with the designer, works
     * for every element the rule matches, and is undone by removing one name — whereas a handler that
     * writes six inline properties has to remember and reverse all six.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_class", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ClassNames extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_class.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public ClassOp op = ClassOp.ADD;
        @InputPort public UIElement element;
        @InputPort public String classes = "";
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            String raw = ctx.getInput("classes", String.class, "");
            if (element == null || raw == null) {
                UIActions.done(ctx, false);
                return;
            }
            String[] names = raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
            ClassOp op = ctx.getOption("op", ClassOp.class, ClassOp.ADD);
            switch (op == null ? ClassOp.ADD : op) {
                case ADD -> element.addClasses(names);
                case REMOVE -> element.removeClasses(names);
                // SET with no names is a legitimate "clear every class", so it runs even when empty.
                case SET -> element.setClasses(names);
                case TOGGLE -> {
                    for (String name : names) {
                        if (element.hasClass(name)) {
                            element.removeClass(name);
                        } else {
                            element.addClass(name);
                        }
                    }
                }
            }
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** Whether an element carries a class name. */
    @NodeAttribute(name = "ldlib2_ui_has_class", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HasClass extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_has_class.tooltip");
        }

        @InputPort public UIElement element;
        @InputPort public String name = "";
        @OutputPort public boolean has;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            String name = ctx.getInput("name", String.class, "");
            ctx.setOutput("has", element != null && name != null && element.hasClass(name));
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    private static StyleOrigin origin(ExecContext ctx) {
        StyleOrigin origin = ctx.getOption("origin", StyleOrigin.class, StyleOrigin.INLINE);
        return origin == null ? StyleOrigin.INLINE : origin;
    }

    /**
     * Renders a graph value as the LSS text its property's parser expects.
     *
     * <p>{@code null} means "nothing to write" and the caller reports {@code ok = false} rather than
     * writing an empty string, which some parsers would happily accept as a value.</p>
     */
    @Nullable
    static String lssText(Property<?> property, @Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s.isBlank() ? null : s;
            // A colour property's parser understands #AARRGGBB, rgb(), and names — but not a bare
            // signed integer, which is exactly what every other colour in this graph is.
            case Number n when property instanceof ColorProperty -> "#%08X".formatted(n.intValue());
            case Number n -> numberText(n);
            case Boolean b -> b.toString();
            default -> value.toString();
        };
    }

    /** Whole numbers without a trailing {@code .0}: {@code width: 12}, not {@code width: 12.0}. */
    private static String numberText(Number n) {
        double d = n.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
