package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.CacheNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RandomNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Subgraphs used as functions.
 *
 * <p>KilaGraph has no separate function-graph type — {@code ShaderFunctionGraph} serves the shader
 * side only. On the blueprint side a function <em>is</em> a subgraph: its {@code READ} graph
 * variables are parameters, its {@code WRITE} variables are return values, and its
 * {@code EXECUTION_FLOW} variables are the entry and exit pins. {@code SubgraphExecGameTest} covers
 * the mechanism at its simplest; this covers it at the sizes real graphs reach — several parameters
 * and returns, three levels of nesting, one function called from two sites, and functions and loops
 * containing each other.</p>
 *
 * <p>These are the shapes the executor's subgraph path is worst at (a fresh child executor, a fresh
 * variable store, and a {@code uid.toString()} per parameter per call), so they are also the shapes
 * that must keep working when that path is optimised.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class FunctionCallGameTest {

    private FunctionCallGameTest() {}

    /** {@code (a, b) -> (a + b, a - b)}: two parameters in, two return values out, one call. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void multipleParametersAndReturns(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();
        defineSumAndDiff(fn);

        outer.add("entry", EntryNode.class);
        outer.call("f", fn);
        outer.constant("f.a", 12f).constant("f.b", 5f);
        outer.wire("f.call", "entry");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        assertEq(helper, "sum", 17f, f(exec.evaluate(outer.outputOf("f.sum"), Float.class)), 1e-5f);
        assertEq(helper, "diff", 7f, f(exec.evaluate(outer.outputOf("f.diff"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /**
     * The same function called from two sites with different arguments answers differently.
     *
     * <p>Each call gets its own child scope today. The subgraph path is a target for executor
     * pooling, and a pool that leaked a variable store or a memo between the two call sites would
     * make both answer the same — which is exactly what this asserts cannot happen.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void twoCallSitesDoNotShareState(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();
        defineSumAndDiff(fn);

        outer.add("entry", EntryNode.class);
        outer.call("f1", fn).constant("f1.a", 10f).constant("f1.b", 1f);
        outer.call("f2", fn).constant("f2.a", 100f).constant("f2.b", 2f);
        outer.wire("f1.call", "entry");
        outer.wire("f2.call", "f1.ret");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        assertEq(helper, "first call sum", 11f, f(exec.evaluate(outer.outputOf("f1.sum"), Float.class)), 1e-5f);
        assertEq(helper, "second call sum", 102f, f(exec.evaluate(outer.outputOf("f2.sum"), Float.class)), 1e-5f);
        assertEq(helper, "first call diff", 9f, f(exec.evaluate(outer.outputOf("f1.diff"), Float.class)), 1e-5f);
        assertEq(helper, "second call diff", 98f, f(exec.evaluate(outer.outputOf("f2.diff"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /**
     * Three levels of nesting, each adding one to the value threaded through it, so a level that
     * silently failed to run changes the answer instead of being invisible.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void threeLevelsDeep(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var lvl1 = outer.subgraph();
        var lvl2 = lvl1.subgraph();
        var lvl3 = lvl2.subgraph();

        // innermost: out = in + 1
        definePlusOne(lvl3, null);
        // middle two: out = callee(in) + 1
        definePlusOne(lvl2, lvl3);
        definePlusOne(lvl1, lvl2);

        outer.add("entry", EntryNode.class);
        outer.call("f", lvl1).constant("f.in", 10f);
        outer.wire("f.call", "entry");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        assertEq(helper, "10 incremented once per level", 13f,
                f(exec.evaluate(outer.outputOf("f.out"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /** A function whose body is a loop: {@code out = sum(0 .. n-1)}. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void functionContainingALoop(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();

        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("n", float.class, 0f, VariableKind.INPUT);
        fn.variable("acc", float.class, 0f, VariableKind.INPUT);
        fn.declare("out", float.class, 0f, VariableKind.OUTPUT);

        // for (i = 0; i < n; i++) { acc += i; out = acc; }
        //
        // Both writes take the accumulator node's value rather than re-reading `acc`, because a
        // variable read is memoised for the generation: after the last iteration's body there is no
        // further clearCache(), so a read of `acc` on the `completed` path would still see the value
        // from before that iteration's write. See
        // ExecVarInteractionGameTest.aVariableReadIsMemoisedUntilClearCache.
        fn.add("loop", ForNode.class).wire("loop.count", "n");
        fn.add("accum", AddNode.class).wire("accum.in1", "acc").wire("accum.in2", "loop.index");
        fn.add("setAcc", SetVarNode.class).option("setAcc", "varName", "acc").wire("setAcc.value", "accum");
        fn.add("setOut", SetVarNode.class).option("setOut", "varName", "out").wire("setOut.value", "accum");
        fn.wire("loop.in", "call");
        fn.wire("setAcc.trigger", "loop.body");
        fn.then("setAcc", "setOut");
        fn.wire("ret", "loop.completed");

        outer.add("entry", EntryNode.class);
        outer.call("f", fn).constant("f.n", 5f);
        outer.wire("f.call", "entry");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        // 0 + 1 + 2 + 3 + 4
        assertEq(helper, "sum of 0..4", 10f, f(exec.evaluate(outer.outputOf("f.out"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /** A loop whose body calls a function: three calls, each doubling the running total. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void loopContainingAFunctionCall(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();

        // fn(in) -> out = in * 2
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("in", float.class, 0f, VariableKind.INPUT);
        fn.declare("out", float.class, 0f, VariableKind.OUTPUT);
        fn.add("dbl", MultiplyNode.class).wire("dbl.in1", "in").constant("dbl.in2", 2f);
        fn.add("setOut", SetVarNode.class).option("setOut", "varName", "out").wire("setOut.value", "dbl");
        fn.then("call", "setOut", "ret");

        outer.variable("total", float.class, 1f, VariableKind.INPUT);
        outer.add("entry", EntryNode.class);
        outer.add("loop", ForNode.class).constant("loop.count", 3);
        outer.call("f", fn).wire("f.in", "total");
        outer.add("setTotal", SetVarNode.class).option("setTotal", "varName", "total")
                .wire("setTotal.value", "f.out");

        outer.wire("loop.in", "entry");
        outer.wire("f.call", "loop.body");
        outer.wire("setTotal.trigger", "f.ret");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        // 1 -> 2 -> 4 -> 8
        assertEq(helper, "doubled three times", 8f,
                num(exec.getEnvironment().variables().get("total")), 1e-5f);
        helper.succeed();
    }

    /**
     * A function's own variables stay in its scope. {@code SubgraphExecGameTest.childVarIsolated}
     * asserts this for a name the parent never mentions; this asserts the harder case, where parent
     * and function use the <em>same</em> name and must not see each other's value.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sameNamedVariablesDoNotCollide(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();

        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.declare("shared", float.class, 0f, VariableKind.OUTPUT);
        fn.add("v", AddNode.class).constant("v.in1", 99f).constant("v.in2", 0f);
        fn.add("setShared", SetVarNode.class).option("setShared", "varName", "shared")
                .wire("setShared.value", "v");
        fn.then("call", "setShared", "ret");

        outer.add("entry", EntryNode.class);
        outer.add("outerVal", AddNode.class).constant("outerVal.in1", 1f).constant("outerVal.in2", 0f);
        outer.add("setOuter", SetVarNode.class).option("setOuter", "varName", "shared")
                .wire("setOuter.value", "outerVal");
        outer.call("f", fn);
        outer.then("entry", "setOuter");
        outer.wire("f.call", "setOuter.next");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));

        assertEq(helper, "parent's 'shared' survives the call", 1f,
                num(exec.getEnvironment().variables().get("shared")), 1e-5f);
        assertEq(helper, "function's 'shared' is harvested from its own scope", 99f,
                f(exec.evaluate(outer.outputOf("f.shared"), Float.class)), 1e-5f);
        helper.succeed();
    }

    /**
     * A {@code Cache} inside a function does not survive from one call of that function to the next.
     *
     * <p>{@code Cache} memoises in the executor's per-node state, and each call of a subgraph gets
     * its own executor — so its memo lasts exactly one call. That is a consequence of how the child
     * executor is built, not of anything {@code Cache} does, which makes it precisely the semantic
     * that reusing child executors across calls would quietly change: the second call would return
     * the first call's value and every assertion about the final number would still pass, because
     * both calls would agree.</p>
     *
     * <p>Two calls with different arguments, summed: leaking the memo gives 2 instead of 3.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aCacheInsideAFunctionDoesNotLeakBetweenCalls(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("x", float.class, 0f, VariableKind.INPUT);
        fn.declare("out", float.class, 0f, VariableKind.OUTPUT);
        fn.add("memo", CacheNode.class).wire("memo.value", "x");
        fn.add("setOut", SetVarNode.class).option("setOut", "varName", "out")
                .wire("setOut.value", "memo.cached");
        fn.then("call", "setOut", "ret");

        outer.variable("total", float.class, 0f, VariableKind.INPUT);
        outer.add("entry", EntryNode.class);
        outer.add("loop", ForNode.class).constant("loop.count", 2);
        outer.add("arg", AddNode.class).wire("arg.in1", "loop.index").constant("arg.in2", 1f);
        outer.call("f", fn).wire("f.x", "arg");
        outer.add("sum", AddNode.class).wire("sum.in1", "total").wire("sum.in2", "f.out");
        outer.add("setTotal", SetVarNode.class).option("setTotal", "varName", "total")
                .wire("setTotal.value", "sum");
        outer.wire("loop.in", "entry");
        outer.wire("f.call", "loop.body");
        outer.wire("setTotal.trigger", "f.ret");

        var exec = new GraphExecutor(outer.graph());
        exec.executeFrom(outer.node("entry"));
        assertEq(helper, "calls returned 1 and 2, not 1 and 1", 3f,
                num(exec.getEnvironment().variables().get("total")), 1e-5f);
        helper.succeed();
    }

    /**
     * A seeded function draws the same random value on every call.
     *
     * <p>Each call builds its RNG from the environment's seed, so the sequence restarts rather than
     * continuing. Like the {@code Cache} case above this falls out of the child executor being new
     * each time, and is the second thing reusing one would change — a reused executor would carry
     * its {@code Random} forward and the two calls would differ.</p>
     *
     * <p>Both calls go through <b>one call site</b>, driven by a loop. Two separate call sites would
     * not test this: they are different nodes, so they never share an executor however the pooling
     * is keyed, and the test would pass without ever reaching the path it is about.</p>
     *
     * <p>Asserted as {@code sum == 2 × last}, which holds exactly when the two draws are equal.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aSeededFunctionRestartsItsRandomSequencePerCall(GameTestHelper helper) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.declare("roll", float.class, 0f, VariableKind.OUTPUT);
        fn.add("rng", RandomNode.class).constant("rng.min", 0f).constant("rng.max", 1000f);
        fn.add("setRoll", SetVarNode.class).option("setRoll", "varName", "roll")
                .wire("setRoll.value", "rng");
        fn.then("call", "setRoll", "ret");

        outer.variable("sum", float.class, 0f, VariableKind.INPUT);
        outer.add("entry", EntryNode.class);
        outer.add("loop", ForNode.class).constant("loop.count", 2);
        outer.call("f", fn);
        outer.add("acc", AddNode.class).wire("acc.in1", "sum").wire("acc.in2", "f.roll");
        outer.add("setSum", SetVarNode.class).option("setSum", "varName", "sum")
                .wire("setSum.value", "acc");
        outer.add("setLast", SetVarNode.class).option("setLast", "varName", "last")
                .wire("setLast.value", "f.roll");
        outer.wire("loop.in", "entry");
        outer.wire("f.call", "loop.body");
        outer.wire("setSum.trigger", "f.ret");
        outer.then("setSum", "setLast");

        var exec = new GraphExecutor(outer.graph(), EvaluationEnvironment.seeded(4242L));
        exec.executeFrom(outer.node("entry"));

        float sum = num(exec.getEnvironment().variables().get("sum"));
        float last = num(exec.getEnvironment().variables().get("last"));
        assertTrue(helper, "the function drew something", last > 0f);
        assertEq(helper, "both calls drew the same value (sum = 2 x last)", 2f * last, sum, 1e-3f);
        helper.succeed();
    }

    // ---- function bodies ---------------------------------------------------------------------

    /** {@code (a, b) -> (sum = a + b, diff = a - b)}, entered at {@code call}, exiting at {@code ret}. */
    private static void defineSumAndDiff(KGGraphBuilder fn) {
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("a", float.class, 0f, VariableKind.INPUT);
        fn.variable("b", float.class, 0f, VariableKind.INPUT);
        fn.declare("sum", float.class, 0f, VariableKind.OUTPUT);
        fn.declare("diff", float.class, 0f, VariableKind.OUTPUT);

        fn.add("add", AddNode.class).wire("add.in1", "a").wire("add.in2", "b");
        fn.add("sub", SubtractNode.class).wire("sub.a", "a").wire("sub.b", "b");
        fn.add("setSum", SetVarNode.class).option("setSum", "varName", "sum").wire("setSum.value", "add");
        fn.add("setDiff", SetVarNode.class).option("setDiff", "varName", "diff").wire("setDiff.value", "sub");
        fn.then("call", "setSum", "setDiff", "ret");
    }

    /**
     * {@code in -> out = in + 1}, or {@code out = callee(in) + 1} when {@code callee} is given —
     * one level of the nesting ladder.
     */
    private static void definePlusOne(KGGraphBuilder fn, KGGraphBuilder callee) {
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("in", float.class, 0f, VariableKind.INPUT);
        fn.declare("out", float.class, 0f, VariableKind.OUTPUT);
        fn.add("inc", AddNode.class).constant("inc.in2", 1f);
        fn.add("setOut", SetVarNode.class).option("setOut", "varName", "out").wire("setOut.value", "inc");

        if (callee == null) {
            fn.wire("inc.in1", "in");
            fn.then("call", "setOut", "ret");
        } else {
            fn.call("inner", callee).wire("inner.in", "in");
            fn.wire("inc.in1", "inner.out");
            fn.wire("inner.call", "call");
            fn.wire("setOut.trigger", "inner.ret");
            fn.wire("ret", "setOut.next");
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static float f(Float v) {
        return v == null ? Float.NaN : v;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
