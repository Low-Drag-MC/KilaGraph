package com.lowdragmc.kilagraph.graph.exec;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable, name-keyed bag of graph-variable values. Lives on {@link EvaluationEnvironment} so the
 * executor (and host code) can feed live values for {@code INPUT} variables and harvest values
 * written by {@code SetVar} or by subgraph composition.
 *
 * <p>Lookup distinguishes "absent" from "present-but-null". {@link #contains(String)} answers the
 * first question; {@link #get(String)} answers the second. The variable runtime in
 * {@link EvaluationEnvironment#lookupVariable} uses this to decide whether to fall back to the
 * variable's static default — only when there is no entry.</p>
 *
 * <p>Values are held in {@link VarCell}s rather than directly in the map. The name-keyed API here is
 * unchanged and still costs one hash lookup per call, which is what a host seeding values per frame
 * should pay; the executor resolves a cell once per node and then reads and writes through the
 * reference, which is what a node running every frame should pay. See {@link VarCell}.</p>
 *
 * <p>Not thread-safe. One store per evaluation, or guard externally.</p>
 */
public final class VariableStore {

    private final Map<String, VarCell> cells;

    public VariableStore() {
        this.cells = new HashMap<>();
    }

    public VariableStore(Map<String, Object> initial) {
        this.cells = new HashMap<>();
        Objects.requireNonNull(initial).forEach(this::put);
    }

    public void put(String name, @Nullable Object value) {
        cell(name).setObject(value);
    }

    public boolean contains(String name) {
        VarCell c = cells.get(name);
        return c != null && c.present;
    }

    @Nullable
    public Object get(String name) {
        VarCell c = cells.get(name);
        return c == null || !c.present ? null : c.boxed();
    }

    /**
     * Sentinel returned by {@link #getOrAbsent} for a name with no entry, so "absent" and
     * "present but null" stay distinguishable in a single lookup.
     */
    static final Object ABSENT = new Object();

    /**
     * The value for {@code name}, or {@link #ABSENT}. Exists so a variable read costs one hash
     * lookup rather than the {@code contains} + {@code get} pair, which is two on every read of
     * every variable node.
     */
    Object getOrAbsent(String name) {
        VarCell c = cells.get(name);
        return c == null || !c.present ? ABSENT : c.boxed();
    }

    /**
     * The cell for {@code name}, creating it if this store has never seen the name.
     *
     * <p>The returned reference stays valid for the life of the store, including across
     * {@link #remove} and {@link #clear} — that is what makes it safe for the executor to cache.</p>
     */
    VarCell cell(String name) {
        return cells.computeIfAbsent(name, VarCell::new);
    }

    /** Mark {@code name} absent. The cell is kept — see {@link VarCell}. */
    public void remove(String name) {
        VarCell c = cells.get(name);
        if (c != null) c.clear();
    }

    /** Mark every name absent. The cells are kept — see {@link VarCell}. */
    public void clear() {
        for (VarCell c : cells.values()) c.clear();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new HashMap<>();
        for (VarCell c : cells.values()) {
            if (c.present) out.put(c.name, c.boxed());
        }
        return Map.copyOf(out);
    }
}
