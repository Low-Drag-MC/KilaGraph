package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AngleConvertNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.Atan2Node;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.DivideNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ExpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.FractNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LogBaseNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LogNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MaxNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MinNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ModuloNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.NegateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.PowNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RandomIntNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RandomNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RemapNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RoundNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SignNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.TrigNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.gametest.framework.GameTestHelper;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;

public final class MathNodeGameTest {
    private static final String MULTIPLY = "math_multiply";
    private static final String DIVIDE = "math_divide";
    private static final String DIVIDE_BY_ZERO = "math_divide_by_zero";
    private static final String MODULO = "math_modulo";
    private static final String NEGATE = "math_negate";
    private static final String ABS = "math_abs";
    private static final String MIN = "math_min";
    private static final String MAX = "math_max";
    private static final String CLAMP = "math_clamp";
    private static final String POW = "math_pow";
    private static final String SQRT = "math_sqrt";
    private static final String LOG = "math_log";
    private static final String EXP = "math_exp";
    private static final String TRIG = "math_trig";
    private static final String ROUND = "math_round";
    private static final String SIGN = "math_sign";
    private static final String LERP = "math_lerp";
    private static final String REMAP = "math_remap";
    private static final String RANDOM_DETERMINISTIC = "math_random_seeded";
    private static final String RANDOM_INT = "math_random_int_seeded";
    private static final String FRACT = "math_fract";
    private static final String ATAN2 = "math_atan2";
    private static final String LOG_BASE = "math_log_base";
    private static final String ANGLE_CONVERT = "math_angle_convert";

    private MathNodeGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(MULTIPLY, MathNodeGameTest::multiply);
        KGGameTests.registerFunction(DIVIDE, MathNodeGameTest::divide);
        KGGameTests.registerFunction(DIVIDE_BY_ZERO, MathNodeGameTest::divideByZero);
        KGGameTests.registerFunction(MODULO, MathNodeGameTest::modulo);
        KGGameTests.registerFunction(NEGATE, MathNodeGameTest::negate);
        KGGameTests.registerFunction(ABS, MathNodeGameTest::abs);
        KGGameTests.registerFunction(MIN, MathNodeGameTest::min);
        KGGameTests.registerFunction(MAX, MathNodeGameTest::max);
        KGGameTests.registerFunction(CLAMP, MathNodeGameTest::clamp);
        KGGameTests.registerFunction(POW, MathNodeGameTest::pow);
        KGGameTests.registerFunction(SQRT, MathNodeGameTest::sqrt);
        KGGameTests.registerFunction(LOG, MathNodeGameTest::log);
        KGGameTests.registerFunction(EXP, MathNodeGameTest::exp);
        KGGameTests.registerFunction(TRIG, MathNodeGameTest::trig);
        KGGameTests.registerFunction(ROUND, MathNodeGameTest::round);
        KGGameTests.registerFunction(SIGN, MathNodeGameTest::sign);
        KGGameTests.registerFunction(LERP, MathNodeGameTest::lerp);
        KGGameTests.registerFunction(REMAP, MathNodeGameTest::remap);
        KGGameTests.registerFunction(RANDOM_DETERMINISTIC, MathNodeGameTest::randomDeterministic);
        KGGameTests.registerFunction(FRACT, MathNodeGameTest::fract);
        KGGameTests.registerFunction(ATAN2, MathNodeGameTest::atan2);
        KGGameTests.registerFunction(LOG_BASE, MathNodeGameTest::logBase);
        KGGameTests.registerFunction(ANGLE_CONVERT, MathNodeGameTest::angleConvert);
        KGGameTests.registerFunction(RANDOM_INT, MathNodeGameTest::randomIntSeeded);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                MULTIPLY, DIVIDE, DIVIDE_BY_ZERO,
                MODULO, NEGATE, ABS,
                MIN, MAX, CLAMP,
                POW, SQRT, LOG,
                EXP, TRIG, ROUND,
                SIGN, LERP, REMAP,
                RANDOM_DETERMINISTIC, FRACT, ATAN2,
                LOG_BASE, ANGLE_CONVERT, RANDOM_INT
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** Run a simple float-out node with provided input constants; assert the output. */
    @SafeVarargs
    private static float runFloat(Class<? extends Node> nodeClass,
                                   Map.Entry<String, Object>... inputs) {
        var g = newGraph();
        var n = addNode(g, nodeClass);
        for (var e : inputs) {
            if (e.getValue() instanceof String s && e.getKey().startsWith("@option:")) {
                setOption(n, e.getKey().substring("@option:".length()), s);
            } else {
                setInputConstant(n, e.getKey(), e.getValue());
            }
        }
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
    }



    public static void multiply(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, MultiplyNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", 2f);
        setInputConstant(n, "in2", 3f);
        setInputConstant(n, "in3", 4f);
        assertEq(helper, "2*3*4", 24f,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }



    public static void divide(GameTestHelper helper) {
        assertEq(helper, "10/4",
                2.5f,
                runFloat(DivideNode.class, Map.entry("a", 10f), Map.entry("b", 4f)),
                1e-5f);
        helper.succeed();
    }



    public static void divideByZero(GameTestHelper helper) {
        assertEq(helper, "x/0 = 0",
                0f,
                runFloat(DivideNode.class, Map.entry("a", 10f), Map.entry("b", 0f)),
                0f);
        helper.succeed();
    }



    public static void modulo(GameTestHelper helper) {
        assertEq(helper, "10%3", 1f,
                runFloat(ModuloNode.class, Map.entry("a", 10f), Map.entry("b", 3f)), 1e-5f);
        assertEq(helper, "10%0 = 0", 0f,
                runFloat(ModuloNode.class, Map.entry("a", 10f), Map.entry("b", 0f)), 0f);
        helper.succeed();
    }



    public static void negate(GameTestHelper helper) {
        assertEq(helper, "-5", -5f, runFloat(NegateNode.class, Map.entry("in", 5f)), 1e-5f);
        assertEq(helper, "--(-3)", 3f, runFloat(NegateNode.class, Map.entry("in", -3f)), 1e-5f);
        helper.succeed();
    }



    public static void abs(GameTestHelper helper) {
        assertEq(helper, "|-7|", 7f, runFloat(AbsNode.class, Map.entry("in", -7f)), 1e-5f);
        assertEq(helper, "|7|", 7f, runFloat(AbsNode.class, Map.entry("in", 7f)), 1e-5f);
        helper.succeed();
    }



    public static void min(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, MinNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", 5f); setInputConstant(n, "in2", 2f); setInputConstant(n, "in3", 7f);
        assertEq(helper, "min(5,2,7)", 2f,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }



    public static void max(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, MaxNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", 5f); setInputConstant(n, "in2", 2f); setInputConstant(n, "in3", 7f);
        assertEq(helper, "max(5,2,7)", 7f,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }



    public static void clamp(GameTestHelper helper) {
        assertEq(helper, "clamp(5, 0, 10)", 5f,
                runFloat(ClampNode.class, Map.entry("in", 5f),
                        Map.entry("min", 0f), Map.entry("max", 10f)), 1e-5f);
        assertEq(helper, "clamp(-1, 0, 10)", 0f,
                runFloat(ClampNode.class, Map.entry("in", -1f),
                        Map.entry("min", 0f), Map.entry("max", 10f)), 1e-5f);
        assertEq(helper, "clamp(99, 0, 10)", 10f,
                runFloat(ClampNode.class, Map.entry("in", 99f),
                        Map.entry("min", 0f), Map.entry("max", 10f)), 1e-5f);
        helper.succeed();
    }



    public static void pow(GameTestHelper helper) {
        assertEq(helper, "2^10", 1024f,
                runFloat(PowNode.class, Map.entry("base", 2f), Map.entry("exp", 10f)),
                1e-3f);
        helper.succeed();
    }



    public static void sqrt(GameTestHelper helper) {
        assertEq(helper, "sqrt(9)", 3f, runFloat(SqrtNode.class, Map.entry("in", 9f)), 1e-5f);
        assertEq(helper, "sqrt(-1) = 0", 0f, runFloat(SqrtNode.class, Map.entry("in", -1f)), 0f);
        helper.succeed();
    }



    public static void log(GameTestHelper helper) {
        // log_e(e) = 1
        assertEq(helper, "log_e(e)", 1f,
                runFloat(LogNode.class,
                        Map.entry("in", (float) Math.E),
                        Map.entry("base", (float) Math.E)),
                1e-5f);
        // log_10(100) = 2
        assertEq(helper, "log_10(100)", 2f,
                runFloat(LogNode.class,
                        Map.entry("in", 100f),
                        Map.entry("base", 10f)),
                1e-4f);
        // non-positive → 0
        assertEq(helper, "log(0) = 0", 0f,
                runFloat(LogNode.class,
                        Map.entry("in", 0f),
                        Map.entry("base", (float) Math.E)),
                0f);
        helper.succeed();
    }



    public static void exp(GameTestHelper helper) {
        assertEq(helper, "e^0", 1f, runFloat(ExpNode.class, Map.entry("in", 0f)), 1e-5f);
        assertEq(helper, "e^1", (float) Math.E,
                runFloat(ExpNode.class, Map.entry("in", 1f)), 1e-4f);
        helper.succeed();
    }



    public static void trig(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, TrigNode.class);
        setOption(n, "op", TrigNode.Op.SIN);
        setInputConstant(n, "in", 0f);
        assertEq(helper, "sin(0)", 0f,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);

        var g2 = newGraph();
        var n2 = addNode(g2, TrigNode.class);
        setOption(n2, "op", TrigNode.Op.COS);
        setInputConstant(n2, "in", 0f);
        assertEq(helper, "cos(0)", 1f,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }



    public static void round(GameTestHelper helper) {
        for (var c : new Object[][]{{RoundNode.Op.ROUND, 3.6f, 4f}, {RoundNode.Op.ROUND, 3.4f, 3f},
                                     {RoundNode.Op.FLOOR, 3.9f, 3f}, {RoundNode.Op.CEIL, 3.1f, 4f},
                                     {RoundNode.Op.TRUNC, 3.9f, 3f}, {RoundNode.Op.TRUNC, -3.9f, -3f}}) {
            var g = newGraph();
            var n = addNode(g, RoundNode.class);
            setOption(n, "op", c[0]);
            setInputConstant(n, "in", (float) c[1]);
            assertEq(helper, c[0] + "(" + c[1] + ")", (float) c[2],
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);
        }
        helper.succeed();
    }



    public static void sign(GameTestHelper helper) {
        assertEq(helper, "sign(5)", 1f, runFloat(SignNode.class, Map.entry("in", 5f)), 0f);
        assertEq(helper, "sign(-5)", -1f, runFloat(SignNode.class, Map.entry("in", -5f)), 0f);
        assertEq(helper, "sign(0)", 0f, runFloat(SignNode.class, Map.entry("in", 0f)), 0f);
        helper.succeed();
    }



    public static void lerp(GameTestHelper helper) {
        assertEq(helper, "lerp(0,10,0.5)", 5f,
                runFloat(LerpNode.class, Map.entry("a", 0f),
                        Map.entry("b", 10f), Map.entry("t", 0.5f)), 1e-5f);
        assertEq(helper, "lerp(0,10,0)", 0f,
                runFloat(LerpNode.class, Map.entry("a", 0f),
                        Map.entry("b", 10f), Map.entry("t", 0f)), 1e-5f);
        assertEq(helper, "lerp(0,10,1)", 10f,
                runFloat(LerpNode.class, Map.entry("a", 0f),
                        Map.entry("b", 10f), Map.entry("t", 1f)), 1e-5f);
        helper.succeed();
    }



    public static void remap(GameTestHelper helper) {
        assertEq(helper, "0.5 of [0,1] → [0,100]", 50f,
                runFloat(RemapNode.class,
                        Map.entry("in", 0.5f),
                        Map.entry("fromMin", 0f), Map.entry("fromMax", 1f),
                        Map.entry("toMin", 0f), Map.entry("toMax", 100f)), 1e-4f);
        helper.succeed();
    }



    public static void randomDeterministic(GameTestHelper helper) {
        // Same seed → same value across two executors
        var g1 = newGraph();
        var n1 = addNode(g1, RandomNode.class);
        setInputConstant(n1, "min", 0f); setInputConstant(n1, "max", 100f);
        var env1 = new EvaluationEnvironment(new VariableStore(), OptionalLong.of(42L));
        Float v1 = new GraphExecutor(g1, env1).evaluate(n1.getOutputsById().get("out"), Float.class);

        var g2 = newGraph();
        var n2 = addNode(g2, RandomNode.class);
        setInputConstant(n2, "min", 0f); setInputConstant(n2, "max", 100f);
        var env2 = new EvaluationEnvironment(new VariableStore(), OptionalLong.of(42L));
        Float v2 = new GraphExecutor(g2, env2).evaluate(n2.getOutputsById().get("out"), Float.class);

        assertEq(helper, "seeded random is deterministic", v1, v2);
        helper.succeed();
    }



    public static void fract(GameTestHelper helper) {
        assertEq(helper, "fract(3.25)", 0.25f, runFloat(FractNode.class, Map.entry("in", 3.25f)), 1e-5f);
        assertEq(helper, "fract(-0.25)", 0.75f, runFloat(FractNode.class, Map.entry("in", -0.25f)), 1e-5f);
        helper.succeed();
    }



    public static void atan2(GameTestHelper helper) {
        assertEq(helper, "atan2(1,1)", (float) (Math.PI / 4),
                runFloat(Atan2Node.class, Map.entry("y", 1f), Map.entry("x", 1f)), 1e-5f);
        assertEq(helper, "atan2(0,1)", 0f,
                runFloat(Atan2Node.class, Map.entry("y", 0f), Map.entry("x", 1f)), 1e-5f);
        helper.succeed();
    }



    public static void logBase(GameTestHelper helper) {
        assertEq(helper, "log_2(8)", 3f,
                runFloat(LogBaseNode.class, Map.entry("value", 8f), Map.entry("base", 2f)), 1e-4f);
        assertEq(helper, "log_10(1000)", 3f,
                runFloat(LogBaseNode.class, Map.entry("value", 1000f), Map.entry("base", 10f)), 1e-4f);
        assertEq(helper, "log_base(value<=0) = 0", 0f,
                runFloat(LogBaseNode.class, Map.entry("value", 0f), Map.entry("base", 2f)), 0f);
        helper.succeed();
    }



    public static void angleConvert(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, AngleConvertNode.class);
        setOption(n, "op", AngleConvertNode.Op.DEG_TO_RAD);
        setInputConstant(n, "in", 180f);
        assertEq(helper, "deg2rad(180)", (float) Math.PI,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);

        var g2 = newGraph();
        var n2 = addNode(g2, AngleConvertNode.class);
        setOption(n2, "op", AngleConvertNode.Op.RAD_TO_DEG);
        setInputConstant(n2, "in", (float) Math.PI);
        assertEq(helper, "rad2deg(PI)", 180f,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Float.class), 1e-3f);
        helper.succeed();
    }



    public static void randomIntSeeded(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, RandomIntNode.class);
        setInputConstant(n, "min", 0); setInputConstant(n, "max", 100);
        var env = new EvaluationEnvironment(new VariableStore(), OptionalLong.of(7L));
        Integer v = new GraphExecutor(g, env).evaluate(n.getOutputsById().get("out"), Integer.class);
        if (v == null || v < 0 || v >= 100) {
            helper.fail("RandomInt out of [0,100): " + v);
            return;
        }
        helper.succeed();
    }
}
