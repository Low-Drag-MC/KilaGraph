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

public final class QuaternionNodes {
    private static final String GROUP = "quaternion";

    private static final float EPSILON_SQ = Vectors.EPSILON * Vectors.EPSILON;

    private QuaternionNodes() {
    }

    @NodeAttribute(name = "quat_identity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Identity extends AnnotatedNode {
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) new Quaternionf());
        }
    }

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

    @NodeAttribute(name = "quat_to_euler", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToEuler extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public float yaw;
        @OutputPort public float pitch;
        @OutputPort public float roll;

        @Override
        public void evaluate(EvalContext ctx) {
            Quaternionf q = unit(ctx, "in");
            Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());

            ctx.setOutput("yaw", (float) -Math.toDegrees(euler.y));
            ctx.setOutput("pitch", (float) Math.toDegrees(euler.x));
            ctx.setOutput("roll", (float) Math.toDegrees(euler.z));
        }
    }

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

    @NodeAttribute(name = "quat_inverse", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Inverse extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) unit(ctx, "in").conjugate());
        }
    }

    @NodeAttribute(name = "quat_normalize", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Normalize extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Quaternionf out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", (Object) unit(ctx, "in"));
        }
    }

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

    @NodeAttribute(name = "quat_angle_between", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AngleBetween extends AnnotatedNode {
        @InputPort public Quaternionf a;
        @InputPort public Quaternionf b;
        @OutputPort public float out;

        @Override
        public void evaluate(EvalContext ctx) {
            float dot = Math.abs(unit(ctx, "a").dot(unit(ctx, "b")));

            double cos = Math.min(1d, dot);
            ctx.setOutput("out", (float) Math.toDegrees(2d * Math.acos(cos)));
        }
    }

    @NodeAttribute(name = "quat_to_vec4", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ToVec4 extends AnnotatedNode {
        @InputPort public Quaternionf in;
        @OutputPort public Vector4f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Quaternionf q = read(ctx, "in");
            ctx.setOutput("out", (Object) new Vector4f(q.x, q.y, q.z, q.w));
        }
    }

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

    public static Quaternionf fromEuler(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yaw),
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(roll));
    }

    private static Quaternionf unit(EvalContext ctx, String id) {
        Quaternionf q = read(ctx, id);
        return lengthSquared(q) < EPSILON_SQ ? new Quaternionf() : q.normalize();
    }

    private static Quaternionf read(EvalContext ctx, String id) {
        return ctx.getInputRaw(id) instanceof Quaternionf q
                ? new Quaternionf(q) : new Quaternionf();
    }

    private static float lengthSquared(Quaternionf q) {
        return q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
    }
}
