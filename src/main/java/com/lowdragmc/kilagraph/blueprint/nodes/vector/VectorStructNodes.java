package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.PortIds;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.Arrays;

import static com.lowdragmc.kilagraph.graph.type.Vectors.at;
import static com.lowdragmc.kilagraph.graph.type.Vectors.carrier;
import static com.lowdragmc.kilagraph.graph.type.Vectors.components;

/**
 * Rebuilding a vector out of the components of others — reordering, concatenating, widening, and
 * replacing one component in place.
 *
 * <p><b>Why these are not just Break plus Make.</b> {@code vector_break} into {@code vector_make}
 * can express any of them, but it costs two nodes and four wires, and it <em>loses the width</em>:
 * Break hands out four floats and Make only builds a Vector3, so a Vector2 that goes through the
 * pair comes back as a Vector3 with a zero glued on. Everything here keeps the width the operation
 * implies instead of routing it through a fixed one.
 *
 * @see VectorNodes for the arithmetic these feed
 */
public final class VectorStructNodes {

    private static final String GROUP = "vector";

    private VectorStructNodes() {
    }

    /**
     * Builds a vector by naming, per output slot, which component of the input to read.
     *
     * <p>The one node that covers reorder ({@code zyx}), narrow ({@code xy} off a Vector4), widen
     * ({@code xyz0}), splat ({@code xxx}) and swap ({@code yx}) — GLSL's swizzle, which is where the
     * name and the syntax come from, and the shader graph's {@code rt_swizzle} in the form this
     * graph can carry.
     *
     * <p>The mask drives the <b>output width</b>: {@code "xy"} answers a Vector2 whatever arrived,
     * which is the reason this is not expressible with Break and Make.
     */
    @NodeAttribute(name = "vector_swizzle", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Swizzle extends AnnotatedNode {
        /** The mask a blank, malformed or single-character option falls back to. */
        private static final String DEFAULT_MASK = "xyz";
        private static final String AXES = "xyzw";
        private static final String ALLOWED = "xyzw01";

        // A free-text option rather than four dropdowns (which is how rt_swizzle does it): the
        // blueprint's annotated options have no choice configurator, and "xzy" is how anyone who has
        // met a swizzle before expects to write one anyway.
        @Option public String mask = DEFAULT_MASK;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            String m = sanitize(ctx.getOption("mask", String.class, DEFAULT_MASK));
            float[] out = new float[m.length()];
            for (int i = 0; i < out.length; i++) {
                char c = m.charAt(i);
                out[i] = switch (c) {
                    case '0' -> 0f;
                    case '1' -> 1f;
                    default -> at(v, AXES.indexOf(c));
                };
            }
            ctx.setOutput("out", carrier(out));
        }

        /**
         * The mask actually used: allowed characters only, at most four, and at least two.
         *
         * <p>Anything else falls back to {@code xyz} rather than being honoured partially. A
         * one-character mask is refused for a concrete reason and not out of tidiness — the output
         * pin is a vector, and {@code carrier} answers a bare float at width one, which no vector
         * pin downstream would accept. {@code vector_get_component} is that operation.</p>
         *
         * <p><b>Returns {@code raw} itself when it is already usable</b>, which is every evaluation
         * of every graph that was authored rather than fuzzed. Cleaning unconditionally would build a
         * {@code StringBuilder}, its backing array and a {@code String} per evaluation — three
         * objects to recompute a constant, on a node whose real work is four array reads.</p>
         */
        public static String sanitize(String raw) {
            if (raw == null) return DEFAULT_MASK;
            if (isUsable(raw)) return raw;
            StringBuilder sb = new StringBuilder(4);
            for (int i = 0; i < raw.length() && sb.length() < 4; i++) {
                char c = Character.toLowerCase(raw.charAt(i));
                if (ALLOWED.indexOf(c) >= 0) sb.append(c);
            }
            return sb.length() < 2 ? DEFAULT_MASK : sb.toString();
        }

        /** Whether {@code raw} can be used as it stands — right length, and every character allowed. */
        private static boolean isUsable(String raw) {
            int length = raw.length();
            if (length < Vectors.MIN_WIDTH || length > Vectors.MAX_WIDTH) return false;
            for (int i = 0; i < length; i++) {
                // deliberately not case-insensitive: an upper-case mask is correct but has to be
                // rewritten, so it takes the cleaning path rather than reaching charAt below as-is
                if (ALLOWED.indexOf(raw.charAt(i)) < 0) return false;
            }
            return true;
        }
    }

    /**
     * Lays several vectors end to end — {@code (1,2)} and {@code (3,4)} give {@code (1,2,3,4)}.
     *
     * <p>Stops at four components, because that is the widest vector the graph has.
     */
    @NodeAttribute(name = "vector_concat", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Concat extends AnnotatedNode {
        @Option public int inputs = 2;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            for (int i = 1; i <= count(optionValue("inputs", Integer.class, inputs)); i++) {
                VectorPorts.in(ctx, PortIds.in(i));
            }
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            int n = count(ctx.getOption("inputs", Integer.class, inputs));
            float[] acc = new float[Vectors.MAX_WIDTH];
            int len = 0;
            for (int i = 1; i <= n && len < Vectors.MAX_WIDTH; i++) {
                float[] v = components(ctx.getInputRaw(PortIds.in(i)));
                for (int c = 0; c < v.length && len < Vectors.MAX_WIDTH; c++) {
                    acc[len++] = v[c];
                }
            }
            // Handed straight over once it filled up, which is the usual case — two inputs of any
            // width past a Vector2 already reach four components, and copying to trim would be a
            // second array for nothing.
            ctx.setOutput("out", carrier(len == Vectors.MAX_WIDTH
                    ? acc : Arrays.copyOf(acc, Vectors.clampWidth(len))));
        }

        /** Two to four inputs: one is not a concatenation, and five could not reach the result. */
        private static int count(int requested) {
            return Vectors.clampWidth(requested);
        }
    }

    /**
     * One more component on the end — a Vector2 UV plus a depth, a Vector3 colour plus an alpha.
     *
     * <p>The other half of {@link Concat}, for the common case where what is being added is a plain
     * number rather than a vector. A number cannot be wired into a vector pin at all, so Concat
     * genuinely cannot do this one.
     */
    @NodeAttribute(name = "vector_append", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Append extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("value", Float.class).withDefaultValue(0f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            if (v.length >= Vectors.MAX_WIDTH) {
                // Nothing to append to. Overwriting w instead would make this a set-component node
                // that lies about its name; vector_set_component is the node that does that.
                ctx.setOutput("out", carrier(v));
                return;
            }
            float[] wider = Arrays.copyOf(v, v.length + 1);
            wider[v.length] = ctx.getFloat("value", 0f);
            ctx.setOutput("out", carrier(wider));
        }
    }

    /**
     * The same vector with one component replaced — "put this entity back on the ground" is
     * {@code set_component(pos, Y, groundLevel)}.
     *
     * <p>Keeps the input's width, so it is the one way to change a single component of a Vector2 or
     * Vector4 without going through Break/Make and being handed a Vector3.
     */
    @NodeAttribute(name = "vector_set_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetComponent extends AnnotatedNode {
        // An int rather than an enum or a Direction.Axis: Axis has no W, and the sibling that came
        // first (vector_flatten) already numbers its axes this way.
        @Option public int axis = 1;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("value", Float.class).withDefaultValue(0f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            int axis = ctx.getOption("axis", Integer.class, 1);
            if (axis >= 0 && axis < v.length) {
                // An axis the value does not have leaves it alone rather than widening it — the same
                // rule vector_flatten uses, so the two nodes cannot disagree about what W means on a
                // Vector3.
                v[axis] = ctx.getFloat("value", 0f);
            }
            ctx.setOutput("out", carrier(v));
        }
    }

    /**
     * One component, chosen by a number rather than by which pin you drag from.
     *
     * <p>{@code vector_break} is the node for "I want X" written at authoring time. This is the node
     * for an index that is computed — a loop over the three axes, an axis carried in a variable.
     */
    @NodeAttribute(name = "vector_get_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetComponent extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("index", Integer.class).withDefaultValue(0);
            ctx.addOutputPort("out", Float.class);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", at(components(ctx.getInputRaw("in")), ctx.getInt("index", 0)));
        }
    }
}
