package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.CaseNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ConcatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ContainsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.EndsWithNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.FormatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.IndexOfNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.JoinNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.LengthNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ReplaceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.SplitNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.StartsWithNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.SubstringNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.TrimNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

@GameTestHolder(Kilagraph.MODID)
public final class StringNodeGameTest {
    private static final String CONCAT = "string_concat";
    private static final String LENGTH = "string_length";
    private static final String SUBSTRING = "string_substring";
    private static final String INDEX_OF = "string_index_of";
    private static final String REPLACE = "string_replace";
    private static final String SPLIT = "string_split";
    private static final String JOIN = "string_join";
    private static final String FORMAT = "string_format";
    private static final String CASE = "string_case";
    private static final String TRIM = "string_trim";
    private static final String CONTAINS = "string_contains";
    private static final String STARTS_WITH = "string_starts_with";
    private static final String ENDS_WITH = "string_ends_with";

    private StringNodeGameTest() {}

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void concat(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ConcatNode.class);
        setOption(n, "inputs", 3);
        setInputConstant(n, "in1", "Hello, ");
        setInputConstant(n, "in2", "World");
        setInputConstant(n, "in3", "!");
        assertEq(helper, "Hello, World!", "Hello, World!",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void length(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, LengthNode.class);
        setInputConstant(n, "in", "Hello");
        assertEq(helper, "len 'Hello'", 5,
                (int) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));

        var g2 = newGraph();
        var n2 = addNode(g2, LengthNode.class);
        setInputConstant(n2, "in", "");
        assertEq(helper, "len ''", 0,
                (int) new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void substring(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, SubstringNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "start", 0);
        setInputConstant(n, "end", 5);
        assertEq(helper, "first 5", "Hello",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));

        var g2 = newGraph();
        var n2 = addNode(g2, SubstringNode.class);
        setInputConstant(n2, "in", "abc");
        setInputConstant(n2, "start", 5);
        setInputConstant(n2, "end", 10);
        assertEq(helper, "out-of-bounds → empty", "",
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void indexOf(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, IndexOfNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "search", "World");
        assertEq(helper, "found 'World'", 5,
                (int) new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Integer.class));

        var g2 = newGraph();
        var n2 = addNode(g2, IndexOfNode.class);
        setInputConstant(n2, "in", "abc");
        setInputConstant(n2, "search", "z");
        assertEq(helper, "missing → -1", -1,
                (int) new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void replace(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ReplaceNode.class);
        setInputConstant(n, "in", "Hello World");
        setInputConstant(n, "search", "World");
        setInputConstant(n, "replacement", "KilaGraph");
        assertEq(helper, "replace World → KilaGraph", "Hello KilaGraph",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void split(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, SplitNode.class);
        setInputConstant(n, "in", "a,b,c");
        setInputConstant(n, "delimiter", ",");

        @SuppressWarnings("unchecked")
        List<Object> out = new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), List.class);
        assertEq(helper, "split size", 3, out.size());
        assertEq(helper, "[0]", "a", out.get(0));
        assertEq(helper, "[1]", "b", out.get(1));
        assertEq(helper, "[2]", "c", out.get(2));

        // empty delimiter → whole string in 1-element list
        var g2 = newGraph();
        var n2 = addNode(g2, SplitNode.class);
        setInputConstant(n2, "in", "abc");
        setInputConstant(n2, "delimiter", "");

        @SuppressWarnings("unchecked")
        List<Object> out2 = new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), List.class);
        assertEq(helper, "empty-delim size", 1, out2.size());
        assertEq(helper, "single elem", "abc", out2.get(0));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void join(GameTestHelper helper) {
        var g = newGraph();
        var combine = addNode(g, ListCombineNode.class);
        setOption(combine, "type", TypeHandles.STRING.getIdentification());
        setOption(combine, "inputs", 3);
        setInputConstant(combine, "in1", "a");
        setInputConstant(combine, "in2", "b");
        setInputConstant(combine, "in3", "c");
        var n = addNode(g, JoinNode.class);
        setInputConstant(n, "delimiter", "-");
        wire(g, n.getInputsById().get("in"), combine.getOutputsById().get("out"));
        assertEq(helper, "join a-b-c", "a-b-c",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void format(GameTestHelper helper) {
        // Format requires UNKNOWN-typed args. Use ListGet trick? Easier: directly use a numeric AddNode.
        var g = newGraph();
        var n = addNode(g, FormatNode.class);
        setOption(n, "pattern", "%.2f");
        setOption(n, "inputs", 1);
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 3.14159f);
        setInputConstant(add, "in2", 0f);
        wire(g, n.getInputsById().get("arg1"), add.getOutputsById().get("out"));
        assertEq(helper, "%.2f", "3.14",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));

        // Malformed pattern: missing arg → return pattern unchanged
        var g2 = newGraph();
        var n2 = addNode(g2, FormatNode.class);
        setOption(n2, "pattern", "%d");
        setOption(n2, "inputs", 0);
        assertEq(helper, "malformed", "%d",
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void caseOp(GameTestHelper helper) {
        for (var c : new Object[][]{{CaseNode.Op.LOWER, "Hello World", "hello world"},
                                     {CaseNode.Op.UPPER, "Hello World", "HELLO WORLD"},
                                     {CaseNode.Op.TITLE, "hello world", "Hello World"}}) {
            var g = newGraph();
            var n = addNode(g, CaseNode.class);
            setOption(n, "op", c[0]);
            setInputConstant(n, "in", (String) c[1]);
            assertEq(helper, c[0] + " '" + c[1] + "'", c[2],
                    new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void trim(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, TrimNode.class);
        setInputConstant(n, "in", "  hello   ");
        assertEq(helper, "trim", "hello",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void contains(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, ContainsNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "needle", "lloW");
        assertEq(helper, "contains 'lloW'", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, ContainsNode.class);
        setInputConstant(n2, "in", "abc");
        setInputConstant(n2, "needle", "z");
        assertEq(helper, "no contains", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void startsWith(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, StartsWithNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "needle", "Hello");
        assertEq(helper, "starts Hello", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));

        var g2 = newGraph();
        var n2 = addNode(g2, StartsWithNode.class);
        setInputConstant(n2, "in", "HelloWorld");
        setInputConstant(n2, "needle", "World");
        assertEq(helper, "no starts World", Boolean.FALSE,
                new GraphExecutor(g2).evaluate(n2.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    @GameTest(template = "empty")

    @PrefixGameTestTemplate(false)

    public static void endsWith(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, EndsWithNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "needle", "World");
        assertEq(helper, "ends World", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }
}
