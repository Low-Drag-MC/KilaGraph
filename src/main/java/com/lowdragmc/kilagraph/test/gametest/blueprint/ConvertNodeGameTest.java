package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.CastNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.NumberFormatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ParseBoolNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ParseNumberNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToFloatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToDoubleNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToIntNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToLongNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToStringNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry.BlockPosNodes;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.id.McIdNodes;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.TypeMismatchException;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.valueSource;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class ConvertNodeGameTest {
    private static final String TO_STRING = "convert_to_string";
    private static final String PARSE_NUMBER = "convert_parse_number";
    private static final String PARSE_BOOL = "convert_parse_bool";
    private static final String NUMBER_FORMAT = "convert_number_format";
    private static final String TO_INT = "convert_to_int";
    private static final String TO_FLOAT = "convert_to_float";

    private ConvertNodeGameTest() {}

    /** A wired Float source (AddNode out) to feed UNKNOWN inputs that have no embedded constant. */
    private static PortModel floatSource(
            BlueprintGraph g, float value) {
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", value);
        setInputConstant(add, "in2", 0f);
        return add.getOutputsById().get("out");
    }

    /** A wired Integer source (ListCombine(INT)+ListGet(INT)) to feed UNKNOWN inputs. */
    private static PortModel intSource(
            BlueprintGraph g, int value) {
        var combine = addNode(g, ListCombineNode.class);
        setOption(combine, "type", TypeHandles.INT.getIdentification());
        setOption(combine, "inputs", 1);
        setInputConstant(combine, "in1", value);
        var get = addNode(g, ListGetNode.class);
        setOption(get, "type", TypeHandles.INT.getIdentification());
        setInputConstant(get, "index", 0);
        wire(g, get.getInputsById().get("list"), combine.getOutputsById().get("out"));
        return get.getOutputsById().get("value");
    }

    /** Helper: feed a String value into an UNKNOWN port via a ListCombine(STRING)+ListGet(STRING). */
    private static PortModel stringSource(
            BlueprintGraph g, String value) {
        var combine = addNode(g, ListCombineNode.class);
        setOption(combine, "type", TypeHandles.STRING.getIdentification());
        setOption(combine, "inputs", 1);
        setInputConstant(combine, "in1", value);
        var get = addNode(g, ListGetNode.class);
        setOption(get, "type", TypeHandles.STRING.getIdentification());
        setInputConstant(get, "index", 0);
        wire(g, get.getInputsById().get("list"), combine.getOutputsById().get("out"));
        return get.getOutputsById().get("value");
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void toStringTest(GameTestHelper helper) {
        // Number to string via wire from a String source (since UNKNOWN port has no constant)
        var g = newGraph();
        var n = addNode(g, ToStringNode.class);
        wire(g, n.getInputsById().get("in"), stringSource(g, "hello"));
        String s = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class);
        assertEq(helper, "hello echoes", "hello", s);

        // Null input → empty string (no wire, no constant)
        var g2 = newGraph();
        var n2 = addNode(g2, ToStringNode.class);
        assertEq(helper, "null → empty", "",
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void parseNumber(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ParseNumberNode.class);
        wire(g, n.getInputsById().get("in"), stringSource(g, "12.5"));
        Float v = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
        assertEq(helper, "12.5", 12.5f, v, 1e-5f);

        // Garbage → 0
        var g2 = newGraph();
        var n2 = addNode(g2, ParseNumberNode.class);
        wire(g2, n2.getInputsById().get("in"), stringSource(g2, "garbage"));
        assertEq(helper, "garbage → 0", 0.0f,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void parseBool(GameTestHelper helper) {
        for (String s : new String[]{"true", "True", "YES", "1"}) {
            var g = newGraph();
            var n = addNode(g, ParseBoolNode.class);
            wire(g, n.getInputsById().get("in"), stringSource(g, s));
            assertEq(helper, "'" + s + "' → true", Boolean.TRUE,
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        }
        for (String s : new String[]{"false", "no", "0", "garbage"}) {
            var g = newGraph();
            var n = addNode(g, ParseBoolNode.class);
            wire(g, n.getInputsById().get("in"), stringSource(g, s));
            assertEq(helper, "'" + s + "' → false", Boolean.FALSE,
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void toInt(GameTestHelper helper) {
        for (var c : new Object[][]{{ToIntNode.Op.TRUNC, 3.9f, 3}, {ToIntNode.Op.TRUNC, -3.9f, -3},
                                     {ToIntNode.Op.FLOOR, 3.9f, 3}, {ToIntNode.Op.CEIL, 3.1f, 4},
                                     {ToIntNode.Op.ROUND, 3.6f, 4}}) {
            var g = newGraph();
            var n = addNode(g, ToIntNode.class);
            setOption(n, "op", c[0]);
            wire(g, n.getInputsById().get("in"), floatSource(g, (float) c[1]));
            Integer v = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class);
            assertEq(helper, c[0] + "(" + c[1] + ")", (int) (Integer) c[2], (int) v);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void toFloat(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ToFloatNode.class);
        wire(g, n.getInputsById().get("in"), intSource(g, 7));
        Float v = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Float.class);
        assertEq(helper, "int 7 → float 7.0", 7.0f, v, 1e-5f);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void numberFormat(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, NumberFormatNode.class);
        setOption(n, "pattern", "#.##");
        setInputConstant(n, "in", 3.14159f);
        String s = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class);
        assertEq(helper, "PI to 2dp", "3.14", s);

        var g2 = newGraph();
        var n2 = addNode(g2, NumberFormatNode.class);
        setOption(n2, "pattern", "0.0000");
        setInputConstant(n2, "in", 1.0f);
        assertEq(helper, "fixed 4dp", "1.0000",
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    /**
     * Cast: the promise that a loosely-typed value really is a given type.
     *
     * <p>Cast does not convert — it re-labels, so the output port carries the promised type and the wire
     * rules downstream accept it. What it actually does at runtime is the same liberal coercion
     * {@code getInput} applies, which is why a number promised as text arrives as text and a value that
     * cannot be represented arrives as nothing rather than as an exception.
     *
     * <p>It is <b>strict</b>: a promise that cannot be honoured throws rather than yielding nothing. That
     * is the right call for this node specifically — everything downstream trusts the output's declared
     * type, so a silent null would push the failure somewhere it cannot be diagnosed. A null <em>input</em>
     * is different and passes through, because "no value yet" is not a broken promise.
     *
     * <h2>The value has to be wired, not typed in</h2>
     * Cast's input is {@code UNKNOWN}, and LDLib2 deliberately gives an {@code UNKNOWN} port no embedded
     * constant — there is no editor for a value whose type is not yet decided. So the test feeds it from
     * a real producer, which is also the only way a graph can use it.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void castRelabelsAndCoerces(GameTestHelper helper) {
        // An int, produced by unpacking a block position.
        assertEq(helper, "int stays an int", 7, castInt(TypeHandles.INT, Integer.class).intValue());
        assertEq(helper, "int promised as float", 7f,
                castInt(TypeHandles.FLOAT, Float.class).floatValue(), 1e-6f);
        assertEq(helper, "int promised as text", "7", castInt(TypeHandles.STRING, String.class));

        assertEq(helper, "text cast to text is itself", "not_a_number",
                castText(TypeHandles.STRING, String.class));

        // A promise that cannot be honoured throws, and the message names both types.
        boolean threw = false;
        try {
            castText(TypeHandles.INT, Integer.class);
        } catch (TypeMismatchException expected) {
            threw = true;
        }
        assertTrue(helper, "non-numeric text promised as int throws", threw);
        helper.succeed();
    }

    /** Casts the int 7, sourced from a Block Pos Unpack so the value arrives over a wire. */
    private static <T> T castInt(TypeHandle target,
                                 Class<T> type) {
        var g = newGraph();
        var src = addNode(g, BlockPosNodes.Unpack.class);
        setInputConstant(src, "in", new BlockPos(7, 0, 0));
        return runCast(g, src.getOutputsById().get("x"), target, type);
    }

    /** Casts the text "not_a_number", sourced from an Identifier Unpack. */
    private static <T> T castText(TypeHandle target,
                                  Class<T> type) {
        var g = newGraph();
        var src = addNode(g, McIdNodes.Unpack.class);
        setInputConstant(src, "in",
                ResourceLocation.fromNamespaceAndPath("minecraft", "not_a_number"));
        return runCast(g, src.getOutputsById().get("path"), target, type);
    }

    private static <T> T runCast(BlueprintGraph g,
                                 PortModel source,
                                 TypeHandle target,
                                 Class<T> type) {
        var n = addNode(g, CastNode.class);
        setOption(n, "targetType", target.getIdentification());
        wire(g, n.getInputsById().get("in"), source);
        return new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), type);
    }

    // ---- precision on the way in --------------------------------------------------------------
    //
    // The convert group used to read every input as ctx.getFloat(...), so it was itself one of the
    // places precision went missing: ToInt(gameTime) answered a neighbouring number, and
    // ParseNumber("<a pasted id>") answered the nearest float. See NumericLane.

    /** A whole number above a float's 24-bit mantissa converts to itself, not to a neighbour. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void toIntKeepsLargeWholeNumbers(GameTestHelper helper) {
        // 20000001 is not representable as a float; the nearest is 20000002.
        int value = 20_000_001;
        var g = newGraph();
        var n = addNode(g, ToIntNode.class);
        wire(g, n.getInputsById().get("in"), valueSource(g.graphModel, "v", long.class, (long) value));
        assertEq(helper, "a large whole number converts to itself", value,
                (int) (Integer) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    /** Out of an int's range, ToInt stops at the limit rather than wrapping round to a wrong sign. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void toIntSaturatesRatherThanWrapping(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ToIntNode.class);
        wire(g, n.getInputsById().get("in"), valueSource(g.graphModel, "v", long.class, 5_000_000_000L));
        assertEq(helper, "5e9 saturates at Integer.MAX_VALUE", Integer.MAX_VALUE,
                (int) (Integer) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ToIntNode.class);
        wire(g2, n2.getInputsById().get("in"), valueSource(g2.graphModel, "v", long.class, -5_000_000_000L));
        assertEq(helper, "-5e9 saturates at Integer.MIN_VALUE", Integer.MIN_VALUE,
                (int) (Integer) new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    /** To Long: no ceiling, and the rounding option still applies to a fractional input. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void toLong(GameTestHelper helper) {
        long big = 3_090_200_953_712_304_400L;
        var g = newGraph();
        var n = addNode(g, ToLongNode.class);
        wire(g, n.getInputsById().get("in"), valueSource(g.graphModel, "v", long.class, big));
        assertEq(helper, "a huge whole number survives", big,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class));

        for (var c : new Object[][]{{ToIntNode.Op.TRUNC, -3.9f, -3L}, {ToIntNode.Op.FLOOR, -3.9f, -4L},
                                    {ToIntNode.Op.CEIL, 3.1f, 4L}, {ToIntNode.Op.ROUND, 3.6f, 4L}}) {
            var gf = newGraph();
            var nf = addNode(gf, ToLongNode.class);
            setOption(nf, "op", c[0]);
            wire(gf, nf.getInputsById().get("in"), floatSource(gf, (float) c[1]));
            assertEq(helper, c[0] + "(" + c[1] + ")", c[2],
                    new GraphExecutor(gf).evaluate(nf.getOutputsById().get("out"), Object.class));
        }
        helper.succeed();
    }

    /** To Double widens without the seven-digit ceiling a float would impose. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void toDouble(GameTestHelper helper) {
        long value = 9_007_199_254_740_991L;   // 2^53 - 1: exact in a double, nowhere near it in a float
        var g = newGraph();
        var n = addNode(g, ToDoubleNode.class);
        wire(g, n.getInputsById().get("in"), valueSource(g.graphModel, "v", long.class, value));
        assertEq(helper, "2^53-1 survives the widening", (double) value,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }

    /** Text of digits parses whole, keeping every digit; text with a point parses fractional. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void parseNumberKeepsWholeTextWhole(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ParseNumberNode.class);
        wire(g, n.getInputsById().get("in"), stringSource(g, "3090200953712304400"));
        assertEq(helper, "a pasted id keeps all its digits", 3_090_200_953_712_304_400L,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Object.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ParseNumberNode.class);
        wire(g2, n2.getInputsById().get("in"), stringSource(g2, "-12"));
        assertEq(helper, "a negative whole number", -12L,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Object.class));

        // A point means decimals were meant, and the value is kept at double precision.
        var g3 = newGraph();
        var n3 = addNode(g3, ParseNumberNode.class);
        wire(g3, n3.getInputsById().get("in"), stringSource(g3, "3.14159265358979"));
        assertEq(helper, "a decimal keeps its digits too", 3.14159265358979d,
                new GraphExecutor(g3).evaluate(n3.getOutputsById().get("out"), Object.class));

        // Too large for a whole number: falls through to the wider parse rather than failing.
        var g4 = newGraph();
        var n4 = addNode(g4, ParseNumberNode.class);
        wire(g4, n4.getInputsById().get("in"), stringSource(g4, "99999999999999999999"));
        assertEq(helper, "past a long, parsed as a decimal", 1.0e20d,
                new GraphExecutor(g4).evaluate(n4.getOutputsById().get("out"), Object.class));
        helper.succeed();
    }
}
