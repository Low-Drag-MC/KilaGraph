package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

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
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The switches on an element that are not appearance: identity, the visibility/interaction flags,
 * and focus.
 *
 * <h2>Why these are not LSS</h2>
 * Everything visual in this graph goes through {@code ldlib2_ui_lss_*} and class names. These do not,
 * because they are not styling — an id is how a selector and a stylesheet find the element in the
 * first place, {@code active} decides whether events reach it at all, and focus is a property of the
 * {@code ModularUI} rather than of the element. {@code display} sits on the line: LSS can hide an
 * element too, but {@code setDisplay(false)} is the one that also takes it out of the layout, and
 * that is the operation a graph reaches for when it is showing and hiding a panel.
 */
public final class UIStateNodes {

    private static final String GROUP = "ui/element";

    private UIStateNodes() {
    }

    /** Sets an element's id — the handle a selector, a stylesheet and {@code find_by_id} all use. */
    @NodeAttribute(name = "ldlib2_ui_set_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_set_id.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public String id = "";
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
            String id = ctx.getInput("id", String.class, "");
            element.setId(id == null ? "" : id);
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** The boolean switches {@code ldlib2_ui_set_flag} can flip. */
    public enum Flag implements StringRepresentable {
        /** Drawn or not. An invisible element still occupies its layout space and still gets events. */
        VISIBLE("visible"),
        /** Receives events or not. An inactive element is what "disabled" means. */
        ACTIVE("active"),
        /** Can take keyboard focus. Off by default on everything that is not a text input. */
        FOCUSABLE("focusable"),
        /** In the layout or not. Off removes it entirely, so siblings close the gap. */
        DISPLAY("display"),
        /** Whether content outside the element's bounds is drawn rather than clipped. */
        OVERFLOW_VISIBLE("overflow_visible"),
        /** Whether the mouse can hit this element. Off makes it click-through. */
        HIT_TEST("hit_test");

        private final String name;

        Flag(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Flips one of an element's state flags.
     *
     * <p>One node with a dropdown rather than six nodes, because they take the same two inputs and
     * differ only in which setter they call — six near-identical entries would make the item library
     * harder to read, not easier.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_set_flag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetFlag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_set_flag.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public Flag flag = Flag.VISIBLE;
        @InputPort public UIElement element;
        @InputPort public boolean value = true;
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
            Flag flag = ctx.getOption("flag", Flag.class, Flag.VISIBLE);
            boolean value = ctx.getBool("value", true);
            switch (flag == null ? Flag.VISIBLE : flag) {
                case VISIBLE -> element.setVisible(value);
                case ACTIVE -> element.setActive(value);
                case FOCUSABLE -> element.setFocusable(value);
                case DISPLAY -> element.setDisplay(value);
                case OVERFLOW_VISIBLE -> element.setOverflowVisible(value);
                case HIT_TEST -> element.setAllowHitTest(value);
            }
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** What {@code ldlib2_ui_focus} does to the focus. */
    public enum FocusOp implements StringRepresentable {
        /** Give this element keyboard focus. Requires it to be focusable. */
        FOCUS("focus"),
        /** Take focus away from this element, if it has it. */
        BLUR("blur"),
        /** Drop focus entirely, whatever holds it. Ignores the element input. */
        CLEAR("clear");

        private final String name;

        FocusOp(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Moves keyboard focus.
     *
     * <p>Focus lives on the {@code ModularUI}, not on the element, so this does nothing for an element
     * that has not been added to a mounted tree yet — which is why focusing something is nearly always
     * an event handler's job rather than a build step.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_focus", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Focus extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_focus.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public FocusOp op = FocusOp.FOCUS;
        @InputPort public UIElement element;
        @InputPort public ModularUI mui;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            FocusOp op = ctx.getOption("op", FocusOp.class, FocusOp.FOCUS);
            UIElement element = UIActions.element(ctx, "element");
            if (op == FocusOp.CLEAR) {
                ModularUI mui = ctx.getInput("mui", ModularUI.class, null);
                if (mui == null && element != null) mui = element.getModularUI();
                if (mui != null) mui.clearFocus();
                UIActions.done(ctx, mui != null);
                return;
            }
            if (element == null || element.getModularUI() == null) {
                UIActions.done(ctx, false);
                return;
            }
            if (op == FocusOp.BLUR) {
                element.blur();
            } else {
                element.focus();
            }
            UIActions.done(ctx, true);
        }
    }
}
