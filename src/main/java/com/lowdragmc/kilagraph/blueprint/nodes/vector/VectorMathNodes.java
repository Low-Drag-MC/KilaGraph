package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

import static com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes.map;
import static com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes.zip;

/**
 * The scalar maths of the {@code math} group, applied to every component at once.
 *
 * <p><b>Why these are separate nodes rather than {@code math_multiply} learning about vectors.</b>
 * The math group folds its operands through {@link com.lowdragmc.kilagraph.graph.exec.NumericLane},
 * which exists so that adding to a tick count stays exact past 2^24 — an integer/long/float/double
 * decision that a vector has no answer to. Widening those nodes would mean every scalar add paying
 * for a type test it can never take. The vector group pays instead, in the one place that needs it.
 *
 * <p>All of them are width-polymorphic on the same terms as the rest of the group: they read
 * however many components arrive and answer in kind, and a component the narrower operand does not
 * have reads zero.
 */
public final class VectorMathNodes {

    private static final String GROUP = "vector";

    private VectorMathNodes() {
    }

    /**
     * Component-wise product — {@code (a.x*b.x, a.y*b.y, ...)}, not a dot or a cross.
     *
     * <p>Scaling each axis by a different amount: a bounding box to world units, a velocity damped
     * harder horizontally than vertically. {@code vector_scale} is the same thing with one number
     * for every axis.
     */
    @NodeAttribute(name = "vector_multiply", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Multiply extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, (x, y) -> x * y);
        }
    }

    /** Component-wise quotient. Division by zero gives zero, exactly as {@code math_divide} does. */
    @NodeAttribute(name = "vector_divide", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Divide extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            // Zero rather than an infinity: a NaN or an Inf entering the graph here surfaces
            // hundreds of nodes downstream as a position that renders nowhere, and math_divide
            // already made this choice for scalars.
            zip(ctx, (x, y) -> y == 0f ? 0f : x / y);
        }
    }

    /** The vector pointing the other way. */
    @NodeAttribute(name = "vector_negate", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Negate extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            map(ctx, v -> -v);
        }
    }

    /** Every component made positive. Note this is not the length — see {@code vector_length}. */
    @NodeAttribute(name = "vector_abs", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Abs extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            map(ctx, Math::abs);
        }
    }

    /** The smaller of each pair of components — the low corner of the box around two points. */
    @NodeAttribute(name = "vector_min", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Min extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, Math::min);
        }
    }

    /** The larger of each pair of components — the high corner of the box around two points. */
    @NodeAttribute(name = "vector_max", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Max extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, Math::max);
        }
    }

    /**
     * Every component held between the same two numbers.
     *
     * <p>The bounds are scalars, not vectors, because that is the case worth a node: clamping to
     * {@code [0,1]} or {@code [-1,1]}. Per-component bounds are {@code vector_max} of the low corner
     * followed by {@code vector_min} of the high one, which reads as what it is — and would
     * otherwise need two vector pins whose defaults could not both be right.
     */
    @NodeAttribute(name = "vector_clamp", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Clamp extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("min", Float.class).withDefaultValue(0f);
            ctx.addInputPort("max", Float.class).withDefaultValue(1f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float lo = ctx.getFloat("min", 0f);
            float hi = ctx.getFloat("max", 1f);
            // max(lo, min(hi, v)), so an inverted range answers lo — the same resolution math_clamp
            // reaches, rather than the two nodes disagreeing about a nonsense input.
            map(ctx, v -> Math.max(lo, Math.min(hi, v)));
        }
    }

    /**
     * Component-wise rounding, snapping and sign, in one dropdown.
     *
     * <p>One node rather than the four the math group has ({@code math_round}, {@code math_fract},
     * {@code math_sign} and a truncate hidden in the first) — the palette entry a graph author looks
     * for is "round a vector", and which flavour is a detail of that, not a different node.
     */
    @NodeAttribute(name = "vector_round", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Round extends AnnotatedNode {

        /** Plain enum, deliberately not {@code StringRepresentable} — see the note on the lang key. */
        public enum Op { ROUND, FLOOR, CEIL, TRUNC, FRACT, SIGN }

        @Option public Op op = Op.ROUND;
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            Op raw = ctx.getOption("op", Op.class, Op.ROUND);
            Op o = raw == null ? Op.ROUND : raw;
            map(ctx, v -> switch (o) {
                case FLOOR -> (float) Math.floor(v);
                case CEIL -> (float) Math.ceil(v);
                // toward zero, so -2.7 is -2 where FLOOR gives -3
                case TRUNC -> (float) (long) v;
                case FRACT -> v - (float) Math.floor(v);
                case SIGN -> Math.signum(v);
                case ROUND -> (float) Math.round(v);
            });
        }

        @Override
        public List<String> optionChoices(String optionId) {
            return "op".equals(optionId)
                    ? List.of("ROUND", "FLOOR", "CEIL", "TRUNC", "FRACT", "SIGN")
                    : List.of();
        }
    }
}
