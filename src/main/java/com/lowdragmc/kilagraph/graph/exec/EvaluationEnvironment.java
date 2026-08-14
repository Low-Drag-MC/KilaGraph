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

    /**
     * The environment a subgraph runs in.
     *
     * <p>A subgraph gets its own variable store — that is what makes its locals local — but
     * everything else about the environment belongs to the run, not to the graph level. A host that
     * hangs its own context here (the entity being animated, the delta owed to this update) would
     * otherwise find it silently absent one level down, and every node reading it would quietly
     * answer zero rather than fail. Override this to carry that context across.
     */
    public EvaluationEnvironment createChild(VariableStore childVariables) {
        return new EvaluationEnvironment(childVariables, seed());
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
