package com.lowdragmc.kilagraph.blueprint.nodes.ui.event;

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
import com.lowdragmc.kilagraph.graph.ui.UICallbacks;
import com.lowdragmc.kilagraph.graph.ui.UISearchConfigurators;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Map;

/**
 * Reacting to what the player does.
 *
 * <h2>How a node can run "later"</h2>
 * {@code ldlib2_ui_on_event} has <b>two</b> exec outputs, and that is the whole idea:
 *
 * <pre>
 *   trigger ──▶ [ on_event ] ──▶ next      ── keeps building the UI, immediately
 *                     │
 *                     ╰────────▶ onEvent   ── runs every time the player clicks, minutes later
 * </pre>
 *
 * <p>The registration pass subscribes a listener and continues out of {@code next}. When the event
 * fires, the listener re-enters this same node, which this time flows {@code onEvent} instead. See
 * {@link UICallbacks} for the mechanism; the consequence worth knowing here is that the handler
 * chain is evaluated <em>fresh</em> — the pull cache is cleared first — so anything it reads from the
 * UI is current rather than whatever was true while the tree was being assembled.</p>
 *
 * <h2>Capture and bubble</h2>
 * An event travels root → target (capture), fires at the target, then target → root (bubble), exactly
 * as in a browser. A bubble listener (the default) is what you want to handle a button's own click.
 * A capture listener sees the event on the way <em>down</em>, before any descendant does — which is
 * how a dialog intercepts clicks meant for what is underneath it.
 *
 * <h2>Client, unless you say otherwise</h2>
 * Input happens on the client, so {@code ldlib2_ui_on_event} handlers run there. When the reaction
 * has to change the world — consume an item, flip a machine — use
 * {@code ldlib2_ui_on_server_event}, which registers an RPC and lets LDLib2's dispatcher relay the
 * event; the handler then runs on the server, where the world is real. Both can listen to the same
 * event type on the same element.
 */
public final class UIEventNodes {

    private static final String GROUP = "ui/event";

    /** Payload key for the dispatched event. */
    private static final String EVENT = "event";

    private UIEventNodes() {
    }

    /**
     * Runs a chain whenever an event reaches an element.
     *
     * <p>The event type is a search over the {@link UIEvents} constants, but free text is accepted:
     * a custom type dispatched by {@code ldlib2_ui_event_dispatch} is not in any registry and still
     * needs to be listenable.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_on_event", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class OnEvent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_on_event.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onEvent;

        @InputPort public UIElement element;
        @InputPort public boolean useCapture = false;
        @OutputPort public UIEvent event;
        @OutputPort public UIElement target;
        @OutputPort public UIElement currentElement;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("eventType", String.class)
                    .withDefaultValue(UIEvents.CLICK)
                    .withConfigurable(UISearchConfigurators.eventTypeOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            String type = ctx.getOption("eventType", String.class, UIEvents.CLICK);
            if (element == null || type == null || type.isBlank()) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            var trampoline = UICallbacks.arm(ctx, "onEvent");
            element.addEventListener(type, e -> trampoline.fire(Map.of(EVENT, e)),
                    ctx.getBool("useCapture", false));
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            publishEvent(ctx);
            UICallbacks.publishRegistered(ctx);
        }
    }

    /**
     * Runs a chain on the <b>server</b> whenever an event reaches an element on the client.
     *
     * <p>Registers an RPC event behind the scenes, which LDLib2's dispatcher fires automatically
     * whenever the matching client-side event occurs. That has two consequences worth planning for:
     * the handler sees a {@link UIEvent} rebuilt from the wire — coordinates, button and key data
     * survive, {@code dragHandler} and {@code customData} do not — and the round trip means the
     * server reacts a tick or so after the click, so a client-side handler should own anything the
     * player must see immediately.</p>
     *
     * <p>Both sides must run this node, in the same order as every other sync registration, or the
     * RPC ids will not line up. Building the same tree on both sides is what guarantees that.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_on_server_event", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class OnServerEvent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_on_server_event.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onEvent;

        @InputPort public UIElement element;
        @InputPort public boolean useCapture = false;
        @OutputPort public UIEvent event;
        @OutputPort public UIElement target;
        @OutputPort public UIElement currentElement;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("eventType", String.class)
                    .withDefaultValue(UIEvents.CLICK)
                    .withConfigurable(UISearchConfigurators.eventTypeOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            String type = ctx.getOption("eventType", String.class, UIEvents.CLICK);
            if (element == null || type == null || type.isBlank()) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            var trampoline = UICallbacks.arm(ctx, "onEvent");
            element.addServerEventListener(type, e -> trampoline.fire(Map.of(EVENT, e)),
                    ctx.getBool("useCapture", false));
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            publishEvent(ctx);
            UICallbacks.publishRegistered(ctx);
        }
    }

    /**
     * Runs a chain once per client tick, while the element is active and displayed.
     *
     * <p>Sugar over {@code UIEvents.TICK}, and worth its own node because it is the hook for anything
     * that has to poll: a countdown, a value that changes without an input event, a progress bar fed
     * by something the UI cannot subscribe to.</p>
     *
     * <p>It runs twenty times a second and clears the pull cache each time, so keep what hangs off it
     * small. A value that changes rarely belongs on a sync value's {@code onReceived} instead.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_on_tick", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class OnTick extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_on_tick.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onTick;

        @InputPort public UIElement element;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            if (element == null) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            var trampoline = UICallbacks.arm(ctx, "onTick");
            element.addEventListener(UIEvents.TICK, e -> trampoline.fire());
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UICallbacks.publishRegistered(ctx);
        }
    }

    /**
     * Everything carried by a dispatched event.
     *
     * <p>{@code x} / {@code y} are in the <b>target element's local space</b>, not screen space —
     * which is what a handler nearly always wants, because it can compare them against the element's
     * own content box without transforming anything.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_event_info", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EventInfo extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_event_info.tooltip");
        }

        @InputPort public UIEvent event;
        @OutputPort public String type;
        @OutputPort public float x;
        @OutputPort public float y;
        @OutputPort public float deltaX;
        @OutputPort public float deltaY;
        @OutputPort public int button;
        @OutputPort public int keyCode;
        @OutputPort public int modifiers;
        @OutputPort public String character;
        @OutputPort public UIElement target;
        @OutputPort public UIElement relatedTarget;
        @OutputPort public String command;
        @OutputPort public boolean shift;
        @OutputPort public boolean ctrl;
        @OutputPort public boolean alt;

        @Override
        public void evaluate(EvalContext ctx) {
            UIEvent event = ctx.getInput("event", UIEvent.class, null);
            ctx.setOutput("type", event == null ? "" : event.type);
            ctx.setOutput("x", event == null ? 0f : event.x);
            ctx.setOutput("y", event == null ? 0f : event.y);
            ctx.setOutput("deltaX", event == null ? 0f : event.deltaX);
            ctx.setOutput("deltaY", event == null ? 0f : event.deltaY);
            ctx.setOutput("button", event == null ? -1 : event.button);
            ctx.setOutput("keyCode", event == null ? -1 : event.keyCode);
            ctx.setOutput("modifiers", event == null ? 0 : event.modifiers);
            // As a String rather than a char: the graph has no char type, and a codePoint of 0 should
            // read as "nothing typed" rather than as a NUL character.
            ctx.setOutput("character", event == null || event.codePoint == 0
                    ? "" : String.valueOf(event.codePoint));
            ctx.setOutput("target", event == null ? null : event.target);
            ctx.setOutput("relatedTarget", event == null ? null : event.relatedTarget);
            ctx.setOutput("command", event == null || event.command == null ? "" : event.command);
            // Read live rather than off the event: a UIEvent does not carry the modifier state, and
            // these are what a handler means by "was shift held".
            ctx.setOutput("shift", UIElement.isShiftDown());
            // 26.1's LDLib2 folds Cmd into the same query, which is what a Mac user means by ctrl anyway.
            ctx.setOutput("ctrl", UIElement.isCtrlOrCmdDown());
            ctx.setOutput("alt", UIElement.isAltDown());
        }
    }

    /** How far {@code ldlib2_ui_event_stop} stops an event. */
    public enum StopMode implements StringRepresentable {
        /** No further element sees it, in either phase. Ancestors and descendants both stop. */
        PROPAGATION("propagation"),
        /** As above, and no other listener on this element runs either. */
        IMMEDIATE("immediate"),
        /** This element's other listeners still run; nothing after this phase does. */
        LATER("later");

        private final String name;

        StopMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Stops an event travelling further.
     *
     * <p>This is how a handler says "I dealt with it": without it, a click on a button inside a
     * scrollable panel also reaches the panel. It is also what
     * {@code UIEvents.VALIDATE_COMMAND} requires — stopping propagation there is the signal that the
     * command <em>is</em> handled.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_event_stop", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Stop extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_event_stop.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @Option public StopMode mode = StopMode.PROPAGATION;
        @InputPort public UIEvent event;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIEvent event = ctx.getInput("event", UIEvent.class, null);
            if (event == null) {
                UIActions.done(ctx, false);
                return;
            }
            StopMode mode = ctx.getOption("mode", StopMode.class, StopMode.PROPAGATION);
            switch (mode == null ? StopMode.PROPAGATION : mode) {
                case PROPAGATION -> event.stopPropagation();
                case IMMEDIATE -> event.stopImmediatePropagation();
                case LATER -> event.stopLaterPropagation();
            }
            UIActions.done(ctx, true);
        }
    }

    /**
     * Builds an event and dispatches it at an element, as though the player had caused it.
     *
     * <p>Two uses. One is synthetic input — clicking a button from a graph. The other is a custom
     * event type as a message bus: dispatch {@code "myMod:refresh"} at the root and let every part of
     * the UI that cares listen for it with {@code ldlib2_ui_on_event}, instead of wiring each of them
     * to the thing that changed.</p>
     *
     * <p>{@code sendServer} decides whether server-side listeners are notified too. Leave it off for
     * a purely cosmetic event; a synthetic event that claims the player did something is worth
     * thinking about before it reaches the server.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_event_dispatch", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Dispatch extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_event_dispatch.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement target;
        @InputPort public float x = 0;
        @InputPort public float y = 0;
        @InputPort public int button = 0;
        @InputPort public boolean capturePhase = true;
        @InputPort public boolean bubblePhase = true;
        @InputPort public boolean sendServer = false;
        @OutputPort public UIEvent event;
        @OutputPort public boolean handled;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("eventType", String.class)
                    .withDefaultValue(UIEvents.CLICK)
                    .withConfigurable(UISearchConfigurators.eventTypeOption())
                    .build();
        }

        @Override
        public void execute(ExecContext ctx) {
            UIElement target = UIActions.element(ctx, "target");
            String type = ctx.getOption("eventType", String.class, UIEvents.CLICK);
            if (target == null || type == null || type.isBlank()) {
                ctx.setOutput("handled", false);
                UIActions.done(ctx, false);
                return;
            }
            UIEvent event = UIEvent.create(type);
            event.target = target;
            event.currentElement = target;
            event.x = ctx.getFloat("x", 0);
            event.y = ctx.getFloat("y", 0);
            event.button = ctx.getInt("button", 0);
            UIEventDispatcher.dispatchEvent(event,
                    ctx.getBool("capturePhase", true),
                    ctx.getBool("bubblePhase", true),
                    ctx.getBool("sendServer", false));
            ctx.setOutput("event", event);
            // hasHandler is set by the dispatcher when at least one listener ran, which is the only
            // answer worth reporting: "did anything care about this".
            ctx.setOutput("handled", event.hasHandler);
            UIActions.done(ctx, true);
        }
    }

    /** Publishes the dispatched event onto the three ports every listener node exposes. */
    private static void publishEvent(EvalContext ctx) {
        Object payload = UICallbacks.payload(ctx, EVENT);
        UIEvent event = payload instanceof UIEvent e ? e : null;
        ctx.setOutput(EVENT, event);
        ctx.setOutput("target", event == null ? null : event.target);
        ctx.setOutput("currentElement", event == null ? null : event.currentElement);
    }
}
