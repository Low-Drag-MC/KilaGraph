package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;

/**
 * The body shared by {@code >}, {@code >=}, {@code <} and {@code <=}: read {@code a} and {@code b} in
 * the {@link NumericLane} they ask for, and compare them there.
 *
 * <p>Written once rather than four times on purpose. The four nodes differ by one operator, and the
 * values where they differ from each other are the values where a transcription slip hides — a NaN is
 * neither greater nor less than anything, so {@code !(a > b)} is not {@code a <= b} and a helper that
 * tried to build all four out of one {@code compare} would get that wrong. Here each operator is
 * spelled with the primitive it means, in every lane.</p>
 *
 * <p>The lane matters most here, and this is where it used to be missing entirely: comparing two
 * {@code long}s as {@code float}s makes everything above 2^24 compare in blocks, so a tick counter
 * and the tick after it are neither greater than nor less than one another. That has no
 * {@code IntGreaterThan} escape hatch the way modulo briefly had — it just answered wrongly.</p>
 */
final class NumericComparison {

    private NumericComparison() {}

    static final int GT = 0;
    static final int GE = 1;
    static final int LT = 2;
    static final int LE = 3;

    static boolean evaluate(EvalContext ctx, int op) {
        return switch (ctx.lane("a", "b")) {
            case NumericLane.INT, NumericLane.LONG -> {
                long va = ctx.getLong("a", 0L);
                long vb = ctx.getLong("b", 0L);
                yield switch (op) {
                    case GT -> va > vb;
                    case GE -> va >= vb;
                    case LT -> va < vb;
                    default -> va <= vb;
                };
            }
            case NumericLane.DOUBLE -> {
                double va = ctx.getDouble("a", 0d);
                double vb = ctx.getDouble("b", 0d);
                yield switch (op) {
                    case GT -> va > vb;
                    case GE -> va >= vb;
                    case LT -> va < vb;
                    default -> va <= vb;
                };
            }
            default -> {
                float va = ctx.getFloat("a", 0f);
                float vb = ctx.getFloat("b", 0f);
                yield switch (op) {
                    case GT -> va > vb;
                    case GE -> va >= vb;
                    case LT -> va < vb;
                    default -> va <= vb;
                };
            }
        };
    }
}
