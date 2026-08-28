package com.lowdragmc.kilagraph.blueprint.nodes.math;

import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;

/**
 * {@link EvalContext#lane} for the nodes whose input ids are {@code in1..inN} with {@code N} coming
 * from an option — {@code Add}, {@code Multiply}, {@code Min}, {@code Max}.
 *
 * <p>A separate fold rather than {@code ctx.lane(ids)} because the ids are not a constant: building
 * the varargs array would mean allocating one per evaluation, on nodes that are otherwise allocation
 * free.</p>
 */
final class VariadicLane {

    private VariadicLane() {}

    /** The lane to fold {@code in1..inN} in — never {@link NumericLane#NONE}. */
    static byte of(EvalContext ctx, int n) {
        byte lane = NumericLane.NONE;
        for (int i = 1; i <= n; i++) lane = NumericLane.widen(lane, ctx.laneOf(PortIds.in(i)));
        return NumericLane.resolve(lane);
    }
}
