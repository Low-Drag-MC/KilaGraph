package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Vector arithmetic, two to four components.
 *
 * <p><b>Width-polymorphic where it can be.</b> A vector operation is the same operation whatever the
 * width, so Add/Subtract/Scale/Dot/Length/Normalize/Distance/Lerp read however many components
 * arrive and answer in kind. Only what is genuinely three-dimensional — Cross, and the yaw between
 * two directions — insists on three.
 *
 * <p>Pins are typed VEC3 because a pin has to name one type (LDLib2 has no polymorphic pin;
 * {@code PortModel.isPolymorphic} is hardcoded false) and three is what most graphs use. Wiring a
 * VEC2 or VEC4 in is allowed by {@link com.lowdragmc.kilagraph.graph.type.KGGraphModel}, which
 * treats any vector width as assignable to any other — refusing it would be the editor enforcing a
 * rule the machine does not have.
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
        @InputPort public Vector3f in;
        @OutputPort public float x;
        @OutputPort public float y;
        @OutputPort public float z;
        @OutputPort public float w;

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
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, (x, y) -> x + y);
        }
    }

    @NodeAttribute(name = "vector_subtract", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Subtract extends AnnotatedNode {
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            zip(ctx, (x, y) -> x - y);
        }
    }

    @NodeAttribute(name = "vector_scale", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Scale extends AnnotatedNode {
        @InputPort public Vector3f in;
        @InputPort public float scale = 1f;
        @OutputPort public Vector3f out;

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
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] q = components(ctx.getInputRaw("b"));
            float sum = 0f;
            for (int i = 0; i < Math.max(p.length, q.length); i++) {
                sum += at(p, i) * at(q, i);
            }
            ctx.setOutput("out", sum);
        }
    }

    @NodeAttribute(name = "vector_length", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Length extends AnnotatedNode {
        @InputPort public Vector3f in;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (float) Math.sqrt(lengthSquared(components(ctx.getInputRaw("in")))));
        }
    }

    /** Zero in, zero out — normalising a zero vector must not put NaN into the graph. */
    @NodeAttribute(name = "vector_normalize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Normalize extends AnnotatedNode {
        @InputPort public Vector3f in;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float length = (float) Math.sqrt(lengthSquared(v));
            float[] unit = new float[v.length];
            if (length >= 1e-6f) {
                for (int i = 0; i < v.length; i++) {
                    unit[i] = v[i] / length;
                }
            }
            ctx.setOutput("out", carrier(unit));
        }
    }

    @NodeAttribute(name = "vector_distance", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Distance extends AnnotatedNode {
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] q = components(ctx.getInputRaw("b"));
            float sum = 0f;
            for (int i = 0; i < Math.max(p.length, q.length); i++) {
                float d = at(p, i) - at(q, i);
                sum += d * d;
            }
            ctx.setOutput("out", (float) Math.sqrt(sum));
        }
    }

    @NodeAttribute(name = "vector_lerp", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Lerp extends AnnotatedNode {
        @InputPort public Vector3f a;
        @InputPort public Vector3f b;
        @InputPort public float t;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float k = Math.min(1f, Math.max(0f, ctx.getFloat("t", 0f)));
            zip(ctx, (x, y) -> x + (y - x) * k);
        }
    }

    /** Genuinely three-dimensional; a 2- or 4-component input is read as its first three. */
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
        @InputPort public Vector3f in;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in")).clone();
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

    private interface Zip {
        float apply(float a, float b);
    }

    private static void zip(EvalContext ctx, Zip op) {
        float[] p = components(ctx.getInputRaw("a"));
        float[] q = components(ctx.getInputRaw("b"));
        int width = Math.max(p.length, q.length);
        float[] out = new float[width];
        for (int i = 0; i < width; i++) {
            out[i] = op.apply(at(p, i), at(q, i));
        }
        ctx.setOutput("out", carrier(out));
    }

    private static float at(float[] v, int index) {
        return index < v.length ? v[index] : 0f;
    }

    private static float lengthSquared(float[] v) {
        float sum = 0f;
        for (float c : v) {
            sum += c * c;
        }
        return sum;
    }

    /**
     * The components behind a pin, whatever width it carries.
     *
     * <p>An unconnected pin can hold a constant of a different width than the node declares, and
     * {@code KGGraphModel} lets a VEC2 or VEC4 wire reach a VEC3 pin. Reading components rather
     * than casting is what keeps those cases arithmetic instead of silently zero.
     */
    public static float[] components(Object raw) {
        if (raw instanceof Vector4f v) {
            return new float[] {v.x, v.y, v.z, v.w};
        }
        if (raw instanceof Vector3f v) {
            return new float[] {v.x, v.y, v.z};
        }
        if (raw instanceof Vector2f v) {
            return new float[] {v.x, v.y};
        }
        if (raw instanceof Number n) {
            return new float[] {n.floatValue()};
        }
        return new float[] {0f, 0f, 0f};
    }

    /** The JOML shape for a width, so a value keeps its type across a wire. */
    public static Object carrier(float[] v) {
        return switch (v.length) {
            case 2 -> new Vector2f(v[0], v[1]);
            case 4 -> new Vector4f(v[0], v[1], v[2], v[3]);
            case 1 -> (Object) v[0];
            default -> new Vector3f(at(v, 0), at(v, 1), at(v, 2));
        };
    }
}
