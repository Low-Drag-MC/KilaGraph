package com.lowdragmc.kilagraph.blueprint.nodes.ui.element;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoPropertyBlock;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import org.joml.Vector2f;

import java.util.List;

/**
 * The properties of a {@link UIElement}, one block each, usable only inside {@link UIElementInfoNode}.
 *
 * <h2>Geometry is client-only, and reads as zero elsewhere</h2>
 * Every number in the layout blocks comes from the taffy layout tree, which only exists once a client
 * {@code ModularUI} has laid the element out. On the server, and on the client before the first
 * layout pass, they read zero. That is a real answer rather than a bug: the element genuinely has no
 * position yet. A graph that positions something relative to another element belongs in a hover or
 * tick handler, not in the build.
 *
 * <h2>The three boxes</h2>
 * An element has three nested rectangles, and mixing them up is the usual source of an off-by-a-few
 * layout:
 * <ul>
 *   <li><b>size</b> — the whole element, border included.</li>
 *   <li><b>padding</b> — inside the border.</li>
 *   <li><b>content</b> — inside the padding. This is where children go, and the one a graph laying
 *       out its own children wants.</li>
 * </ul>
 */
public final class UIElementInfoBlocks {

    private static final String GROUP = "ui/element";

    private UIElementInfoBlocks() {
    }

    /** Base for the blocks here — saves each one restating its target type. */
    private abstract static class ElementBlock extends InfoPropertyBlock<UIElement> {
        @Override
        protected final Class<UIElement> targetClass() {
            return UIElement.class;
        }
    }

    // ---- identity ----------------------------------------------------------------------------

    /** The element's id and registered type name, and the classes on it. */
    @NodeAttribute(name = "ldlib2_ui_info_identity", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Identity extends ElementBlock {
        @OutputPort public String id;
        @OutputPort public String type;
        @OutputPort public List<?> classes;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("id", element.getId());
            ctx.setOutput("type", element.getElementName());
            ctx.setOutput("classes", List.copyOf(element.getClasses()));
        }
    }

    // ---- state -------------------------------------------------------------------------------

    /**
     * The visibility and interaction flags.
     *
     * <p>{@code displayed} is not simply the element's own display flag — it walks up the ancestors,
     * so it answers "would this be drawn at all", which is the question a graph is asking when it
     * checks. An element inside a hidden panel reports false here and true for {@code visible}.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_info_state", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class State extends ElementBlock {
        @OutputPort public boolean visible;
        @OutputPort public boolean active;
        @OutputPort public boolean focusable;
        @OutputPort public boolean displayed;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("visible", element.isVisible());
            ctx.setOutput("active", element.isActive());
            ctx.setOutput("focusable", element.isFocusable());
            ctx.setOutput("displayed", element.isDisplayed());
        }
    }

    /**
     * Whether the mouse is over the element and whether it holds keyboard focus.
     *
     * <p>{@code selfOrChildHover} is the one a container wants: a panel is not itself hovered when the
     * cursor is over a button inside it, but it usually wants to know anyway.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_info_interaction", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Interaction extends ElementBlock {
        @OutputPort public boolean hover;
        @OutputPort public boolean selfOrChildHover;
        @OutputPort public boolean focused;
        @OutputPort public boolean childFocused;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("hover", element.isHover());
            ctx.setOutput("selfOrChildHover", element.isSelfOrChildHover());
            ctx.setOutput("focused", element.isFocused());
            ctx.setOutput("childFocused", element.isChildFocused());
        }
    }

    // ---- structure ---------------------------------------------------------------------------

    /** Where the element sits in the tree. */
    @NodeAttribute(name = "ldlib2_ui_info_structure", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Structure extends ElementBlock {
        @OutputPort public UIElement parent;
        @OutputPort public int childCount;
        @OutputPort public int siblingIndex;
        @OutputPort public int depth;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("parent", element.getParent());
            ctx.setOutput("childCount", element.getChildren().size());
            ctx.setOutput("siblingIndex", element.getSiblingIndex());
            // The structure path includes the element itself, so its length minus one is how many
            // ancestors it has — which is what "depth" means to anyone reading it.
            ctx.setOutput("depth", Math.max(0, element.getStructurePath().size() - 1));
        }
    }

    // ---- geometry ----------------------------------------------------------------------------

    /** The element's position, relative to its parent's content box. */
    @NodeAttribute(name = "ldlib2_ui_info_position", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Position extends ElementBlock {
        @OutputPort public float x;
        @OutputPort public float y;
        @OutputPort public Vector2f xy;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            float x = element.getPositionX();
            float y = element.getPositionY();
            ctx.setOutput("x", x);
            ctx.setOutput("y", y);
            // Also as a vector, so it composes with the graph's vector maths without a make-vector node.
            ctx.setOutput("xy", new Vector2f(x, y));
        }
    }

    /** The element's own width and height, border included. */
    @NodeAttribute(name = "ldlib2_ui_info_size", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Size extends ElementBlock {
        @OutputPort public float width;
        @OutputPort public float height;
        @OutputPort public Vector2f size;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            float w = element.getSizeWidth();
            float h = element.getSizeHeight();
            ctx.setOutput("width", w);
            ctx.setOutput("height", h);
            ctx.setOutput("size", new Vector2f(w, h));
        }
    }

    /** The box children are laid out in: inside the padding. */
    @NodeAttribute(name = "ldlib2_ui_info_content_box", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class ContentBox extends ElementBlock {
        @OutputPort public float x;
        @OutputPort public float y;
        @OutputPort public float width;
        @OutputPort public float height;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("x", element.getContentX());
            ctx.setOutput("y", element.getContentY());
            ctx.setOutput("width", element.getContentWidth());
            ctx.setOutput("height", element.getContentHeight());
        }
    }

    /** The element's margins, one per side. */
    @NodeAttribute(name = "ldlib2_ui_info_margin", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(UIElementInfoNode.class)
    public static class Margin extends ElementBlock {
        @OutputPort public float top;
        @OutputPort public float bottom;
        @OutputPort public float left;
        @OutputPort public float right;

        @Override
        protected void read(UIElement element, EvalContext ctx) {
            ctx.setOutput("top", element.getMarginTop());
            ctx.setOutput("bottom", element.getMarginBottom());
            ctx.setOutput("left", element.getMarginLeft());
            ctx.setOutput("right", element.getMarginRight());
        }
    }
}
