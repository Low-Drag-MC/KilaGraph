package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.ui.UIElements;
import com.lowdragmc.kilagraph.graph.ui.UISearchConfigurators;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * Finding elements in a tree that already exists.
 *
 * <h2>Selectors</h2>
 * {@code UIElement.select} takes the CSS-like syntax LSS uses: {@code #save} by id, {@code .row} by
 * class, a bare {@code button} by registered type name, and combinations. That is the same language
 * the stylesheets are written in, which is the point — a graph reaches for the elements a designer
 * already named for styling, instead of needing a second naming scheme.
 *
 * <h2>Selecting from a {@code ModularUI} is not the same as selecting from an element</h2>
 * {@code ldlib2_ui_find_by_id} goes through the {@code ModularUI}'s id index and is a map lookup;
 * {@code ldlib2_ui_select} walks the subtree. Both find the same elements when the tree is mounted,
 * but only the walk works <em>before</em> it is — during the build, while nothing has a
 * {@code ModularUI} yet. Build-time code wants {@code select}; a handler firing later can use either
 * and should prefer the index.
 *
 * <h2>Every query is pure</h2>
 * No exec ports here. A query is pulled when something downstream needs it, and re-pulled after each
 * event dispatch clears the cache — so a handler always sees the tree as it is now, not as it was
 * when it was wired.
 */
public final class UIQueryNodes {

    private static final String GROUP = "ui/element";

    private UIQueryNodes() {
    }

    /** An element's parent, and whether it has one. */
    @NodeAttribute(name = "ldlib2_ui_parent", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Parent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_parent.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public UIElement parent;
        @OutputPort public boolean hasParent;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("parent", element == null ? null : element.getParent());
            ctx.setOutput("hasParent", element != null && element.hasParent());
        }
    }

    /** An element's direct children. */
    @NodeAttribute(name = "ldlib2_ui_children", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Children extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_children.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public List<?> children;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            // A defensive copy: getChildren() is the live backing list, and a ForEach over it while the
            // body adds or removes a child would throw ConcurrentModificationException.
            List<UIElement> children = element == null ? List.of() : List.copyOf(element.getChildren());
            ctx.setOutput("children", children);
            ctx.setOutput("count", children.size());
        }
    }

    /** Every descendant, depth-first — the whole subtree flattened. */
    @NodeAttribute(name = "ldlib2_ui_descendants", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Descendants extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_descendants.tooltip");
        }

        @InputPort public UIElement element;
        @InputPort public boolean includeSelf = false;
        @OutputPort public List<?> elements;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            List<UIElement> result;
            if (element == null) {
                result = List.of();
            } else {
                result = (ctx.getBool("includeSelf", false)
                        ? element.selfAndAllChildren()
                        : element.allChildrenStream()).toList();
            }
            ctx.setOutput("elements", result);
            ctx.setOutput("count", result.size());
        }
    }

    /** The child at an index, or nothing if the index is out of range. */
    @NodeAttribute(name = "ldlib2_ui_child_at", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ChildAt extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_child_at.tooltip");
        }

        @InputPort public UIElement element;
        @InputPort public int index = 0;
        @OutputPort public UIElement child;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            int index = ctx.getInt("index", 0);
            List<UIElement> children = element == null ? List.of() : element.getChildren();
            boolean inRange = index >= 0 && index < children.size();
            ctx.setOutput("child", inRange ? children.get(index) : null);
            ctx.setOutput("ok", inRange);
        }
    }

    /** The nearest ancestor of a chosen element type — how a child finds the panel it lives in. */
    @NodeAttribute(name = "ldlib2_ui_ancestor_of_type", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AncestorOfType extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_ancestor_of_type.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public UIElement ancestor;
        @OutputPort public boolean found;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("type", String.class)
                    .withDefaultValue(UIElements.DEFAULT)
                    .withConfigurable(UISearchConfigurators.uiElementTypeOption())
                    .build();
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            Class<? extends UIElement> type =
                    UIElements.classOf(ctx.getOption("type", String.class, UIElements.DEFAULT));
            UIElement found = element == null || type == null ? null : element.getFirstAncestorOfType(type);
            ctx.setOutput("ancestor", found);
            ctx.setOutput("found", found != null);
        }
    }

    /**
     * Elements matching an LSS-style selector, searched from {@code root} downwards.
     *
     * <p>{@code first} is there because most selectors are meant to match one thing and unpacking a
     * single-element list to get at it is pure noise.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_select", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Select extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_select.tooltip");
        }

        @InputPort public UIElement root;
        @InputPort public String selector = "";
        @OutputPort public List<?> elements;
        @OutputPort public UIElement first;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement root = UIActions.element(ctx, "root");
            String selector = ctx.getInput("selector", String.class, "");
            publish(ctx, root == null || selector == null || selector.isBlank()
                    ? Stream.<UIElement>empty() : root.select(selector));
        }
    }

    /** Elements whose id matches exactly, searched from {@code root} downwards. */
    @NodeAttribute(name = "ldlib2_ui_select_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SelectId extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_select_id.tooltip");
        }

        @InputPort public UIElement root;
        @InputPort public String id = "";
        @OutputPort public List<?> elements;
        @OutputPort public UIElement first;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement root = UIActions.element(ctx, "root");
            String id = ctx.getInput("id", String.class, "");
            publish(ctx, root == null || id == null || id.isEmpty()
                    ? Stream.<UIElement>empty() : root.selectId(id));
        }
    }

    /**
     * Elements whose id matches a regular expression.
     *
     * <p>The reason this exists beside {@code select_id}: a list built from a template gives its rows
     * ids like {@code slot_0}, {@code slot_1}, and {@code slot_\d+} is how a graph gets all of them
     * without knowing how many there are.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_select_regex", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SelectRegex extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_select_regex.tooltip");
        }

        @InputPort public UIElement root;
        @InputPort public String regex = "";
        @OutputPort public List<?> elements;
        @OutputPort public UIElement first;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement root = UIActions.element(ctx, "root");
            String regex = ctx.getInput("regex", String.class, "");
            publish(ctx, root == null || regex == null || regex.isBlank()
                    ? Stream.<UIElement>empty() : root.selectRegex(regex));
        }
    }

    /**
     * An element by id, through the {@code ModularUI}'s index rather than a tree walk.
     *
     * <p>Only finds elements that are actually mounted — an element built but not yet added to the
     * tree is invisible here, which is the usual reason this returns nothing during a build.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_find_by_id", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FindById extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_find_by_id.tooltip");
        }

        @InputPort public ModularUI mui;
        @InputPort public String id = "";
        @OutputPort public UIElement first;
        @OutputPort public List<?> elements;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            ModularUI mui = ctx.getInput("mui", ModularUI.class, null);
            String id = ctx.getInput("id", String.class, "");
            List<UIElement> found = mui == null || id == null || id.isEmpty()
                    ? List.of() : List.copyOf(mui.getElementsById(id));
            ctx.setOutput("elements", found);
            ctx.setOutput("first", found.isEmpty() ? null : found.getFirst());
            ctx.setOutput("count", found.size());
        }
    }

    /** The {@code elements} / {@code first} / {@code count} trio every selector node publishes. */
    private static void publish(EvalContext ctx, Stream<UIElement> stream) {
        List<UIElement> found = stream.toList();
        ctx.setOutput("elements", found);
        ctx.setOutput("first", found.isEmpty() ? null : found.getFirst());
        ctx.setOutput("count", found.size());
    }
}
