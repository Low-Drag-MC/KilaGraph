package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapContainsKeyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapContainsValueNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapIsEmptyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapKeysNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapMergeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapPutNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapRemoveNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapSizeNode;
import com.lowdragmc.kilagraph.blueprint.nodes.map.MapValuesNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.Map;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class MapNodeGameTest {
    private static final String CREATE = "map_create";
    private static final String GET = "map_get";
    private static final String GET_DEFAULT = "map_get_default";
    private static final String PUT = "map_put";
    private static final String REMOVE = "map_remove";
    private static final String CONTAINS_KEY = "map_contains_key";
    private static final String CONTAINS_VALUE = "map_contains_value";
    private static final String KEYS = "map_keys";
    private static final String VALUES = "map_values";
    private static final String SIZE = "map_size";
    private static final String IS_EMPTY = "map_is_empty";
    private static final String MERGE = "map_merge";
    private static final String IMMUTABILITY = "map_immutability";

    private MapNodeGameTest() {}

    /** Build a String→String map with the given alternating key/value pairs. */
    private static NodeModel stringStringMap(BlueprintGraph g, String... kvPairs) {
        NodeModel n = addNode(g, MapCreateNode.class);
        setOption(n, "keyType", TypeHandles.STRING.getIdentification());
        setOption(n, "valueType", TypeHandles.STRING.getIdentification());
        int pairs = kvPairs.length / 2;
        setOption(n, "inputs", Math.max(1, pairs));
        for (int i = 0; i < pairs; i++) {
            setInputConstant(n, "key" + (i + 1), kvPairs[i * 2]);
            setInputConstant(n, "value" + (i + 1), kvPairs[i * 2 + 1]);
        }
        return n;
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void create(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2");

        @SuppressWarnings("unchecked")
        Map<Object, Object> map = new GraphExecutor(g).evaluate(m.getOutputsById().get("out"), Map.class);
        assertEq(helper, "size", 2, map.size());
        assertEq(helper, "a", "1", map.get("a"));
        assertEq(helper, "b", "2", map.get("b"));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void get(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2");
        var get = addNode(g, MapGetNode.class);
        setOption(get, "keyType", TypeHandles.STRING.getIdentification());
        setOption(get, "valueType", TypeHandles.STRING.getIdentification());
        setInputConstant(get, "key", "a");
        setInputConstant(get, "defaultValue", "X");
        wire(g, get.getInputsById().get("map"), m.getOutputsById().get("out"));
        assertEq(helper, "get a", "1",
                new GraphExecutor(g).evaluate(get.getOutputsById().get("value"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void getDefault(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1");
        var get = addNode(g, MapGetNode.class);
        setOption(get, "keyType", TypeHandles.STRING.getIdentification());
        setOption(get, "valueType", TypeHandles.STRING.getIdentification());
        setInputConstant(get, "key", "missing");
        setInputConstant(get, "defaultValue", "fallback");
        wire(g, get.getInputsById().get("map"), m.getOutputsById().get("out"));
        assertEq(helper, "missing → default", "fallback",
                new GraphExecutor(g).evaluate(get.getOutputsById().get("value"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void put(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1");
        var put = addNode(g, MapPutNode.class);
        setOption(put, "keyType", TypeHandles.STRING.getIdentification());
        setOption(put, "valueType", TypeHandles.STRING.getIdentification());
        setInputConstant(put, "key", "b");
        setInputConstant(put, "value", "2");
        wire(g, put.getInputsById().get("map"), m.getOutputsById().get("out"));

        @SuppressWarnings("unchecked")
        Map<Object, Object> result = new GraphExecutor(g).evaluate(put.getOutputsById().get("out"), Map.class);
        assertEq(helper, "size", 2, result.size());
        assertEq(helper, "b", "2", result.get("b"));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void remove(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2");
        var rm = addNode(g, MapRemoveNode.class);
        setOption(rm, "keyType", TypeHandles.STRING.getIdentification());
        setInputConstant(rm, "key", "a");
        wire(g, rm.getInputsById().get("map"), m.getOutputsById().get("out"));

        @SuppressWarnings("unchecked")
        Map<Object, Object> result = new GraphExecutor(g).evaluate(rm.getOutputsById().get("out"), Map.class);
        assertEq(helper, "size", 1, result.size());
        assertEq(helper, "b kept", "2", result.get("b"));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void containsKey(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1");
        var ck = addNode(g, MapContainsKeyNode.class);
        setOption(ck, "keyType", TypeHandles.STRING.getIdentification());
        setInputConstant(ck, "key", "a");
        wire(g, ck.getInputsById().get("map"), m.getOutputsById().get("out"));
        assertEq(helper, "has a", Boolean.TRUE,
                new GraphExecutor(g).evaluate(ck.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var m2 = stringStringMap(g2, "a", "1");
        var ck2 = addNode(g2, MapContainsKeyNode.class);
        setOption(ck2, "keyType", TypeHandles.STRING.getIdentification());
        setInputConstant(ck2, "key", "z");
        wire(g2, ck2.getInputsById().get("map"), m2.getOutputsById().get("out"));
        assertEq(helper, "no z", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(ck2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void containsValue(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1");
        var cv = addNode(g, MapContainsValueNode.class);
        wire(g, cv.getInputsById().get("map"), m.getOutputsById().get("out"));
        // Wire value via list-get scalar
        PortModel scalar1 = scalarString(g, "1");
        wire(g, cv.getInputsById().get("value"), scalar1);
        assertEq(helper, "has 1", Boolean.TRUE,
                new GraphExecutor(g).evaluate(cv.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    private static PortModel scalarString(BlueprintGraph g, String value) {
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

    public static void keys(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2", "c", "3");
        var k = addNode(g, MapKeysNode.class);
        setOption(k, "keyType", TypeHandles.STRING.getIdentification());
        wire(g, k.getInputsById().get("map"), m.getOutputsById().get("out"));

        @SuppressWarnings("unchecked")
        List<Object> keys = new GraphExecutor(g).evaluate(k.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 3, keys.size());
        // LinkedHashMap preserves insertion order
        assertEq(helper, "[0]", "a", keys.get(0));
        assertEq(helper, "[1]", "b", keys.get(1));
        assertEq(helper, "[2]", "c", keys.get(2));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void values(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2");
        var v = addNode(g, MapValuesNode.class);
        wire(g, v.getInputsById().get("map"), m.getOutputsById().get("out"));

        @SuppressWarnings("unchecked")
        List<Object> values = new GraphExecutor(g).evaluate(v.getOutputsById().get("out"), List.class);
        assertEq(helper, "size", 2, values.size());
        assertEq(helper, "[0]", "1", values.get(0));
        assertEq(helper, "[1]", "2", values.get(1));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void size(GameTestHelper helper) {
        var g = newGraph();
        var m = stringStringMap(g, "a", "1", "b", "2", "c", "3");
        var s = addNode(g, MapSizeNode.class);
        wire(g, s.getInputsById().get("map"), m.getOutputsById().get("out"));
        assertEq(helper, "size", 3,
                (int) new GraphExecutor(g).evaluate(s.getOutputsById().get("size"), Integer.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void isEmpty(GameTestHelper helper) {
        var g = newGraph();
        var e = addNode(g, MapIsEmptyNode.class);
        assertEq(helper, "default empty", Boolean.TRUE,
                new GraphExecutor(g).evaluate(e.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var m2 = stringStringMap(g2, "a", "1");
        var e2 = addNode(g2, MapIsEmptyNode.class);
        wire(g2, e2.getInputsById().get("map"), m2.getOutputsById().get("out"));
        assertEq(helper, "non-empty", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(e2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void merge(GameTestHelper helper) {
        var g = newGraph();
        var mA = stringStringMap(g, "a", "1", "b", "2");
        var mB = stringStringMap(g, "b", "Bnew", "c", "3");
        var merge = addNode(g, MapMergeNode.class);
        wire(g, merge.getInputsById().get("a"), mA.getOutputsById().get("out"));
        wire(g, merge.getInputsById().get("b"), mB.getOutputsById().get("out"));

        @SuppressWarnings("unchecked")
        Map<Object, Object> result = new GraphExecutor(g).evaluate(merge.getOutputsById().get("out"), Map.class);
        assertEq(helper, "size", 3, result.size());
        assertEq(helper, "a", "1", result.get("a"));
        assertEq(helper, "b (b wins)", "Bnew", result.get("b"));
        assertEq(helper, "c", "3", result.get("c"));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void immutability(GameTestHelper helper) {
        // Put must not mutate the source — re-evaluate source after put.
        var g = newGraph();
        var src = stringStringMap(g, "a", "1");
        var put = addNode(g, MapPutNode.class);
        setOption(put, "keyType", TypeHandles.STRING.getIdentification());
        setOption(put, "valueType", TypeHandles.STRING.getIdentification());
        setInputConstant(put, "key", "b");
        setInputConstant(put, "value", "2");
        wire(g, put.getInputsById().get("map"), src.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);

        @SuppressWarnings("unchecked")
        Map<Object, Object> s1 = exec.evaluate(src.getOutputsById().get("out"), Map.class);

        @SuppressWarnings("unchecked")
        Map<Object, Object> pAfter = exec.evaluate(put.getOutputsById().get("out"), Map.class);

        @SuppressWarnings("unchecked")
        Map<Object, Object> s2 = exec.evaluate(src.getOutputsById().get("out"), Map.class);

        assertEq(helper, "src pre", 1, s1.size());
        assertEq(helper, "put size", 2, pAfter.size());
        assertEq(helper, "src post", 1, s2.size());
        helper.succeed();
    }
}
