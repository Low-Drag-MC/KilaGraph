package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.optional.DefaultNode;
import com.lowdragmc.kilagraph.blueprint.nodes.optional.IsNullNode;
import com.lowdragmc.kilagraph.blueprint.nodes.optional.NotNullNode;
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
public final class OptionalNodeGameTest {
    private static final String IS_NULL = "optional_is_null";
    private static final String NOT_NULL = "optional_not_null";
    private static final String DEFAULT_KEEPS = "optional_default_keeps_non_null";
    private static final String DEFAULT_FALLBACK = "optional_default_uses_fallback";

    private OptionalNodeGameTest() {}

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void isNull(GameTestHelper helper) {
        // null case: unconnected UNKNOWN port has no constant → null
        var g1 = newGraph();
        var n1 = addNode(g1, IsNullNode.class);
        var e1 = new GraphExecutor(g1);
        assertEq(helper, "null → true", Boolean.TRUE, e1.evaluate(n1.getOutputsById().get("out"), Boolean.class));

        // non-null: wire AddNode.out (Float 0f) → IsNullNode.in
        var g2 = newGraph();
        var add = addNode(g2, AddNode.class);
        var n2 = addNode(g2, IsNullNode.class);
        wire(g2, n2.getInputsById().get("in"), add.getOutputsById().get("out"));
        var e2 = new GraphExecutor(g2);
        assertEq(helper, "non-null → false", Boolean.FALSE, e2.evaluate(n2.getOutputsById().get("out"), Boolean.class));

        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void notNull(GameTestHelper helper) {
        var g1 = newGraph();
        var n1 = addNode(g1, NotNullNode.class);
        var e1 = new GraphExecutor(g1);
        assertEq(helper, "null → false", Boolean.FALSE, e1.evaluate(n1.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var add = addNode(g2, AddNode.class);
        var n2 = addNode(g2, NotNullNode.class);
        wire(g2, n2.getInputsById().get("in"), add.getOutputsById().get("out"));
        var e2 = new GraphExecutor(g2);
        assertEq(helper, "non-null → true", Boolean.TRUE, e2.evaluate(n2.getOutputsById().get("out"), Boolean.class));

        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void defaultKeeps(GameTestHelper helper) {
        // type=Float — DefaultNode's ports become Float-typed which HAVE constants.
        var g = newGraph();
        var n = addNode(g, DefaultNode.class);
        setOption(n, "type", TypeHandles.FLOAT.getIdentification());
        setInputConstant(n, "in", 5.0f);
        setInputConstant(n, "defaultValue", 99.0f);
        var exec = new GraphExecutor(g);
        assertEq(helper, "non-null kept", 5.0f,
                exec.evaluate(n.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void defaultFallback(GameTestHelper helper) {
        // type=UNKNOWN: no embedded constants → "in" is null → fallback to defaultValue (wired).
        var g = newGraph();
        var n = addNode(g, DefaultNode.class);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 42.0f);
        wire(g, n.getInputsById().get("defaultValue"), add.getOutputsById().get("out"));
        var exec = new GraphExecutor(g);
        Object out = exec.evaluate(n.getOutputsById().get("out"), Object.class);
        if (!(out instanceof Number num) || Math.abs(num.floatValue() - 42.0f) > 1e-5f) {
            helper.fail("expected fallback to 42f, got " + out);
            return;
        }
        helper.succeed();
    }
}
