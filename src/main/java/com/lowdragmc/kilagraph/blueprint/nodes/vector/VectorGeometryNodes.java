package com.lowdragmc.kilagraph.blueprint.nodes.vector;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.lowdragmc.kilagraph.graph.type.Vectors.at;
import static com.lowdragmc.kilagraph.graph.type.Vectors.carrier;
import static com.lowdragmc.kilagraph.graph.type.Vectors.components;
import static com.lowdragmc.kilagraph.graph.type.Vectors.dot;
import static com.lowdragmc.kilagraph.graph.type.Vectors.lengthSquared;

/**
 * The geometry a vector is for: directions relative to other directions, angles, and rotation.
 *
 * <p><b>Degenerate inputs answer, they do not fail.</b> Projecting onto the zero vector, the angle
 * to a zero-length direction, rotating about no axis — each of those is a division by zero in the
 * obvious formula, and a NaN that enters a graph here surfaces as an entity teleported nowhere,
 * hundreds of nodes later, with nothing pointing back. Every node here names its degenerate case in
 * its own doc and answers something finite, the same choice {@code vector_normalize} already made.
 *
 * <p>{@code fromRotation}/{@code toRotation} use <b>Minecraft's</b> yaw/pitch convention rather than
 * the mathematically tidier one, for the same reason {@code vector_yaw_between} does: they exist to
 * talk to entities.
 */
public final class VectorGeometryNodes {

    private static final String GROUP = "vector";

    private VectorGeometryNodes() {
    }

    /**
     * Length without the square root.
     *
     * <p>For comparing distances — "is this closer than that", "is this within range" — which is
     * what most length tests actually are. Compare against the square of the radius.
     */
    @NodeAttribute(name = "vector_length_squared", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LengthSquared extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addOutputPort("out", Float.class);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", lengthSquared(components(ctx.getInputRaw("in"))));
        }
    }

    /** Distance without the square root — {@link LengthSquared} for the gap between two points. */
    @NodeAttribute(name = "vector_distance_squared", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DistanceSquared extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.reduce(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", Vectors.distanceSquared(
                    components(ctx.getInputRaw("a")), components(ctx.getInputRaw("b"))));
        }
    }

    /**
     * The same direction, with its length held between two numbers.
     *
     * <p>A speed limit that does not change where something is heading — which is what clamping the
     * components individually would do, and why that is not the same node.
     */
    @NodeAttribute(name = "vector_clamp_length", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ClampLength extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            ctx.addInputPort("min", Float.class).withDefaultValue(0f);
            ctx.addInputPort("max", Float.class).withDefaultValue(1f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float length = (float) Math.sqrt(lengthSquared(v));
            // A zero vector has no direction to give the result, so no minimum can be honoured:
            // stretching it would have to invent one. Zero in, zero out.
            if (length >= Vectors.EPSILON) {
                float lo = ctx.getFloat("min", 0f);
                float hi = ctx.getFloat("max", 1f);
                float target = Math.max(lo, Math.min(hi, length));
                float k = target / length;
                for (int i = 0; i < v.length; i++) v[i] *= k;
            }
            ctx.setOutput("out", carrier(v));
        }
    }

    /**
     * The part of {@code a} that lies along {@code b} — {@code b * (a·b / b·b)}.
     *
     * <p>How much of a velocity is "forward", how far along a rail a push actually moves something.
     * {@code b} does not have to be a unit vector. A zero {@code b} names no direction to project
     * onto, and answers zero.
     */
    @NodeAttribute(name = "vector_project", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Project extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", carrier(project(
                    components(ctx.getInputRaw("a")), components(ctx.getInputRaw("b")))));
        }
    }

    /**
     * The part of {@code a} that does not lie along {@code b} — {@code a - project(a, b)}.
     *
     * <p>The sideways half of the same split: the component of a movement that a wall does not stop,
     * a velocity with its climb removed. A zero {@code b} removes nothing and answers {@code a}.
     */
    @NodeAttribute(name = "vector_reject", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Reject extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.binary(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] parallel = project(p, components(ctx.getInputRaw("b")));
            // subtracted into the projection rather than into a third array: project() allocated it
            // for this node alone and nothing else can see it
            for (int i = 0; i < parallel.length; i++) parallel[i] = at(p, i) - parallel[i];
            ctx.setOutput("out", carrier(parallel));
        }
    }

    /**
     * A direction bounced off a surface — {@code in - 2(in·n̂)n̂}.
     *
     * <p>A projectile off a wall, a look direction off a mirror. The normal is normalised here, so it
     * does not have to arrive as a unit vector; a zero normal is no surface at all and passes
     * {@code in} through unchanged.
     */
    @NodeAttribute(name = "vector_reflect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Reflect extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "in");
            VectorPorts.in(ctx, "normal");
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float[] n = components(ctx.getInputRaw("normal"));
            int width = Math.max(v.length, n.length);
            float nn = lengthSquared(n);
            float[] out = new float[width];
            if (nn < Vectors.EPSILON * Vectors.EPSILON) {
                for (int i = 0; i < width; i++) out[i] = at(v, i);
            } else {
                // 2(v·n)/(n·n) rather than normalising n first: same result, one square root fewer,
                // and no second place for a near-zero length to be tested differently.
                float k = 2f * dot(v, n) / nn;
                for (int i = 0; i < width; i++) out[i] = at(v, i) - k * at(n, i);
            }
            ctx.setOutput("out", carrier(out));
        }
    }

    /**
     * The unsigned angle between two directions, in degrees, from 0 to 180.
     *
     * <p>"Is that thing in front of me" and "how far off am I", without the axis assumption
     * {@code vector_yaw_between} makes — this one counts pitch as well, and never tells you which
     * way to turn. Either input being zero has no angle to report, and answers 0.
     */
    @NodeAttribute(name = "vector_angle_between", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AngleBetween extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.reduce(ctx);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] q = components(ctx.getInputRaw("b"));
            double denom = Math.sqrt((double) lengthSquared(p) * lengthSquared(q));
            if (denom < Vectors.EPSILON) {
                ctx.setOutput("out", 0f);
                return;
            }
            // Clamped because rounding can push the cosine a hair past 1, and acos of 1.0000001 is
            // NaN — a failure mode that only appears for vectors that are exactly parallel, i.e.
            // the case a test written with tidy numbers hits first.
            double cos = Math.max(-1d, Math.min(1d, dot(p, q) / denom));
            ctx.setOutput("out", (float) Math.toDegrees(Math.acos(cos)));
        }
    }

    /**
     * A vector turned about an arbitrary axis, by an angle in degrees.
     *
     * <p>Rodrigues' rotation. Right-handed, so a positive angle turns counter-clockwise seen from
     * the tip of the axis looking back down it — the same handedness as {@code vector_cross}, which
     * is the operation it is built from.
     *
     * <p>Three-dimensional, like Cross: a wider input is read as its first three components and the
     * answer is a Vector3. A zero axis names no rotation and passes the input through.
     */
    @NodeAttribute(name = "vector_rotate_axis", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RotateAxis extends AnnotatedNode {
        @InputPort public Vector3f in;
        @InputPort public Vector3f axis;
        @InputPort public float angle = 0f;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            float[] k = components(ctx.getInputRaw("axis"));
            float vx = at(v, 0), vy = at(v, 1), vz = at(v, 2);
            double axisLength = Math.sqrt(at(k, 0) * at(k, 0) + at(k, 1) * at(k, 1) + at(k, 2) * at(k, 2));
            if (axisLength < Vectors.EPSILON) {
                ctx.setOutput("out", (Object) new Vector3f(vx, vy, vz));
                return;
            }
            double kx = at(k, 0) / axisLength, ky = at(k, 1) / axisLength, kz = at(k, 2) / axisLength;
            double theta = Math.toRadians(ctx.getFloat("angle", 0f));
            double cos = Math.cos(theta), sin = Math.sin(theta);
            // v cos + (k x v) sin + k (k.v)(1 - cos)
            double cx = ky * vz - kz * vy;
            double cy = kz * vx - kx * vz;
            double cz = kx * vy - ky * vx;
            double kv = (kx * vx + ky * vy + kz * vz) * (1d - cos);
            ctx.setOutput("out", (Object) new Vector3f(
                    (float) (vx * cos + cx * sin + kx * kv),
                    (float) (vy * cos + cy * sin + ky * kv),
                    (float) (vz * cos + cz * sin + kz * kv)));
        }
    }

    /**
     * The unit direction an entity with this yaw and pitch is looking.
     *
     * <p>Straight from {@code Vec3.directionFromRotation}, so it is the game's convention and not a
     * re-derivation of it: yaw 0 looks down +Z, yaw −90 down +X, and a <b>positive pitch looks
     * down</b>. Getting either sign wrong produces a vector that is plausible everywhere except in
     * front of the player.
     */
    @NodeAttribute(name = "vector_from_rotation", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromRotation extends AnnotatedNode {
        @InputPort public float yaw = 0f;
        @InputPort public float pitch = 0f;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Vec3 d = Vec3.directionFromRotation(ctx.getFloat("pitch", 0f), ctx.getFloat("yaw", 0f));
            ctx.setOutput("out", (Object) McConvert.toJoml(d));
        }
    }

    /**
     * The yaw and pitch that would make an entity look along this vector — the inverse of
     * {@link FromRotation}.
     *
     * <p>Feed these to anything that sets an entity's rotation. Yaw comes out folded into
     * {@code [-180, 180)}; a zero vector has no direction and answers zero for both.
     */
    @NodeAttribute(name = "vector_to_rotation", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToRotation extends AnnotatedNode {
        @InputPort public Vector3f in;
        @OutputPort public float yaw;
        @OutputPort public float pitch;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            double x = at(v, 0), y = at(v, 1), z = at(v, 2);
            double horizontal = Math.sqrt(x * x + z * z);
            // atan2(-x, z) is the same convention vector_yaw_between reads, and atan2(0, 0) is 0
            // rather than an error, which is what makes a straight-up vector answer yaw 0.
            ctx.setOutput("yaw", (float) Math.toDegrees(Math.atan2(-x, z)));
            ctx.setOutput("pitch", (float) -Math.toDegrees(Math.atan2(y, horizontal)));
        }
    }

    /**
     * A step from one point toward another, no longer than {@code maxDelta}.
     *
     * <p>Smooth chasing without overshoot: the result is exactly {@code to} once the gap is within
     * one step, so it settles instead of oscillating. A negative step moves away from the target,
     * which is Unity's rule for the same operation and occasionally what you want.
     */
    @NodeAttribute(name = "vector_move_towards", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class MoveTowards extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "from");
            VectorPorts.in(ctx, "to");
            ctx.addInputPort("maxDelta", Float.class).withDefaultValue(1f);
            VectorPorts.out(ctx, "out");
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("from"));
            float[] q = components(ctx.getInputRaw("to"));
            int width = Math.max(p.length, q.length);
            float[] delta = new float[width];
            for (int i = 0; i < width; i++) delta[i] = at(q, i) - at(p, i);
            float distance = (float) Math.sqrt(lengthSquared(delta));
            float step = ctx.getFloat("maxDelta", 1f);
            if (distance < Vectors.EPSILON || distance <= step) {
                // Land exactly on the target rather than a step short of it — the difference between
                // arriving and jittering around the destination forever.
                for (int i = 0; i < width; i++) delta[i] = at(q, i);
            } else {
                float k = step / distance;
                for (int i = 0; i < width; i++) delta[i] = at(p, i) + delta[i] * k;
            }
            ctx.setOutput("out", carrier(delta));
        }
    }

    /**
     * Whether two vectors agree to within a tolerance, component by component.
     *
     * <p>The comparison to reach for instead of an equality test: two floats that came from
     * different arithmetic are almost never bit-identical, so {@code ==} on a computed vector is a
     * condition that reads as correct and is false forever.
     */
    @NodeAttribute(name = "vector_nearly_equals", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class NearlyEquals extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            VectorPorts.in(ctx, "a");
            VectorPorts.in(ctx, "b");
            ctx.addInputPort("epsilon", Float.class).withDefaultValue(1e-4f);
            ctx.addOutputPort("out", Boolean.class);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("a"));
            float[] q = components(ctx.getInputRaw("b"));
            float eps = Math.abs(ctx.getFloat("epsilon", 1e-4f));
            boolean equal = true;
            // Over the wider width, so a Vector4 that differs only in w is not reported as equal to
            // a Vector3 — the missing component reads zero, exactly as it does everywhere else here.
            for (int i = 0, w = Math.max(p.length, q.length); i < w && equal; i++) {
                equal = Math.abs(at(p, i) - at(q, i)) <= eps;
            }
            ctx.setOutput("out", equal);
        }
    }

    /**
     * {@code q * (p·q / q·q)}, zero when {@code q} is zero.
     *
     * <p>Takes the component arrays rather than the context so {@link Reject} can hand it the ones it
     * already read — reading them again would allocate a second copy of both, on a node that is two
     * array reads of real work.</p>
     */
    private static float[] project(float[] p, float[] q) {
        int width = Math.max(p.length, q.length);
        float[] out = new float[width];
        float qq = lengthSquared(q);
        if (qq >= Vectors.EPSILON * Vectors.EPSILON) {
            float k = dot(p, q) / qq;
            for (int i = 0; i < width; i++) out[i] = at(q, i) * k;
        }
        return out;
    }
}
