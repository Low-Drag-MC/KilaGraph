package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.DivideNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MaxNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MinNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ModuloNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.NegateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SignNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The {@code NumericLane} rule, end to end through real graphs.
 *
 * <h2>What is being pinned down</h2>
 * The math and comparison nodes declare {@code float} ports but decide at evaluation time whether to
 * work in {@code long}, {@code float} or {@code double}, from what actually reaches them. Two halves
 * of that are worth a test each, and they pull in opposite directions:
 * <ul>
 *   <li><b>the fix</b> — a whole number on a wire has to stay exact, past 2^24 where a float stops
 *       being able to tell consecutive integers apart;</li>
 *   <li><b>the promise not to break anything</b> — a graph built before the rule existed, whose
 *       inputs are all float constants, has to keep answering the identical {@code Float}. The weak
 *       constant rule is what makes both true at once, so most of these tests are really about
 *       it.</li>
 * </ul>
 *
 * <p>Long values come from an {@code INPUT} graph variable declared {@code long}, because that is the
 * shortest thing in the library that puts a real {@code Long} on a wire — the way {@code gameTime}
 * and the NBT readers do in a player's graph, and the way a float-typed embedded constant deliberately
 * does not.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class NumericPromotionGameTest {

    private NumericPromotionGameTest() {}

    /** A tick count well past 2^24, where a float can no longer hold consecutive integers apart. */
    private static final long BIG = 3_090_200_953_712_304_400L;

    /** Just past 2^24 (16777216) — the first place the old float lane started skipping values. */
    private static final long PAST_MANTISSA = 16_777_217L;

    // ---- plumbing ----------------------------------------------------------------------------

    private static PortModel source(BlueprintGraph g, String name, Class<?> type, Object value) {
        return valueSource(g.graphModel, name, type, value);
    }

    private static PortModel longSource(BlueprintGraph g, String name, long value) {
        return source(g, name, long.class, value);
    }

    /** The node's {@code out}, as whatever object it actually published. */
    private static Object out(BlueprintGraph g, NodeModel n) {
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class);
    }

    /** {@code out}, computed with intrinsics on and again with them off; fails if they disagree. */
    private static Object outBothWays(GameTestHelper helper, String label,
                                      BlueprintGraph g, NodeModel n) {
        var port = n.getOutputsById().get("out");
        var on = new GraphExecutor(g);
        var off = new GraphExecutor(g);
        off.setOptimisationEnabled(GraphExecutor.Opt.INTRINSICS, false);
        Object a = on.evaluate(port, Object.class);
        Object b = off.evaluate(port, Object.class);
        // The runtime class has to match too, not just the value: the whole point of the lane check
        // inside the intrinsic is that it declines to answer with a Float where the node says Long.
        boolean same = a == null ? b == null : b != null && a.getClass() == b.getClass() && a.equals(b);
        if (!same) {
            helper.fail(label + ": intrinsic gave " + render(a) + ", node gave " + render(b));
        }
        return a;
    }

    private static String render(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName() + ":" + v;
    }

    /** A two-input node with {@code a} wired to a long and {@code b} left as its float constant. */
    private static NodeModel longAndConstant(BlueprintGraph g, Class<? extends Node> nodeClass,
                                             long a, String bId, Object b) {
        var n = addNode(g, nodeClass);
        wire(g, n.getInputsById().get("a"), longSource(g, "a", a));
        setInputConstant(n, bId, b);
        return n;
    }

    // ---- the fix -----------------------------------------------------------------------------

    /**
     * The bug this whole rule exists for: {@code gameTime % 40} freezing on one value.
     *
     * <p>Consecutive ticks must give consecutive remainders. Through the old float-only node they
     * gave the <em>same</em> remainder, because past 2^24 a tick and the next tick are one float — so
     * the "different from the last one" check below is the actual regression test, and the exact-value
     * assertions are what say it is right rather than merely varying.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void moduloOnATickCounterIsExact(GameTestHelper helper) {
        long previous = Long.MIN_VALUE;
        for (long i = 0; i < 5; i++) {
            long tick = BIG + i;
            var g = newGraph();
            var n = longAndConstant(g, ModuloNode.class, tick, "b", 40f);
            Object got = outBothWays(helper, "tick " + tick + " % 40", g, n);
            assertEq(helper, "tick " + tick + " % 40", tick % 40L, got);
            if (got instanceof Long l) {
                if (l == previous) {
                    helper.fail("consecutive ticks gave the same remainder (" + l
                            + ") — the float lane leaked back in");
                    return;
                }
                previous = l;
            }
        }
        helper.succeed();
    }

    /** Sign and the zero divisor keep the behaviour the float lane had. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void moduloEdgesInTheWholeLane(GameTestHelper helper) {
        var g1 = newGraph();
        assertEq(helper, "-7 % 3 takes the sign of a", -1L,
                out(g1, longAndConstant(g1, ModuloNode.class, -7L, "b", 3f)));

        var g2 = newGraph();
        assertEq(helper, "10 % 0 = 0", 0L,
                out(g2, longAndConstant(g2, ModuloNode.class, 10L, "b", 0f)));
        helper.succeed();
    }

    /** Add, Subtract, Multiply, Min, Max, Clamp, Abs, Sign and Negate all reach the whole lane. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void wholeNumbersSurviveEveryPromotedOperation(GameTestHelper helper) {
        var gAdd = newGraph();
        var add = addNode(gAdd, AddNode.class);
        wire(gAdd, add.getInputsById().get("in1"), longSource(gAdd, "t", PAST_MANTISSA));
        setInputConstant(add, "in2", 1f);
        assertEq(helper, "2^24+1 plus 1", PAST_MANTISSA + 1L,
                outBothWays(helper, "add", gAdd, add));

        var gSub = newGraph();
        assertEq(helper, "2^24+1 minus 1", PAST_MANTISSA - 1L,
                outBothWays(helper, "sub", gSub,
                        longAndConstant(gSub, SubtractNode.class, PAST_MANTISSA, "b", 1f)));

        var gMul = newGraph();
        var mul = addNode(gMul, MultiplyNode.class);
        wire(gMul, mul.getInputsById().get("in1"), longSource(gMul, "t", PAST_MANTISSA));
        setInputConstant(mul, "in2", 3f);
        assertEq(helper, "(2^24+1) * 3", PAST_MANTISSA * 3L, outBothWays(helper, "mul", gMul, mul));

        var gMin = newGraph();
        var min = addNode(gMin, MinNode.class);
        wire(gMin, min.getInputsById().get("in1"), longSource(gMin, "t", PAST_MANTISSA));
        setInputConstant(min, "in2", 2e7f);
        assertEq(helper, "min keeps the exact whole number", PAST_MANTISSA,
                outBothWays(helper, "min", gMin, min));

        var gMax = newGraph();
        var max = addNode(gMax, MaxNode.class);
        wire(gMax, max.getInputsById().get("in1"), longSource(gMax, "t", PAST_MANTISSA));
        setInputConstant(max, "in2", 0f);
        assertEq(helper, "max keeps the exact whole number", PAST_MANTISSA,
                outBothWays(helper, "max", gMax, max));

        var gClamp = newGraph();
        var clamp = addNode(gClamp, ClampNode.class);
        wire(gClamp, clamp.getInputsById().get("in"), longSource(gClamp, "t", PAST_MANTISSA));
        setInputConstant(clamp, "min", 0f);
        setInputConstant(clamp, "max", 1e18f);
        assertEq(helper, "clamp inside the range is the input", PAST_MANTISSA,
                outBothWays(helper, "clamp", gClamp, clamp));

        var gAbs = newGraph();
        var abs = addNode(gAbs, AbsNode.class);
        wire(gAbs, abs.getInputsById().get("in"), longSource(gAbs, "t", -PAST_MANTISSA));
        assertEq(helper, "abs of a large negative whole number", PAST_MANTISSA,
                outBothWays(helper, "abs", gAbs, abs));

        var gNeg = newGraph();
        var neg = addNode(gNeg, NegateNode.class);
        wire(gNeg, neg.getInputsById().get("in"), longSource(gNeg, "t", PAST_MANTISSA));
        assertEq(helper, "negate keeps every digit", -PAST_MANTISSA,
                outBothWays(helper, "negate", gNeg, neg));

        var gSign = newGraph();
        var sign = addNode(gSign, SignNode.class);
        wire(gSign, sign.getInputsById().get("in"), longSource(gSign, "t", -BIG));
        assertEq(helper, "sign of a whole number is whole", -1L,
                outBothWays(helper, "sign", gSign, sign));

        helper.succeed();
    }

    /**
     * The comparisons, which had the same defect with no escape hatch at all.
     *
     * <p>{@code BIG} and {@code BIG + 1} are the same {@code float}, so through the old lane
     * {@code a < b} was false and {@code a >= b} was true — the graph could not see the tick
     * advance.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void comparisonsSeeConsecutiveTicksApart(GameTestHelper helper) {
        record Case(Class<? extends Node> node, String label, long a, long b, boolean expected) {}
        // Both orders of the pair, because each operator is only <em>distinguishing</em> in one of
        // them: in the float lane BIG and BIG+1 are equal, so "BIG > BIG+1" is false either way and
        // proves nothing. It is "BIG+1 > BIG" that separates the lanes for '>' and '>='.
        var cases = new Case[]{
                new Case(LessThanNode.class, "BIG < BIG+1", BIG, BIG + 1L, true),
                new Case(LessEqualNode.class, "BIG <= BIG+1", BIG, BIG + 1L, true),
                new Case(GreaterThanNode.class, "BIG > BIG+1", BIG, BIG + 1L, false),
                new Case(GreaterEqualNode.class, "BIG >= BIG+1", BIG, BIG + 1L, false),
                new Case(GreaterThanNode.class, "BIG+1 > BIG", BIG + 1L, BIG, true),
                new Case(GreaterEqualNode.class, "BIG+1 >= BIG", BIG + 1L, BIG, true),
                new Case(LessThanNode.class, "BIG+1 < BIG", BIG + 1L, BIG, false),
                new Case(LessEqualNode.class, "BIG+1 <= BIG", BIG + 1L, BIG, false),
        };
        for (Case c : cases) {
            var g = newGraph();
            var n = addNode(g, c.node());
            wire(g, n.getInputsById().get("a"), longSource(g, "a", c.a()));
            wire(g, n.getInputsById().get("b"), longSource(g, "b", c.b()));
            assertEq(helper, c.label(), c.expected(), outBothWays(helper, c.label(), g, n));
        }
        helper.succeed();
    }

    // ---- the promise not to break anything ----------------------------------------------------

    /**
     * A graph of nothing but float constants answers the identical {@code Float} it always did.
     *
     * <p>The runtime class is asserted, not just the value: a {@code Long} 5 where a {@code Float}
     * 5.0 used to be would still pass a numeric comparison and would still be wrong — {@code ToString}
     * downstream would start printing "5" instead of "5.0".</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void constantOnlyGraphsStayFloat(GameTestHelper helper) {
        var gMod = newGraph();
        var mod = addNode(gMod, ModuloNode.class);
        setInputConstant(mod, "a", 10f);
        setInputConstant(mod, "b", 3f);
        assertEq(helper, "10 % 3 stays a Float", 1f, outBothWays(helper, "mod", gMod, mod));

        var gSub = newGraph();
        var sub = addNode(gSub, SubtractNode.class);
        setInputConstant(sub, "a", 7f);
        setInputConstant(sub, "b", 2f);
        assertEq(helper, "7 - 2 stays a Float", 5f, outBothWays(helper, "sub", gSub, sub));

        var gAdd = newGraph();
        var add = addNode(gAdd, AddNode.class);
        setInputConstant(add, "in1", 2f);
        setInputConstant(add, "in2", 3f);
        assertEq(helper, "2 + 3 stays a Float", 5f, outBothWays(helper, "add", gAdd, add));

        var gCmp = newGraph();
        var cmp = addNode(gCmp, GreaterThanNode.class);
        setInputConstant(cmp, "a", 3f);
        setInputConstant(cmp, "b", 2f);
        assertEq(helper, "3 > 2", true, outBothWays(helper, "gt", gCmp, cmp));
        helper.succeed();
    }

    /**
     * A whole-looking float constant is weak — it adopts the wire's lane instead of dragging the
     * arithmetic back into float.
     *
     * <p>This is the crux of the whole design. {@code Modulo}'s {@code b} port is declared
     * {@code float}, so typing 40 stores {@code 40.0f}; if that counted as "someone asked for float"
     * then {@code gameTime % 40} — the exact case being fixed — would still be broken.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aWholeFloatConstantDoesNotForceTheFloatLane(GameTestHelper helper) {
        var g = newGraph();
        var n = longAndConstant(g, SubtractNode.class, PAST_MANTISSA, "b", 1f);
        Object got = outBothWays(helper, "sub", g, n);
        assertEq(helper, "the constant went along with the wire", PAST_MANTISSA - 1L, got);
        if (!(got instanceof Long)) {
            helper.fail("expected the whole lane, got " + render(got));
            return;
        }
        helper.succeed();
    }

    /**
     * ...but a constant that carries a fraction <em>does</em> force a float lane, because a fraction
     * is not expressible in the whole one. Asking for {@code x - 0.5} has to mean it.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aFractionalConstantForcesTheFloatLane(GameTestHelper helper) {
        var g = newGraph();
        var n = longAndConstant(g, SubtractNode.class, 10L, "b", 0.5f);
        Object got = outBothWays(helper, "10 - 0.5", g, n);
        assertEq(helper, "10 - 0.5", 9.5f, got);
        if (!(got instanceof Float)) {
            helper.fail("a fractional constant must keep the float lane, got " + render(got));
            return;
        }
        helper.succeed();
    }

    /** A genuinely double producer wins over a long one — the widest lane asked for is the lane. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void doubleBeatsWhole(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, AddNode.class);
        wire(g, n.getInputsById().get("in1"), longSource(g, "a", 1L));
        wire(g, n.getInputsById().get("in2"), source(g, "b", double.class, 0.25d));
        Object got = outBothWays(helper, "1 + 0.25", g, n);
        assertEq(helper, "1 + 0.25", 1.25d, got);
        if (!(got instanceof Double)) {
            helper.fail("expected the double lane, got " + render(got));
            return;
        }
        helper.succeed();
    }

    /** The lane folds across every input of a variadic node, not just the first two. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theLaneFoldsOverEveryVariadicInput(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, AddNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", 1f);
        setInputConstant(n, "in2", 2f);
        wire(g, n.getInputsById().get("in3"), longSource(g, "t", PAST_MANTISSA));
        assertEq(helper, "the third input names the lane", PAST_MANTISSA + 3L,
                outBothWays(helper, "add3", g, n));
        helper.succeed();
    }

    /**
     * The operations that are <em>not</em> closed over whole numbers keep their float answer whatever
     * they are fed.
     *
     * <p>{@code Divide} is the deliberate one: promoting it would turn {@code 7 / 2} into {@code 3},
     * which is a worse trap than the one being fixed. {@code Sqrt} and {@code Lerp} have no whole
     * answer to give in the first place.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void realValuedOperationsStayFloat(GameTestHelper helper) {
        var gDiv = newGraph();
        Object div = outBothWays(helper, "7 / 2", gDiv,
                longAndConstant(gDiv, DivideNode.class, 7L, "b", 2f));
        assertEq(helper, "7 / 2 is not integer division", 3.5f, div);

        var gSqrt = newGraph();
        var sqrt = addNode(gSqrt, SqrtNode.class);
        wire(gSqrt, sqrt.getInputsById().get("in"), longSource(gSqrt, "t", 9L));
        assertEq(helper, "sqrt(9)", 3f, outBothWays(helper, "sqrt", gSqrt, sqrt));

        var gLerp = newGraph();
        var lerp = addNode(gLerp, LerpNode.class);
        wire(gLerp, lerp.getInputsById().get("a"), longSource(gLerp, "t", 0L));
        setInputConstant(lerp, "b", 10f);
        setInputConstant(lerp, "t", 0.5f);
        assertEq(helper, "lerp(0, 10, 0.5)", 5f, outBothWays(helper, "lerp", gLerp, lerp));
        helper.succeed();
    }

    /**
     * A chain keeps its lane: the whole number that comes out of one promoted node names the lane of
     * the next, so {@code (gameTime % 40) + 1} is exact all the way through rather than only at the
     * first hop.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void theLaneSurvivesAChainOfNodes(GameTestHelper helper) {
        var g = newGraph();
        var mod = longAndConstant(g, ModuloNode.class, BIG, "b", 40f);
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), mod.getOutputsById().get("out"));
        setInputConstant(add, "in2", 1f);
        assertEq(helper, "(BIG % 40) + 1", (BIG % 40L) + 1L, outBothWays(helper, "chain", g, add));
        helper.succeed();
    }
}
