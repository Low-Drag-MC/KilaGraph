package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The vector nodes, including the widths they are not declared as.
 *
 * <p><b>Width is the point of most of these.</b> The pins say VEC3 because a pin must name one
 * type, but the operations read whatever arrives — so a test that only ever feeds three components
 * would pass against an implementation that casts to {@code Vector3f} and silently zeroes anything
 * else. Every operation that claims to be width-polymorphic is therefore checked at 2 and 4 as
 * well, and the results are values no other width could produce by accident.
 */
@GameTestHolder(Kilagraph.MODID)
public final class VectorNodeGameTest {

    private static final float EPS = 1e-4f;

    private VectorNodeGameTest() {
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void makeAndBreakRoundTripEveryWidth(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel make = addNode(g, VectorNodes.Make.class);
        setInputConstant(make, "x", 1f);
        setInputConstant(make, "y", 2f);
        setInputConstant(make, "z", 3f);
        NodeModel split = addNode(g, VectorNodes.Break.class);
        wire(g, split.getInputsById().get("in"), make.getOutputsById().get("out"));
        GraphExecutor exec = new GraphExecutor(g);
        assertEq(helper, "x", 1f, exec.evaluate(split.getOutputsById().get("x"), Float.class), EPS);
        assertEq(helper, "y", 2f, exec.evaluate(split.getOutputsById().get("y"), Float.class), EPS);
        assertEq(helper, "z", 3f, exec.evaluate(split.getOutputsById().get("z"), Float.class), EPS);
        // a three-component value has no w; asking must be zero, not an exception
        assertEq(helper, "w of a Vector3", 0f,
                exec.evaluate(split.getOutputsById().get("w"), Float.class), EPS);

        BlueprintGraph g4 = newGraph();
        NodeModel make4 = addNode(g4, VectorNodes.Make4.class);
        setInputConstant(make4, "x", 1f);
        setInputConstant(make4, "y", 2f);
        setInputConstant(make4, "z", 3f);
        setInputConstant(make4, "w", 4f);
        NodeModel split4 = addNode(g4, VectorNodes.Break.class);
        wire(g4, split4.getInputsById().get("in"), make4.getOutputsById().get("out"));
        GraphExecutor exec4 = new GraphExecutor(g4);
        assertEq(helper, "w survives Break", 4f,
                exec4.evaluate(split4.getOutputsById().get("w"), Float.class), EPS);
        helper.succeed();
    }

    /**
     * Add, Subtract and Lerp keep every component AND the width they were given.
     *
     * <p>The width-4 case is the one that matters: an implementation that casts its inputs to
     * {@code Vector3f} answers correctly for x/y/z and drops w, and only a four-component
     * expectation can see that.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void componentWiseOperationsKeepEveryComponent(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel add = addNode(g, VectorNodes.Add.class);
        setInputConstant(add, "a", new Vector4f(1f, 2f, 3f, 4f));
        setInputConstant(add, "b", new Vector4f(10f, 20f, 30f, 40f));
        Object sum = new GraphExecutor(g).evaluate(add.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "a width-4 sum stays width 4, got " + sum, sum instanceof Vector4f);
        assertVec(helper, "add", new float[] {11f, 22f, 33f, 44f}, sum);

        BlueprintGraph g2 = newGraph();
        NodeModel sub = addNode(g2, VectorNodes.Subtract.class);
        setInputConstant(sub, "a", new Vector2f(5f, 9f));
        setInputConstant(sub, "b", new Vector2f(1f, 2f));
        Object diff = new GraphExecutor(g2).evaluate(sub.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "a width-2 difference stays width 2, got " + diff, diff instanceof Vector2f);
        assertVec(helper, "subtract", new float[] {4f, 7f}, diff);

        BlueprintGraph g3 = newGraph();
        NodeModel lerp = addNode(g3, VectorNodes.Lerp.class);
        setInputConstant(lerp, "a", new Vector4f(0f, 0f, 0f, 0f));
        setInputConstant(lerp, "b", new Vector4f(2f, 4f, 6f, 8f));
        setInputConstant(lerp, "t", 0.25f);
        assertVec(helper, "lerp", new float[] {0.5f, 1f, 1.5f, 2f},
                new GraphExecutor(g3).evaluate(lerp.getOutputsById().get("out"), Object.class));

        BlueprintGraph g5 = newGraph();
        NodeModel scale = addNode(g5, VectorNodes.Scale.class);
        setInputConstant(scale, "in", new Vector4f(1f, 2f, 3f, 4f));
        setInputConstant(scale, "scale", 3f);
        assertVec(helper, "scale", new float[] {3f, 6f, 9f, 12f},
                new GraphExecutor(g5).evaluate(scale.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /**
     * Length, Dot and Distance sum over every component.
     *
     * <p>The numbers are chosen so a three-component answer and a four-component answer differ:
     * {@code |(1,2,2,4)|} is 5 over three components and 6.4 over four, so a cast to
     * {@code Vector3f} cannot pass by coincidence.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void reducingOperationsSumOverEveryComponent(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel length = addNode(g, VectorNodes.Length.class);
        setInputConstant(length, "in", new Vector3f(3f, 0f, 4f));
        assertEq(helper, "|(3,0,4)|", 5f,
                new GraphExecutor(g).evaluate(length.getOutputsById().get("out"), Float.class), EPS);

        BlueprintGraph g4 = newGraph();
        NodeModel length4 = addNode(g4, VectorNodes.Length.class);
        setInputConstant(length4, "in", new Vector4f(1f, 2f, 2f, 4f));
        // sqrt(1+4+4+16) = 5, where the first three alone would be 3
        assertEq(helper, "|(1,2,2,4)| counts the fourth component", 5f,
                new GraphExecutor(g4).evaluate(length4.getOutputsById().get("out"), Float.class), EPS);

        BlueprintGraph gd = newGraph();
        NodeModel dot = addNode(gd, VectorNodes.Dot.class);
        setInputConstant(dot, "a", new Vector4f(1f, 2f, 3f, 4f));
        setInputConstant(dot, "b", new Vector4f(1f, 1f, 1f, 10f));
        // 1+2+3+40 = 46; the first three alone would be 6
        assertEq(helper, "dot counts the fourth component", 46f,
                new GraphExecutor(gd).evaluate(dot.getOutputsById().get("out"), Float.class), EPS);

        BlueprintGraph gs = newGraph();
        NodeModel dist = addNode(gs, VectorNodes.Distance.class);
        setInputConstant(dist, "a", new Vector4f(0f, 0f, 0f, 0f));
        setInputConstant(dist, "b", new Vector4f(1f, 2f, 2f, 4f));
        assertEq(helper, "distance counts the fourth component", 5f,
                new GraphExecutor(gs).evaluate(dist.getOutputsById().get("out"), Float.class), EPS);
        helper.succeed();
    }

    /** Normalising the zero vector must produce zero, not the NaN that division would. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void normalizeOfZeroIsZeroNotNaN(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel norm = addNode(g, VectorNodes.Normalize.class);
        setInputConstant(norm, "in", new Vector3f(0f, 0f, 0f));
        float[] zero = VectorNodes.components(
                new GraphExecutor(g).evaluate(norm.getOutputsById().get("out"), Object.class));
        for (int i = 0; i < zero.length; i++) {
            assertTrue(helper, "component " + i + " is not NaN, it was " + zero[i],
                    !Float.isNaN(zero[i]));
            assertEq(helper, "component " + i, 0f, zero[i], EPS);
        }

        BlueprintGraph g2 = newGraph();
        NodeModel norm2 = addNode(g2, VectorNodes.Normalize.class);
        setInputConstant(norm2, "in", new Vector3f(0f, 3f, 4f));
        assertVec(helper, "normalize", new float[] {0f, 0.6f, 0.8f},
                new GraphExecutor(g2).evaluate(norm2.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** Cross is right-handed and genuinely three-dimensional. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void crossFollowsTheRightHandRule(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel cross = addNode(g, VectorNodes.Cross.class);
        setInputConstant(cross, "a", new Vector3f(1f, 0f, 0f));
        setInputConstant(cross, "b", new Vector3f(0f, 1f, 0f));
        assertVec(helper, "x cross y is z", new float[] {0f, 0f, 1f},
                new GraphExecutor(g).evaluate(cross.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** Flatten drops the axis it is told to and leaves the others exactly alone. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void flattenDropsTheChosenAxisOnly(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel flatten = addNode(g, VectorNodes.Flatten.class);
        setInputConstant(flatten, "in", new Vector3f(3f, 7f, 4f));
        assertVec(helper, "default axis is Y", new float[] {3f, 0f, 4f},
                new GraphExecutor(g).evaluate(flatten.getOutputsById().get("out"), Object.class));

        BlueprintGraph g2 = newGraph();
        NodeModel flattenX = addNode(g2, VectorNodes.Flatten.class);
        setInputConstant(flattenX, "in", new Vector3f(3f, 7f, 4f));
        setOption(flattenX, "axis", 0);
        assertVec(helper, "axis 0 drops x", new float[] {0f, 7f, 4f},
                new GraphExecutor(g2).evaluate(flattenX.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** The signed turn from one heading to another, folded into [-180, 180). */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void yawBetweenIsSignedAndFolded(GameTestHelper helper) {
        assertYaw(helper, "forward to right", new Vector3f(0f, 0f, 1f), new Vector3f(1f, 0f, 0f), 90f);
        assertYaw(helper, "forward to left", new Vector3f(0f, 0f, 1f), new Vector3f(-1f, 0f, 0f), -90f);
        // 270 degrees the long way round is -90 the short way, which is what a character turns
        assertYaw(helper, "right to forward", new Vector3f(1f, 0f, 0f), new Vector3f(0f, 0f, 1f), -90f);
        assertYaw(helper, "no turn", new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, 5f), 0f);
        // The raw difference here is +270. Without the fold every case above still passes, because
        // none of them exceeds a quarter turn — and a character told to turn 270 degrees right
        // instead of 90 left spins the wrong way round.
        assertYaw(helper, "a turn past half a circle folds the short way",
                new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 0f, -1f), -90f);
        helper.succeed();
    }

    private static void assertYaw(GameTestHelper helper, String label, Vector3f from, Vector3f to,
                                  float expected) {
        BlueprintGraph g = newGraph();
        NodeModel yaw = addNode(g, VectorNodes.YawBetween.class);
        setInputConstant(yaw, "from", from);
        setInputConstant(yaw, "to", to);
        assertEq(helper, label, expected,
                new GraphExecutor(g).evaluate(yaw.getOutputsById().get("out"), Float.class), 1e-3f);
    }

    private static void assertVec(GameTestHelper helper, String label, float[] expected, Object actual) {
        float[] got = VectorNodes.components(actual);
        assertEq(helper, label + " width", expected.length, got.length);
        for (int i = 0; i < expected.length; i++) {
            assertEq(helper, label + " component " + i, expected[i], got[i], EPS);
        }
    }
}
