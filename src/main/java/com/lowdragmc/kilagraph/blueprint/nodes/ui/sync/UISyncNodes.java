package com.lowdragmc.kilagraph.blueprint.nodes.ui.sync;

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
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Keeping a value the same on both sides.
 *
 * <h2>What a sync value is</h2>
 * One named slot that LDLib2 mirrors across the network every tick it changes. Register it on an
 * element, and it lives as long as that element is in the UI. Compared to sending an RPC by hand it
 * is the right tool whenever the question is "what is this value now" rather than "something just
 * happened": a furnace's burn time, a tank's fill level, the contents of a text field.
 *
 * <h2>⚠ Registration order is the wire protocol</h2>
 * {@code UISyncManager} identifies a sync value on the wire by <b>the order it was registered in</b>,
 * not by its name. Both sides therefore have to register the same values in the same sequence, or
 * every packet decodes into the wrong slot — and it fails quietly, because the bytes still parse.
 *
 * <p>For a graph that means: <b>build the same tree on both sides.</b> Do not branch the structure on
 * {@code ldlib2_ui_side}, do not skip an element because the server has nothing to show in it, and do
 * not add sync values from inside a client-only handler. Branch on the side for <em>appearance</em>,
 * which is exactly what LDLib2's own guards already do.</p>
 *
 * <h2>Where the value comes from</h2>
 * Two ways, and they compose:
 * <ul>
 *   <li>Wire something into {@code source}. The sending side then pulls that expression every tick —
 *       the value tracks whatever it is computed from, with nothing else to write.</li>
 *   <li>Leave {@code source} unwired and push with {@code ldlib2_ui_sync_set}. Right when the value
 *       changes at a moment rather than continuously.</li>
 * </ul>
 *
 * <p>The receiving side gets {@code onReceived} and the {@code value} output. A value never arrives on
 * the side that sent it.</p>
 */
public final class UISyncNodes {

    private static final String GROUP = "ui/sync";

    /** Payload key for a received value. */
    private static final String VALUE = "value";

    private UISyncNodes() {
    }

    /** Which way a synced value travels. */
    public enum Direction implements StringRepresentable {
        /** Server to client. The usual one: the server owns the truth, the client displays it. */
        SERVER_TO_CLIENT("s2c"),
        /** Client to server. For what the player edits — a text field, a slider. */
        CLIENT_TO_SERVER("c2s"),
        /** Both ways. Convenient, and the shape that can loop if both sides write; see the notes. */
        BOTH("both");

        private final String name;

        Direction(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * Declares a synced value on an element.
     *
     * <p>{@code valueType} must be a concrete type — the sync layer needs an accessor to write bytes
     * with, and {@code Unknown} has none. The node refuses with {@code ok = false} rather than
     * throwing, so a half-configured graph still builds its UI.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_sync_value", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Declare extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_sync_value.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onReceived;

        @Option public Direction direction = Direction.SERVER_TO_CLIENT;
        @Option public SyncStrategy strategy = SyncStrategy.CHANGED_PERIODIC;
        @InputPort public UIElement element;
        @InputPort public String name = "value";
        @OutputPort public SyncValue<?> syncValue;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            TypeHandle type = valueType(this);
            context.addInputPort("source", type);
            context.addOutputPort("value", type);
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            TypeHandle handle = valueType(this);
            Type javaType = handle.resolve();
            if (element == null || TypeHandles.UNKNOWN.equals(handle) || javaType == null) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            String name = ctx.getInput("name", String.class, "value");
            SyncValue<Object> value = new SyncValue<>(
                    name == null || name.isBlank() ? "value" : name, javaType, handle.getDefaultValue());

            Direction direction = ctx.getOption("direction", Direction.class, Direction.SERVER_TO_CLIENT);
            applyDirection(value, direction == null ? Direction.SERVER_TO_CLIENT : direction);
            SyncStrategy strategy = ctx.getOption("strategy", SyncStrategy.class, SyncStrategy.CHANGED_PERIODIC);
            value.setSyncStrategy(strategy == null ? SyncStrategy.CHANGED_PERIODIC : strategy);

            installProvider(ctx, value);
            var trampoline = UICallbacks.arm(ctx, "onReceived");
            // An empty payload rather than a placeholder when the incoming value is null: substituting
            // "" would make a String-typed sync value report an empty string where it should report
            // nothing, and Map.of refuses a null value outright.
            value.addListener(v -> trampoline.fire(v == null ? Map.of() : Map.of(VALUE, v)));

            element.addSyncValue(value);
            UIActions.produce(ctx, "syncValue", value);
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "syncValue");
            // The received value comes from the dispatch payload while a handler runs, and from the
            // sync value itself otherwise — so reading it outside a handler still answers "what is it
            // now" rather than "what arrived last".
            Object received = UICallbacks.payload(ctx, VALUE);
            if (received == null) {
                Object stored = UICallbacks.state(ctx).get("syncValue");
                if (stored instanceof SyncValue<?> value) received = value.getValue();
            }
            ctx.setOutput(VALUE, received);
            UICallbacks.publishRegistered(ctx);
        }

        /**
         * Turns a direction into the pair of flags LDLib2 actually uses, for <em>this</em> side.
         *
         * <p>{@code toSync} means "this side transmits"; {@code acceptSync} means "this side accepts
         * what arrives". They are not symmetric knobs on one channel — a value that accepts sync it
         * was not meant to receive throws inside {@code readSyncData}, and the manager then reads the
         * <em>rest</em> of that packet from the wrong offset. Getting these right is why the direction
         * is an explicit option rather than something inferred.</p>
         */
        private static void applyDirection(SyncValue<?> value, Direction direction) {
            boolean client = LDLib2.isRemote();
            boolean sends = switch (direction) {
                case SERVER_TO_CLIENT -> !client;
                case CLIENT_TO_SERVER -> client;
                case BOTH -> true;
            };
            value.setToSync(sends);
            value.setAcceptSync(direction == Direction.BOTH || !sends);
        }

        /**
         * Wires the {@code source} expression up as the value provider, when something is wired to it.
         *
         * <p>The provider is called from {@code UISyncManager.tick()}, outside any flow, so it re-pulls
         * through the executor directly. The cache clear before the pull is what makes the value
         * <em>track</em> its expression instead of reporting whatever was memoised while the UI was
         * being built — the same reason a callback dispatch clears it. It is cheap: {@code clearCache}
         * only touches slots written since the last clear.</p>
         */
        private static void installProvider(ExecContext ctx, SyncValue<Object> value) {
            // connectedSourceNodes rather than a null check on the pulled value: an unwired port and a
            // port wired to something that currently evaluates to null are different situations, and
            // only the first one means "there is no expression here".
            if (ctx.connectedSourceNodes("source").isEmpty()) return;
            var node = ctx.getNode();
            if (node == null) return;
            PortModel source = node.getInputsById().get("source");
            if (source == null) return;
            var executor = ctx.getExecutor();
            value.setValueProvider(() -> {
                executor.clearCache();
                return executor.pullInputValue(source);
            });
        }
    }

    /** The value a sync value currently holds, on whichever side asks. */
    @NodeAttribute(name = "ldlib2_ui_sync_get", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Get extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_sync_get.tooltip");
        }

        @InputPort public SyncValue<?> syncValue;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addOutputPort("value", valueType(this));
        }

        @Override
        public void evaluate(EvalContext ctx) {
            SyncValue<?> value = ctx.getInput("syncValue", SyncValue.class, null);
            ctx.setOutput("value", value == null ? null : value.getValue());
            ctx.setOutput("ok", value != null);
        }
    }

    /**
     * Writes a sync value, so the other side sees it on the next tick.
     *
     * <p>Only meaningful on the side the value's direction says transmits — writing a server-to-client
     * value on the client changes it locally and is then overwritten by the next packet. The node does
     * not refuse that, because {@code BOTH} makes it legitimate; it is simply worth knowing.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_sync_set", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Set extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_sync_set.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public SyncValue<?> syncValue;
        @InputPort public boolean markChanged = true;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            context.addInputPort("value", valueType(this));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void execute(ExecContext ctx) {
            SyncValue value = ctx.getInput("syncValue", SyncValue.class, null);
            if (value == null) {
                UIActions.done(ctx, false);
                return;
            }
            try {
                value.setValue(ctx.getInputRaw("value"));
            } catch (ClassCastException e) {
                UIActions.done(ctx, false);
                return;
            }
            // markAsChanged forces a send even when the accessor decides nothing changed — needed for
            // a mutable value (a list, a tag) whose contents were edited in place, since the dirty
            // check compares against the very instance that was mutated.
            if (ctx.getBool("markChanged", true)) value.markAsChanged();
            UIActions.done(ctx, true);
        }
    }

    // ---- shared option plumbing --------------------------------------------------------------
    //
    // Kept as thin named delegates rather than inlining UIActions at every call site: the RPC and
    // binding nodes reach for these too, and one name for "this node's picked value type" reads
    // better in their port definitions than the generic option lookup.

    static TypeHandle valueType(AnnotatedNode node) {
        return UIActions.optionTypeHandle(node, "valueType");
    }

    static List<TypeHandle> supportedTypes(AnnotatedNode node) {
        return UIActions.supportedTypes(node);
    }
}
