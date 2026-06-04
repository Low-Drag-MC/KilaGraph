package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-evaluation context: passes graph state into a single node's {@code evaluate(...)} call.
 * <p>
 * Three ways to read values:
 * <ul>
 *   <li>{@link #getInput} — input port; resolves wire-pull or embedded-constant value.</li>
 *   <li>{@link #getOption} — node option; never wire-resolved.</li>
 *   <li>{@link #setOutput} — write an output port value (cached by the executor).</li>
 * </ul>
 *
 * <p>Coercion is liberal. The "with default" overloads never throw — types in the graph are
 * declared loosely (option-driven, UNKNOWN, etc.) and each node is responsible for deciding
 * what to do when an upstream value doesn't fit. Coercion rules applied in order:</p>
 * <ol>
 *   <li>{@code null} or not a {@code Number}/{@code String}/already-of-type → returns {@code null} or the default.</li>
 *   <li>Target {@code Number} (or wrapper) ← any {@code Number}: use {@code Number.xxxValue()}.</li>
 *   <li>Target {@code String}: use {@code toString()}.</li>
 *   <li>Target {@code Boolean} ← any {@code Boolean}: pass-through. Numbers / strings / null
 *       do <em>not</em> auto-coerce to boolean — return the default.</li>
 *   <li>Otherwise return the default. ({@link #getInput(String, Class)} no-default form throws
 *       {@link TypeMismatchException} instead.)</li>
 * </ol>
 *
 * <p>Single-threaded by contract today; the node instance never owns evaluation state, so a
 * future async/parallel executor can hand each evaluation its own {@code EvalContext}.</p>
 */
public final class EvalContext {
    private final GraphExecutor executor;
    private final NodeModel node;
    final Map<String, Object> outputs = new HashMap<>();   // populated by setOutput, flushed by executor

    EvalContext(GraphExecutor executor, NodeModel node) {
        this.executor = executor;
        this.node = node;
    }

    // ---- input port reads --------------------------------------------------------------------

    /** Typed read with default. Never throws — returns {@code defaultIfMissing} on null / unrepresentable. */
    public <T> T getInput(String inputId, Class<T> type, T defaultIfMissing) {
        Object raw = pullRaw(inputId);
        T t = coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    /** Typed read; throws {@link TypeMismatchException} if the value is null or unrepresentable. */
    public <T> T getInput(String inputId, Class<T> type) {
        Object raw = pullRaw(inputId);
        T t = coerce(raw, type);
        if (t == null) {
            throw new TypeMismatchException("Input '" + inputId + "' on " + node.getUid()
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
    }

    /** Untyped read; useful for dynamic ports. */
    public Optional<Object> getInput(String inputId) {
        return Optional.ofNullable(pullRaw(inputId));
    }

    @Nullable
    private Object pullRaw(String inputId) {
        PortModel pm = node.getInputsById().get(inputId);
        if (pm == null) throw new IllegalArgumentException("No input port '" + inputId + "' on " + node.getUid());
        return executor.pullInput(pm, Object.class);
    }

    // ---- option reads ------------------------------------------------------------------------

    public <T> T getOption(String optionId, Class<T> type, T defaultIfMissing) {
        Object raw = rawOption(optionId);
        T t = coerce(raw, type);
        return t != null ? t : defaultIfMissing;
    }

    public <T> T getOption(String optionId, Class<T> type) {
        Object raw = rawOption(optionId);
        T t = coerce(raw, type);
        if (t == null) {
            throw new TypeMismatchException("Option '" + optionId + "' on " + node.getUid()
                    + " is null or not assignable to " + type.getName() + " (got " + raw + ")");
        }
        return t;
    }

    public Optional<Object> getOption(String optionId) {
        return Optional.ofNullable(rawOption(optionId));
    }

    @Nullable
    private Object rawOption(String optionId) {
        INodeOption opt = node.getNodeOptionById(optionId);
        if (opt == null) return null;
        return opt.tryGetValue(Object.class).result().orElse(null);
    }

    // ---- output writes -----------------------------------------------------------------------

    public void setOutput(String outputId, Object value) {
        outputs.put(outputId, value);
    }

    // ---- meta --------------------------------------------------------------------------------

    public NodeModel getNode() {
        return node;
    }

    public GraphExecutor getExecutor() {
        return executor;
    }

    // ---- coercion ----------------------------------------------------------------------------

    /**
     * Liberal coercion used by {@link #getInput} / {@link #getOption} and exposed for nodes that
     * want to apply the same rules themselves (e.g. {@code CastNode}). See class javadoc for the
     * full coercion table.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T coerce(@Nullable Object raw, Class<T> type) {
        if (raw == null || type == null) return null;
        if (type.isInstance(raw)) return (T) raw;

        // Number polymorphism — works uniformly across all java.lang.Number subclasses including
        // BigInteger / BigDecimal. We probe by the *destination* wrapper class.
        if (raw instanceof Number n) {
            if (type == Integer.class || type == int.class)   return (T) Integer.valueOf(n.intValue());
            if (type == Long.class    || type == long.class)  return (T) Long.valueOf(n.longValue());
            if (type == Float.class   || type == float.class) return (T) Float.valueOf(n.floatValue());
            if (type == Double.class  || type == double.class)return (T) Double.valueOf(n.doubleValue());
            if (type == Short.class   || type == short.class) return (T) Short.valueOf(n.shortValue());
            if (type == Byte.class    || type == byte.class)  return (T) Byte.valueOf(n.byteValue());
        }

        // Universal toString — everything is convertible to a String.
        if (type == String.class) return (T) raw.toString();

        // Note: we do NOT auto-coerce to Boolean from non-Boolean values. Truthiness is too
        // ambiguous (string "false" / number 0 / etc.) — leave it to the node.
        return null;
    }
}
