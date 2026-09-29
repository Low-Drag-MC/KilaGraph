package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Regression tests for exec-flow *semantics* (ordering + nesting), as opposed to per-node behaviour.
 * These pin down two correctness properties that the per-node tests don't exercise:
 *
 * <ol>
 *   <li><b>Sequence runs each output's chain to completion before the next</b> — not breadth-first
 *       interleaving.</li>
 *   <li><b>Nested loops: the inner loop body can read the outer loop's index</b> — the inner loop's
 *       cache invalidation must not destroy the outer loop's live index.</li>
 *   <li><b>A pure node is worked out again for each exec node that reads it</b>, and once for everything
 *       one exec node reads — Unreal's rule.</li>
 * </ol>
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecSemanticsGameTest {
    private static final String SEQUENCE_TO_COMPLETION = "exec_sequence_runs_to_completion";
    private static final String NESTED_FOR_OUTER_INDEX = "exec_nested_for_outer_index";

    private ExecSemanticsGameTest() {}

    /**
     * <pre>
     * Entry → SetVar(a ← shared) → SetVar(b ← shared)        shared = 2 + 3
     * </pre>
     * Two exec nodes read one pure node: it is worked out for each — Unreal inlines a pure node's code in
     * front of every impure node that needs it ({@code KismetCompiler.cpp:2816-2900}).
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPureNodeIsWorkedOutAgainForEachExecNodeThatReadsIt(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.add("shared", AddNode.class).constant("shared.in1", 2f).constant("shared.in2", 3f);
        b.add("setA", SetVarNode.class).option("setA", "varName", "a").wire("setA.value", "shared");
        b.add("setB", SetVarNode.class).option("setB", "varName", "b").wire("setB.value", "shared");
        b.then("entry", "setA", "setB");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "worked out once for each reader", 2, trace.evalCount(b.node("shared").getUid()));
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → SetVar(a ← both)        both = shared + shared
     * </pre>
     * One exec node reading a pure node twice has it worked out once for both reads — Unreal gathers an
     * impure node's pure inputs into a set before inlining them.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPureNodeReadTwiceByOneExecNodeIsWorkedOutOnce(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.add("shared", AddNode.class).constant("shared.in1", 2f).constant("shared.in2", 3f);
        b.add("both", AddNode.class).wire("both.in1", "shared").wire("both.in2", "shared");
        b.add("setA", SetVarNode.class).option("setA", "varName", "a").wire("setA.value", "both");
        b.then("entry", "setA");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "worked out once", 1, trace.evalCount(b.node("shared").getUid()));
        Object a = exec.getEnvironment().variables().get("a");
        assertEq(helper, "and read twice", 10f, a instanceof Number n ? n.floatValue() : Float.NaN, 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → SetVar(x ← 1) → SetVar(seen1 ← x) → SetVar(x ← 2) → SetVar(seen2 ← x)
     * </pre>
     * One get-node for {@code x}, read on both sides of a write in one run: the second read sees the write,
     * because a read is worked out again for the exec node that asks.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aVariableReadAfterAWriteInTheSameRunSeesIt(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("x", int.class, 0, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("one", AddNode.class).constant("one.in1", 1f).constant("one.in2", 0f);
        b.add("two", AddNode.class).constant("two.in1", 2f).constant("two.in2", 0f);
        b.add("setX1", SetVarNode.class).option("setX1", "varName", "x").wire("setX1.value", "one");
        b.add("seen1", SetVarNode.class).option("seen1", "varName", "seen1").wire("seen1.value", "x");
        b.add("setX2", SetVarNode.class).option("setX2", "varName", "x").wire("setX2.value", "two");
        b.add("seen2", SetVarNode.class).option("seen2", "varName", "seen2").wire("seen2.value", "x");
        b.then("entry", "setX1", "seen1", "setX2", "seen2");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        var vars = exec.getEnvironment().variables();
        assertEq(helper, "the first read sees the first write", 1f,
                vars.get("seen1") instanceof Number n ? n.floatValue() : Float.NaN, 1e-5f);
        assertEq(helper, "the second read, after the second write, sees that", 2f,
                vars.get("seen2") instanceof Number n ? n.floatValue() : Float.NaN, 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → Sequence(2)
     *           out1 → SetVar(v = 1) → SetVar(marker = v)   (two-step chain)
     *           out2 → SetVar(v = 2)
     * </pre>
     * Run-to-completion (correct): out1's whole chain runs first → marker captures v=1, then out2 sets v=2.
     * Breadth-first (buggy): setV1, setV2 run before setMarker → marker captures v=2.
     * Asserts marker == 1.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sequenceToCompletion(GameTestHelper helper) {
        var g = newGraph();
        // 'v' is an INPUT variable → READ modifier → its variable node exposes an OUTPUT (get) port.
        var vVar = (VariableDeclarationModelBase) g.graphModel.createVariable("v", int.class, 0, VariableKind.INPUT);
        var vGet = g.graphModel.createVariableNode(vVar, new Vector2f(0, 0), null, null);

        var entry = addNode(g, EntryNode.class);
        var seq = addNode(g, SequenceNode.class);
        setOption(seq, "outputs", 2);

        // out1 chain: setV1 (v=1) → setMarker (marker = v)
        var setV1 = addNode(g, SetVarNode.class);
        setOption(setV1, "varName", "v");
        var one = addNode(g, AddNode.class);
        setInputConstant(one, "in1", 1.0f);
        setInputConstant(one, "in2", 0.0f);
        wire(g, setV1.getInputsById().get("value"), one.getOutputsById().get("out"));

        var setMarker = addNode(g, SetVarNode.class);
        setOption(setMarker, "varName", "marker");
        wire(g, setMarker.getInputsById().get("value"), vGet.getOutputPort());

        // out2 chain: setV2 (v=2)
        var setV2 = addNode(g, SetVarNode.class);
        setOption(setV2, "varName", "v");
        var two = addNode(g, AddNode.class);
        setInputConstant(two, "in1", 2.0f);
        setInputConstant(two, "in2", 0.0f);
        wire(g, setV2.getInputsById().get("value"), two.getOutputsById().get("out"));

        // Exec wiring
        wire(g, seq.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, setV1.getInputsById().get("trigger"), seq.getOutputsById().get("out1"));
        wire(g, setMarker.getInputsById().get("trigger"), setV1.getOutputsById().get("next"));
        wire(g, setV2.getInputsById().get("trigger"), seq.getOutputsById().get("out2"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object marker = exec.getEnvironment().variables().get("marker");
        if (!(marker instanceof Number n) || Math.abs(n.floatValue() - 1.0f) > 1e-5f) {
            helper.fail("Sequence should run out1's chain to completion (marker=1), got marker=" + marker);
            return;
        }
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → ForOuter(count=3)
     *           body → ForInner(count=1)
     *                    body → SetVar(seen = outerIndex)
     * </pre>
     * Each outer iteration runs the inner loop once, whose body reads the OUTER index. The last write
     * must be outerIndex == 2. If the inner loop's clearCache destroys the outer index, seen reads
     * null/0 → test fails.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void nestedForOuterIndex(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var outer = addNode(g, ForNode.class);
        setInputConstant(outer, "count", 3);
        var inner = addNode(g, ForNode.class);
        setInputConstant(inner, "count", 1);

        var setSeen = addNode(g, SetVarNode.class);
        setOption(setSeen, "varName", "seen");
        // seen = outerIndex (wire outer.index → setSeen.value)
        wire(g, setSeen.getInputsById().get("value"), outer.getOutputsById().get("index"));

        wire(g, outer.getInputsById().get("in"), entry.getOutputsById().get("next"));
        wire(g, inner.getInputsById().get("in"), outer.getOutputsById().get("body"));
        wire(g, setSeen.getInputsById().get("trigger"), inner.getOutputsById().get("body"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);
        Object seen = exec.getEnvironment().variables().get("seen");
        if (!(seen instanceof Number n) || Math.abs(n.floatValue() - 2.0f) > 1e-5f) {
            helper.fail("inner body should read outer index (last seen=2), got seen=" + seen);
            return;
        }
        helper.succeed();
    }
}
