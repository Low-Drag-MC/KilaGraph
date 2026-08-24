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
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBinding;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.function.Consumers;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Two-way data binding: connecting an element's own value straight to a server-side source.
 *
 * <h2>Why this exists beside {@code ldlib2_ui_sync_value}</h2>
 * A sync value is a slot the graph reads and writes. A <b>binding</b> goes one step further and hands
 * that slot to the element itself, through the {@code IBindable} interface LDLib2's own widgets are
 * built around. Once bound, an {@code ItemSlot} shows the server's stack, a {@code TextField} sends
 * what the player types, and a {@code ProgressBar} tracks a machine — with no handler in between.
 *
 * <p>That is the only way to reach some of them: an {@code ItemSlot}'s real contents are not a
 * {@code @Configurable} field and cannot be set with {@code ldlib2_ui_set_property}. The binding
 * <em>is</em> the API.</p>
 *
 * <h2>Which side provides what</h2>
 * <ul>
 *   <li><b>Server</b> — the {@code serverValue} input. Pulled every tick, so the binding tracks
 *       whatever expression is wired there.</li>
 *   <li><b>Client</b> — the bound element. {@code ldlib2_ui_bind} makes the element the client-side
 *       source, so what it displays and what the player edits are the same slot.</li>
 * </ul>
 *
 * <p>{@code onReceived} fires on whichever side an update lands on, with the new value. That is where
 * a graph reacts to a player edit — the binding itself only moves the value, it does not act on it.</p>
 *
 * <h2>Same registration-order rule</h2>
 * A binding registers a sync value, so it counts toward the ordering constraint that
 * {@link UISyncNodes} describes. Build the same tree on both sides.
 */
public final class UIBindingNodes {

    private static final String GROUP = "ui/sync";

    private static final String VALUE = "value";

    private UIBindingNodes() {
    }

    /**
     * Builds a binding. Attach it to an element with {@code ldlib2_ui_bind}.
     *
     * <p>Separate from the bind step because one binding can legitimately be attached to more than
     * one element — a label and a progress bar showing the same number — and because the element is
     * often selected out of a tree that is built later.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_binding", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Create extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_binding.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onReceived;

        @Option public SyncStrategy s2cStrategy = SyncStrategy.CHANGED_PERIODIC;
        @Option public SyncStrategy c2sStrategy = SyncStrategy.CHANGED_PERIODIC;
        @InputPort public String name = "binding";
        @OutputPort public IBinding<?> binding;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            context.addOption("valueType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(
                            () -> UISyncNodes.supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            TypeHandle type = UISyncNodes.valueType(this);
            context.addInputPort("serverValue", type);
            context.addOutputPort("value", type);
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            TypeHandle handle = UISyncNodes.valueType(this);
            Type javaType = handle.resolve();
            if (TypeHandles.UNKNOWN.equals(handle) || javaType == null) {
                // Same reason as a sync value: the sync layer needs an accessor to write bytes with.
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            String name = ctx.getInput("name", String.class, "binding");
            var trampoline = UICallbacks.arm(ctx, "onReceived");

            // The setter is a no-op on purpose: "what to do when a value arrives" is the graph's
            // decision, and it makes it in the onReceived chain, where it has the whole node set
            // available. A setter here could only write into one place.
            var builder = DataBindingBuilder.create(serverGetter(ctx), Consumers.<Object>nop())
                    .syncType(javaType)
                    .initialValue(handle.getDefaultValue())
                    .s2cStrategy(strategy(ctx, "s2cStrategy"))
                    .c2sStrategy(strategy(ctx, "c2sStrategy"))
                    .name(name == null || name.isBlank() ? "binding" : name);

            // build(isRemote) rather than build(): the no-arg form infers the side from the current
            // thread and throws when it is neither, which a game test's server thread can be.
            var binding = builder.build(LDLib2.isRemote());
            // Listener attached after build, not through a builder hook: it has to work on both
            // sides, and the client's slot is claimed by ldlib2_ui_bind — installing a remote setter
            // here would stop the element from becoming the client-side source at all.
            binding.registerListener(v -> trampoline.fire(payloadOf(v)));
            UIActions.produce(ctx, "binding", binding);
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "binding");
            Object received = UICallbacks.payload(ctx, VALUE);
            if (received == null && UICallbacks.state(ctx).get("binding") instanceof IBinding<?> b) {
                received = b.getSyncValue().getValue();
            }
            ctx.setOutput(VALUE, received);
            UICallbacks.publishRegistered(ctx);
        }

        /**
         * The server side's value, re-pulled from the graph each time the sync manager asks.
         *
         * <p>Same clear-then-pull as a sync value's provider, and for the same reason: without the
         * clear the binding would report whatever was memoised while the UI was being built and never
         * change again.</p>
         */
        private static java.util.function.Supplier<Object> serverGetter(ExecContext ctx) {
            var node = ctx.getNode();
            PortModel source = node == null ? null : node.getInputsById().get("serverValue");
            if (source == null) return () -> null;
            var executor = ctx.getExecutor();
            return () -> {
                executor.clearCache();
                return executor.pullInputValue(source);
            };
        }

        /** A payload map even for a null value, so {@code onReceived} still fires on a cleared slot. */
        private static Map<String, Object> payloadOf(Object value) {
            return value == null ? Map.of() : Map.of(VALUE, value);
        }

        private static SyncStrategy strategy(ExecContext ctx, String optionId) {
            SyncStrategy strategy = ctx.getOption(optionId, SyncStrategy.class, SyncStrategy.CHANGED_PERIODIC);
            return strategy == null ? SyncStrategy.CHANGED_PERIODIC : strategy;
        }
    }

    /**
     * Attaches a binding to an element, making the element the client-side end of it.
     *
     * <p>Only works on an element that implements {@code IBindable} — {@code ItemSlot},
     * {@code FluidSlot}, {@code ProgressBar}, {@code Toggle}, {@code Slider}, {@code TextField},
     * {@code Selector}, {@code Label} and the rest of the value-carrying set. Anything else reports
     * {@code ok = false}; use a sync value and a handler instead.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_bind", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Bind extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_bind.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public IBinding<?> binding;
        @OutputPort public UIElement out;
        @OutputPort public boolean ok;

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            IBinding binding = ctx.getInput("binding", IBinding.class, null);
            ctx.setOutput("out", element);
            if (!(element instanceof IBindable bindable) || binding == null) {
                UIActions.done(ctx, false);
                return;
            }
            try {
                bindable.bind(binding);
            } catch (ClassCastException e) {
                // The binding's value type does not match what the element holds. A normal authoring
                // mistake given the type comes from an option, so refuse rather than throw.
                UIActions.done(ctx, false);
                return;
            }
            UIActions.done(ctx, true);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", UIActions.element(ctx, "element"));
        }
    }

    /** Detaches a binding, so the element stops mirroring it. */
    @NodeAttribute(name = "ldlib2_ui_unbind", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unbind extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_unbind.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public IBinding<?> binding;
        @OutputPort public boolean ok;

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            IBinding binding = ctx.getInput("binding", IBinding.class, null);
            if (!(element instanceof IBindable bindable) || binding == null) {
                UIActions.done(ctx, false);
                return;
            }
            bindable.unbind(binding);
            UIActions.done(ctx, true);
        }
    }
}
