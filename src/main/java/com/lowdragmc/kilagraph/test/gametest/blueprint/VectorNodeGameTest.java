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
        // a is deliberately non-zero and not a multiple of b: with a = 0 the formula collapses to
        // b*t, so Lerp written as Scale would pass
        setInputConstant(lerp, "a", new Vector4f(1f, 10f, 100f, 1000f));
        setInputConstant(lerp, "b", new Vector4f(3f, 14f, 108f, 1016f));
        setInputConstant(lerp, "t", 0.25f);
        assertVec(helper, "lerp", new float[] {1.5f, 11f, 102f, 1004f},
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
     * {@code |(1,2,2,4)|} is 3 over the first three components and 5 over all four, so a cast to
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
        // neither end at the origin: with a = 0 the answer is |b|, so Distance written as Length
        // would pass. |a| here is 52.4 and |b| is 57.7, and the distance is still 5.
        setInputConstant(dist, "a", new Vector4f(10f, 20f, 20f, 40f));
        setInputConstant(dist, "b", new Vector4f(11f, 22f, 22f, 44f));
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

        // width 4: |(1,2,2,4)| is 5 over four components and 3 over three, so a three-component
        // implementation cannot produce these numbers by accident
        BlueprintGraph g3 = newGraph();
        NodeModel norm4 = addNode(g3, VectorNodes.Normalize.class);
        setInputConstant(norm4, "in", new Vector4f(1f, 2f, 2f, 4f));
        Object unit4 = new GraphExecutor(g3).evaluate(norm4.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "a width-4 unit vector stays width 4, got " + unit4, unit4 instanceof Vector4f);
        assertVec(helper, "normalize width 4", new float[] {0.2f, 0.4f, 0.4f, 0.8f}, unit4);
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

        // Axis vectors leave two of the three components at zero, so swapping two of the formulas
        // still passes. Nothing here is zero and nothing is symmetric.
        BlueprintGraph g2 = newGraph();
        NodeModel cross2 = addNode(g2, VectorNodes.Cross.class);
        setInputConstant(cross2, "a", new Vector3f(1f, 2f, 3f));
        setInputConstant(cross2, "b", new Vector3f(4f, 5f, 6f));
        assertVec(helper, "(1,2,3) x (4,5,6)", new float[] {-3f, 6f, -3f},
                new GraphExecutor(g2).evaluate(cross2.getOutputsById().get("out"), Object.class));
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
        // Minecraft's convention: +Z is zero and +X is -90, so turning from +Z to +X is -90.
        // The opposite sign would be self-consistent and wrong in exactly the way that makes a
        // character strafe while walking straight.
        assertYaw(helper, "forward to +X", new Vector3f(0f, 0f, 1f), new Vector3f(1f, 0f, 0f), -90f);
        assertYaw(helper, "forward to -X", new Vector3f(0f, 0f, 1f), new Vector3f(-1f, 0f, 0f), 90f);
        assertYaw(helper, "+X back to forward", new Vector3f(1f, 0f, 0f), new Vector3f(0f, 0f, 1f), 90f);
        assertYaw(helper, "no turn", new Vector3f(0f, 0f, 1f), new Vector3f(0f, 0f, 5f), 0f);
        // The raw difference here is +270. Without the fold every case above still passes, because
        // none of them exceeds a quarter turn — and a character told to turn 270 degrees right
        // instead of 90 left spins the wrong way round.
        assertYaw(helper, "a turn past half a circle folds the short way",
                new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 0f, -1f), 90f);
        helper.succeed();
    }

    /**
     * Two inputs of different widths: the result is as wide as the wider one, and the narrower one's
     * absent components read zero.
     *
     * <p>Every other test here feeds both pins the same width, which cannot see this: an
     * implementation that took the width of {@code a}, or of the first pin, or that refused a
     * mismatch, passes all of them. The mixed case is also the one a real graph hits by accident,
     * since {@code KGGraphModel} lets any vector width reach any vector pin.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void mismatchedWidthsTakeTheWiderAndZeroFill(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel add = addNode(g, VectorNodes.Add.class);
        setInputConstant(add, "a", new Vector2f(1f, 2f));
        setInputConstant(add, "b", new Vector4f(10f, 20f, 30f, 40f));
        Object sum = new GraphExecutor(g).evaluate(add.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "width 2 plus width 4 is width 4, got " + sum, sum instanceof Vector4f);
        // z and w come from b alone, which is what "the missing components read zero" means
        assertVec(helper, "add mixed widths", new float[] {11f, 22f, 30f, 40f}, sum);

        // Subtract the other way round, so the wider input is on the left this time.
        BlueprintGraph g2 = newGraph();
        NodeModel sub = addNode(g2, VectorNodes.Subtract.class);
        setInputConstant(sub, "a", new Vector4f(10f, 20f, 30f, 40f));
        setInputConstant(sub, "b", new Vector2f(1f, 2f));
        assertVec(helper, "subtract mixed widths", new float[] {9f, 18f, 30f, 40f},
                new GraphExecutor(g2).evaluate(sub.getOutputsById().get("out"), Object.class));

        // Dot: 1*1 + 2*1 + 0*1 + 0*10 = 3. Stopping at the narrower width would also give 3, so the
        // b components past a's width are deliberately large — 10 would show up as 20 or 30 if the
        // missing side read anything but zero.
        BlueprintGraph g3 = newGraph();
        NodeModel dot = addNode(g3, VectorNodes.Dot.class);
        setInputConstant(dot, "a", new Vector2f(1f, 2f));
        setInputConstant(dot, "b", new Vector4f(1f, 1f, 1f, 10f));
        assertEq(helper, "dot over mixed widths", 3f,
                new GraphExecutor(g3).evaluate(dot.getOutputsById().get("out"), Float.class), EPS);

        // Distance: the difference is (3,4,0,-12), so 13 over four components where the first three
        // alone would give 5. That distinguishes zero-filling from truncating.
        BlueprintGraph g4 = newGraph();
        NodeModel dist = addNode(g4, VectorNodes.Distance.class);
        setInputConstant(dist, "a", new Vector2f(3f, 4f));
        setInputConstant(dist, "b", new Vector4f(0f, 0f, 0f, 12f));
        assertEq(helper, "distance over mixed widths", 13f,
                new GraphExecutor(g4).evaluate(dist.getOutputsById().get("out"), Float.class), EPS);
        helper.succeed();
    }

    /**
     * Lerp clamps t, so it interpolates and never extrapolates.
     *
     * <p>Without the clamp a t of 2 doubles the distance past b and a t of -1 runs backwards past a.
     * Both are plausible-looking numbers, which is why nothing downstream would flag them.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void lerpClampsTToTheUnitRange(GameTestHelper helper) {
        assertLerp(helper, "t below 0 gives a", -1f, new float[] {10f, 20f, 30f});
        assertLerp(helper, "t of 0 gives a", 0f, new float[] {10f, 20f, 30f});
        assertLerp(helper, "t of 1 gives b", 1f, new float[] {20f, 40f, 60f});
        // 2 would give (30,60,90) unclamped — a point past b, on the far side
        assertLerp(helper, "t above 1 gives b", 2f, new float[] {20f, 40f, 60f});
        assertLerp(helper, "t half way", 0.5f, new float[] {15f, 30f, 45f});
        helper.succeed();
    }

    /**
     * Cross reads the first three components of whatever width it is given.
     *
     * <p>It is the one vector node that is not width-polymorphic, and the docs say so. A width-4
     * input must therefore behave exactly as the width-3 case does, ignoring w rather than folding
     * it in or refusing the input.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void crossReadsTheFirstThreeOfAnyWidth(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel cross = addNode(g, VectorNodes.Cross.class);
        // same xyz as crossFollowsTheRightHandRule's second case, plus a w that must not matter;
        // the two w values differ in sign and magnitude so any use of them would move the answer
        setInputConstant(cross, "a", new Vector4f(1f, 2f, 3f, 99f));
        setInputConstant(cross, "b", new Vector4f(4f, 5f, 6f, -7f));
        Object out = new GraphExecutor(g).evaluate(cross.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "cross always answers width 3, got " + out, out instanceof Vector3f);
        assertVec(helper, "cross ignores w", new float[] {-3f, 6f, -3f}, out);
        helper.succeed();
    }

    /** An axis the input does not have leaves the vector alone rather than failing. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void flattenIgnoresAnAxisTheInputDoesNotHave(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel flatten = addNode(g, VectorNodes.Flatten.class);
        setInputConstant(flatten, "in", new Vector3f(3f, 7f, 4f));
        setOption(flatten, "axis", 3);   // a Vector3 has no w
        assertVec(helper, "axis past the width changes nothing", new float[] {3f, 7f, 4f},
                new GraphExecutor(g).evaluate(flatten.getOutputsById().get("out"), Object.class));

        // and the last valid axis really is reachable, so the guard is not simply off by one
        BlueprintGraph g2 = newGraph();
        NodeModel flattenZ = addNode(g2, VectorNodes.Flatten.class);
        setInputConstant(flattenZ, "in", new Vector3f(3f, 7f, 4f));
        setOption(flattenZ, "axis", 2);
        assertVec(helper, "axis 2 drops z", new float[] {3f, 7f, 0f},
                new GraphExecutor(g2).evaluate(flattenZ.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /**
     * Yaw is a turn about the vertical axis, so the vertical component of either input is ignored.
     *
     * <p>Everything in {@link #yawBetweenIsSignedAndFolded} has y = 0, which cannot tell this from an
     * implementation that folded y in somewhere. A mob looking up a slope has a non-zero y in its
     * facing, and its turn must not change because of it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void yawIgnoresTheVerticalComponent(GameTestHelper helper) {
        // same headings as the "forward to +X" case, now steeply pitched: still -90
        assertYaw(helper, "pitched inputs give the same turn",
                new Vector3f(0f, 12f, 1f), new Vector3f(1f, -30f, 0f), -90f);
        // straight up has no horizontal direction at all; atan2(0, 0) is 0, so this must not throw
        assertYaw(helper, "a purely vertical target is treated as zero heading",
                new Vector3f(0f, 0f, 1f), new Vector3f(0f, 5f, 0f), 0f);
        helper.succeed();
    }

    /** Make fills unconnected components with zero, and Make2 really produces two of them. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void makeDefaultsToZeroAndMake2StaysWidthTwo(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel make = addNode(g, VectorNodes.Make.class);
        setInputConstant(make, "x", 1f);   // y and z left alone
        assertVec(helper, "unset components read zero", new float[] {1f, 0f, 0f},
                new GraphExecutor(g).evaluate(make.getOutputsById().get("out"), Object.class));

        BlueprintGraph g2 = newGraph();
        NodeModel make2 = addNode(g2, VectorNodes.Make2.class);
        setInputConstant(make2, "x", 6f);
        setInputConstant(make2, "y", 8f);
        Object flat = new GraphExecutor(g2).evaluate(make2.getOutputsById().get("out"), Object.class);
        assertTrue(helper, "Make2 answers width 2, got " + flat, flat instanceof Vector2f);
        assertVec(helper, "make2", new float[] {6f, 8f}, flat);

        // and it stays width 2 through an operation, rather than being widened on the way
        BlueprintGraph g3 = newGraph();
        NodeModel make2b = addNode(g3, VectorNodes.Make2.class);
        setInputConstant(make2b, "x", 6f);
        setInputConstant(make2b, "y", 8f);
        NodeModel length = addNode(g3, VectorNodes.Length.class);
        wire(g3, length.getInputsById().get("in"), make2b.getOutputsById().get("out"));
        assertEq(helper, "|(6,8)|", 10f,
                new GraphExecutor(g3).evaluate(length.getOutputsById().get("out"), Float.class), EPS);
        helper.succeed();
    }

    private static void assertLerp(GameTestHelper helper, String label, float t, float[] expected) {
        BlueprintGraph g = newGraph();
        NodeModel lerp = addNode(g, VectorNodes.Lerp.class);
        setInputConstant(lerp, "a", new Vector3f(10f, 20f, 30f));
        setInputConstant(lerp, "b", new Vector3f(20f, 40f, 60f));
        setInputConstant(lerp, "t", t);
        assertVec(helper, label, expected,
                new GraphExecutor(g).evaluate(lerp.getOutputsById().get("out"), Object.class));
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
