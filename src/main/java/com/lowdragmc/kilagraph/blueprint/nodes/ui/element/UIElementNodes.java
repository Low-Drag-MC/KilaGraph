package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.ui.UIElements;
import com.lowdragmc.kilagraph.graph.ui.UISearchConfigurators;
import com.lowdragmc.kilagraph.graph.ui.UIXml;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Making UI elements and arranging them into a tree.
 *
 * <h2>Three ways to make one, and when each is right</h2>
 * <ul>
 *   <li>{@code ldlib2_ui_element_new} — pick a registered type from a dropdown. One element, fully
 *       under the graph's control. Right when the element's identity is a decision the graph makes.</li>
 *   <li>{@code ldlib2_ui_element_from_xml} — write the element, and any subtree under it, as XML.
 *       Right when the shape is fixed and writing it out is shorter than wiring it up.</li>
 *   <li>{@code ldlib2_ui_load_xml} / {@code ldlib2_ui_template_load} (in {@code ldlib2_ui_doc}) —
 *       load a whole UI authored elsewhere, then reach into it with {@code ldlib2_ui_select}. Right
 *       when a designer owns the layout and the graph only owns the behaviour.</li>
 * </ul>
 *
 * <p>They mix freely: the usual shape is to load a designed UI, select the elements that need
 * behaviour by id, and attach events and sync values to those.</p>
 *
 * <h2>Making an element is an exec node, not a pure value</h2>
 * {@code ldlib2_ui_element_new} has {@code trigger} / {@code next} pins, which looks odd for a node
 * whose whole job is to produce a value. It has to: an element has identity, so <em>when</em> one is
 * made must be something the graph states rather than a consequence of when something happened to
 * pull it. {@link UIActions#produce} spells out the two bugs the alternative causes — handlers
 * receiving a different element than the player clicked, and loops producing one row instead of ten.
 * Queries ({@link UIQueryNodes}) stay pure data, because finding something is not making it.
 *
 * <h2>Structure runs on both sides</h2>
 * Nothing in this file is client-only. That is deliberate and load-bearing — see
 * {@link UIActions} for why the two sides must build the same tree in the same order.
 */
public final class UIElementNodes {

    private static final String GROUP = "ui/element";

    private UIElementNodes() {
    }

    /**
     * A new element of a registered type.
     *
     * <p>The type option is a search over LDLib2's {@code ldlib2:ui_element} registry — the same table
     * that maps an XML tag name to a class, so {@code button} here and {@code <button/>} in XML build
     * the same thing. Picking a type that a since-removed mod registered falls back to a plain
     * container element rather than failing the build.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_element_new", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class New extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_element_new.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public String id = "";
        @InputPort public String classes = "";
        @OutputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            // Imperative rather than @Option: the value is a registry name and needs a search picker,
            // which the annotation surface cannot express (see docs/CONVENTIONS.md §1).
            context.addOption("type", String.class)
                    .withDefaultValue(UIElements.DEFAULT)
                    .withConfigurable(UISearchConfigurators.uiElementTypeOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            String type = ctx.getOption("type", String.class, UIElements.DEFAULT);
            UIElement created = UIElements.create(type);
            String id = ctx.getInput("id", String.class, "");
            if (id != null && !id.isEmpty()) created.setId(id);
            applyClasses(created, ctx.getInput("classes", String.class, ""));
            UIActions.produce(ctx, "element", created);
            UIActions.done(ctx, UIElements.find(type) != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "element");
        }
    }

    /**
     * Elements written as XML, in as little of it as you like: {@code <button id="ok"/>} is enough.
     *
     * <p>{@code element} is the first one, which is the common case; {@code elements} is all of them,
     * so a fragment naming several siblings does not silently lose the rest.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_element_from_xml", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromXml extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_element_from_xml.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public String xml = "";
        @OutputPort public UIElement element;
        @OutputPort public List<?> elements;
        @OutputPort public int count;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            List<UIElement> parsed = UIXml.parseElements(ctx.getInput("xml", String.class, ""));
            UIActions.produce(ctx, "element", parsed.isEmpty() ? null : parsed.getFirst());
            UIActions.produce(ctx, "elements", parsed);
            UIActions.produce(ctx, "count", parsed.size());
            UIActions.done(ctx, !parsed.isEmpty());
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "element", "elements", "count");
        }
    }

    /**
     * Adds a child to a parent.
     *
     * <p>{@code index} of {@code -1} appends. Any other value inserts there, which matters because
     * sibling order is what a flex layout lays out in.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_add_child", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AddChild extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_add_child.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement parent;
        @InputPort public UIElement child;
        @InputPort public int index = -1;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement parent = UIActions.element(ctx, "parent");
            UIElement child = UIActions.element(ctx, "child");
            ctx.setOutput("out", parent);
            if (parent == null || child == null || parent == child || child.isAncestorOf(parent)
                    || parent.hasChild(child)) {
                // A cycle would make getStructurePath loop forever the first time anything hit-tested;
                // a duplicate add throws inside LDLib2 rather than being a no-op.
                UIActions.done(ctx, false);
                return;
            }
            // LDLib2's addChildAt takes a real list index and throws on anything out of range, so the
            // "-1 appends" convention is translated here rather than passed through.
            int index = ctx.getInt("index", -1);
            if (index < 0 || index > parent.getChildren().size()) {
                parent.addChild(child);
            } else {
                parent.addChildAt(child, index);
            }
            UIActions.done(ctx, parent.hasChild(child));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            // Republished on pull so a chain can keep threading the parent along after the cache clear
            // that precedes an event dispatch.
            ctx.setOutput("out", UIActions.element(ctx, "parent"));
        }
    }

    /** Adds every element of a list to a parent, in list order. */
    @NodeAttribute(name = "ldlib2_ui_add_children", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AddChildren extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_add_children.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement parent;
        @InputPort public List<?> children = List.of();
        @OutputPort public UIElement out;
        @OutputPort public int added;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement parent = UIActions.element(ctx, "parent");
            ctx.setOutput("out", parent);
            if (parent == null) {
                ctx.setOutput("added", 0);
                UIActions.done(ctx, false);
                return;
            }
            int added = 0;
            for (UIElement child : UIActions.list(ctx, "children", UIElement.class)) {
                // hasChild too: a list containing the same element twice would throw on the second add.
                if (child == parent || child.isAncestorOf(parent) || parent.hasChild(child)) continue;
                parent.addChild(child);
                if (parent.hasChild(child)) added++;
            }
            ctx.setOutput("added", added);
            UIActions.done(ctx, added > 0);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "parent"));
        }
    }

    /** Removes a child from its parent. */
    @NodeAttribute(name = "ldlib2_ui_remove_child", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveChild extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_remove_child.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement parent;
        @InputPort public UIElement child;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement parent = UIActions.element(ctx, "parent");
            UIElement child = UIActions.element(ctx, "child");
            UIActions.done(ctx, parent != null && child != null && parent.removeChild(child));
        }
    }

    /** Removes an element from whatever parent it currently has. */
    @NodeAttribute(name = "ldlib2_ui_remove_self", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveSelf extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_remove_self.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            UIActions.done(ctx, element != null && element.removeSelf());
        }
    }

    /**
     * Removes an element's children.
     *
     * <p>{@code keepInternal} is on by default and is the setting that matters: a {@code Button} owns
     * an internal {@code TextElement} for its caption, a {@code Scroller} owns its handle. Clearing
     * those leaves a button that can never show text again. The default therefore clears only the
     * children a graph or an XML file added, which is what "clear the children" almost always means.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_clear_children", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ClearChildren extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_clear_children.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public boolean keepInternal = true;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            if (element == null) {
                UIActions.done(ctx, false);
                return;
            }
            if (ctx.getBool("keepInternal", true)) {
                element.clearAllExternalChildren();
            } else {
                element.clearAllChildren();
            }
            UIActions.done(ctx, true);
        }
    }

    /**
     * An independent copy of an element and everything under it.
     *
     * <p>LDLib2 implements {@code copy()} by serialising and deserialising, so the copy carries the
     * tree, ids, classes and persisted fields — but <b>not</b> event listeners, sync values or RPC
     * events, which live in Java objects that NBT cannot hold. A copied button is the same button to
     * look at and does nothing when clicked. Attach behaviour after copying, not before.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_copy", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Copy extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_copy.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            UIActions.produce(ctx, "out", element == null ? null : element.copy());
            UIActions.done(ctx, element != null);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "out");
        }
    }

    /** An element's tree as NBT — the same shape a {@code UITemplate} stores. */
    @NodeAttribute(name = "ldlib2_ui_serialize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Serialize extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_serialize.tooltip");
        }

        @InputPort public UIElement element;
        @OutputPort public CompoundTag nbt;

        @Override
        public void evaluate(EvalContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            ctx.setOutput("nbt", element == null ? new CompoundTag()
                    : element.serializeNBT(Platform.getFrozenRegistry()));
        }
    }

    /** Loads an NBT tree into an existing element, replacing what was there. */
    @NodeAttribute(name = "ldlib2_ui_deserialize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Deserialize extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_deserialize.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public CompoundTag nbt;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            CompoundTag nbt = ctx.getInput("nbt", CompoundTag.class, null);
            if (element == null || nbt == null || nbt.isEmpty()) {
                UIActions.done(ctx, false);
                return;
            }
            element.deserializeNBT(Platform.getFrozenRegistry(), nbt);
            UIActions.done(ctx, true);
        }
    }

    /** Splits a space-separated class list onto an element. Shared by {@code New} and the style nodes. */
    static void applyClasses(UIElement element, String classes) {
        if (classes == null || classes.isBlank()) return;
        for (String clazz : classes.trim().split("\\s+")) {
            if (!clazz.isEmpty()) element.addClass(clazz);
        }
    }
}
