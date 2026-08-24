package com.lowdragmc.kilagraph.blueprint.nodes.ui.event;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.DragHandler;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * Drag and drop.
 *
 * <h2>Why these two nodes and no others</h2>
 * The drag <em>events</em> — {@code dragEnter}, {@code dragLeave}, {@code dragUpdate},
 * {@code dragPerform}, {@code dragEnd} — need nothing special: they are ordinary event types and
 * {@code ldlib2_ui_on_event} already listens for them. What LDLib2 does not express as an event is
 * the pair at either end of the gesture:
 *
 * <ul>
 *   <li><b>Starting one.</b> A drag does not begin because the mouse moved; it begins because
 *       something called {@code startDrag} with a payload, usually from a {@code mouseDown} handler.
 *       Without {@code ldlib2_ui_start_drag} a graph can only ever receive drags that some Java code
 *       started.</li>
 *   <li><b>Reading the payload.</b> The dragged object lives on the event's {@code DragHandler}, not
 *       on the event itself, so {@code ldlib2_ui_event_info} cannot reach it. A {@code dragPerform}
 *       handler that cannot ask <em>what was dropped</em> is not much of a drop target.</li>
 * </ul>
 *
 * <h2>Client only</h2>
 * Dragging is a pointer gesture and LDLib2 never sends drag events to the server (see
 * {@code UIEvents}). A graph that has to act on a drop server-side should have the client handler
 * send an RPC or a message once it knows what was dropped.
 */
public final class UIDragNodes {

    private static final String GROUP = "ui/event";

    private UIDragNodes() {
    }

    /**
     * Begins a drag, carrying an arbitrary payload.
     *
     * <p>Call it from a {@code mouseDown} handler on whatever the player grabbed. The payload can be
     * anything the graph carries — an item stack, an index, the source element itself — and comes back
     * out of {@code ldlib2_ui_drag_info} on the element that receives the drop.</p>
     *
     * <p>{@code source} defaults to the dragged element and is what {@code dragEnd} is dispatched at
     * once the gesture finishes, so a graph that wants to know its own drag ended should listen
     * there.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_start_drag", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class StartDrag extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_start_drag.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            // UNKNOWN because the payload is deliberately untyped — it is whatever this UI means by
            // "the thing being dragged", and only the drop handler has to agree. Unlike the LSS value
            // pin, there is no sensible inline literal for it, so having no embedded constant costs
            // nothing: a payload always arrives over a wire.
            context.addInputPort("payload", TypeHandles.UNKNOWN);
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            var mui = element == null ? null : element.getModularUI();
            if (mui == null) {
                // No live UI means no DragHandler — the gesture has nowhere to live.
                UIActions.done(ctx, false);
                return;
            }
            mui.getDragHandler().startDrag(ctx.getInputRaw("payload"), null, element);
            UIActions.done(ctx, true);
        }
    }

    /**
     * What is being dragged, read off a drag event.
     *
     * <p>{@code payload} is whatever {@code ldlib2_ui_start_drag} was given. It is untyped on the
     * wire, so a drop handler that expects an item stack should route it through a cast or a type
     * check rather than assuming.</p>
     *
     * <p>Also accepts the element instead of the event: a {@code tick} or {@code mouseMove} handler
     * has no drag event to hand but can still ask its UI whether a drag is in progress.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_drag_info", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DragInfo extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_drag_info.tooltip");
        }

        @InputPort public UIEvent event;
        @InputPort public UIElement element;
        @OutputPort public boolean dragging;
        @OutputPort public UIElement source;
        @OutputPort public float startX;
        @OutputPort public float startY;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addOutputPort("payload", TypeHandles.UNKNOWN);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            DragHandler handler = resolve(ctx);
            ctx.setOutput("dragging", handler != null && handler.isDragging());
            ctx.setOutput("payload", handler == null ? null : handler.draggingObject);
            ctx.setOutput("source", handler == null ? null : handler.dragSource);
            ctx.setOutput("startX", handler == null ? 0f : handler.startX);
            ctx.setOutput("startY", handler == null ? 0f : handler.startY);
        }

        /** The event's own handler if it carries one, else the element's UI's. */
        private static DragHandler resolve(EvalContext ctx) {
            UIEvent event = ctx.getInput("event", UIEvent.class, null);
            if (event != null && event.dragHandler != null) return event.dragHandler;
            UIElement element = UIActions.element(ctx, "element");
            var mui = element == null ? null : element.getModularUI();
            return mui == null ? null : mui.getDragHandler();
        }
    }
}
