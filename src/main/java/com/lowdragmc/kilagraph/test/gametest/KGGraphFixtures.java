package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToIntNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.ForNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.GateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AngleConvertNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.FractNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MaxNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.NegateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RoundNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SignNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.TrigNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtCreateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtSetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.nbt.NbtValueType;
import com.lowdragmc.kilagraph.blueprint.nodes.string.FormatNode;
import com.lowdragmc.kilagraph.blueprint.nodes.vector.VectorNodes;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;

import java.util.List;

/**
 * Graphs shared by the behaviour tests, the differential harness, and the benchmarks.
 *
 * <p>They live together on purpose. A benchmark that measures one graph while the correctness tests
 * cover a different one can report a speed-up on a shape nothing proves is still correct; building
 * both from the same fixture means "it got faster" and "it still computes the right answer" are
 * statements about the same graph.</p>
 */
public final class KGGraphFixtures {

    private KGGraphFixtures() {}

    /**
     * Five statements over ~21 nodes with a Branch and two values fed back through the variable
     * store — the "Interp To" pattern:
     *
     * <pre>
     *   speed     = lerp(speed, sqrt(vx*vx + vz*vz), 0.2)
     *   direction = clamp(yaw - facing, -180, 180)
     *   if (speed &gt; 0.1)  lean = lerp(lean, direction * 0.5, 0.3)
     *   else               lean = lerp(lean, 0, 0.3)
     * </pre>
     *
     * <p>The same graph {@code ExecutorBenchGameTest.locomotion} builds by hand. Kept here so the
     * hand-built version has something to be checked against, and so later tests can reuse it
     * without a third copy.</p>
     *
     * <p>Driven by {@code executeFrom(node("entry"))} with {@code vx/vz/yaw/facing} seeded in the
     * environment; converges to speed 5, direction 45, lean 22.5.</p>
     */
    public static KGGraphBuilder locomotion() {
        var b = KGGraphBuilder.blueprint();
        b.variable("vx", float.class, 0f, VariableKind.INPUT)
                .variable("vz", float.class, 0f, VariableKind.INPUT)
                .variable("yaw", float.class, 0f, VariableKind.INPUT)
                .variable("facing", float.class, 0f, VariableKind.INPUT)
                .variable("speed", float.class, 0f, VariableKind.INPUT)
                .variable("lean", float.class, 0f, VariableKind.INPUT);

        // speed = lerp(speed, sqrt(vx*vx + vz*vz), 0.2)
        b.add("vxSq", MultiplyNode.class).wire("vxSq.in1", "vx").wire("vxSq.in2", "vx");
        b.add("vzSq", MultiplyNode.class).wire("vzSq.in1", "vz").wire("vzSq.in2", "vz");
        b.add("sumSq", AddNode.class).wire("sumSq.in1", "vxSq").wire("sumSq.in2", "vzSq");
        b.add("len", SqrtNode.class).wire("len.in", "sumSq");
        b.add("speedLerp", LerpNode.class)
                .wire("speedLerp.a", "speed").wire("speedLerp.b", "len").constant("speedLerp.t", 0.2f);

        // direction = clamp(yaw - facing, -180, 180)
        b.add("diff", SubtractNode.class).wire("diff.a", "yaw").wire("diff.b", "facing");
        b.add("dirClamp", ClampNode.class).wire("dirClamp.in", "diff")
                .constant("dirClamp.min", -180f).constant("dirClamp.max", 180f);

        // lean, both branches
        b.add("moving", GreaterThanNode.class).wire("moving.a", "speedLerp").constant("moving.b", 0.1f);
        b.add("leanTarget", MultiplyNode.class)
                .wire("leanTarget.in1", "dirClamp").constant("leanTarget.in2", 0.5f);
        b.add("leanMoving", LerpNode.class)
                .wire("leanMoving.a", "lean").wire("leanMoving.b", "leanTarget").constant("leanMoving.t", 0.3f);
        b.add("leanStill", LerpNode.class)
                .wire("leanStill.a", "lean").constant("leanStill.b", 0f).constant("leanStill.t", 0.3f);

        // Entry → set speed → set direction → Branch → set lean (either side)
        b.add("entry", EntryNode.class);
        b.add("setSpeed", SetVarNode.class).option("setSpeed", "varName", "speed")
                .wire("setSpeed.value", "speedLerp");
        b.add("setDir", SetVarNode.class).option("setDir", "varName", "direction")
                .wire("setDir.value", "dirClamp");
        b.add("branch", BranchNode.class).wire("branch.cond", "moving");
        b.add("setLeanMoving", SetVarNode.class).option("setLeanMoving", "varName", "lean")
                .wire("setLeanMoving.value", "leanMoving");
        b.add("setLeanStill", SetVarNode.class).option("setLeanStill", "varName", "lean")
                .wire("setLeanStill.value", "leanStill");

        b.then("entry", "setSpeed", "setDir", "branch");
        b.wire("setLeanMoving.trigger", "branch.trueExec");
        b.wire("setLeanStill.trigger", "branch.falseExec");
        return b;
    }

    /**
     * A chain of {@code length} {@code Add} nodes, each adding one to the last, so the value at the
     * tip is the depth actually traversed. Node {@code i} is named {@code "n" + i}.
     *
     * <p>Deliberately the simplest shape that is still deep: the data side of the executor recurses
     * per link, and the slot, stamp and cycle tables all have to grow past their initial size.</p>
     */
    public static KGGraphBuilder chainOfAdds(int length) {
        var b = KGGraphBuilder.blueprint();
        b.addMany("n", AddNode.class, length);
        b.constant("n0.in1", 0f);
        for (int i = 0; i < length; i++) {
            b.constant("n" + i + ".in2", 1f);
            if (i > 0) b.wire("n" + i + ".in1", "n" + (i - 1));
        }
        return b;
    }

    /**
     * A chain of {@code length} {@code Round} nodes — one input and <b>one option</b> each, so it is
     * {@link #monomorphicChain} plus exactly one option read per node.
     *
     * <p>That pairing is the point: {@code Abs} and {@code Round} take the same single input and do
     * comparable arithmetic, so the difference between the two chains is one option read, and an
     * option read is a thing the executor can be asked to do two different ways. Node {@code i} is
     * named {@code "u" + i}, matching the other chains so they are interchangeable in a benchmark.</p>
     *
     * <p>Rounding is idempotent on an integral value, so the chain stays at 0 like the others.</p>
     */
    public static KGGraphBuilder optionChain(int length) {
        var b = KGGraphBuilder.blueprint();
        b.addMany("u", RoundNode.class, length);
        b.constant("u0.in", 0f);
        for (int i = 1; i < length; i++) b.wire("u" + i + ".in", "u" + (i - 1));
        return b;
    }

    /**
     * {@code entry} followed by {@code length} chained {@code Noop}s — pure exec flow with no data.
     * Step {@code i} is named {@code "step" + i}. Isolates the per-step cost of the exec VM.
     */
    public static KGGraphBuilder execChain(int length) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.addMany("step", NoopNode.class, length);
        b.wire("step0.in", "entry");
        for (int i = 1; i < length; i++) b.wire("step" + i + ".in", "step" + (i - 1) + ".out");
        return b;
    }

    /**
     * {@code for i in 0..count-1: acc += i}, leaving the sum in the {@code acc} graph variable.
     *
     * <p>The body reads {@code acc} through its own node and writes it back, so each iteration
     * depends on the previous one — which is what makes the per-iteration {@code clearCache()} part
     * of the result rather than an implementation detail.</p>
     */
    public static KGGraphBuilder accumulatingLoop(int count) {
        var b = KGGraphBuilder.blueprint();
        b.variable("acc", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("loop", ForNode.class).constant("loop.count", count);
        b.add("next", AddNode.class).wire("next.in1", "acc").wire("next.in2", "loop.index");
        b.add("setAcc", SetVarNode.class).option("setAcc", "varName", "acc").wire("setAcc.value", "next");
        b.wire("loop.in", "entry");
        b.wire("setAcc.trigger", "loop.body");
        return b;
    }

    /**
     * A chain of {@code length} one-in/one-out math nodes, all of the same class — the monomorphic
     * control for {@link #polymorphicChain}. Node {@code i} is named {@code "u" + i}.
     *
     * <p>Every node fixes 0, so the chain's value stays 0 however long it is and the measurement is
     * not perturbed by denormals or infinities appearing part-way down.</p>
     */
    public static KGGraphBuilder monomorphicChain(int length) {
        var b = KGGraphBuilder.blueprint();
        b.addMany("u", AbsNode.class, length);
        b.constant("u0.in", 0f);
        for (int i = 1; i < length; i++) b.wire("u" + i + ".in", "u" + (i - 1));
        return b;
    }

    /**
     * The same chain as {@link #monomorphicChain} — same length, same arity, same wiring — built
     * from eight <em>different</em> node classes in rotation.
     *
     * <p>The pair exists to isolate one thing: the cost of the call site in {@code evaluateNode}
     * going megamorphic. With one receiver class the JIT installs a type guard and inlines the whole
     * node body along with its input reads; with eight it cannot, and every {@code getFloat} becomes
     * a real call. Holding arity and topology fixed is what makes the difference between the two
     * attributable to dispatch rather than to the nodes doing different amounts of work — which is
     * why the control is an {@code Abs} chain and not the three-input {@code Lerp} chain the older
     * benchmark uses.</p>
     *
     * <p>All eight classes map 0 to 0, so this chain also stays at 0 and the two are comparable.</p>
     */
    public static KGGraphBuilder polymorphicChain(int length) {
        List<Class<? extends com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node>> cycle = List.of(
                AbsNode.class, SqrtNode.class, RoundNode.class, SignNode.class,
                FractNode.class, NegateNode.class, TrigNode.class, AngleConvertNode.class);
        var b = KGGraphBuilder.blueprint();
        for (int i = 0; i < length; i++) b.add("u" + i, cycle.get(i % cycle.size()));
        b.constant("u0.in", 0f);
        for (int i = 1; i < length; i++) b.wire("u" + i + ".in", "u" + (i - 1));
        return b;
    }

    /**
     * {@code entry} followed by {@code calls} sequential calls to a three-node function
     * {@code f(x) = x * 2 + 1}, each call feeding the next. Call {@code i} is named {@code "c" + i};
     * the final value lands in the {@code result} graph variable.
     *
     * <p>Exists because nothing else measures the subgraph path, which is the executor's most
     * expensive: every entry builds a variable store, a child environment and a whole child executor
     * whose value tables start empty.</p>
     */
    public static KGGraphBuilder subgraphCalls(int calls) {
        var outer = KGGraphBuilder.blueprint();
        var fn = outer.subgraph();
        fn.execVariable("call", VariableKind.INPUT);
        fn.execVariable("ret", VariableKind.OUTPUT);
        fn.variable("x", float.class, 0f, VariableKind.INPUT);
        fn.declare("y", float.class, 0f, VariableKind.OUTPUT);
        fn.add("dbl", MultiplyNode.class).wire("dbl.in1", "x").constant("dbl.in2", 2f);
        fn.add("inc", AddNode.class).wire("inc.in1", "dbl").constant("inc.in2", 1f);
        fn.add("setY", SetVarNode.class).option("setY", "varName", "y").wire("setY.value", "inc");
        fn.then("call", "setY", "ret");

        outer.add("entry", EntryNode.class);
        outer.add("seed", AddNode.class).constant("seed.in1", 1f).constant("seed.in2", 0f);
        for (int i = 0; i < calls; i++) {
            outer.call("c" + i, fn);
            outer.wire("c" + i + ".x", i == 0 ? "seed" : "c" + (i - 1) + ".y");
            outer.wire("c" + i + ".call", i == 0 ? "entry" : "c" + (i - 1) + ".ret");
        }
        outer.add("setResult", SetVarNode.class).option("setResult", "varName", "result")
                .wire("setResult.value", "c" + (calls - 1) + ".y");
        outer.wire("setResult.trigger", "c" + (calls - 1) + ".ret");
        return outer;
    }

    /**
     * {@code count} chained {@code (GreaterThan → Branch)} pairs, alternating which side is taken so
     * neither branch direction is the only one measured. Branch {@code i} is named {@code "br" + i};
     * the flow ends at {@code "done"}.
     */
    public static KGGraphBuilder branchLadder(int count) {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        for (int i = 0; i < count; i++) {
            boolean takeTrue = i % 2 == 0;
            b.add("cmp" + i, GreaterThanNode.class)
                    .constant("cmp" + i + ".a", takeTrue ? 1f : 0f)
                    .constant("cmp" + i + ".b", takeTrue ? 0f : 1f);
            b.add("br" + i, BranchNode.class).wire("br" + i + ".cond", "cmp" + i);
            b.wire("br" + i + ".in", i == 0 ? "entry" : "br" + (i - 1) + (i % 2 == 0 ? ".falseExec" : ".trueExec"));
        }
        b.add("done", SetVarNode.class).option("done", "varName", "reachedEnd");
        b.add("one", AddNode.class).constant("one.in1", 1f).constant("one.in2", 0f);
        b.wire("done.value", "one");
        b.wire("done.trigger", "br" + (count - 1) + (count % 2 == 1 ? ".trueExec" : ".falseExec"));
        return b;
    }

    /**
     * {@code writes} sequential {@code read → +1 → write} steps cycling over four graph variables —
     * the variable store under load, which nothing else measures.
     *
     * <p>Each step reads through its own node, so a later step sees the value the earlier one wrote:
     * a read is memoised per node per generation, not per variable.</p>
     */
    public static KGGraphBuilder variablePingPong(int writes) {
        int vars = 4;
        var b = KGGraphBuilder.blueprint();
        for (int v = 0; v < vars; v++) b.variable("v" + v, float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        for (int i = 0; i < writes; i++) {
            String v = "v" + (i % vars);
            b.readAgain("g" + i, v);
            b.add("a" + i, AddNode.class).wire("a" + i + ".in1", "g" + i).constant("a" + i + ".in2", 1f);
            b.add("s" + i, SetVarNode.class).option("s" + i, "varName", v).wire("s" + i + ".value", "a" + i);
            b.wire("s" + i + ".trigger", i == 0 ? "entry" : "s" + (i - 1) + ".next");
        }
        return b;
    }

    /**
     * One graph containing every node that has an exec intrinsic, on both of its paths where it has
     * two: {@code Entry}, {@code Noop}, {@code Branch} taken and not taken, {@code Gate} open and
     * shut, and {@code SetVar}.
     *
     * <p>It exists so the differential harness has something that exercises all of them. Without it
     * {@code Gate} appeared in no scenario at all — it has a behaviour test, but nothing compared its
     * intrinsic against the node it was transcribed from, which is the check that catches a
     * transcription slip rather than a missing feature.</p>
     *
     * <p>Every branch writes a distinct variable, so which paths ran is visible in the result rather
     * than only in the trace.</p>
     */
    public static KGGraphBuilder execIntrinsicSampler() {
        var b = KGGraphBuilder.blueprint();
        b.add("entry", EntryNode.class);
        b.add("noop", NoopNode.class);

        b.add("yes", GreaterThanNode.class).constant("yes.a", 1f).constant("yes.b", 0f);
        b.add("branch", BranchNode.class).wire("branch.cond", "yes");
        b.add("one", AddNode.class).constant("one.in1", 1f).constant("one.in2", 0f);
        b.add("setTaken", SetVarNode.class).option("setTaken", "varName", "taken").wire("setTaken.value", "one");
        b.add("setUntaken", SetVarNode.class).option("setUntaken", "varName", "untaken")
                .wire("setUntaken.value", "one");

        // Gate open, then Gate shut — the second must stop the flow dead.
        b.add("gateOpen", GateNode.class).constant("gateOpen.enabled", true);
        b.add("setPassed", SetVarNode.class).option("setPassed", "varName", "passed").wire("setPassed.value", "one");
        b.add("gateShut", GateNode.class).constant("gateShut.enabled", false);
        b.add("setBlocked", SetVarNode.class).option("setBlocked", "varName", "blocked")
                .wire("setBlocked.value", "one");

        b.then("entry", "noop");
        b.wire("branch.in", "noop.out");
        b.wire("setTaken.trigger", "branch.trueExec");
        b.wire("setUntaken.trigger", "branch.falseExec");
        b.wire("gateOpen.in", "setTaken.next");
        b.wire("setPassed.trigger", "gateOpen.out");
        b.wire("gateShut.in", "setPassed.next");
        b.wire("setBlocked.trigger", "gateShut.out");
        return b;
    }

    /**
     * {@code entry} fanning out to a loop <em>and</em> a node after it, both in the same frame.
     *
     * <p>Built for one specific transition: a node that pushes a frame while its own frame still has
     * work queued behind it. In a linear chain that never happens — each node enqueues its successor
     * only as it runs, so the queue is empty by the time a loop pushes its body — which makes the
     * whole case invisible to every other fixture here. An exec output takes multiple wires, so
     * wiring {@code entry} to both the loop and the node after it produces it.</p>
     *
     * <p>The order that must hold is loop-then-after: the pushed frame runs before the rest of the
     * queue it was pushed from. A driver that kept draining its own frame would run {@code after}
     * first and nothing about the values would look wrong.</p>
     */
    public static KGGraphBuilder fanOutIntoLoop(int count) {
        var b = KGGraphBuilder.blueprint();
        b.variable("log", float.class, 0f, VariableKind.INPUT);
        b.add("entry", EntryNode.class);
        b.add("loop", ForNode.class).constant("loop.count", count);
        b.add("body", AddNode.class).wire("body.in1", "log").constant("body.in2", 1f);
        b.add("setBody", SetVarNode.class).option("setBody", "varName", "log").wire("setBody.value", "body");
        b.add("after", AddNode.class).wire("after.in1", "log").constant("after.in2", 100f);
        b.add("setAfter", SetVarNode.class).option("setAfter", "varName", "log").wire("setAfter.value", "after");
        b.wire("loop.in", "entry");
        b.wire("setAfter.trigger", "entry");   // second wire off entry's exec output — the fan-out
        b.wire("setBody.trigger", "loop.body");
        return b;
    }

    /**
     * The mixed workload: two levels of function call inside a loop, then a post-loop stage over
     * NBT, vectors, lists and string formatting.
     *
     * <pre>
     *   bonus(tier)          = tier &gt; 2 ? 10 : 0                    // innermost function
     *   score(qty, weight)   = qty * weight + bonus(qty)             // calls bonus
     *   for i in 0..3:  total += score(i + 1, 2)                     // 2,4,16,18  =&gt; total 40
     *
     *                peak   = max over the same scores                // =&gt; 18
     *
     *   then, once:  v      = |(total*0.6, total*0.8, 0)|            // = total
     *                tag    = {total: 40, len: 40, peak: 18}
     *                label  = "total=40.0 len=40.0"
     * </pre>
     *
     * <p>Every stage feeds the next, so a stage that silently stopped running changes a value the
     * test asserts rather than going unnoticed. The post-loop stage reads {@code total} through its
     * <em>own</em> node ({@code totalAfter}) because the loop body's read is memoised for the last
     * iteration — see {@link KGGraphBuilder#readAgain}.</p>
     *
     * <p>Driven with {@code executeFrom(node("entry"))}. Shared with the benchmark suite so the
     * shape that is measured is the shape that is checked.</p>
     */
    public static KGGraphBuilder mixedWorkload() {
        var outer = KGGraphBuilder.blueprint();

        // The two functions, outermost first: a call site can only target a subgraph local to the
        // graph the call node lives in, so `bonus` has to be a subgraph of `score`, not of `outer`.
        var score = outer.subgraph();
        var bonus = score.subgraph();

        // ---- innermost function: bonus(tier) = tier > 2 ? 10 : 0
        bonus.execVariable("call", VariableKind.INPUT);
        bonus.execVariable("ret", VariableKind.OUTPUT);
        bonus.variable("tier", float.class, 0f, VariableKind.INPUT);
        bonus.declare("bonus", float.class, 0f, VariableKind.OUTPUT);
        bonus.add("big", GreaterThanNode.class).wire("big.a", "tier").constant("big.b", 2f);
        bonus.add("pick", BranchNode.class).wire("pick.cond", "big");
        bonus.add("ten", AddNode.class).constant("ten.in1", 10f).constant("ten.in2", 0f);
        bonus.add("zero", AddNode.class).constant("zero.in1", 0f).constant("zero.in2", 0f);
        bonus.add("setBig", SetVarNode.class).option("setBig", "varName", "bonus").wire("setBig.value", "ten");
        bonus.add("setNone", SetVarNode.class).option("setNone", "varName", "bonus").wire("setNone.value", "zero");
        bonus.wire("pick.in", "call");
        bonus.wire("setBig.trigger", "pick.trueExec");
        bonus.wire("setNone.trigger", "pick.falseExec");
        // Both arms must reach the exit pin. Wiring only one is a real bug shape — the function
        // silently returns nothing on the other branch and the caller's flow stops dead — and it is
        // what this fixture did before MixedWorkloadGameTest caught the missing 6 points.
        bonus.wire("ret", "setBig.next");
        bonus.wire("ret", "setNone.next");

        // ---- middle function: score(qty, weight) = qty * weight + bonus(qty)
        score.execVariable("call", VariableKind.INPUT);
        score.execVariable("ret", VariableKind.OUTPUT);
        score.variable("qty", float.class, 0f, VariableKind.INPUT);
        score.variable("weight", float.class, 0f, VariableKind.INPUT);
        score.declare("score", float.class, 0f, VariableKind.OUTPUT);
        score.add("base", MultiplyNode.class).wire("base.in1", "qty").wire("base.in2", "weight");
        score.call("bonusOf", bonus).wire("bonusOf.tier", "qty");
        score.add("sum", AddNode.class).wire("sum.in1", "base").wire("sum.in2", "bonusOf.bonus");
        score.add("setScore", SetVarNode.class).option("setScore", "varName", "score")
                .wire("setScore.value", "sum");
        score.wire("bonusOf.call", "call");
        score.wire("setScore.trigger", "bonusOf.ret");
        score.wire("ret", "setScore.next");

        // ---- outer: the accumulating loop
        outer.variable("total", float.class, 0f, VariableKind.INPUT);
        outer.add("entry", EntryNode.class);
        outer.add("loop", ForNode.class).constant("loop.count", 4);
        outer.add("qty", AddNode.class).wire("qty.in1", "loop.index").constant("qty.in2", 1f);
        outer.add("weight", AddNode.class).constant("weight.in1", 2f).constant("weight.in2", 0f);
        outer.call("scoreOf", score).wire("scoreOf.qty", "qty").wire("scoreOf.weight", "weight");
        outer.add("accum", AddNode.class).wire("accum.in1", "total").wire("accum.in2", "scoreOf.score");
        outer.add("setTotal", SetVarNode.class).option("setTotal", "varName", "total")
                .wire("setTotal.value", "accum");
        // A second loop-carried value, so the loop has more than one accumulator to keep straight.
        outer.variable("peak", float.class, 0f, VariableKind.INPUT);
        outer.add("peakOf", MaxNode.class).wire("peakOf.in1", "peak").wire("peakOf.in2", "scoreOf.score");
        outer.add("setPeak", SetVarNode.class).option("setPeak", "varName", "peak")
                .wire("setPeak.value", "peakOf");
        outer.wire("loop.in", "entry");
        outer.wire("scoreOf.call", "loop.body");
        outer.wire("setTotal.trigger", "scoreOf.ret");
        outer.then("setTotal", "setPeak");

        // ---- post-loop stage: vector, NBT, string
        outer.readAgain("totalAfter", "total");
        outer.add("vx", MultiplyNode.class).wire("vx.in1", "totalAfter").constant("vx.in2", 0.6f);
        outer.add("vy", MultiplyNode.class).wire("vy.in1", "totalAfter").constant("vy.in2", 0.8f);
        outer.add("vec", VectorNodes.Make.class).wire("vec.x", "vx").wire("vec.y", "vy").constant("vec.z", 0f);
        outer.add("len", VectorNodes.Length.class).wire("len.in", "vec");

        outer.add("totalInt", ToIntNode.class).wire("totalInt.in", "totalAfter");
        outer.add("lenInt", ToIntNode.class).wire("lenInt.in", "len");
        outer.readAgain("peakAfter", "peak");
        outer.add("peakInt", ToIntNode.class).wire("peakInt.in", "peakAfter");
        outer.add("tag", NbtCreateNode.class);
        outer.add("putTotal", NbtSetNode.class).option("putTotal", "valueType", NbtValueType.INT)
                .wire("putTotal.tag", "tag").constant("putTotal.key", "total").wire("putTotal.value", "totalInt");
        outer.add("putLen", NbtSetNode.class).option("putLen", "valueType", NbtValueType.INT)
                .wire("putLen.tag", "putTotal").constant("putLen.key", "len").wire("putLen.value", "lenInt");
        outer.add("putPeak", NbtSetNode.class).option("putPeak", "valueType", NbtValueType.INT)
                .wire("putPeak.tag", "putLen").constant("putPeak.key", "peak").wire("putPeak.value", "peakInt");

        outer.add("label", FormatNode.class)
                .option("label", "inputs", 2).option("label", "pattern", "total=%s len=%s")
                .wire("label.arg1", "totalAfter").wire("label.arg2", "len");

        outer.add("setTag", SetVarNode.class).option("setTag", "varName", "tag").wire("setTag.value", "putPeak");
        outer.add("setLabel", SetVarNode.class).option("setLabel", "varName", "label")
                .wire("setLabel.value", "label");
        outer.wire("setTag.trigger", "loop.completed");
        outer.then("setTag", "setLabel");
        return outer;
    }
}
