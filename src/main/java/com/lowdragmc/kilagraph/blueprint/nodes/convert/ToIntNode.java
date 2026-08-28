package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.NumericLane;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Convert a numeric value to {@code int}, choosing how to drop the fractional part. Input is
 * {@code UNKNOWN} so any {@link Number} wire coerces; non-numeric values yield 0.
 *
 * <p>A value that is <em>already</em> whole is passed through rather than rounded, and never goes via
 * {@code float} on the way. It used to: the node read {@code ctx.getFloat("in", 0f)} and cast, so
 * converting a {@code long} above 2^24 answered a neighbouring number instead of the one it was
 * given — {@code ToInt} was itself one of the places precision went missing. See
 * {@link NumericLane}.</p>
 *
 * <p>Out-of-range values saturate at {@link Integer#MIN_VALUE} / {@link Integer#MAX_VALUE}, which is
 * what a {@code float}-to-{@code int} cast did before and what {@code Number.intValue()} would
 * <em>not</em> do — that truncates a long's low 32 bits and can flip the sign.</p>
 */
@NodeAttribute(name = "convert_to_int", group = "convert", graphTypes = BlueprintGraph.class)
public class ToIntNode extends AnnotatedNode {

    public enum Op { TRUNC, FLOOR, CEIL, ROUND }

    @Option public Op op = Op.TRUNC;
    @OutputPort public int out;

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("in", TypeHandles.UNKNOWN);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        long r = whole(ctx, "in", ctx.getOption("op", Op.class, Op.TRUNC));
        ctx.setOutput("out", (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, r)));
    }

    /**
     * {@code inputId} as a whole number, applying {@code op} only if it has a fractional part to
     * drop. Shared with {@link ToLongNode}, which is the same read without the range clamp.
     *
     * <p>Anything that is not a number reads as 0, as it always did. So do NaN and the infinities:
     * {@code (long) NaN} is 0 and the infinities saturate, which is Java's own answer for the cast
     * this replaced.</p>
     */
    static long whole(EvalContext ctx, String inputId, Op op) {
        Object raw = ctx.getInputRaw(inputId);
        if (!(raw instanceof Number n)) return 0L;
        if (NumericLane.isIntegral(n)) return n.longValue();
        double v = n.doubleValue();
        return switch (op) {
            case FLOOR -> (long) Math.floor(v);
            case CEIL -> (long) Math.ceil(v);
            case ROUND -> Math.round(v);
            case TRUNC -> (long) v;
        };
    }

    @Override
    public List<String> optionChoices(String optionId) {
        return "op".equals(optionId) ? List.of("TRUNC", "FLOOR", "CEIL", "ROUND") : List.of();
    }
}
