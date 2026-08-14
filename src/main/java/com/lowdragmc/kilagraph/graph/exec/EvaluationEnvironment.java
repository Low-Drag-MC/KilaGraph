package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.IVariable;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Per-evaluation context for things the executor cannot derive from the graph alone:
 * graph-variable live values (via {@link VariableStore}) and an optional RNG seed for
 * {@code Random}-class nodes.
 *
 * <p>Variable resolution rule: if the {@link VariableStore} contains an entry for the variable's
 * name, that value wins (even if {@code null}). Otherwise fall back to
 * {@link IVariable#tryGetDefaultValue(java.lang.reflect.Type)} so unset INPUTs still resolve to
 * something sane.</p>
 */
public class EvaluationEnvironment {

    /** Default empty env — no preloaded variables, unseeded RNG. */
    public static final EvaluationEnvironment EMPTY = new EvaluationEnvironment();

    private final VariableStore variables;
    private final OptionalLong seed;

    public EvaluationEnvironment() {
        this(new VariableStore(), OptionalLong.empty());
    }

    public EvaluationEnvironment(VariableStore variables, OptionalLong seed) {
        this.variables = Objects.requireNonNull(variables);
        this.seed = Objects.requireNonNull(seed);
    }

    /** Fresh, empty env (mutable variable store, unseeded). */
    public static EvaluationEnvironment defaults() {
        return new EvaluationEnvironment();
    }

    /** Env with a preloaded variable store and unseeded RNG. */
    public static EvaluationEnvironment with(Map<String, Object> initialVariables) {
        return new EvaluationEnvironment(new VariableStore(initialVariables), OptionalLong.empty());
    }

    /** Env with a seeded RNG and an empty variable store. */
    public static EvaluationEnvironment seeded(long seed) {
        return new EvaluationEnvironment(new VariableStore(), OptionalLong.of(seed));
    }

    public VariableStore variables() {
        return variables;
    }

    public OptionalLong seed() {
        return seed;
    }

    /**
     * Resolve the current value of an {@link IVariable}: store hit wins; otherwise the variable's
     * declared default.
     */
    @Nullable
    public Object lookupVariable(IVariable variable) {
        Object value = variables.getOrAbsent(variable.getName());
        if (value != VariableStore.ABSENT) return value;
        return variable.tryGetDefaultValue(variable.getDataType()).result().orElse(null);
    }
}
