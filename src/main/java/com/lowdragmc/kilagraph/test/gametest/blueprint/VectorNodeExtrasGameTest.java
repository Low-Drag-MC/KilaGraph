package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorGeometryNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorMathNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorStructNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
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
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

/**
 * The structural, component-wise and geometric vector nodes — everything outside
 * {@link VectorNodeGameTest}'s original fifteen.
 *
 * <p><b>Two properties are checked everywhere, because both fail silently.</b> The first is width:
 * an implementation that casts to {@code Vector3f} passes any test written with three components and
 * quietly drops the fourth, so every operation is exercised at 2 and 4 as well. The second is the
 * degenerate case — a zero axis, a zero divisor, a zero-length direction — where the obvious formula
 * divides by zero and puts a NaN into the graph that surfaces hundreds of nodes later with nothing
 * pointing back at the cause. Both are asserted per node rather than once, because each node makes
 * the choice itself.
 *
 * <p>That the pins <em>say</em> so is {@link VectorPinTypeGameTest}'s business; this file is about
 * what the arithmetic does.
 */
@GameTestHolder(Kilagraph.MODID)
public final class VectorNodeExtrasGameTest {

    private static final float EPS = 1e-4f;
    /** Rotation goes through {@code Mth}'s sine table, which is good to about 5e-5 per component. */
    private static final float TRIG_EPS = 1e-3f;

    private VectorNodeExtrasGameTest() {
    }

    // ---- structure

    /**
     * Swizzle reorders, narrows, widens and repeats — and the mask, not the input, sets the width.
     *
     * <p>The widening cases are the ones Break-into-Make cannot reach, and the reason this node
     * exists at all: that pair always answers a Vector3 whatever went in.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void swizzleMaskDecidesTheOutputWidth(GameTestHelper helper) {
        // narrow: a Vec4 down to its first two components
        Object xy = swizzled(new Vector4f(1f, 2f, 3f, 4f), "xy");
        assertTrue(helper, "mask xy answers width 2, got " + xy, xy instanceof Vector2f);
        assertVec(helper, "narrow", new float[] {1f, 2f}, xy);

        // reorder: nothing symmetric, so a mask that was ignored could not produce this
        assertVec(helper, "reverse", new float[] {3f, 2f, 1f},
                swizzled(new Vector3f(1f, 2f, 3f), "zyx"));

        // repeat: one source component into every slot
        assertVec(helper, "splat", new float[] {7f, 7f, 7f}, swizzled(new Vector3f(7f, 8f, 9f), "xxx"));

        // widen with a literal — a Vec3 to a Vec4 with a zero glued on
        Object widened = swizzled(new Vector3f(1f, 2f, 3f), "xyz0");
        assertTrue(helper, "mask xyz0 answers width 4, got " + widened, widened instanceof Vector4f);
        assertVec(helper, "widen with 0", new float[] {1f, 2f, 3f, 0f}, widened);
        assertVec(helper, "widen with 1", new float[] {1f, 2f, 3f, 1f},
                swizzled(new Vector3f(1f, 2f, 3f), "xyz1"));

        // an axis the input does not have reads zero rather than failing
        assertVec(helper, "w of a Vec2", new float[] {1f, 2f, 0f, 0f},
                swizzled(new Vector2f(1f, 2f), "xyzw"));
        helper.succeed();
    }

    /**
     * A mask that cannot be honoured falls back to {@code xyz} rather than being applied partly.
     *
     * <p>The one-character case is not tidiness: at width one {@code carrier} answers a bare float,
     * which no vector pin downstream would accept, so the node would have a dead output pin.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void swizzleRefusesMasksItCannotHonour(GameTestHelper helper) {
        float[] input = {1f, 2f, 3f};
        assertVec(helper, "single character", input, swizzled(new Vector3f(1f, 2f, 3f), "x"));
        assertVec(helper, "empty", input, swizzled(new Vector3f(1f, 2f, 3f), ""));
        assertVec(helper, "all junk", input, swizzled(new Vector3f(1f, 2f, 3f), "!!"));
        // partly valid: the junk is dropped, and what is left is still a usable mask
        assertVec(helper, "junk between axes", new float[] {1f, 3f},
                swizzled(new Vector3f(1f, 2f, 3f), "x?z"));
        // more than four is truncated, not refused
        assertVec(helper, "five characters", new float[] {1f, 1f, 1f, 1f},
                swizzled(new Vector3f(1f, 2f, 3f), "xxxxx"));
        // case does not matter
        assertVec(helper, "upper case", new float[] {3f, 2f, 1f},
                swizzled(new Vector3f(1f, 2f, 3f), "ZYX"));
        helper.succeed();
    }

    /** Concat lays vectors end to end and stops at four components. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void concatJoinsInOrderAndStopsAtFour(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel concat = addNode(g, VectorStructNodes.Concat.class);
        setInputConstant(concat, "in1", new Vector2f(1f, 2f));
        setInputConstant(concat, "in2", new Vector2f(3f, 4f));
        Object joined = eval(g, concat, "out");
        assertTrue(helper, "two Vec2s join into a Vec4, got " + joined, joined instanceof Vector4f);
        assertVec(helper, "concat", new float[] {1f, 2f, 3f, 4f}, joined);

        // over-long: the fifth and sixth components have nowhere to go
        BlueprintGraph g2 = newGraph();
        NodeModel over = addNode(g2, VectorStructNodes.Concat.class);
        setInputConstant(over, "in1", new Vector3f(1f, 2f, 3f));
        setInputConstant(over, "in2", new Vector3f(4f, 5f, 6f));
        assertVec(helper, "truncated at four", new float[] {1f, 2f, 3f, 4f}, eval(g2, over, "out"));

        // three inputs, so the count option really drives the ports rather than being decoration
        BlueprintGraph g3 = newGraph();
        NodeModel three = addNode(g3, VectorStructNodes.Concat.class);
        setOption(three, "inputs", 3);
        setInputConstant(three, "in1", new Vector2f(1f, 2f));
        setInputConstant(three, "in2", new Vector2f(3f, 4f));
        assertTrue(helper, "a third input port exists after raising the count",
                three.getInputsById().containsKey("in3"));
        assertVec(helper, "three inputs", new float[] {1f, 2f, 3f, 4f}, eval(g3, three, "out"));
        helper.succeed();
    }

    /** Append widens by exactly one, and has nowhere to go at width four. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void appendWidensByOneAndStopsAtFour(GameTestHelper helper) {
        Object three = appended(new Vector2f(1f, 2f), 9f);
        assertTrue(helper, "Vec2 plus a number is a Vec3, got " + three, three instanceof Vector3f);
        assertVec(helper, "append to a Vec2", new float[] {1f, 2f, 9f}, three);

        Object four = appended(new Vector3f(1f, 2f, 3f), 9f);
        assertTrue(helper, "Vec3 plus a number is a Vec4, got " + four, four instanceof Vector4f);
        assertVec(helper, "append to a Vec3", new float[] {1f, 2f, 3f, 9f}, four);

        // The value is deliberately different from every component, so overwriting w — which would
        // make this Set Component under another name — could not pass.
        assertVec(helper, "a Vec4 comes back unchanged", new float[] {1f, 2f, 3f, 4f},
                appended(new Vector4f(1f, 2f, 3f, 4f), 9f));
        helper.succeed();
    }

    /** Set Component replaces one axis, keeps the width, and ignores an axis the value lacks. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void setComponentKeepsTheInputWidth(GameTestHelper helper) {
        assertVec(helper, "default axis is Y", new float[] {1f, 9f, 3f},
                withComponent(new Vector3f(1f, 2f, 3f), 1, 9f));
        assertVec(helper, "axis 0 sets X", new float[] {9f, 2f, 3f},
                withComponent(new Vector3f(1f, 2f, 3f), 0, 9f));

        // width 4 is the case a Break-into-Make replacement would get wrong: it would come back as
        // a Vector3 with w gone
        Object four = withComponent(new Vector4f(1f, 2f, 3f, 4f), 3, 9f);
        assertTrue(helper, "a Vec4 stays a Vec4, got " + four, four instanceof Vector4f);
        assertVec(helper, "axis 3 sets W", new float[] {1f, 2f, 3f, 9f}, four);

        // and width 2 likewise, rather than being widened to make room for the axis
        Object two = withComponent(new Vector2f(1f, 2f), 2, 9f);
        assertTrue(helper, "a Vec2 stays a Vec2, got " + two, two instanceof Vector2f);
        assertVec(helper, "an axis past the width changes nothing", new float[] {1f, 2f}, two);
        helper.succeed();
    }

    /** Get Component reads by index, and answers zero outside the vector rather than throwing. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void getComponentReadsByIndexAndZeroesOutOfRange(GameTestHelper helper) {
        for (int i = 0; i < 4; i++) {
            assertEq(helper, "component " + i, (float) (i + 1),
                    component(new Vector4f(1f, 2f, 3f, 4f), i), EPS);
        }
        assertEq(helper, "w of a Vec3", 0f, component(new Vector3f(1f, 2f, 3f), 3), EPS);
        assertEq(helper, "past the end", 0f, component(new Vector3f(1f, 2f, 3f), 7), EPS);
        // the index comes off a pin, so a graph can hand this a negative — an unguarded array read
        // would throw here rather than answer
        assertEq(helper, "negative index", 0f, component(new Vector3f(1f, 2f, 3f), -1), EPS);
        helper.succeed();
    }

    // ---- component-wise arithmetic

    /**
     * Multiply and Divide act per component, at the wider operand's width.
     *
     * <p>The factors differ per axis on purpose: a Multiply implemented as Scale — one number for
     * every component — is the plausible wrong version, and equal factors would not see it.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multiplyAndDivideActPerComponent(GameTestHelper helper) {
        Object product = binary(VectorMathNodes.Multiply.class,
                new Vector4f(1f, 2f, 3f, 4f), new Vector4f(10f, 20f, 30f, 40f));
        assertTrue(helper, "a width-4 product stays width 4, got " + product, product instanceof Vector4f);
        assertVec(helper, "multiply", new float[] {10f, 40f, 90f, 160f}, product);

        assertVec(helper, "divide", new float[] {5f, 4f, 3f},
                binary(VectorMathNodes.Divide.class, new Vector3f(10f, 20f, 30f), new Vector3f(2f, 5f, 10f)));

        // A zero divisor gives zero for that component, and — the point of the test — leaves the
        // others alone. An infinity here would spread through everything downstream.
        Object divided = binary(VectorMathNodes.Divide.class,
                new Vector3f(10f, 20f, 30f), new Vector3f(2f, 0f, 10f));
        float y = Vectors.components(divided)[1];
        assertTrue(helper, "y is finite, it was " + y, Float.isFinite(y));
        assertVec(helper, "divide by a zero component", new float[] {5f, 0f, 3f}, divided);
        helper.succeed();
    }

    /** Negate, Abs, Min and Max, each per component and each keeping the width. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void negateAbsMinAndMaxActPerComponent(GameTestHelper helper) {
        assertVec(helper, "negate", new float[] {-1f, 2f, -3f, 0f},
                unary(VectorMathNodes.Negate.class, new Vector4f(1f, -2f, 3f, 0f)));
        assertVec(helper, "abs", new float[] {1f, 2f, 3f, 4f},
                unary(VectorMathNodes.Abs.class, new Vector4f(1f, -2f, 3f, -4f)));

        // The winner alternates between a and b per component, so a node that answered one whole
        // input could not pass either of these.
        assertVec(helper, "min", new float[] {1f, 5f, 2f, 7f},
                binary(VectorMathNodes.Min.class,
                        new Vector4f(1f, 9f, 2f, 8f), new Vector4f(4f, 5f, 6f, 7f)));
        assertVec(helper, "max", new float[] {4f, 9f, 6f, 8f},
                binary(VectorMathNodes.Max.class,
                        new Vector4f(1f, 9f, 2f, 8f), new Vector4f(4f, 5f, 6f, 7f)));
        helper.succeed();
    }

    /** Clamp holds every component between the scalar bounds, inverted range included. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void clampHoldsEveryComponentBetweenTheBounds(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel clamp = addNode(g, VectorMathNodes.Clamp.class);
        setInputConstant(clamp, "in", new Vector4f(-5f, 0.5f, 5f, 1f));
        setInputConstant(clamp, "min", 0f);
        setInputConstant(clamp, "max", 1f);
        assertVec(helper, "clamp", new float[] {0f, 0.5f, 1f, 1f}, eval(g, clamp, "out"));

        // An inverted range answers min, which is what Clamp does for numbers — the two nodes
        // agreeing about a nonsense input is the property here.
        BlueprintGraph g2 = newGraph();
        NodeModel inverted = addNode(g2, VectorMathNodes.Clamp.class);
        setInputConstant(inverted, "in", new Vector3f(-5f, 0.5f, 5f));
        setInputConstant(inverted, "min", 1f);
        setInputConstant(inverted, "max", 0f);
        assertVec(helper, "inverted range", new float[] {1f, 1f, 1f}, eval(g2, inverted, "out"));
        helper.succeed();
    }

    /**
     * Every rounding mode, on a value that separates them all.
     *
     * <p>-2.7 and 2.5 are chosen so that no two modes agree: Floor gives -3 where Trunc gives -2,
     * and Round's half-up rule shows on the 2.5.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyRoundingModeDiffers(GameTestHelper helper) {
        Vector4f in = new Vector4f(-2.7f, 2.5f, -0.5f, 0f);
        assertVec(helper, "round", new float[] {-3f, 3f, 0f, 0f},
                rounded(in, VectorMathNodes.Round.Op.ROUND));
        assertVec(helper, "floor", new float[] {-3f, 2f, -1f, 0f},
                rounded(in, VectorMathNodes.Round.Op.FLOOR));
        assertVec(helper, "ceil", new float[] {-2f, 3f, -0f, 0f},
                rounded(in, VectorMathNodes.Round.Op.CEIL));
        assertVec(helper, "trunc", new float[] {-2f, 2f, -0f, 0f},
                rounded(in, VectorMathNodes.Round.Op.TRUNC));
        assertVec(helper, "fract", new float[] {0.3f, 0.5f, 0.5f, 0f},
                rounded(in, VectorMathNodes.Round.Op.FRACT));
        assertVec(helper, "sign", new float[] {-1f, 1f, -1f, 0f},
                rounded(in, VectorMathNodes.Round.Op.SIGN));
        helper.succeed();
    }

    // ---- geometry

    /** The squared forms count every component, and really are the square. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void squaredFormsCountEveryComponent(GameTestHelper helper) {
        // |(1,2,2,4)| is 5 over four components and 3 over three, so 25 cannot come from a cast
        BlueprintGraph g = newGraph();
        NodeModel lengthSq = addNode(g, VectorGeometryNodes.LengthSquared.class);
        setInputConstant(lengthSq, "in", new Vector4f(1f, 2f, 2f, 4f));
        assertEq(helper, "length squared", 25f, evalF(g, lengthSq, "out"), EPS);

        BlueprintGraph g2 = newGraph();
        NodeModel distSq = addNode(g2, VectorGeometryNodes.DistanceSquared.class);
        // difference is (3,4,0,-12): 169 over four components, 25 over the first three
        setInputConstant(distSq, "a", new Vector4f(3f, 4f, 0f, 0f));
        setInputConstant(distSq, "b", new Vector4f(0f, 0f, 0f, 12f));
        assertEq(helper, "distance squared", 169f, evalF(g2, distSq, "out"), EPS);
        helper.succeed();
    }

    /** Clamp Length rescales without steering, and a zero vector has no direction to stretch. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void clampLengthRescalesWithoutTurning(GameTestHelper helper) {
        // |(3,0,4)| is 5; capped at 1 the direction must survive as (0.6, 0, 0.8). Clamping the
        // components instead would give (1, 0, 1), which points somewhere else entirely.
        assertVec(helper, "capped", new float[] {0.6f, 0f, 0.8f},
                lengthClamped(new Vector3f(3f, 0f, 4f), 0f, 1f));
        // below the minimum: stretched to length 10, same direction
        assertVec(helper, "stretched", new float[] {6f, 0f, 8f},
                lengthClamped(new Vector3f(3f, 0f, 4f), 10f, 100f));
        // inside the range: untouched
        assertVec(helper, "within range", new float[] {3f, 0f, 4f},
                lengthClamped(new Vector3f(3f, 0f, 4f), 1f, 100f));
        // a zero vector cannot be stretched to the minimum without inventing a direction
        float[] zero = Vectors.components(lengthClamped(new Vector3f(), 10f, 100f));
        for (int i = 0; i < zero.length; i++) {
            assertTrue(helper, "component " + i + " is not NaN, it was " + zero[i], !Float.isNaN(zero[i]));
            assertEq(helper, "zero stays zero, component " + i, 0f, zero[i], EPS);
        }
        helper.succeed();
    }

    /**
     * Project and Reject are the two halves of one split, and add back up to the original.
     *
     * <p>Checking the sum is what catches a sign error in either: both halves can look plausible on
     * their own and still not reconstruct the input.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void projectAndRejectSplitTheVector(GameTestHelper helper) {
        Vector3f a = new Vector3f(3f, 4f, 0f);
        // b is deliberately not a unit vector: its length must divide out
        Vector3f b = new Vector3f(5f, 0f, 0f);
        Object parallel = binary(VectorGeometryNodes.Project.class, a, b);
        Object perpendicular = binary(VectorGeometryNodes.Reject.class, a, b);
        assertVec(helper, "project", new float[] {3f, 0f, 0f}, parallel);
        assertVec(helper, "reject", new float[] {0f, 4f, 0f}, perpendicular);
        float[] along = Vectors.components(parallel);
        float[] across = Vectors.components(perpendicular);
        float[] original = Vectors.components(a);
        for (int i = 0; i < original.length; i++) {
            assertEq(helper, "the halves reconstruct a, component " + i,
                    original[i], along[i] + across[i], EPS);
        }

        // a zero direction names nothing to project onto
        assertVec(helper, "project onto zero", new float[] {0f, 0f, 0f},
                binary(VectorGeometryNodes.Project.class, a, new Vector3f()));
        assertVec(helper, "reject from zero returns a", new float[] {3f, 4f, 0f},
                binary(VectorGeometryNodes.Reject.class, a, new Vector3f()));
        helper.succeed();
    }

    /** Reflect mirrors about the plane, keeps the length, and tolerates an unnormalised normal. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void reflectMirrorsAboutTheNormal(GameTestHelper helper) {
        BlueprintGraph g = newGraph();
        NodeModel reflect = addNode(g, VectorGeometryNodes.Reflect.class);
        setInputConstant(reflect, "in", new Vector3f(1f, -1f, 0f));
        setInputConstant(reflect, "normal", new Vector3f(0f, 1f, 0f));
        assertVec(helper, "bounce off the ground", new float[] {1f, 1f, 0f}, eval(g, reflect, "out"));

        // the same surface described by a longer normal must give the same answer — i.e. the node
        // divides by the normal's length rather than assuming it is 1
        BlueprintGraph g2 = newGraph();
        NodeModel scaled = addNode(g2, VectorGeometryNodes.Reflect.class);
        setInputConstant(scaled, "in", new Vector3f(1f, -1f, 0f));
        setInputConstant(scaled, "normal", new Vector3f(0f, 5f, 0f));
        assertVec(helper, "an unnormalised normal", new float[] {1f, 1f, 0f}, eval(g2, scaled, "out"));

        // no surface at all: the input passes through rather than becoming NaN
        BlueprintGraph g3 = newGraph();
        NodeModel none = addNode(g3, VectorGeometryNodes.Reflect.class);
        setInputConstant(none, "in", new Vector3f(1f, -1f, 0f));
        setInputConstant(none, "normal", new Vector3f());
        assertVec(helper, "a zero normal", new float[] {1f, -1f, 0f}, eval(g3, none, "out"));
        helper.succeed();
    }

    /**
     * Angle Between is unsigned, counts pitch, and survives exactly-parallel inputs.
     *
     * <p>The parallel case is the one that matters: rounding can push the cosine a hair past 1, and
     * {@code acos} of 1.0000001 is NaN — which only shows up for the tidy inputs a first test uses.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void angleBetweenIsUnsignedAndSafeAtTheEnds(GameTestHelper helper) {
        assertAngle(helper, "perpendicular", new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), 90f);
        assertAngle(helper, "opposite", new Vector3f(1f, 0f, 0f), new Vector3f(-1f, 0f, 0f), 180f);
        // parallel but different lengths, so the cosine is computed rather than trivially 1
        assertAngle(helper, "parallel", new Vector3f(1f, 2f, 3f), new Vector3f(4f, 8f, 12f), 0f);
        // unsigned: swapping the inputs cannot change the answer, unlike Yaw Between
        assertAngle(helper, "unsigned one way", new Vector3f(0f, 0f, 1f), new Vector3f(1f, 0f, 1f), 45f);
        assertAngle(helper, "unsigned the other", new Vector3f(1f, 0f, 1f), new Vector3f(0f, 0f, 1f), 45f);
        // vertical only: Yaw Between would report 0 here, which is the difference between the nodes
        assertAngle(helper, "counts pitch", new Vector3f(0f, 0f, 1f), new Vector3f(0f, 1f, 0f), 90f);
        assertAngle(helper, "a zero input has no angle", new Vector3f(), new Vector3f(0f, 0f, 1f), 0f);
        helper.succeed();
    }

    /** Rotate About Axis is right-handed, periodic, and passes a zero axis through. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rotateAboutAxisIsRightHanded(GameTestHelper helper) {
        // +X turned about +Y by 90 degrees goes to -Z. The opposite sign is self-consistent and
        // wrong, exactly as it is for Cross Product, which this is built from.
        assertVec(helper, "right-handed quarter turn", new float[] {0f, 0f, -1f},
                rotated(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), 90f));
        assertVec(helper, "half turn", new float[] {-1f, 0f, 0f},
                rotated(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), 180f));
        assertVec(helper, "a full turn is the identity", new float[] {1f, 2f, 3f},
                rotated(new Vector3f(1f, 2f, 3f), new Vector3f(0f, 1f, 0f), 360f));
        // a component along the axis is unaffected, which a formula missing the k(k.v) term would
        // get wrong while still passing every case above
        assertVec(helper, "the along-axis part survives", new float[] {0f, 5f, -1f},
                rotated(new Vector3f(1f, 5f, 0f), new Vector3f(0f, 1f, 0f), 90f));
        // an unnormalised axis names the same rotation
        assertVec(helper, "an unnormalised axis", new float[] {0f, 0f, -1f},
                rotated(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 9f, 0f), 90f));
        // no axis, no rotation — and no NaN from dividing by its length
        assertVec(helper, "a zero axis", new float[] {1f, 2f, 3f},
                rotated(new Vector3f(1f, 2f, 3f), new Vector3f(), 90f));
        helper.succeed();
    }

    /**
     * Direction and rotation convert both ways, in Minecraft's convention.
     *
     * <p>The named cases pin the convention — yaw 0 down +Z, yaw −90 down +X, positive pitch
     * <em>down</em> — and the round trip catches an inverse that is self-consistently wrong.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rotationAndDirectionAgreeWithTheGame(GameTestHelper helper) {
        assertVec(helper, "yaw 0 looks along +Z", new float[] {0f, 0f, 1f}, direction(0f, 0f), TRIG_EPS);
        assertVec(helper, "yaw -90 looks along +X", new float[] {1f, 0f, 0f}, direction(-90f, 0f), TRIG_EPS);
        assertVec(helper, "positive pitch looks down", new float[] {0f, -1f, 0f}, direction(0f, 90f), TRIG_EPS);

        // and back the other way, on the same three
        assertRotation(helper, "+Z is yaw 0", new Vector3f(0f, 0f, 1f), 0f, 0f);
        assertRotation(helper, "+X is yaw -90", new Vector3f(1f, 0f, 0f), -90f, 0f);
        assertRotation(helper, "down is pitch 90", new Vector3f(0f, -1f, 0f), 0f, 90f);

        // A round trip through an angle that is on no axis: an inverse with both signs flipped
        // would satisfy the axis cases above and fail here.
        BlueprintGraph g = newGraph();
        NodeModel from = addNode(g, VectorGeometryNodes.FromRotation.class);
        setInputConstant(from, "yaw", 34f);
        setInputConstant(from, "pitch", -21f);
        NodeModel to = addNode(g, VectorGeometryNodes.ToRotation.class);
        wire(g, to, from);
        GraphExecutor exec = new GraphExecutor(g);
        assertEq(helper, "yaw round trip", 34f,
                exec.evaluate(to.getOutputsById().get("yaw"), Float.class), 0.05f);
        assertEq(helper, "pitch round trip", -21f,
                exec.evaluate(to.getOutputsById().get("pitch"), Float.class), 0.05f);

        // the length of the input must not matter, only its direction
        assertRotation(helper, "a long vector", new Vector3f(0f, 0f, 40f), 0f, 0f);
        assertRotation(helper, "a zero vector has no direction", new Vector3f(), 0f, 0f);
        helper.succeed();
    }

    /** Move Towards steps at most maxDelta and lands exactly on the target once it is in range. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void moveTowardsLandsExactlyOnTheTarget(GameTestHelper helper) {
        Vector3f from = new Vector3f(1f, 0f, 0f);
        Vector3f to = new Vector3f(11f, 0f, 0f);
        assertVec(helper, "a step short of the target", new float[] {4f, 0f, 0f}, stepped(from, to, 3f));
        // exactly on the target, not a hair past it — the difference between arriving and jittering
        assertVec(helper, "a step past the target lands on it", new float[] {11f, 0f, 0f},
                stepped(from, to, 50f));
        assertVec(helper, "a step exactly reaching it", new float[] {11f, 0f, 0f},
                stepped(from, to, 10f));
        // already there: no NaN from normalising a zero difference
        assertVec(helper, "already there", new float[] {11f, 0f, 0f}, stepped(to, to, 3f));
        // diagonal, so the step is measured as a distance rather than per axis: |(3,4,0)| is 5, so
        // a step of 2.5 covers half of it
        assertVec(helper, "the step is a distance, not per axis", new float[] {1.5f, 2f, 0f},
                stepped(new Vector3f(), new Vector3f(3f, 4f, 0f), 2.5f));
        helper.succeed();
    }

    /** Nearly Equals compares over the wider width, and honours the tolerance. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void nearlyEqualsComparesOverTheWiderWidth(GameTestHelper helper) {
        assertTrue(helper, "identical vectors are equal",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector3f(1f, 2f, 3f), EPS));
        assertTrue(helper, "within the tolerance",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector3f(1.00001f, 2f, 3f), EPS));
        assertFalse(helper, "outside the tolerance",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector3f(1.5f, 2f, 3f), EPS));
        // A Vec4 whose w is the only difference: comparing at the narrower width would call these
        // equal, which is the failure the wider-width rule exists to prevent.
        assertFalse(helper, "a differing W is not ignored",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector4f(1f, 2f, 3f, 5f), EPS));
        assertTrue(helper, "a zero W matches a Vec3",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector4f(1f, 2f, 3f, 0f), EPS));
        // a generous tolerance really widens what counts as equal
        assertTrue(helper, "a wide tolerance",
                nearlyEqual(new Vector3f(1f, 2f, 3f), new Vector3f(1.5f, 2f, 3f), 1f));
        helper.succeed();
    }

    // ---- helpers

    private static Object swizzled(Object in, String mask) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorStructNodes.Swizzle.class);
        setInputConstant(n, "in", in);
        setOption(n, "mask", mask);
        return eval(g, n, "out");
    }

    private static Object appended(Object in, float value) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorStructNodes.Append.class);
        setInputConstant(n, "in", in);
        setInputConstant(n, "value", value);
        return eval(g, n, "out");
    }

    private static Object withComponent(Object in, int axis, float value) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorStructNodes.SetComponent.class);
        setInputConstant(n, "in", in);
        setInputConstant(n, "value", value);
        setOption(n, "axis", axis);
        return eval(g, n, "out");
    }

    private static float component(Object in, int index) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorStructNodes.GetComponent.class);
        setInputConstant(n, "in", in);
        setInputConstant(n, "index", index);
        return evalF(g, n, "out");
    }

    private static Object rounded(Object in, VectorMathNodes.Round.Op op) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorMathNodes.Round.class);
        setInputConstant(n, "in", in);
        setOption(n, "op", op);
        return eval(g, n, "out");
    }

    private static Object lengthClamped(Object in, float min, float max) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.ClampLength.class);
        setInputConstant(n, "in", in);
        setInputConstant(n, "min", min);
        setInputConstant(n, "max", max);
        return eval(g, n, "out");
    }

    private static Object rotated(Object in, Object axis, float angle) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.RotateAxis.class);
        setInputConstant(n, "in", in);
        setInputConstant(n, "axis", axis);
        setInputConstant(n, "angle", angle);
        return eval(g, n, "out");
    }

    private static Object direction(float yaw, float pitch) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.FromRotation.class);
        setInputConstant(n, "yaw", yaw);
        setInputConstant(n, "pitch", pitch);
        return eval(g, n, "out");
    }

    private static Object stepped(Object from, Object to, float maxDelta) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.MoveTowards.class);
        setInputConstant(n, "from", from);
        setInputConstant(n, "to", to);
        setInputConstant(n, "maxDelta", maxDelta);
        return eval(g, n, "out");
    }

    private static boolean nearlyEqual(Object a, Object b, float epsilon) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.NearlyEquals.class);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        setInputConstant(n, "epsilon", epsilon);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class);
    }

    /** Any node whose inputs are the conventional {@code a}/{@code b} pair. */
    private static Object binary(Class<? extends Node> nodeClass, Object a, Object b) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        return eval(g, n, "out");
    }

    /** Any node whose single input is {@code in}. */
    private static Object unary(Class<? extends Node> nodeClass, Object in) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setInputConstant(n, "in", in);
        return eval(g, n, "out");
    }

    private static void assertAngle(GameTestHelper helper, String label, Vector3f a, Vector3f b,
                                    float expected) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.AngleBetween.class);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        float actual = evalF(g, n, "out");
        assertTrue(helper, label + " is not NaN, it was " + actual, !Float.isNaN(actual));
        assertEq(helper, label, expected, actual, 1e-3f);
    }

    private static void assertRotation(GameTestHelper helper, String label, Vector3f in,
                                       float yaw, float pitch) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, VectorGeometryNodes.ToRotation.class);
        setInputConstant(n, "in", in);
        GraphExecutor exec = new GraphExecutor(g);
        assertEq(helper, label + " yaw", yaw,
                exec.evaluate(n.getOutputsById().get("yaw"), Float.class), TRIG_EPS);
        assertEq(helper, label + " pitch", pitch,
                exec.evaluate(n.getOutputsById().get("pitch"), Float.class), TRIG_EPS);
    }

    private static void wire(BlueprintGraph g, NodeModel dst, NodeModel src) {
        com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire(
                g, dst.getInputsById().get("in"), src.getOutputsById().get("out"));
    }

    private static Object eval(BlueprintGraph g, NodeModel node, String port) {
        return new GraphExecutor(g).evaluate(node.getOutputsById().get(port), Object.class);
    }

    private static float evalF(BlueprintGraph g, NodeModel node, String port) {
        return new GraphExecutor(g).evaluate(node.getOutputsById().get(port), Float.class);
    }

    private static void assertVec(GameTestHelper helper, String label, float[] expected, Object actual) {
        assertVec(helper, label, expected, actual, EPS);
    }

    private static void assertVec(GameTestHelper helper, String label, float[] expected, Object actual,
                                  float epsilon) {
        float[] got = Vectors.components(actual);
        assertEq(helper, label + " width", expected.length, got.length);
        for (int i = 0; i < expected.length; i++) {
            assertEq(helper, label + " component " + i, expected[i], got[i], epsilon);
        }
    }
}
