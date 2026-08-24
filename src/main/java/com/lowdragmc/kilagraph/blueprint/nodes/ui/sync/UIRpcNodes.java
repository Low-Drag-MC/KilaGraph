package com.lowdragmc.kilagraph.blueprint.nodes.ui.sync;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.ui.UIActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.ui.UICallbacks;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Calling across the network: "something happened, do this on the other side."
 *
 * <h2>RPC versus a sync value</h2>
 * A sync value answers "what is this now" and is mirrored continuously. An RPC is a <em>moment</em>:
 * the player pressed the button, so take the item. Use a sync value for state, an RPC for events. A
 * UI usually has a few of each.
 *
 * <h2>Two flavours, and which to reach for</h2>
 * <ul>
 *   <li><b>Messages</b> ({@code ldlib2_ui_on_message} / {@code ldlib2_ui_send_message}) — a name and
 *       a {@code CompoundTag}. No configuration at all, and the graph already has a full set of NBT
 *       nodes to build the payload with. <b>Start here.</b></li>
 *   <li><b>Typed RPCs</b> ({@code ldlib2_ui_rpc_define} / {@code ldlib2_ui_rpc_send}) — up to four
 *       typed arguments and an optional typed return. Worth the setup when the pins being typed
 *       matters, or when you need an answer back.</li>
 * </ul>
 *
 * <h2>⚠ Both sides must register the same RPCs, in the same order</h2>
 * The same rule as sync values, for the same reason — the wire carries a registration index, not a
 * name. See {@link UISyncNodes}.
 *
 * <h2>The return trip is asynchronous</h2>
 * {@code ldlib2_ui_rpc_send} flows {@code next} immediately; {@code onReturn} fires when the answer
 * packet arrives, at least a tick later. A graph that needs the answer must continue from
 * {@code onReturn}, not from {@code next}.
 */
public final class UIRpcNodes {

    private static final String GROUP = "ui/sync";

    /** The most arguments a typed RPC can carry. Beyond this, send a {@code CompoundTag}. */
    private static final int MAX_ARGS = 4;

    private UIRpcNodes() {
    }

    /**
     * Declares an RPC on an element and runs a chain whenever the other side calls it.
     *
     * <p>To answer, end the {@code onCall} chain at {@code ldlib2_ui_rpc_return}. Without one — or
     * with {@code returnType} left as {@code Unknown} — the call is one-way.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_rpc_define", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Define extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_rpc_define.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onCall;

        @InputPort public UIElement element;
        @OutputPort public RPCEmitter rpc;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            defineArgOptions(this, context);
            context.addOption("returnType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(
                            () -> UISyncNodes.supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            int count = argCount(this);
            for (int i = 1; i <= count; i++) {
                context.addOutputPort("arg" + i, argType(this, i));
            }
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            if (element == null) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            int count = argCount(this);
            var builder = RPCEventBuilder.create();
            for (int i = 1; i <= count; i++) {
                TypeHandle handle = argType(this, i);
                Type type = handle.resolve();
                if (type == null || TypeHandles.UNKNOWN.equals(handle)) {
                    // An untyped argument has no accessor, so the event could not encode it. Refusing
                    // to register at all beats registering a shape the other side cannot decode —
                    // which, given that ids are positional, would corrupt every RPC after it.
                    UICallbacks.markRegistered(ctx, false);
                    ctx.flow("next");
                    return;
                }
                builder.arg(type, handle.getDefaultValue());
            }
            TypeHandle returnHandle = handleOf(this, "returnType");
            boolean hasReturn = !TypeHandles.UNKNOWN.equals(returnHandle) && returnHandle.resolve() != null;
            if (hasReturn) builder.returnType(returnHandle.resolve(), returnHandle.getDefaultValue());

            var trampoline = UICallbacks.arm(ctx, "onCall");
            // The executor has to name the very event it belongs to, so that ldlib2_ui_rpc_return can
            // scope its answer to this call — and the event does not exist until build() returns.
            // A one-slot holder closes that cycle; it is written before anything can invoke it.
            var self = new RPCEvent[1];
            RPCEvent event = builder.executor(args -> {
                Map<String, Object> payload = new HashMap<>();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null) payload.put("arg" + (i + 1), args[i]);
                }
                UIRpcCalls.open(self[0]);
                Object answered;
                try {
                    trampoline.fire(payload);
                } finally {
                    // close() in a finally: an unbalanced open would leave a stale frame that the
                    // next call's rpc_return would answer instead.
                    answered = UIRpcCalls.close();
                }
                return answered;
            }).build();
            self[0] = event;

            RPCEmitter emitter = element.addRPCEvent(event);
            UIActions.produce(ctx, "rpc", emitter);
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            UIActions.republish(ctx, "rpc");
            for (int i = 1; i <= argCount(this); i++) {
                ctx.setOutput("arg" + i, UICallbacks.payload(ctx, "arg" + i));
            }
            UICallbacks.publishRegistered(ctx);
        }
    }

    /**
     * Answers the RPC call currently being handled.
     *
     * <p>Leave {@code rpc} unwired to answer the innermost call, which is what a handler wants
     * whenever it is not itself nested inside another. Wiring it is only needed to disambiguate.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_rpc_return", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Return extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_rpc_return.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public RPCEmitter rpc;
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
            context.addInputPort("value", handleOf(this, "valueType"));
        }

        @Override
        public void execute(ExecContext ctx) {
            RPCEmitter emitter = ctx.getInput("rpc", RPCEmitter.class, null);
            UIActions.done(ctx, UIRpcCalls.answer(
                    emitter == null ? null : emitter.event(), ctx.getInputRaw("value")));
        }
    }

    /**
     * Calls an RPC on the other side.
     *
     * <p>The argument types must match the defining node's, position for position — the wire carries
     * no names. Getting one wrong throws on the receiving side rather than here.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_rpc_send", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Send extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_rpc_send.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onReturn;

        @InputPort public RPCEmitter rpc;
        @OutputPort public boolean ok;

        @Override
        protected void onDefineExtraOptions(IOptionDefinitionContext context) {
            defineArgOptions(this, context);
            context.addOption("returnType", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(
                            () -> UISyncNodes.supportedTypes(this)))
                    .build();
        }

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext context) {
            int count = argCount(this);
            for (int i = 1; i <= count; i++) {
                context.addInputPort("arg" + i, argType(this, i));
            }
            context.addOutputPort("result", handleOf(this, "returnType"));
        }

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            RPCEmitter emitter = ctx.getInput("rpc", RPCEmitter.class, null);
            if (emitter == null) {
                ctx.setOutput("ok", false);
                ctx.flow("next");
                return;
            }
            int count = argCount(this);
            Object[] args = new Object[count];
            for (int i = 0; i < count; i++) {
                args[i] = ctx.getInputRaw("arg" + (i + 1));
            }
            var trampoline = UICallbacks.arm(ctx, "onReturn");
            boolean sent = emitter.send(
                    value -> trampoline.fire(value == null ? Map.of() : Map.of("result", value)), args);
            ctx.setOutput("ok", sent);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("result", UICallbacks.payload(ctx, "result"));
        }
    }

    // ---- messages ----------------------------------------------------------------------------

    /**
     * Runs a chain when the other side sends a named message to this element.
     *
     * <p>The zero-configuration path: no types to line up, and the payload is a {@code CompoundTag}
     * the graph's NBT nodes can take apart. LDLib2 registers one shared RPC per element for all of
     * its messages, so adding a second listener does not add a second wire id.</p>
     */
    @NodeAttribute(name = "ldlib2_ui_on_message", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class OnMessage extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_on_message.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;
        @ExecOutputPort public ExecutionFlow onMessage;

        @InputPort public UIElement element;
        @InputPort public String name = "message";
        @OutputPort public CompoundTag data;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            if (UICallbacks.relayDispatch(ctx)) return;
            UIElement element = UIActions.element(ctx, "element");
            String name = ctx.getInput("name", String.class, "message");
            if (element == null || name == null || name.isBlank()) {
                UICallbacks.markRegistered(ctx, false);
                ctx.flow("next");
                return;
            }
            var trampoline = UICallbacks.arm(ctx, "onMessage");
            element.onMessage(name, payload ->
                    trampoline.fire(Map.of("data", payload == null ? new CompoundTag() : payload)));
            UICallbacks.markRegistered(ctx, true);
            ctx.flow("next");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            Object data = UICallbacks.payload(ctx, "data");
            ctx.setOutput("data", data instanceof CompoundTag tag ? tag : new CompoundTag());
            UICallbacks.publishRegistered(ctx);
        }
    }

    /** Sends a named message with an NBT payload to the same element on the other side. */
    @NodeAttribute(name = "ldlib2_ui_send_message", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SendMessage extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.ldlib2_ui_send_message.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public UIElement element;
        @InputPort public String name = "message";
        @InputPort public CompoundTag data;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            UIElement element = UIActions.element(ctx, "element");
            String name = ctx.getInput("name", String.class, "message");
            if (element == null || name == null || name.isBlank()) {
                UIActions.done(ctx, false);
                return;
            }
            CompoundTag data = ctx.getInput("data", CompoundTag.class, null);
            element.sendMessage(name, data == null ? new CompoundTag() : data);
            UIActions.done(ctx, true);
        }
    }

    // ---- shared option plumbing --------------------------------------------------------------

    /**
     * Declares {@code argCount} and the four argument-type pickers.
     *
     * <p>All four always exist, and {@code argCount} only decides how many <em>ports</em> appear.
     * The alternative — deriving the option count from {@code argCount} — cannot work here: options
     * are not registered on the model until {@code onDefineOptions} returns, so reading one from
     * inside the same pass would see nothing on the first definition and the pickers would vanish on
     * reload. Four unused pickers in the inspector is the cheaper problem.</p>
     */
    private static void defineArgOptions(AnnotatedNode node, IOptionDefinitionContext context) {
        context.addOption("argCount", Integer.class).withDefaultValue(0).build();
        for (int i = 1; i <= MAX_ARGS; i++) {
            context.addOption("arg" + i + "Type", String.class)
                    .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                    .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(
                            () -> UISyncNodes.supportedTypes(node)))
                    .showInInspectorOnly()
                    .build();
        }
    }

    private static int argCount(AnnotatedNode node) {
        var option = node.getNodeOptionById("argCount");
        if (option == null) return 0;
        Object value = option.tryGetValue(Object.class).result().orElse(null);
        // Clamped rather than trusted: the option is a plain int the user can type into, and a value
        // above MAX_ARGS would define ports the arg-type options cannot describe.
        return value instanceof Number n ? Math.clamp(n.intValue(), 0, MAX_ARGS) : 0;
    }

    private static TypeHandle argType(AnnotatedNode node, int index) {
        return handleOf(node, "arg" + index + "Type");
    }

    private static TypeHandle handleOf(AnnotatedNode node, String optionId) {
        return UIActions.optionTypeHandle(node, optionId);
    }

}
