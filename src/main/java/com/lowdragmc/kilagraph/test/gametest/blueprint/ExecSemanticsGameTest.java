package com.lowdragmc.kilagraph.test.gametest.blueprint;


import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTest;
import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForEachNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SequenceNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.WhileNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListRemoveAtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

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
 *   <li><b>Nested loops: the inner loop body can read the outer loop's index</b> — the inner loop
 *       must not destroy the outer loop's live index.</li>
 *   <li><b>A pure node is worked out again for each exec node that reads it</b>, and once for everything
 *       one exec node reads — Unreal's rule — in a loop body too, and for what a loop reads before its
 *       next iteration and a host reads after the flow.</li>
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
     * Entry → For(3) body → SetVar(acc ← inc) → SetVar(out ← inc)        inc = acc + 1
     * </pre>
     * The rule holds inside a loop body, which nothing clears between iterations any more: each exec node
     * works {@code inc} out for itself, so {@code out} is read after the write — 4, not 3.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aPureReadInALoopBodyIsWorkedOutForEachExecNode(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("acc", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("loop", ForNode.class).constant("loop.count", 3);
        b.add("inc", AddNode.class).wire("inc.in1", "acc").constant("inc.in2", 1f);
        b.add("setAcc", SetVarNode.class).option("setAcc", "varName", "acc").wire("setAcc.value", "inc");
        b.add("setOut", SetVarNode.class).option("setOut", "varName", "out").wire("setOut.value", "inc");
        b.wire("loop.in", "entry");
        b.wire("setAcc.trigger", "loop.body");
        b.then("setAcc", "setOut");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        var vars = exec.getEnvironment().variables();
        assertEq(helper, "three iterations", 3f, number(vars.get("acc")), 1e-5f);
        assertEq(helper, "the second reader saw the first one's write", 4f, number(vars.get("out")), 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → For(count ← n) body → SetVar(n ← n - 1) → SetVar(runs ← runs + 1)        n = 4
     * </pre>
     * The count is read again before each iteration, as Unreal's {@code ForLoop} compares against
     * {@code LastIndex} on every pass: 0 &lt; 4, 1 &lt; 3, 2 &lt; 2 stops — two runs, not four.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aForReadsItsCountAgainBeforeEachIteration(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("n", float.class, 4f, VariableKind.INPUT);
        b.variable("runs", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("loop", ForNode.class).wire("loop.count", "n");
        b.add("less", SubtractNode.class).wire("less.a", "n").constant("less.b", 1f);
        b.add("setN", SetVarNode.class).option("setN", "varName", "n").wire("setN.value", "less");
        b.add("more", AddNode.class).wire("more.in1", "runs").constant("more.in2", 1f);
        b.add("setRuns", SetVarNode.class).option("setRuns", "varName", "runs").wire("setRuns.value", "more");
        b.wire("loop.in", "entry");
        b.wire("setN.trigger", "loop.body");
        b.then("setN", "setRuns");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "the loop saw its count shrink", 2f,
                number(exec.getEnvironment().variables().get("runs")), 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → ForEach(list ← L) body → SetVar(L ← RemoveAt(L, 0)) → SetVar(runs ← runs + 1)        L = [1, 2, 3]
     * </pre>
     * The list is read again before each iteration, as Unreal's {@code ForEachLoop} takes the array's length
     * on every pass: index 0 of three, index 1 of two, index 2 of one stops — two runs.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aForEachReadsItsListAgainBeforeEachIteration(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("L", List.class, null, VariableKind.INPUT);
        b.variable("runs", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("loop", ForEachNode.class).wire("loop.list", "L");
        b.add("rest", ListRemoveAtNode.class).wire("rest.list", "L").constant("rest.index", 0);
        b.add("setL", SetVarNode.class).option("setL", "varName", "L").wire("setL.value", "rest");
        b.add("more", AddNode.class).wire("more.in1", "runs").constant("more.in2", 1f);
        b.add("setRuns", SetVarNode.class).option("setRuns", "varName", "runs").wire("setRuns.value", "more");
        b.wire("loop.in", "entry");
        b.wire("setL.trigger", "loop.body");
        b.then("setL", "setRuns");

        var exec = new GraphExecutor(b.graph());
        exec.getEnvironment().variables().put("L", new ArrayList<>(List.of(1, 2, 3)));
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "the loop saw its list shrink", 2f,
                number(exec.getEnvironment().variables().get("runs")), 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → While(cond ← n &lt; 3) body → SetVar(n ← n + 1)
     * </pre>
     * One get-node for {@code n} feeds both the condition and the body. The condition is read after the
     * body's last step, so it sees the write: three passes, then out.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aWhileConditionSeesItsBodysWrite(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("n", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("below", LessThanNode.class).wire("below.a", "n").constant("below.b", 3f);
        b.add("loop", WhileNode.class).option("loop", "maxIterations", 100).wire("loop.cond", "below");
        b.add("inc", AddNode.class).wire("inc.in1", "n").constant("inc.in2", 1f);
        b.add("setN", SetVarNode.class).option("setN", "varName", "n").wire("setN.value", "inc");
        b.wire("loop.in", "entry");
        b.wire("setN.trigger", "loop.body");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "stopped when the condition read the third write", 3f,
                number(exec.getEnvironment().variables().get("n")), 1e-5f);
        helper.succeed();
    }

    /**
     * <pre>
     * Entry → SetVar(x ← inc)        inc = x + 1, then read x and inc from outside
     * </pre>
     * A read after the flow is a new one: it sees what the last step wrote rather than what that step
     * pulled on its way in.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aReadAfterTheFlowSeesItsLastWrite(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.variable("x", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("inc", AddNode.class).wire("inc.in1", "x").constant("inc.in2", 1f);
        b.add("setX", SetVarNode.class).option("setX", "varName", "x").wire("setX.value", "inc");
        b.then("entry", "setX");

        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));
        assertEq(helper, "x after the write", 1f, number(exec.evaluate(b.outputOf("x"), Object.class)), 1e-5f);
        assertEq(helper, "inc worked out from it", 2f, number(exec.evaluate(b.outputOf("inc"), Object.class)), 1e-5f);
        helper.succeed();
    }

    private static float number(Object value) {
        return value instanceof Number n ? n.floatValue() : Float.NaN;
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
