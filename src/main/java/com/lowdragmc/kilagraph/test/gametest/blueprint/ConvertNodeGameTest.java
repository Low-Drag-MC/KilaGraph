package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.NumberFormatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ParseBoolNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ParseNumberNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToFloatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToIntNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToStringNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import net.minecraft.gametest.framework.GameTestHelper;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
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
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel floatSource(
            com.lowdragmc.kilagraph.blueprint.BlueprintGraph g, float value) {
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", value);
        setInputConstant(add, "in2", 0f);
        return add.getOutputsById().get("out");
    }

    /** A wired Integer source (ListCombine(INT)+ListGet(INT)) to feed UNKNOWN inputs. */
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel intSource(
            com.lowdragmc.kilagraph.blueprint.BlueprintGraph g, int value) {
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
    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel stringSource(
            com.lowdragmc.kilagraph.blueprint.BlueprintGraph g, String value) {
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
}
