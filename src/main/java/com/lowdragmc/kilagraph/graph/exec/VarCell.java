package com.lowdragmc.kilagraph.graph.exec;

import org.jetbrains.annotations.Nullable;

/**
 * One graph variable's storage, addressed by reference instead of by name.
 *
 * <h2>Why a cell and not a slot</h2>
 * Ports get integer slots because {@link PreparedGraph} assigns them and every executor over that
 * graph agrees on the numbering. Variables cannot work that way: a prepared graph is <em>shared</em>
 * by executors with different environments, so a slot index resolved against one
 * {@link VariableStore}'s layout means nothing in another. {@code SetVar} also writes whatever name
 * its option says, which need not be a declared variable at all, and hosts seed the store by name at
 * runtime.
 *
 * <p>So the store keeps its name-keyed map and hands out cells. A node resolves its cell once and
 * then reads and writes through the reference — the hash lookup happens on the first access rather
 * than on every one, and different environments naturally hand out different cells.</p>
 *
 * <h2>Two lanes, matching the port table</h2>
 * A number lives in {@link #num} as raw bits with {@link #kind} recording the declared width, so a
 * float going from a node into a variable and back out again never becomes a {@code Float}. That was
 * the last routine allocation on the exec path: {@code SetVar} boxed every value it wrote, because
 * the store was a {@code Map<String,Object>}.
 *
 * <h2>Cells outlive their values</h2>
 * {@link VariableStore#remove} and {@link VariableStore#clear} mark a cell absent rather than
 * dropping it. A dropped cell would leave every node that had resolved it holding a reference to
 * storage the store no longer consults, and writes through that reference would vanish. Keeping the
 * cell costs one entry per name ever used, which is bounded by the graph.
 */
final class VarCell {

    final String name;

    /** The value when {@link #kind} is {@link GraphExecutor#KIND_OBJECT}; null otherwise. */
    @Nullable Object value;
    /** Raw bits of the value when {@link #kind} is numeric. */
    long num;
    byte kind = GraphExecutor.KIND_OBJECT;
    /**
     * Whether this variable has a value at all.
     *
     * <p>Distinct from holding {@code null}: an absent variable falls back to its declared default,
     * a present-but-null one does not. That distinction is what {@code VariableStore.ABSENT} carried
     * before, and {@code EvaluationEnvironment.lookupVariable} still depends on it.</p>
     */
    boolean present;

    VarCell(String name) {
        this.name = name;
    }

    void setObject(@Nullable Object v) {
        value = v;
        kind = GraphExecutor.KIND_OBJECT;
        present = true;
    }

    void setNum(byte k, long bits) {
        value = null;          // release whatever reference the object lane was holding
        num = bits;
        kind = k;
        present = true;
    }

    /** Mark absent, releasing any reference. The cell itself stays — see the class javadoc. */
    void clear() {
        value = null;
        kind = GraphExecutor.KIND_OBJECT;
        present = false;
    }

    /**
     * The value as an object, boxing a numeric lane back into the wrapper its declared width calls
     * for — the same rule {@code GraphExecutor.boxed} applies to port slots, so a variable read
     * through the name-keyed API yields exactly the type it used to.
     */
    @Nullable
    Object boxed() {
        return kind == GraphExecutor.KIND_OBJECT ? value : GraphExecutor.boxed(kind, num);
    }
}
