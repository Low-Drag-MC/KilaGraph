package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.DeltaAngleNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.InverseLerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ModuloNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MoveTowardsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.NearlyEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RoundNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SmoothstepNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SnapNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.StepNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.TrigNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.WaveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.WrapNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertFalse;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * The scalar maths beyond {@link MathNodeGameTest}'s original arithmetic: the range, easing and
 * angle nodes.
 *
 * <p>Each one is checked at the place its formula is easiest to get wrong — a negative input to a
 * fold, the exact boundary of a half-open range, the last step before a target — rather than only
 * in the middle, where nearly any implementation agrees.</p>
 */
public final class MathNodeExtrasGameTest {
    private static final String WRAP = "math_extras_wrap";
    private static final String WRAP_EXACT = "math_extras_wrap_exact";
    private static final String DELTA_ANGLE = "math_extras_delta_angle";
    private static final String MOVE_TOWARDS = "math_extras_move_towards";
    private static final String NEARLY_EQUALS = "math_extras_nearly_equals";
    private static final String SMOOTHSTEP = "math_extras_smoothstep";
    private static final String STEP = "math_extras_step";
    private static final String INVERSE_LERP = "math_extras_inverse_lerp";
    private static final String SNAP = "math_extras_snap";
    private static final String WAVE = "math_extras_wave";
    private static final String TRIG = "math_extras_trig";

    public static void registerFunctions() {
        KGGameTests.registerFunction(WRAP, MathNodeExtrasGameTest::wrapFoldsFromBothSides);
        KGGameTests.registerFunction(WRAP_EXACT, MathNodeExtrasGameTest::wrapOnATickCounterIsExact);
        KGGameTests.registerFunction(DELTA_ANGLE, MathNodeExtrasGameTest::deltaAngleTakesTheShortWay);
        KGGameTests.registerFunction(MOVE_TOWARDS, MathNodeExtrasGameTest::moveTowardsArrivesExactly);
        KGGameTests.registerFunction(NEARLY_EQUALS, MathNodeExtrasGameTest::nearlyEqualsForgivesFloatDrift);
        KGGameTests.registerFunction(SMOOTHSTEP, MathNodeExtrasGameTest::smoothstepIsClampedAndEased);
        KGGameTests.registerFunction(STEP, MathNodeExtrasGameTest::stepSwitchesAtTheEdgeInclusive);
        KGGameTests.registerFunction(INVERSE_LERP, MathNodeExtrasGameTest::inverseLerpInvertsLerp);
        KGGameTests.registerFunction(SNAP, MathNodeExtrasGameTest::snapQuantisesAndAgreesWithRoundAtOne);
        KGGameTests.registerFunction(WAVE, MathNodeExtrasGameTest::wavesAreBoundedAndPeriodic);
        KGGameTests.registerFunction(TRIG, MathNodeExtrasGameTest::trigStillAnswersAndNowHasHyperbolics);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        var data = KGGameTests.defaultTestData(environment);
        for (String p : new String[]{
                WRAP, WRAP_EXACT, DELTA_ANGLE, MOVE_TOWARDS, NEARLY_EQUALS, SMOOTHSTEP, STEP,
                INVERSE_LERP, SNAP, WAVE, TRIG
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), data);
        }
    }

    private static final float EPS = 1e-4f;

    private MathNodeExtrasGameTest() {
    }

    public static void wrapFoldsFromBothSides(GameTestHelper helper) {
        assertEq(helper, "370 into [0,360)", 10f, wrap(370f, 0f, 360f), EPS);
        assertEq(helper, "-10 into [0,360)", 350f, wrap(-10f, 0f, 360f), EPS);
        assertEq(helper, "-370 into [0,360)", 350f, wrap(-370f, 0f, 360f), EPS);
        assertEq(helper, "a value already inside is untouched", 123f, wrap(123f, 0f, 360f), EPS);

        assertEq(helper, "360 into [0,360)", 0f, wrap(360f, 0f, 360f), EPS);
        assertEq(helper, "0 into [0,360)", 0f, wrap(0f, 0f, 360f), EPS);

        assertEq(helper, "-190 into [-180,180)", 170f, wrap(-190f, -180f, 180f), EPS);
        assertEq(helper, "190 into [-180,180)", -170f, wrap(190f, -180f, 180f), EPS);
        assertEq(helper, "180 into [-180,180)", -180f, wrap(180f, -180f, 180f), EPS);

        // The reason the node exists: Java's remainder keeps the dividend's sign.
        assertEq(helper, "modulo leaves a negative negative", -10f,
                runFloat(ModuloNode.class, Map.entry("a", -10f), Map.entry("b", 360f)), EPS);

        assertEq(helper, "an empty range answers min", 10f, wrap(5f, 10f, 10f), EPS);
        assertEq(helper, "an inverted range answers min", 10f, wrap(5f, 10f, 0f), EPS);
        helper.succeed();
    }

    /**
     * Past 2^24 a float can no longer count one by one, so a tick counter wrapped through the float
     * lane would put two adjacent ticks in the same slot.
     */
    public static void wrapOnATickCounterIsExact(GameTestHelper helper) {
        long first = 16_777_216L;
        Object a = wrapLong(first, 0f, 40f);
        Object b = wrapLong(first + 1L, 0f, 40f);
        assertTrue(helper, "a wrapped long answers a whole number, got " + render(a), a instanceof Long);
        assertEq(helper, "16777216 into [0,40)", first % 40L, a);
        assertEq(helper, "16777217 into [0,40)", (first + 1L) % 40L, b);
        if (a.equals(b)) {
            helper.fail("adjacent ticks wrapped to the same slot (" + a + ") — the float lane leaked in");
            return;
        }

        assertEq(helper, "-1 into [0,40)", 39L, wrapLong(-1L, 0f, 40f));
        helper.succeed();
    }

    public static void deltaAngleTakesTheShortWay(GameTestHelper helper) {
        assertEq(helper, "179 to -179", 2f, delta(179f, -179f), EPS);
        assertEq(helper, "-179 to 179", -2f, delta(-179f, 179f), EPS);
        assertEq(helper, "10 to 350", -20f, delta(10f, 350f), EPS);

        assertEq(helper, "no turn", 0f, delta(90f, 90f), EPS);
        assertEq(helper, "an ordinary turn is just the difference", 30f, delta(10f, 40f), EPS);

        assertEq(helper, "730 to 10 is no turn at all", 0f, delta(730f, 10f), EPS);
        assertEq(helper, "-350 and 10 are the same heading", 0f, delta(-350f, 10f), EPS);

        // Exactly opposite: both ways round are 180, and the half-open range picks the lower end.
        assertEq(helper, "0 to 180", -180f, delta(0f, 180f), EPS);
        helper.succeed();
    }

    public static void moveTowardsArrivesExactly(GameTestHelper helper) {
        assertEq(helper, "one step up", 2f, moveTowards(0f, 10f, 2f), EPS);
        assertEq(helper, "one step down", -2f, moveTowards(0f, -10f, 2f), EPS);

        assertEq(helper, "the last step lands exactly", 10f, moveTowards(9.5f, 10f, 2f), EPS);
        assertEq(helper, "already there", 10f, moveTowards(10f, 10f, 2f), EPS);
        assertEq(helper, "a zero step still cannot overshoot", 10f, moveTowards(10f, 10f, 0f), EPS);

        float v = 0f;
        for (int i = 0; i < 20; i++) v = moveTowards(v, 10f, 1f);
        assertEq(helper, "twenty steps of 1 have arrived", 10f, v, 0f);

        // The difference from a lerp, stated as a test rather than as a comment.
        float lerped = 0f;
        for (int i = 0; i < 20; i++) lerped = lerp(lerped, 10f, 0.1f);
        assertFalse(helper, "lerp at 0.1 has not arrived after twenty ticks, it is at " + lerped,
                lerped == 10f);

        assertEq(helper, "a negative limit moves away", -2f, moveTowards(0f, 10f, -2f), EPS);
        helper.succeed();
    }

    public static void nearlyEqualsForgivesFloatDrift(GameTestHelper helper) {
        float drifted = 0f;
        for (int i = 0; i < 10; i++) drifted += 0.1f;
        assertFalse(helper, "ten tenths is not exactly one, it is " + drifted, drifted == 1f);
        assertTrue(helper, "but it is nearly one", nearlyEquals(drifted, 1f, 1e-4f));

        assertTrue(helper, "identical values", nearlyEquals(5f, 5f, 1e-4f));
        assertFalse(helper, "genuinely different values", nearlyEquals(5f, 5.5f, 1e-4f));
        assertTrue(helper, "exactly at the tolerance counts as equal", nearlyEquals(5f, 5.1f, 0.1f));

        assertTrue(helper, "a negative tolerance is read as its magnitude",
                nearlyEquals(5f, 5.05f, -0.1f));
        helper.succeed();
    }

    public static void smoothstepIsClampedAndEased(GameTestHelper helper) {
        assertEq(helper, "below the range", 0f, smoothstep(-5f, 0f, 10f), EPS);
        assertEq(helper, "at the bottom", 0f, smoothstep(0f, 0f, 10f), EPS);
        assertEq(helper, "the middle", 0.5f, smoothstep(5f, 0f, 10f), EPS);
        assertEq(helper, "at the top", 1f, smoothstep(10f, 0f, 10f), EPS);
        assertEq(helper, "above the range", 1f, smoothstep(50f, 0f, 10f), EPS);

        // Eased, not linear: a quarter of the way along is well under a quarter of the way up.
        float quarter = smoothstep(2.5f, 0f, 10f);
        assertTrue(helper, "a quarter along is below a quarter up, it was " + quarter, quarter < 0.2f);
        assertTrue(helper, "but still above zero, it was " + quarter, quarter > 0f);

        assertEq(helper, "reversed edges, bottom", 1f, smoothstep(0f, 10f, 0f), EPS);
        assertEq(helper, "reversed edges, top", 0f, smoothstep(10f, 10f, 0f), EPS);

        assertEq(helper, "equal edges below", 0f, smoothstep(4f, 5f, 5f), EPS);
        assertEq(helper, "equal edges at", 1f, smoothstep(5f, 5f, 5f), EPS);
        helper.succeed();
    }

    public static void stepSwitchesAtTheEdgeInclusive(GameTestHelper helper) {
        assertEq(helper, "below", 0f, step(4.9f, 5f), EPS);
        assertEq(helper, "exactly at the edge", 1f, step(5f, 5f), EPS);
        assertEq(helper, "above", 1f, step(5.1f, 5f), EPS);
        assertEq(helper, "negative edge", 1f, step(0f, -1f), EPS);
        helper.succeed();
    }

    public static void inverseLerpInvertsLerp(GameTestHelper helper) {
        assertEq(helper, "bottom", 0f, inverseLerp(10f, 20f, 10f), EPS);
        assertEq(helper, "middle", 0.5f, inverseLerp(10f, 20f, 15f), EPS);
        assertEq(helper, "top", 1f, inverseLerp(10f, 20f, 20f), EPS);

        assertEq(helper, "below the range", -0.5f, inverseLerp(10f, 20f, 5f), EPS);
        assertEq(helper, "above the range", 1.5f, inverseLerp(10f, 20f, 25f), EPS);

        float t = inverseLerp(10f, 20f, 13.7f);
        assertEq(helper, "lerp undoes inverse lerp", 13.7f, lerp(10f, 20f, t), 1e-3f);

        assertEq(helper, "a descending range", 0.25f, inverseLerp(20f, 10f, 17.5f), EPS);

        assertEq(helper, "a range of no width", 0f, inverseLerp(5f, 5f, 5f), EPS);
        helper.succeed();
    }

    public static void snapQuantisesAndAgreesWithRoundAtOne(GameTestHelper helper) {
        assertEq(helper, "onto halves", 2.5f, snap(2.6f, 0.5f), EPS);
        assertEq(helper, "onto 45s, rounding up", 45f, snap(30f, 45f), EPS);
        assertEq(helper, "onto 45s, rounding down", 0f, snap(20f, 45f), EPS);
        assertEq(helper, "a negative value", -2.5f, snap(-2.6f, 0.5f), EPS);
        assertEq(helper, "already on the grid", 8f, snap(8f, 4f), EPS);

        for (float v : new float[] {0.5f, 1.5f, -1.5f, 2.4f, -2.6f, 7f}) {
            assertEq(helper, "snap(" + v + ", 1) matches round", roundOp(v, RoundNode.Op.ROUND),
                    snap(v, 1f), EPS);
        }

        assertEq(helper, "a step of zero passes the value through", 3.7f, snap(3.7f, 0f), EPS);
        helper.succeed();
    }

    public static void wavesAreBoundedAndPeriodic(GameTestHelper helper) {
        for (WaveNode.Op op : WaveNode.Op.values()) {
            for (int i = 0; i <= 16; i++) {
                float t = i / 16f;
                float v = wave(t, op);
                if (!(v >= -1.0001f && v <= 1.0001f)) {
                    helper.fail(op + " left [-1,1] at t=" + t + ": " + v);
                    return;
                }

                for (float cycles : new float[] {1f, 2f, -3f}) {
                    assertEq(helper, op + " at t=" + t + " repeats after " + cycles,
                            v, wave(t + cycles, op), 1e-3f);
                }
            }
        }

        assertEq(helper, "sine starts at zero", 0f, wave(0f, WaveNode.Op.SINE), EPS);
        assertEq(helper, "sine peaks a quarter in", 1f, wave(0.25f, WaveNode.Op.SINE), EPS);
        assertEq(helper, "the square has no middle ground", 1f,
                Math.abs(wave(0.3f, WaveNode.Op.SQUARE)), EPS);
        assertTrue(helper, "the square changes sign across the cycle",
                wave(0.25f, WaveNode.Op.SQUARE) != wave(0.75f, WaveNode.Op.SQUARE));

        assertTrue(helper, "the sawtooth ramps up",
                wave(0.1f, WaveNode.Op.SAWTOOTH) < wave(0.4f, WaveNode.Op.SAWTOOTH));
        helper.succeed();
    }

    public static void trigStillAnswersAndNowHasHyperbolics(GameTestHelper helper) {
        assertEq(helper, "sin(0)", 0f, trig(0f, TrigNode.Op.SIN), EPS);
        assertEq(helper, "cos(0)", 1f, trig(0f, TrigNode.Op.COS), EPS);

        assertEq(helper, "sinh(0)", 0f, trig(0f, TrigNode.Op.SINH), EPS);
        assertEq(helper, "cosh(0)", 1f, trig(0f, TrigNode.Op.COSH), EPS);
        assertEq(helper, "tanh(0)", 0f, trig(0f, TrigNode.Op.TANH), EPS);
        assertEq(helper, "sinh(1)", (float) Math.sinh(1d), trig(1f, TrigNode.Op.SINH), EPS);

        float huge = trig(100f, TrigNode.Op.TANH);
        assertTrue(helper, "tanh of a large input approaches 1 without reaching it, it was " + huge,
                huge > 0.999f && huge <= 1f);
        assertEq(helper, "and is odd", -huge, trig(-100f, TrigNode.Op.TANH), EPS);
        helper.succeed();
    }

    @SafeVarargs
    private static float runFloat(Class<? extends Node> nodeClass, Map.Entry<String, Object>... inputs) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        for (var e : inputs) setInputConstant(n, e.getKey(), e.getValue());
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
    }

    private static float wrap(float v, float lo, float hi) {
        return runFloat(WrapNode.class, Map.entry("in", v), Map.entry("min", lo), Map.entry("max", hi));
    }

    /** Wraps through a wired long source, so the node sees the integer lane rather than a constant. */
    private static Object wrapLong(long v, float lo, float hi) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, WrapNode.class);
        wire(g, n.getInputsById().get("in"), valueSource(g.graphModel, "tick", long.class, v));

        setInputConstant(n, "min", lo);
        setInputConstant(n, "max", hi);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class);
    }

    private static float delta(float from, float to) {
        return runFloat(DeltaAngleNode.class, Map.entry("from", from), Map.entry("to", to));
    }

    private static float moveTowards(float from, float to, float maxDelta) {
        return runFloat(MoveTowardsNode.class, Map.entry("from", from), Map.entry("to", to),
                Map.entry("maxDelta", maxDelta));
    }

    private static float lerp(float a, float b, float t) {
        return runFloat(LerpNode.class, Map.entry("a", a), Map.entry("b", b), Map.entry("t", t));
    }

    private static float inverseLerp(float a, float b, float value) {
        return runFloat(InverseLerpNode.class, Map.entry("a", a), Map.entry("b", b),
                Map.entry("value", value));
    }

    private static float smoothstep(float v, float e0, float e1) {
        return runFloat(SmoothstepNode.class, Map.entry("in", v), Map.entry("edge0", e0),
                Map.entry("edge1", e1));
    }

    private static float step(float v, float edge) {
        return runFloat(StepNode.class, Map.entry("in", v), Map.entry("edge", edge));
    }

    private static float snap(float v, float stepSize) {
        return runFloat(SnapNode.class, Map.entry("in", v), Map.entry("step", stepSize));
    }

    private static boolean nearlyEquals(float a, float b, float epsilon) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, NearlyEqualsNode.class);
        setInputConstant(n, "a", a);
        setInputConstant(n, "b", b);
        setInputConstant(n, "epsilon", epsilon);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class);
    }

    private static float wave(float t, WaveNode.Op op) {
        return withOp(WaveNode.class, t, op);
    }

    private static float trig(float v, TrigNode.Op op) {
        return withOp(TrigNode.class, v, op);
    }

    private static float roundOp(float v, RoundNode.Op op) {
        return withOp(RoundNode.class, v, op);
    }

    private static float withOp(Class<? extends Node> nodeClass, float v, Object op) {
        BlueprintGraph g = newGraph();
        NodeModel n = addNode(g, nodeClass);
        setOption(n, "op", op);
        setInputConstant(n, "in", v);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
    }

    private static String render(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName() + ":" + v;
    }
}
