package com.lowdragmc.kilagraph.blueprint.nodes.quaternion;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.lowdragmc.kilagraph.graph.type.Vectors.at;
import static com.lowdragmc.kilagraph.graph.type.Vectors.components;

/**
 * Rotations, as unit quaternions ({@link KGTypeHandles#QUAT}).
 *
 * <p><b>Every node here normalizes what it reads and what it writes.</b> A quaternion only means a
 * rotation while it is unit length, and repeated composition drifts off it — so the invariant is
 * restored at each node rather than left to the graph author to remember. A value that is not a
 * rotation at all (zero length, or a pin that was never wired) reads as the identity, which is the
 * same "answer, do not fail" rule the vector geometry nodes follow.
 *
 * <p><b>Angles are in degrees, and yaw/pitch/roll are Minecraft's.</b> Yaw is measured clockwise
 * looking down, which is the opposite sense to JOML's Y rotation — hence the sign flips in
 * {@link #fromEuler} and {@link ToEuler}. Getting this wrong is a mirror image that only shows up as
 * an entity facing backwards, so the round trip is pinned by a test.
 */
public final class QuaternionNodes {
    private static final String GROUP = "quaternion";

    /** Squared-length floor below which a quaternion is not a rotation. @see Vectors#EPSILON */
    private static final float EPSILON_SQ = Vectors.EPSILON * Vectors.EPSILON;

    private QuaternionNodes() {
    }

    /** The rotation that does nothing — the starting point of a composition chain. */
    @NodeAttribute(name = "quat_identity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Identity extends AnnotatedNode {
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) new Quaternionf());
        }
    }

    /**
     * A turn of {@code angle} degrees about {@code axis}, right-handed.
     *
     * <p>Degenerate case: no axis is no rotation, so a zero axis answers the identity rather than a
     * NaN quaternion that would poison everything downstream.</p>
     */
    @NodeAttribute(name = "quat_from_axis_angle", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromAxisAngle extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            ctx.addInputPort("axis", KGTypeHandles.VEC3).withDefaultValue(new Vector3f(0f, 1f, 0f));
            ctx.addInputPort("angle", Float.class).withDefaultValue(0f);
            ctx.addOutputPort("out", KGTypeHandles.QUAT);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] a = components(ctx.getInputRaw("axis"));
            float x = at(a, 0), y = at(a, 1), z = at(a, 2);
            if (x * x + y * y + z * z < EPSILON_SQ) {
                ctx.setOutput("out", (Object) new Quaternionf());
                return;
            }
            ctx.setOutput("out", (Object) new Quaternionf().rotationAxis(
                    (float) Math.toRadians(ctx.getFloat("angle", 0f)), x, y, z));
        }
    }

    /** Minecraft yaw/pitch/roll in degrees → a rotation. The inverse of {@link ToEuler}. */
    @NodeAttribute(name = "quat_from_euler", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromEuler extends AnnotatedNode {
        @InputPort public float yaw = 0f;
        @InputPort public float pitch = 0f;
        @InputPort public float roll = 0f;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) fromEuler(
                    ctx.getFloat("yaw", 0f), ctx.getFloat("pitch", 0f), ctx.getFloat("roll", 0f)));
        }
    }

    /**
     * A rotation → Minecraft yaw/pitch/roll in degrees.
     *
     * <p>⚠️ Not a unique answer: every orientation has infinitely many Euler triples, and near
     * pitch ±90° neighbouring rotations map to wildly different yaw/roll (gimbal lock). Compose in
     * quaternions and convert once at the end, not per step.</p>
     */
    @NodeAttribute(name = "quat_to_euler", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToEuler extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public float yaw;
        @OutputPort public float pitch;
        @OutputPort public float roll;

        @Override
        public void evaluate(EvalContext ctx) {
            Quaternionf q = unit(ctx, "in");
            // YXZ, matching fromEuler's rotationYXZ — a different order would round-trip to a
            // different triple for the same rotation.
            Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());

            ctx.setOutput("yaw", (float) -Math.toDegrees(euler.y));
            ctx.setOutput("pitch", (float) Math.toDegrees(euler.x));
            ctx.setOutput("roll", (float) Math.toDegrees(euler.z));
        }
    }

    /**
     * The shortest rotation taking direction {@code from} to direction {@code to}.
     *
     * <p>Lengths are ignored — only the directions matter. Degenerate case: either side being zero
     * has no direction, and answers the identity.</p>
     */
    @NodeAttribute(name = "quat_from_to", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromTo extends AnnotatedNode {
        @Override
        protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
            ctx.addInputPort("from", KGTypeHandles.VEC3).withDefaultValue(new Vector3f(0f, 0f, 1f));
            ctx.addInputPort("to", KGTypeHandles.VEC3).withDefaultValue(new Vector3f(0f, 0f, 1f));
            ctx.addOutputPort("out", KGTypeHandles.QUAT);
        }

        @Override
        public void evaluate(EvalContext ctx) {
            float[] p = components(ctx.getInputRaw("from"));
            float[] q = components(ctx.getInputRaw("to"));
            Vector3f a = new Vector3f(at(p, 0), at(p, 1), at(p, 2));
            Vector3f b = new Vector3f(at(q, 0), at(q, 1), at(q, 2));
            if (a.lengthSquared() < EPSILON_SQ || b.lengthSquared() < EPSILON_SQ) {
                ctx.setOutput("out", (Object) new Quaternionf());
                return;
            }
            ctx.setOutput("out", (Object) new Quaternionf().rotationTo(a, b).normalize());
        }
    }

    /**
     * Composition, {@code a * b}: the rotation that applies <b>{@code b} first, then {@code a}</b> —
     * the same order as multiplying the equivalent matrices, and the opposite of the reading order.
     *
     * <p>Chaining is what this type is for. The alternative a graph reaches for — converting to Euler
     * angles and adding them — is only correct one axis at a time.</p>
     */
    @NodeAttribute(name = "quat_multiply", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Multiply extends AnnotatedNode {
        @InputPort public Quaternionf a;
        @InputPort public Quaternionf b;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) unit(ctx, "a").mul(unit(ctx, "b")).normalize());
        }
    }

    /**
     * The rotation that undoes {@code in}.
     *
     * <p>The conjugate rather than the true inverse, which is the conjugate over the squared length
     * — the same thing for a unit quaternion, and {@link #unit} has already made it one.</p>
     */
    @NodeAttribute(name = "quat_inverse", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Inverse extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) unit(ctx, "in").conjugate());
        }
    }

    /**
     * Restores unit length. Every node here does this to what it reads anyway; this one exists for a
     * value that arrived over a {@code quat_from_vec4} bridge and is about to be stored.
     */
    @NodeAttribute(name = "quat_normalize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Normalize extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) unit(ctx, "in"));
        }
    }

    /**
     * Interpolates along the shortest arc from {@code a} to {@code b} at constant angular speed —
     * the whole reason to hold an orientation as a quaternion rather than as three angles.
     *
     * <p>{@code t} is clamped to [0, 1]. JOML's {@code slerp} already takes the short way round, so
     * a pair whose dot product is negative does not spin the long way.</p>
     */
    @NodeAttribute(name = "quat_slerp", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Slerp extends AnnotatedNode {
        @InputPort public Quaternionf a;
        @InputPort public Quaternionf b;
        @InputPort public float t = 0f;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            float k = Math.min(1f, Math.max(0f, ctx.getFloat("t", 0f)));
            ctx.setOutput("out", (Object) unit(ctx, "a").slerp(unit(ctx, "b"), k).normalize());
        }
    }

    /** Applies the rotation to a vector. Length is preserved, since the rotation is unit. */
    @NodeAttribute(name = "quat_rotate_vector", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RotateVector extends AnnotatedNode {
        @InputPort public Quaternionf q;
        @InputPort public Vector3f in;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            Vector3f target = new Vector3f(at(v, 0), at(v, 1), at(v, 2));
            ctx.setOutput("out", (Object) unit(ctx, "q").transform(target));
        }
    }

    /**
     * The angle in degrees of the shortest rotation between two orientations, in [0, 180].
     *
     * <p>{@code |dot|} rather than {@code dot}: {@code q} and {@code -q} are the same rotation, and
     * without the absolute value two identical orientations that happened to be signed differently
     * would read as 360° apart.</p>
     */
    @NodeAttribute(name = "quat_angle_between", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AngleBetween extends AnnotatedNode {
        @InputPort public Quaternionf a;
        @InputPort public Quaternionf b;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            float dot = Math.abs(unit(ctx, "a").dot(unit(ctx, "b")));

            // acos is undefined past 1, and a normalized pair can land a hair above it.
            double cos = Math.min(1d, dot);
            ctx.setOutput("out", (float) Math.toDegrees(2d * Math.acos(cos)));
        }
    }

    /** The four components as a VEC4 {@code (x, y, z, w)} — the escape hatch into vector maths. */
    @NodeAttribute(name = "quat_to_vec4", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVec4 extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Vector4f out;

        @Override
        public void evaluate(EvalContext ctx) {
            // read, not unit: this is the bridge out, and it reports the components that are there.
            Quaternionf q = read(ctx, "in");
            ctx.setOutput("out", (Object) new Vector4f(q.x, q.y, q.z, q.w));
        }
    }

    /** A VEC4 {@code (x, y, z, w)} read back as a rotation. A zero vector answers the identity. */
    @NodeAttribute(name = "quat_from_vec4", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromVec4 extends AnnotatedNode {
        @InputPort public Vector4f in;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            float[] v = components(ctx.getInputRaw("in"));
            Quaternionf q = new Quaternionf(at(v, 0), at(v, 1), at(v, 2), at(v, 3));
            ctx.setOutput("out", (Object) (lengthSquared(q) < EPSILON_SQ ? new Quaternionf() : q));
        }
    }

    /**
     * Minecraft yaw/pitch/roll in degrees → a rotation.
     *
     * <p>Public because it is the one place the convention is written down: {@code rotationYXZ} with
     * yaw negated. Anything else that needs to build a rotation from a Minecraft facing goes through
     * here rather than repeating the sign flip and eventually mirroring it.</p>
     */
    public static Quaternionf fromEuler(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yaw),
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(roll));
    }

    /** The pin's value as a unit quaternion — the identity when it is not a rotation. */
    private static Quaternionf unit(EvalContext ctx, String id) {
        Quaternionf q = read(ctx, id);
        return lengthSquared(q) < EPSILON_SQ ? new Quaternionf() : q.normalize();
    }

    /**
     * The pin's value, <b>copied</b>. Never the instance on the wire: JOML's operations mutate in
     * place, so composing would rewrite the upstream node's output and corrupt any other consumer.
     */
    private static Quaternionf read(EvalContext ctx, String id) {
        return ctx.getInputRaw(id) instanceof Quaternionf q
                ? new Quaternionf(q) : new Quaternionf();
    }

    private static float lengthSquared(Quaternionf q) {
        return q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
    }
}
