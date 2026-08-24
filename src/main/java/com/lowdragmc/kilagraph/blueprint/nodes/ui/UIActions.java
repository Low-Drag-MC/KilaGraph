package com.lowdragmc.kilagraph.blueprint.nodes.ui;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared rules for the {@code ldlib2_ui_*} nodes.
 *
 * <h2>Every mutating node reports {@code ok} and flows on</h2>
 * Same contract as {@link com.lowdragmc.kilagraph.blueprint.nodes.mc.action.McActions}, and for the
 * same reason: a UI graph runs on both sides, and half of what these nodes touch is client-only.
 * {@code layout()} and {@code style(Consumer)} are outright no-ops on the server (LDLib2 guards
 * them), a {@code ModularUI} may not exist yet while a tree is still being assembled, and an element
 * may simply not implement the interface a node needs. None of that is exceptional. A node that
 * threw would make the same graph crash on one side and work on the other, so instead each one
 * reports {@code ok = false} and continues; a graph that cares can branch on it.
 *
 * <h2>Which side does what</h2>
 * Structure — children, ids, classes, event listeners, sync values, RPC registration — behaves
 * identically on both sides, and has to: {@code UISyncManager} identifies sync values by their
 * <em>registration order</em>, so the client and the server must walk the same graph and register
 * the same things in the same sequence or the packets decode against the wrong values. Appearance is
 * client-only. {@code ldlib2_ui_side} exists so a graph can branch when it needs to, but the right
 * default is to build the same tree everywhere and let the guards drop what the server cannot use.
 */
public final class UIActions {

    private UIActions() {
    }

    /** Report the outcome and continue. Every mutating UI node ends this way, failures included. */
    public static void done(ExecContext ctx, boolean ok) {
        ctx.setOutput("ok", ok);
        ctx.flow("next");
    }

    /**
     * Publishes something this node <em>constructed</em>, and remembers it.
     *
     * <h2>Why every UI constructor is an exec node</h2>
     * A node that makes a new {@code UIElement} is not a pure function of its inputs: the element has
     * identity. Two things break if it is treated as one.
     *
     * <ul>
     *   <li><b>Event handlers would get a different element.</b> A dispatch clears the pull cache
     *       before re-entering the graph (it has to — see {@link com.lowdragmc.kilagraph.graph.ui.UICallbacks}),
     *       so a pure-data constructor would run again and hand the handler a <em>freshly built</em>
     *       button rather than the one the player just clicked. The graph would look right and do
     *       nothing.</li>
     *   <li><b>Loops would get the same element.</b> Memoising in node state to fix the first problem
     *       breaks the second: a {@code ForEach} building one row per item would produce one row and
     *       reuse it, because node state deliberately survives the per-iteration cache clear.</li>
     * </ul>
     *
     * <p>Being an exec node resolves both, because it makes "when is a new one made" an explicit part
     * of the graph rather than a consequence of when something happened to be pulled: the constructor
     * runs exactly when the flow reaches it — once during a build, once per iteration inside a loop —
     * and {@link #republish} serves that same instance to every later pull.</p>
     *
     * <p>Nodes that only <em>look something up</em> stay pure data: a stylesheet fetched from
     * {@code StylesheetManager} or a template from the resource library is the same shared instance
     * every time, so re-pulling it is free and cannot surprise anyone.</p>
     */
    public static <T> T produce(ExecContext ctx, String outputId, @Nullable T value) {
        ctx.state().put(outputId, value);
        ctx.setOutput(outputId, value);
        return value;
    }

    /**
     * Republishes what {@link #produce} made, on the pull side. Call from {@code evaluate}.
     *
     * <p>The same convention the loop nodes use for their per-iteration outputs, and it works for the
     * same reason: node state outlives the pull cache.</p>
     */
    public static void republish(EvalContext ctx, String... outputIds) {
        var node = ctx.getNode();
        if (node == null) return;
        var state = ctx.getExecutor().nodeState(node.getUid());
        for (String id : outputIds) {
            ctx.setOutput(id, state.get(id));
        }
    }

    /** The element on {@code inputId}, or null. */
    @Nullable
    public static UIElement element(ExecContext ctx, String inputId) {
        return ctx.getInput(inputId, UIElement.class, null);
    }

    /** The element on {@code inputId}, or null — pull side. */
    @Nullable
    public static UIElement element(EvalContext ctx, String inputId) {
        return ctx.getInput(inputId, UIElement.class, null);
    }

    /**
     * Reads a list-typed input as {@code List<T>}, keeping only the entries that really are {@code T}.
     *
     * <p>Filtering rather than casting because {@code LIST} is a single erased wire type in this graph
     * — the element type lives on the producing node as an option, and nothing stops a graph wiring a
     * list of strings into a port that wants elements. Dropping the strangers is the same lenience the
     * rest of the graph applies at consumption time.</p>
     */
    public static <T> List<T> list(ExecContext ctx, String inputId, Class<T> type) {
        return filter(ctx.getInputRaw(inputId), type);
    }

    /** {@link #list(ExecContext, String, Class)} on the pull side. */
    public static <T> List<T> list(EvalContext ctx, String inputId, Class<T> type) {
        return filter(ctx.getInputRaw(inputId), type);
    }

    // ---- option reading ----------------------------------------------------------------------
    //
    // Several UI nodes let the user pick a type, an element type or a property name, and every one of
    // them stores that pick as a String option. These three read the *live* option value rather than
    // the Java field, per docs/CONVENTIONS.md §5, and are shared so the seven nodes that need them
    // cannot drift apart on how a missing or blank option is treated.

    /** The current value of a String-typed option, or {@code fallback} when unset. */
    public static String optionString(AnnotatedNode node, String optionId, String fallback) {
        var option = node.getNodeOptionById(optionId);
        if (option == null) return fallback;
        // Read through Object and toString rather than tryGetValue(String.class): see
        // docs/CONVENTIONS.md §6 for the overload trap this avoids.
        Object value = option.tryGetValue(Object.class).result().orElse(null);
        return value == null ? fallback : value.toString();
    }

    /**
     * The {@link TypeHandle} named by a type-picker option, or {@code UNKNOWN} while nothing is picked.
     *
     * <p>{@code UNKNOWN} is a meaningful answer, not a failure: it is what makes a value pin
     * anything-goes, and the nodes that genuinely cannot work without a concrete type (a sync value,
     * an RPC argument) check for it and refuse.</p>
     */
    public static TypeHandle optionTypeHandle(AnnotatedNode node, String optionId) {
        String id = optionString(node, optionId, TypeHandles.UNKNOWN.getIdentification());
        return id.isEmpty() ? TypeHandles.UNKNOWN : TypeHandle.create(id);
    }

    /** The types this node's graph offers, for a type-picker option's candidate list. */
    public static List<TypeHandle> supportedTypes(AnnotatedNode node) {
        var model = node.getNodeModel() == null ? null : node.getNodeModel().getGraphModel();
        return model instanceof GraphModel gm ? gm.getSupportTypes() : List.of();
    }

    private static <T> List<T> filter(@Nullable Object raw, Class<T> type) {
        if (!(raw instanceof List<?> source)) return List.of();
        var result = new ArrayList<T>(source.size());
        for (Object o : source) {
            if (type.isInstance(o)) result.add(type.cast(o));
        }
        return result;
    }
}
