package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.lowdragmc.kilagraph.graph.type.Vectors.at;
import static com.lowdragmc.kilagraph.graph.type.Vectors.carrier;
import static com.lowdragmc.kilagraph.graph.type.Vectors.components;
import static com.lowdragmc.kilagraph.graph.type.Vectors.lengthSquared;

/**
 * Vector arithmetic, two to four components.
 *
 * <p><b>Width-polymorphic where it can be.</b> A vector operation is the same operation whatever the
 * width, so Add/Subtract/Scale/Dot/Length/Normalize/Distance/Lerp read however many components
 * arrive and answer in kind. Only what is genuinely three-dimensional — Cross, and the yaw between
 * two directions — insists on three.
 *
 * <p><b>The pin says which.</b> The polymorphic ones declare {@code VECTOR}
 * ({@link com.lowdragmc.kilagraph.graph.type.KGTypeHandles#VECTOR}) and the three-dimensional ones
 * declare {@code VEC3}, so the port colour tells a graph author whether a Vector2 will be operated on
 * or truncated. Make/Make2/Make4 keep their exact widths for the same reason: each produces one
 * specific width and says so.
 *
 * @see VectorPorts for why the polymorphic ports are declared imperatively
 * @see Vectors for the component arithmetic all of these share
 */
public final class VectorNodes {

    private static final String GROUP = "vector";

    private VectorNodes() {
    }

    // ---- component access

    @NodeAttribute(name = "vector_make", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make extends AnnotatedNode {
        @InputPort public float x;
        @InputPort public float y;
        @InputPort public float z;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) new Vector3f(
                    ctx.getFloat("x", 0f), ctx.getFloat("y", 0f), ctx.getFloat("z", 0f)));
        }
    }

    @NodeAttribute(name = "vector_make2", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make2 extends AnnotatedNode {
        @InputPort public float x;
        @InputPort public float y;
        @OutputPort public Vector2f out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) new Vector2f(ctx.getFloat("x", 0f), ctx.getFloat("y", 0f)));
        }
    }

    @NodeAttribute(name = "vector_make4", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make4 extends AnnotatedNode {
        @InputPort public float x;
        @InputPort public float y;
        @InputPort public float z;
        @InputPort public float w;
        @OutputPort public Vector4f out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) new Vector4f(ctx.getFloat("x", 0f), ctx.getFloat("y", 0f),
                    ctx.getFloat("z", 0f), ctx.getFloat("w", 0f)));
        }
    }

    /** Splits any width; components the value does not have read zero rather than failing. */
    @NodeAttribute(name = "vector_break", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Break extends AnnotatedNode {
        @OutputPort public float x;
        @OutputPort public float y;
        @OutputPort public float z;
        @OutputPort public float w;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            ctx.setOutput("x", at(v, 0));
            ctx.setOutput("y", at(v, 1));
            ctx.setOutput("z", at(v, 2));
            ctx.setOutput("w", at(v, 3));
        }
    }

    // ---- arithmetic

    @NodeAttribute(name = "vector_add", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Add extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, (x, y) -> x + y);
        }
    }

    @NodeAttribute(name = "vector_subtract", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Subtract extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, (x, y) -> x - y);
        }
    }

    @NodeAttribute(name = "vector_scale", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Scale extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("scale", Float.class).withDefaultValue(1f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float k = ctx.getFloat("scale", 1f);
            float[] scaled = new float[v.length];
            for (int i = 0; i < v.length; i++) {
                scaled[i] = v[i] * k;
            }
            ctx.setOutput("out", carrier(scaled));
        }
    }

    @NodeAttribute(name = "vector_dot", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Dot extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.reduce(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", Vectors.dot(
                    components(ctx.getInputRaw("a")), components(ctx.getInputRaw("b"))));
        }
    }

    @NodeAttribute(name = "vector_length", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Length extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addOutputPort("out", Float.class);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (float) Math.sqrt(lengthSquared(components(ctx.getInputRaw("in")))));
        }
    }

    /** Zero in, zero out — normalising a zero vector must not put NaN into the graph. */
    @NodeAttribute(name = "vector_normalize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Normalize extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float length = (float) Math.sqrt(lengthSquared(v));
            float[] unit = new float[v.length];
            if (length >= Vectors.EPSILON) {
                for (int i = 0; i < v.length; i++) {
                    unit[i] = v[i] / length;
                }
            }
            ctx.setOutput("out", carrier(unit));
        }
    }

    @NodeAttribute(name = "vector_distance", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Distance extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.reduce(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (float) Math.sqrt(Vectors.distanceSquared(
                    components(ctx.getInputRaw("a")), components(ctx.getInputRaw("b")))));
        }
    }

    @NodeAttribute(name = "vector_lerp", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Lerp extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "a");
            VectorPorts.in(ctx, "b");
            ctx.addInputPort("t", Float.class).withDefaultValue(0f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float k = Math.min(1f, Math.max(0f, ctx.getFloat("t", 0f)));
            zip(ctx, (x, y) -> x + (y - x) * k);
        }
    }

    /**
     * Genuinely three-dimensional; a 2- or 4-component input is read as its first three.
     *
     * <p>Hence VEC3 pins rather than VECTOR: the colour is the warning that a Vector4 wired in here
     * loses its w, which is the one thing a width-polymorphic pin would have promised not to do.
     */
    @NodeAttribute(name = "vector_cross", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Cross extends AnnotatedNode {
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] q = components(ctx.getInputRaw("b"));
            ctx.setOutput("out", (Object) new Vector3f(
                    at(p, 1) * at(q, 2) - at(p, 2) * at(q, 1),
                    at(p, 2) * at(q, 0) - at(p, 0) * at(q, 2),
                    at(p, 0) * at(q, 1) - at(p, 1) * at(q, 0)));
        }
    }

    /**
     * Drops one component, leaving the others alone — a velocity flattened onto the ground plane.
     *
     * <p>Which axis is up is the caller's business, so it is an option rather than baked in.
     */
    @NodeAttribute(name = "vector_flatten", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Flatten extends AnnotatedNode {
        @Option public int axis = 1;

        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.unary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            int axis = ctx.getOption("axis", Integer.class, 1);
            if (axis >= 0 && axis < v.length) {
                v[axis] = 0f;
            }
            ctx.setOutput("out", carrier(v));
        }
    }

    /**
     * Signed angle in degrees from one direction to another about the Y axis — the turn a character
     * has to make to face where it is going.
     *
     * <p><b>Minecraft's yaw convention</b>, i.e. {@code atan2(-x, z)}: zero looks down +Z and
     * positive turns toward -X, which is what {@code Vec3.directionFromRotation} produces. The
     * mathematically tidier {@code atan2(x, z)} would answer the negative of this for every input —
     * a sign flip that reads as a character strafing while it walks straight, and that nothing but a
     * convention-aware test can catch.
     *
     * <p>VEC3 pins: a turn about the vertical axis is a three-dimensional idea, and only x and z are
     * read at all.
     */
    @NodeAttribute(name = "vector_yaw_between", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class YawBetween extends AnnotatedNode {
        @InputPort public Vector3f from;
        @InputPort public Vector3f to;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("from"));
            float[] q = components(ctx.getInputRaw("to"));
            double angle = Math.toDegrees(Math.atan2(-at(q, 0), at(q, 2)) - Math.atan2(-at(p, 0), at(p, 2)));
            // folded into [-180, 180): a turn of 350 degrees is a turn of -10
            angle = ((angle + 180d) % 360d + 360d) % 360d - 180d;
            ctx.setOutput("out", (float) angle);
        }
    }

    // ---- shared
    //
    // The component arithmetic itself lives in Vectors, in the type layer, because the VECTOR
    // handle's codec and editor need it too. What stays here is the pair of folds that read and
    // write through an EvalContext, which is a node-layer idea.

    /** A component-wise binary operation, for {@link #zip}. */
    public interface Zip {
        float apply(float a, float b);
    }

    /** A component-wise unary operation, for {@link #map}. */
    public interface Unary {
        float apply(float v);
    }

    /** {@code out = op(a, b)} over ports {@code a}/{@code b}, at the wider operand's width. */
    public static void zip(EvalContext ctx, Zip op) {
        float[] p = components(ctx.getInputRaw("a"));
        float[] q = components(ctx.getInputRaw("b"));
        int width = Math.max(p.length, q.length);
        float[] out = new float[width];
        for (int i = 0; i < width; i++) {
            out[i] = op.apply(at(p, i), at(q, i));
        }
        ctx.setOutput("out", carrier(out));
    }

    /** {@code out = op(in)} over port {@code in}, keeping its width. */
    public static void map(EvalContext ctx, Unary op) {
        float[] v = components(ctx.getInputRaw("in"));
        for (int i = 0; i < v.length; i++) {
            v[i] = op.apply(v[i]);
        }
        ctx.setOutput("out", carrier(v));
    }
}
