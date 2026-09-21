package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.quaternion.QuaternionNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorGeometryNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;

@GameTestHolder(Kilagraph.MODID)
public final class QuaternionNodeGameTest {
    private static final float EPS = 1e-4f;

    private static final float ROT_EPS = 1e-3f;

    private static final Vector3f FORWARD = new Vector3f(0f, 0f, 1f);

    private static final float[][] ANGLES = {
            {0f, 0f}, {90f, 0f}, {-90f, 0f}, {180f, 0f},
            {0f, 45f}, {0f, -45f}, {37f, 21f}, {-143f, -67f}, {250f, 12f},
    };

    private QuaternionNodeGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fromEulerMatchesVectorFromRotation(GameTestHelper helper) {
        for (float[] yp : ANGLES) {
            float yaw = yp[0], pitch = yp[1];
            Object expected = fromRotation(yaw, pitch);
            Object actual = rotateVector(fromEuler(yaw, pitch, 0f), FORWARD);
            assertVec(helper, "yaw " + yaw + " pitch " + pitch,
                    Vectors.components(expected), actual, ROT_EPS);
        }

        assertVec(helper, "looking straight down", new float[] {0f, -1f, 0f},
                rotateVector(fromEuler(0f, 90f, 0f), FORWARD), ROT_EPS);
        assertVec(helper, "looking straight up", new float[] {0f, 1f, 0f},
                rotateVector(fromEuler(0f, -90f, 0f), FORWARD), ROT_EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void eulerRoundTrips(GameTestHelper helper) {
        for (float[] yp : ANGLES) {
            Quaternionf q = fromEuler(yp[0], yp[1], 0f);
            float[] back = toEuler(q);

            assertSame(helper, "round trip at yaw " + yp[0] + " pitch " + yp[1],
                    q, fromEuler(back[0], back[1], back[2]));
        }

        float[] back = toEuler(fromEuler(37f, 21f, 15f));
        assertEq(helper, "yaw survives", 37f, back[0], ROT_EPS);
        assertEq(helper, "pitch survives", 21f, back[1], ROT_EPS);
        assertEq(helper, "roll survives", 15f, back[2], ROT_EPS);

        float[] folded = toEuler(fromEuler(250f, 0f, 0f));
        assertEq(helper, "250 degrees of yaw is -110", -110f, folded[0], ROT_EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fromAxisAngleMatchesRotateAboutAxis(GameTestHelper helper) {
        Vector3f[] axes = {
                new Vector3f(0f, 1f, 0f), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 0f, 1f),
                new Vector3f(1f, 2f, 3f),
        };
        Vector3f v = new Vector3f(1f, 0.5f, -2f);
        for (Vector3f axis : axes) {
            for (float angle : new float[] {0f, 30f, 90f, -120f, 270f}) {
                Object viaVector = rotateAboutAxis(v, axis, angle);
                Object viaQuat = rotateVector(fromAxisAngle(axis, angle), v);
                assertVec(helper, "about " + axis + " by " + angle,
                        Vectors.components(viaVector), viaQuat, ROT_EPS);
            }
        }

        assertSame(helper, "a zero axis", new Quaternionf(),
                fromAxisAngle(new Vector3f(0f, 0f, 0f), 90f));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multiplyComposesRightToLeft(GameTestHelper helper) {
        Quaternionf turn = fromEuler(90f, 0f, 0f);
        Quaternionf look = fromEuler(0f, 45f, 0f);
        Vector3f v = new Vector3f(0f, 0f, 1f);

        Object stepwise = rotateVector(turn, (Vector3f) rotateVector(look, v));
        Object composed = rotateVector(multiply(turn, look), v);
        assertVec(helper, "turn after look", Vectors.components(stepwise), composed, ROT_EPS);

        Object swapped = rotateVector(multiply(look, turn), v);
        assertFalse(helper, "swapping the operands must not give the same answer",
                nearly(Vectors.components(stepwise), Vectors.components(swapped), 1e-2f));

        Quaternionf none = identity();
        assertSame(helper, "the identity changes nothing", turn, multiply(turn, none));
        assertSame(helper, "from either side", turn, multiply(none, turn));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void identityIsNoRotation(GameTestHelper helper) {
        Quaternionf none = identity();
        assertEq(helper, "x", 0f, none.x, 0f);
        assertEq(helper, "y", 0f, none.y, 0f);
        assertEq(helper, "z", 0f, none.z, 0f);

        assertEq(helper, "w", 1f, none.w, 0f);

        assertVec(helper, "a vector through it is unchanged", new float[] {1f, 2f, 3f},
                rotateVector(none, new Vector3f(1f, 2f, 3f)), EPS);
        assertEq(helper, "no angle from itself", 0f, angleBetween(none, none), EPS);
        float[] euler = toEuler(none);
        assertEq(helper, "yaw", 0f, euler[0], EPS);
        assertEq(helper, "pitch", 0f, euler[1], EPS);
        assertEq(helper, "roll", 0f, euler[2], EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void inverseUndoesTheRotation(GameTestHelper helper) {
        Quaternionf q = fromEuler(37f, 21f, 15f);
        assertSame(helper, "q times its inverse is the identity",
                new Quaternionf(), multiply(q, inverse(q)));

        Vector3f v = new Vector3f(1f, 0.5f, -2f);
        Object there = rotateVector(q, v);
        Object andBack = rotateVector(inverse(q), (Vector3f) there);
        assertVec(helper, "rotate and unrotate", new float[] {1f, 0.5f, -2f}, andBack, ROT_EPS);

        assertSame(helper, "the inverse of nothing", new Quaternionf(),
                inverse(new Quaternionf(0f, 0f, 0f, 0f)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void slerpTakesTheShortArc(GameTestHelper helper) {
        Quaternionf none = new Quaternionf();
        Quaternionf ninety = fromEuler(90f, 0f, 0f);

        assertSame(helper, "t=0 is a", none, slerp(none, ninety, 0f));
        assertSame(helper, "t=1 is b", ninety, slerp(none, ninety, 1f));
        assertSame(helper, "halfway is 45 degrees of yaw",
                fromEuler(45f, 0f, 0f), slerp(none, ninety, 0.5f));

        Quaternionf almostRound = fromEuler(350f, 0f, 0f);
        assertSame(helper, "the short arc, not the long one",
                fromEuler(355f, 0f, 0f), slerp(none, almostRound, 0.5f));

        assertSame(helper, "t below zero clamps", none, slerp(none, ninety, -5f));
        assertSame(helper, "t above one clamps", ninety, slerp(none, ninety, 5f));

        for (float t = 0f; t <= 1f; t += 0.125f) {
            Quaternionf mid = slerp(none, fromEuler(37f, 21f, 15f), t);
            assertEq(helper, "unit length at t=" + t, 1f, length(mid), EPS);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void fromToTurnsOneDirectionIntoTheOther(GameTestHelper helper) {
        Vector3f[][] pairs = {
                {new Vector3f(0f, 0f, 1f), new Vector3f(1f, 0f, 0f)},
                {new Vector3f(0f, 0f, 1f), new Vector3f(0f, 1f, 0f)},
                {new Vector3f(1f, 2f, 3f), new Vector3f(-4f, 0.5f, 2f)},

                {new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, -1f)},
                {new Vector3f(0f, 1f, 0f), new Vector3f(0f, -1f, 0f)},

                {new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, 1f)},
        };
        for (Vector3f[] pair : pairs) {
            Object rotated = rotateVector(fromTo(pair[0], pair[1]), pair[0]);

            assertComponents(helper, pair[0] + " onto " + pair[1],
                    unit(pair[1]), unit(rotated), ROT_EPS);
        }

        assertSame(helper, "a zero source", new Quaternionf(),
                fromTo(new Vector3f(0f, 0f, 0f), new Vector3f(0f, 0f, 1f)));
        assertSame(helper, "a zero target", new Quaternionf(),
                fromTo(new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, 0f)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void angleBetweenIsTheShortestTurn(GameTestHelper helper) {
        Quaternionf none = new Quaternionf();
        assertEq(helper, "nothing to nothing", 0f, angleBetween(none, none), EPS);
        assertEq(helper, "a quarter turn", 90f, angleBetween(none, fromEuler(90f, 0f, 0f)), ROT_EPS);
        assertEq(helper, "a half turn", 180f, angleBetween(none, fromEuler(180f, 0f, 0f)), 1e-2f);

        assertEq(helper, "350 degrees of yaw is ten degrees away", 10f,
                angleBetween(none, fromEuler(350f, 0f, 0f)), ROT_EPS);

        Quaternionf q = fromEuler(37f, 21f, 15f);
        Quaternionf negated = new Quaternionf(-q.x, -q.y, -q.z, -q.w);
        assertEq(helper, "a quaternion and its negation", 0f, angleBetween(q, negated), ROT_EPS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theVec4BridgeRoundTripsExactly(GameTestHelper helper) {
        Quaternionf q = fromEuler(37f, 21f, 15f);
        Object packed = toVec4(q);
        assertTrue(helper, "packs into a Vector4, got " + packed, packed instanceof Vector4f);
        Vector4f v = (Vector4f) packed;

        assertEq(helper, "x", q.x, v.x, 0f);
        assertEq(helper, "y", q.y, v.y, 0f);
        assertEq(helper, "z", q.z, v.z, 0f);
        assertEq(helper, "w is the fourth component", q.w, v.w, 0f);

        Object back = fromVec4(v);
        assertTrue(helper, "unpacks into a Quaternionf, got " + back, back instanceof Quaternionf);
        assertSame(helper, "the round trip", q, (Quaternionf) back);

        Object unnormalised = fromVec4(new Vector4f(0f, 0f, 0f, 2f));
        assertEq(helper, "a half-length quaternion is not rescaled", 2f,
                length((Quaternionf) unnormalised), EPS);

        assertSame(helper, "an all-zero Vector4", new Quaternionf(),
                (Quaternionf) fromVec4(new Vector4f(0f, 0f, 0f, 0f)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void quatPinsDoNotAcceptVectorsOrViceVersa(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel packer = addNode(g, QuaternionNodes.ToVec4.class);
        NodeModel unpacker = addNode(g, QuaternionNodes.FromVec4.class);

        NodeModel otherUnpacker = addNode(g, QuaternionNodes.FromVec4.class);
        NodeModel normalize = addNode(g, QuaternionNodes.Normalize.class);

        assertEq(helper, "quat_to_vec4 in", KGTypeHandles.QUAT, handle(packer, "in", true));
        assertEq(helper, "quat_to_vec4 out", KGTypeHandles.VEC4, handle(packer, "out", false));
        assertEq(helper, "quat_from_vec4 in", KGTypeHandles.VEC4, handle(unpacker, "in", true));
        assertEq(helper, "quat_from_vec4 out", KGTypeHandles.QUAT, handle(unpacker, "out", false));

        assertTrue(helper, "a quaternion reaches the packer",
                canAssign(g, unpacker.getOutputsById().get("out"), normalize, "in"));
        assertTrue(helper, "a Vector4 reaches the unpacker",
                canAssign(g, packer.getOutputsById().get("out"), unpacker, "in"));

        assertFalse(helper, "a Vector4 must not reach a quaternion pin directly",
                canAssign(g, packer.getOutputsById().get("out"), normalize, "in"));
        assertFalse(helper, "a quaternion must not reach a vector pin directly",
                canAssign(g, unpacker.getOutputsById().get("out"), otherUnpacker, "in"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void degenerateInputsAnswerRatherThanFail(GameTestHelper helper) {
        Quaternionf zero = new Quaternionf(0f, 0f, 0f, 0f);
        assertSame(helper, "normalizing nothing", new Quaternionf(), normalize(zero));
        assertSame(helper, "composing with nothing", new Quaternionf(), multiply(zero, zero));
        assertEq(helper, "the angle from nothing", 0f, angleBetween(zero, zero), EPS);
        assertVec(helper, "a zero rotation leaves the vector alone", new float[] {1f, 2f, 3f},
                rotateVector(zero, new Vector3f(1f, 2f, 3f)), EPS);
        assertSame(helper, "interpolating between nothings", new Quaternionf(),
                slerp(zero, zero, 0.5f));

        assertVec(helper, "rotating a zero vector", new float[] {0f, 0f, 0f},
                rotateVector(fromEuler(37f, 21f, 15f), new Vector3f(0f, 0f, 0f)), EPS);
        helper.succeed();
    }

    private static Quaternionf identity() {
        BlueprintGraph g = newGraph();
        return quat(g, addNode(g, QuaternionNodes.Identity.class), "out");
    }

    private static Quaternionf fromEuler(float yaw, float pitch, float roll) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.FromEuler.class);
        setInputConstant(n, "yaw", yaw);
        setInputConstant(n, "pitch", pitch);
        setInputConstant(n, "roll", roll);
        return quat(g, n, "out");
    }

    private static float[] toEuler(Quaternionf q) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.ToEuler.class);
        setInputConstant(n, "in", q);
        GraphExecutor exec = new GraphExecutor(g);
        return new float[] {
                exec.evaluate(n.getOutputsById().get("yaw"), Float.class),
                exec.evaluate(n.getOutputsById().get("pitch"), Float.class),
                exec.evaluate(n.getOutputsById().get("roll"), Float.class),
        };
    }

    private static Quaternionf fromAxisAngle(Vector3f axis, float angle) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.FromAxisAngle.class);
        setInputConstant(n, "axis", axis);
        setInputConstant(n, "angle", angle);
        return quat(g, n, "out");
    }

    private static Quaternionf fromTo(Vector3f from, Vector3f to) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.FromTo.class);
        setInputConstant(n, "from", from);
        setInputConstant(n, "to", to);
        return quat(g, n, "out");
    }

    private static Quaternionf multiply(Quaternionf a, Quaternionf b) {
        return binaryQuat(QuaternionNodes.Multiply.class, a, b);
    }

    private static Quaternionf inverse(Quaternionf q) {
        return unaryQuat(QuaternionNodes.Inverse.class, q);
    }

    private static Quaternionf normalize(Quaternionf q) {
        return unaryQuat(QuaternionNodes.Normalize.class, q);
    }

    private static Quaternionf slerp(Quaternionf a, Quaternionf b, float t) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.Slerp.class);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        setInputConstant(n, "t", t);
        return quat(g, n, "out");
    }

    private static float angleBetween(Quaternionf a, Quaternionf b) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.AngleBetween.class);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
    }

    private static Object rotateVector(Quaternionf q, Vector3f v) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, QuaternionNodes.RotateVector.class);
        setInputConstant(n, "q", q);
        setInputConstant(n, "in", v);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class);
    }

    private static Object toVec4(Quaternionf q) {
        Built b = newGraphWith(QuaternionNodes.ToVec4.class, "in", q);
        return new GraphExecutor(b.graph).evaluate(b.outPort(), Object.class);
    }

    private static Object fromVec4(Vector4f v) {
        Built b = newGraphWith(QuaternionNodes.FromVec4.class, "in", v);
        return new GraphExecutor(b.graph).evaluate(b.outPort(), Object.class);
    }

    private static Object fromRotation(float yaw, float pitch) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.FromRotation.class);
        setInputConstant(n, "yaw", yaw);
        setInputConstant(n, "pitch", pitch);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class);
    }

    private static Object rotateAboutAxis(Vector3f v, Vector3f axis, float angle) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.RotateAxis.class);
        setInputConstant(n, "in", v);
        setInputConstant(n, "axis", axis);
        setInputConstant(n, "angle", angle);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class);
    }

    private record Built(BlueprintGraph graph, NodeModel node) {
        PortModel outPort() {
            return node.getOutputsById().get("out");
        }
    }

    private static Built newGraphWith(Class<? extends Node> nodeClass, String portId, Object value) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, portId, value);
        return new Built(g, n);
    }

    private static Quaternionf unaryQuat(Class<? extends Node> nodeClass, Quaternionf in) {
        Built b = newGraphWith(nodeClass, "in", in);
        return quat(b.graph, b.node, "out");
    }

    private static Quaternionf binaryQuat(Class<? extends Node> nodeClass, Quaternionf a, Quaternionf b) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        return quat(g, n, "out");
    }

    private static Quaternionf quat(BlueprintGraph g, NodeModel node, String port) {
        Object v = new GraphExecutor(g).evaluate(node.getOutputsById().get(port), Object.class);
        if (!(v instanceof Quaternionf q)) {
            throw new AssertionError(node.getUid() + "." + port + " answered " + v + ", not a quaternion");
        }
        return q;
    }

    private static TypeHandle handle(NodeModel node, String portId, boolean input) {
        PortModel port = input ? node.getInputsById().get(portId) : node.getOutputsById().get(portId);
        return port == null ? null : port.getDataTypeHandle();
    }

    private static boolean canAssign(BlueprintGraph g, PortModel source, NodeModel node, String portId) {
        return g.graphModel.canAssignTo(node.getInputsById().get(portId), source);
    }

    private static float length(Quaternionf q) {
        return (float) Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
    }

    private static float[] unit(Object v) {
        float[] c = Vectors.components(v);
        float len = (float) Math.sqrt(Vectors.lengthSquared(c));
        if (len < Vectors.EPSILON) return c;
        float[] out = new float[c.length];
        for (int i = 0; i < c.length; i++) out[i] = c[i] / len;
        return out;
    }

    private static void assertSame(GameTestHelper helper, String label, Quaternionf expected,
                                   Quaternionf actual) {
        float la = length(expected), lb = length(actual);
        if (la < Vectors.EPSILON || lb < Vectors.EPSILON) {
            helper.fail(label + ": a zero quaternion is not an orientation (" + expected + " vs " + actual + ")");
            return;
        }
        float dot = Math.abs((expected.x * actual.x + expected.y * actual.y
                + expected.z * actual.z + expected.w * actual.w) / (la * lb));
        if (dot < 1f - 1e-4f) {
            helper.fail(label + ": " + expected + " and " + actual + " are different orientations");
        }
    }

    private static boolean nearly(float[] a, float[] b, float epsilon) {
        for (int i = 0, w = Math.max(a.length, b.length); i < w; i++) {
            if (Math.abs(Vectors.at(a, i) - Vectors.at(b, i)) > epsilon) return false;
        }
        return true;
    }

    private static void assertVec(GameTestHelper helper, String label, float[] expected,
                                  Object actual, float epsilon) {
        assertComponents(helper, label, expected, Vectors.components(actual), epsilon);
    }

    private static void assertComponents(GameTestHelper helper, String label, float[] expected,
                                         float[] actual, float epsilon) {
        assertEq(helper, label + " width", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEq(helper, label + " component " + i, expected[i], actual[i], epsilon);
        }
    }
}
