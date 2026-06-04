package com.lowdragmc.kilagraph.graph.exec;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable, name-keyed bag of graph-variable values. Lives on {@link EvaluationEnvironment} so the
 * executor (and host code) can feed live values for {@code INPUT} variables and harvest values
 * written by {@code SetVar} (future) or by subgraph composition.
 *
 * <p>Lookup distinguishes "absent" from "present-but-null". {@link #contains(String)} answers the
 * first question; {@link #get(String)} answers the second. The variable runtime in
 * {@link EvaluationEnvironment#lookupVariable} uses this to decide whether to fall back to the
 * variable's static default — only when there is no entry.</p>
 *
 * <p>Not thread-safe. One store per evaluation, or guard externally.</p>
 */
public final class VariableStore {

    private final Map<String, Object> values;

    public VariableStore() {
        this.values = new HashMap<>();
    }

    public VariableStore(Map<String, Object> initial) {
        this.values = new HashMap<>(Objects.requireNonNull(initial));
    }

    public void put(String name, @Nullable Object value) {
        values.put(name, value);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    @Nullable
    public Object get(String name) {
        return values.get(name);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }
}
