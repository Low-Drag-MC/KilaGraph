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
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.style.Property;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleSlot;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.StyleAnimation;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The two style operations that do not fit the plain "set a property" shape: animating one over time,
 * and giving an element a tooltip.
 *
 * <h2>Both still go through LSS properties</h2>
 * Neither invents a vocabulary. {@code ldlib2_ui_animate} interpolates a named LSS property towards
 * a target written in LSS syntax; {@code ldlib2_ui_tooltip} writes the {@code tooltips} property. What
 * makes them separate nodes is that one takes time and the other takes a list of chat components,
 * and neither of those fits in a single declaration string.
 */
public final class UIAnimationNodes {

    private static final String GROUP = "ui/style";

    private UIAnimationNodes() {
    }

    /**
     * Interpolates a style property from wherever it is now to a target value.
     *
     * <p><b>Client only.</b> The animation engine lives on the {@code ModularUI}, and the server has
     * neither one nor anything to draw — the node reports {@code ok = false} there rather than
     * pretending. A graph that also has to do something on the server should branch on
     * {@code ldlib2_ui_side} <em>around this node</em>, never around the tree it animates.</p>
     *
     * <p>The animation writes at the {@code ANIMATION} origin, which outranks both stylesheet rules
     * and inline values while it runs, then leaves the final value behind as {@code INLINE}. So an
     * animated property stays where the animation put it — to give it back to the stylesheet, remove
     * it with {@code ldlib2_ui_lss_remove} at the {@code INLINE} origin afterwards.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_animate", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Animate extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_animate.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public Eases ease = Eases.LINEAR;
        @InputPort public UIElement element;
        /** Same String-typed pin as {@code ldlib2_ui_lss_set}, converted by the same rules. */
        @InputPort public String to = "";
        @InputPort public float duration = 0.25f;
        @InputPort public float delay = 0f;
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
            Property<?> property = PropertyRegistry.byName(ctx.getOption("property", String.class, ""));
            var mui = element == null ? null : element.getModularUI();
            String target = property == null ? null : UIStyleNodes.lssText(property, ctx.getInputRaw("to"));
            if (mui == null || target == null || LDLib2.isServer()) {
                UIActions.done(ctx, false);
                return;
            }
            Eases ease = ctx.getOption("ease", Eases.class, Eases.LINEAR);
            StyleAnimation.of(mui)
                    .select(element)
                    .duration(Math.max(0f, ctx.getFloat("duration", 0.25f)))
                    .delay(Math.max(0f, ctx.getFloat("delay", 0f)))
                    .ease(ease == null ? Eases.LINEAR : ease)
                    .lss(property.name, target)
                    .start();
            UIActions.done(ctx, true);
        }
    }

    /**
     * Sets the tooltip lines an element shows on hover.
     *
     * <p>Writes the {@code tooltips} style property directly rather than through
     * {@code BasicStyle.tooltips(...)}, for two reasons: that path is a no-op on the server, and this
     * one participates in the origin cascade like every other style value — so a tooltip set here can
     * be removed again with {@code ldlib2_ui_lss_remove}.</p>
     *
     * <p>An empty list clears the tooltip, which is how a graph turns a hint off once it no longer
     * applies.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_tooltip", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Tooltip extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_tooltip.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public StyleOrigin origin = StyleOrigin.INLINE;
        @InputPort public UIElement element;
        @InputPort public Component line;
        @InputPort public List<?> lines = List.of();
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("out", element);
            if (element == null) {
                UIActions.done(ctx, false);
                return;
            }
            // A single line and a list, because one line is the overwhelmingly common case and
            // wrapping it in a list node every time would be pure ceremony.
            var texts = new java.util.ArrayList<>(UIActions.list(ctx, "lines", Component.class));
            Component single = ctx.getInput("line", Component.class, null);
            if (single != null) texts.addFirst(single);

            StyleOrigin origin = ctx.getOption("origin", StyleOrigin.class, StyleOrigin.INLINE);
            element.getStyleBag().replaceOrPutCandidate(PropertyRegistry.TOOLTIPS,
                    StyleSlot.of(PropertyRegistry.TOOLTIPS, origin == null ? StyleOrigin.INLINE : origin,
                            999, 999, Tooltips.of(texts)));
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }
}
