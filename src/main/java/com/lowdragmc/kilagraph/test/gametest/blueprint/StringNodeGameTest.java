package com.lowdragmc.kilagraph.test.gametest.blueprint;


import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListCombineNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.CaseNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ConcatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ContainsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.EndsWithNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.FindNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.FormatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.IndexOfNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.JoinNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.LengthNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.MatchesNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ReplaceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.ReplaceRegexNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.SplitNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.StartsWithNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.SubstringNode;
import com.lowdragmc.kilagraph.blueprint.nodes.string.TrimNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import java.util.List;
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
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

public final class StringNodeGameTest {
    private static final String REGEX_MATCHES_WHOLE_STRING = "string_node_regex_matches_whole_string";
    private static final String REGEX_FINDS_AND_CAPTURES = "string_node_regex_finds_and_captures";
    private static final String REGEX_REPLACES_WITH_GROUPS = "string_node_regex_replaces_with_groups";
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




    public static void registerFunctions() {
        KGGameTests.registerFunction(CONCAT, StringNodeGameTest::concat);
        KGGameTests.registerFunction(LENGTH, StringNodeGameTest::length);
        KGGameTests.registerFunction(SUBSTRING, StringNodeGameTest::substring);
        KGGameTests.registerFunction(INDEX_OF, StringNodeGameTest::indexOf);
        KGGameTests.registerFunction(REPLACE, StringNodeGameTest::replace);
        KGGameTests.registerFunction(SPLIT, StringNodeGameTest::split);
        KGGameTests.registerFunction(JOIN, StringNodeGameTest::join);
        KGGameTests.registerFunction(FORMAT, StringNodeGameTest::format);
        KGGameTests.registerFunction(CASE, StringNodeGameTest::caseOp);
        KGGameTests.registerFunction(TRIM, StringNodeGameTest::trim);
        KGGameTests.registerFunction(CONTAINS, StringNodeGameTest::contains);
        KGGameTests.registerFunction(STARTS_WITH, StringNodeGameTest::startsWith);
        KGGameTests.registerFunction(ENDS_WITH, StringNodeGameTest::endsWith);
        KGGameTests.registerFunction(REGEX_MATCHES_WHOLE_STRING, StringNodeGameTest::regexMatchesWholeString);
        KGGameTests.registerFunction(REGEX_FINDS_AND_CAPTURES, StringNodeGameTest::regexFindsAndCaptures);
        KGGameTests.registerFunction(REGEX_REPLACES_WITH_GROUPS, StringNodeGameTest::regexReplacesWithGroups);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                CONCAT, LENGTH, SUBSTRING,
                INDEX_OF, REPLACE, SPLIT,
                JOIN, FORMAT, CASE,
                TRIM, CONTAINS, STARTS_WITH,
                ENDS_WITH, REGEX_MATCHES_WHOLE_STRING, REGEX_FINDS_AND_CAPTURES,
                REGEX_REPLACES_WITH_GROUPS
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

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



    public static void trim(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, TrimNode.class);
        setInputConstant(n, "in", "  hello   ");
        assertEq(helper, "trim", "hello",
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), String.class));
        helper.succeed();
    }



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



    public static void endsWith(GameTestHelper helper) {
        var g = newGraph();
        var n = addNode(g, EndsWithNode.class);
        setInputConstant(n, "in", "HelloWorld");
        setInputConstant(n, "needle", "World");
        assertEq(helper, "ends World", Boolean.TRUE,
                new GraphExecutor(g).evaluate(n.getOutputsById().get("out"), Boolean.class));
        helper.succeed();
    }

    // ---- regex ---------------------------------------------------------------------------------

    /**
     * {@code string_matches} is whole-string, which is the thing about it people get wrong.
     *
     * <p>The {@code x42} pair is the whole point of the test: if this node ever became "contains a match"
     * both halves would still look reasonable in isolation, and only the pair pins the rule.</p>
     */
    public static void regexMatchesWholeString(GameTestHelper helper) {
        var digits = node(MatchesNode.class, "in", "42", "pattern", "\\d+");
        assertEq(helper, "42 is all digits", Boolean.TRUE, eval(digits, "out", Boolean.class));
        assertEq(helper, "and the pattern was valid", Boolean.TRUE, eval(digits, "ok", Boolean.class));

        var partial = node(MatchesNode.class, "in", "x42", "pattern", "\\d+");
        assertEq(helper, "x42 is not ALL digits", Boolean.FALSE, eval(partial, "out", Boolean.class));

        var anchored = node(MatchesNode.class, "in", "x42", "pattern", ".*\\d+");
        assertEq(helper, "but it does end in digits", Boolean.TRUE, eval(anchored, "out", Boolean.class));

        // A pattern is user input, so a syntax error is a false answer with ok = false, not a crash.
        var broken = node(MatchesNode.class, "in", "42", "pattern", "[");
        assertEq(helper, "a broken pattern does not match", Boolean.FALSE, eval(broken, "out", Boolean.class));
        assertEq(helper, "and says the pattern was bad", Boolean.FALSE, eval(broken, "ok", Boolean.class));
        helper.succeed();
    }

    /**
     * {@code string_find} against the text a command actually produces.
     *
     * <p>The input is the shape of {@code /list} output on purpose — turning that line back into two
     * numbers is the reason these nodes exist, and a test on {@code "abc"} would not show it.</p>
     */
    public static void regexFindsAndCaptures(GameTestHelper helper) {
        String line = "There are 3 of a max of 20 players online";
        var hit = node(FindNode.class, "in", line, "pattern", "(\\d+) of a max of (\\d+)");
        assertEq(helper, "found the phrase", Boolean.TRUE, eval(hit, "found", Boolean.class));
        assertEq(helper, "matched text", "3 of a max of 20", eval(hit, "match", String.class));
        assertEq(helper, "captured both numbers", List.of("3", "20"), eval(hit, "groups", List.class));

        int start = eval(hit, "start", Integer.class);
        int end = eval(hit, "end", Integer.class);
        assertEq(helper, "match starts where the first number is", 10, start);
        assertEq(helper, "and the indices really cut out the match", "3 of a max of 20",
                line.substring(start, end));

        // No match: the indices are -1 rather than 0, because 0 is a real position.
        var miss = node(FindNode.class, "in", line, "pattern", "zzz(\\d+)");
        assertEq(helper, "no match", Boolean.FALSE, eval(miss, "found", Boolean.class));
        assertEq(helper, "empty match text", "", eval(miss, "match", String.class));
        assertEq(helper, "no groups", 0, eval(miss, "groups", List.class).size());
        assertEq(helper, "start is -1, not 0", -1, eval(miss, "start", Integer.class).intValue());
        assertEq(helper, "end is -1, not 0", -1, eval(miss, "end", Integer.class).intValue());
        assertEq(helper, "but the pattern itself was fine", Boolean.TRUE, eval(miss, "ok", Boolean.class));

        // A group that did not take part comes back as an empty string, not as a hole in the list.
        var optional = node(FindNode.class, "in", "b", "pattern", "(a)?(b)");
        assertEq(helper, "optional group absent", List.of("", "b"), eval(optional, "groups", List.class));

        var broken = node(FindNode.class, "in", line, "pattern", "(");
        assertEq(helper, "a broken pattern finds nothing", Boolean.FALSE, eval(broken, "found", Boolean.class));
        assertEq(helper, "and reports itself", Boolean.FALSE, eval(broken, "ok", Boolean.class));
        helper.succeed();
    }

    /** {@code string_replace_regex}, including the two ways the replacement text itself can be wrong. */
    public static void regexReplacesWithGroups(GameTestHelper helper) {
        var swap = node(ReplaceRegexNode.class, "in", "x=1, y=2",
                "pattern", "(\\w+)=(\\w+)", "replacement", "$2=$1");
        assertEq(helper, "groups were substituted", "1=x, 2=y", eval(swap, "out", String.class));
        assertEq(helper, "twice", 2, eval(swap, "count", Integer.class).intValue());
        assertEq(helper, "and it worked", Boolean.TRUE, eval(swap, "ok", Boolean.class));

        // Matching nothing is a success that changed nothing — count is what tells them apart.
        var none = node(ReplaceRegexNode.class, "in", "x=1", "pattern", "zzz", "replacement", "q");
        assertEq(helper, "unchanged", "x=1", eval(none, "out", String.class));
        assertEq(helper, "nothing replaced", 0, eval(none, "count", Integer.class).intValue());
        assertEq(helper, "yet the pattern was valid", Boolean.TRUE, eval(none, "ok", Boolean.class));

        var badPattern = node(ReplaceRegexNode.class, "in", "x=1", "pattern", "(", "replacement", "q");
        assertEq(helper, "a broken pattern leaves the text alone", "x=1", eval(badPattern, "out", String.class));
        assertEq(helper, "and reports failure", Boolean.FALSE, eval(badPattern, "ok", Boolean.class));

        // $9 names a group the pattern does not have: a mistake in the replacement, reported the same way.
        var badGroup = node(ReplaceRegexNode.class, "in", "x=1",
                "pattern", "(\\w+)=(\\w+)", "replacement", "$9");
        assertEq(helper, "a missing group leaves the text alone", "x=1", eval(badGroup, "out", String.class));
        assertEq(helper, "and reports failure", Boolean.FALSE, eval(badGroup, "ok", Boolean.class));
        assertEq(helper, "having replaced nothing", 0, eval(badGroup, "count", Integer.class).intValue());

        // A trailing backslash is the other malformed-replacement case, and it throws a different type.
        var badEscape = node(ReplaceRegexNode.class, "in", "x=1", "pattern", "x", "replacement", "\\");
        assertEq(helper, "a dangling escape is refused", "x=1", eval(badEscape, "out", String.class));
        assertEq(helper, "and reports failure", Boolean.FALSE, eval(badEscape, "ok", Boolean.class));
        helper.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** One node in its own graph, carried with the graph so it can be evaluated. */
    private record Probe(BlueprintGraph graph, NodeModel model) {
    }

    /** A node in its own graph with the given input constants applied, as {@code id, value} pairs. */
    private static Probe node(Class<? extends Node> cls, Object... inputs) {
        var g = newGraph();
        NodeModel n = addNode(g, cls);
        for (int i = 0; i + 1 < inputs.length; i += 2) {
            setInputConstant(n, (String) inputs[i], inputs[i + 1]);
        }
        return new Probe(g, n);
    }

    private static <T> T eval(Probe probe, String output, Class<T> type) {
        return new GraphExecutor(probe.graph())
                .evaluate(probe.model().getOutputsById().get(output), type);
    }
}
