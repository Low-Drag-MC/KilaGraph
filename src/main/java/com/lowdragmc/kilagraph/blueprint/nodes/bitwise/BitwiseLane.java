package com.lowdragmc.kilagraph.blueprint.nodes.bitwise;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;

/**
 * Whether a bitwise node should work in 64 bits rather than 32.
 *
 * <p>The bitwise nodes read the {@link NumericLane} more strictly than the arithmetic ones, and for a
 * different reason. For arithmetic, a wider lane is never a worse answer, so {@code INT} and
 * {@code LONG} are the same request. Here the width <em>is</em> the answer: {@code ~0} is
 * {@code -1} either way but {@code 1 << 35} is {@code 8} in 32 bits and {@code 34359738368} in 64,
 * and masking to the low 32 bits is frequently the point of the graph. So only an actual
 * {@code Long} widens, and an {@code Integer} — however it arrived — keeps the 32-bit answer it
 * always had.</p>
 *
 * <p>What that fixes is the case where 32 bits was never plausible: {@code BlockPos.asLong()},
 * an entity id, a seed, a packed NBT long. Those used to be truncated to their low 32 bits on the way
 * in, silently, and a mask applied to the result was then masking the wrong half of the number.</p>
 */
final class BitwiseLane {

    private BitwiseLane() {}

    /**
     * Whether {@code ids} include a {@code long}.
     *
     * <p>Shift nodes pass only their <em>value</em> input, not the distance: {@code int << long} is an
     * {@code int} in Java too, because the distance says how far to shift and not how wide the result
     * is.</p>
     */
    static boolean wide(EvalContext ctx, String... ids) {
        for (String id : ids) {
            if (ctx.laneOf(id) == NumericLane.LONG) return true;
        }
        return false;
    }
}
