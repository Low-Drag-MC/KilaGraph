package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RemapNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGBench;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import java.util.OptionalLong;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Cost of the graph executor, measured without a display so it can run in the iteration loop
 * ({@code runGameTestServer}) rather than only in the real client.
 *
 * <p><b>These tests assert correctness, never timing.</b> A wall-clock assertion is flaky on a busy
 * machine and vacuous on a fast one. Each test checks the graph really computed the expected value,
 * and logs the cost through {@link KGBench} for a human to compare across a change.</p>
 *
 * <p>Three shapes, chosen so the numbers answer real questions:</p>
 * <ol>
 *   <li>{@code lerpChain16} — 16 static-port data nodes pulled through {@code clearCache()} +
 *       {@code evaluate(out)}. The cleanest read of pull-path cost: no dynamic ports, no exec VM.</li>
 *   <li>{@code addChain16} — the same chain built from {@code AddNode}, which has dynamic ports and
 *       reads its inputs as {@code "in" + i}. The delta against (1) is what the shipped node
 *       library's own conventions cost, as opposed to the executor's.</li>
 *   <li>{@code locomotion} — ~21 nodes / 5 statements with a Branch and feedback through the
 *       variable store: a KilaGraph rebuild of the Event Graph EntityStudio benchmarks. Its
 *       {@code ms/frame @1510} figure is directly comparable to the 0.288-0.309 ms/frame that ES's
 *       own compiled program costs on the same machine (doc 18 §7.2).</li>
 * </ol>
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecutorBenchGameTest {

    private ExecutorBenchGameTest() {}

    private static final int CHAIN_LENGTH = 16;
    private static final int WARMUP = 4_000;
    private static final int TIMED = 20_000;

    // ---- 1. pure data pull, static ports ---------------------------------------------------

    /**
     * A chain of {@link LerpNode}s: {@code out[i] = lerp(out[i-1], 1, 0.5)}. Every node reads three
     * inputs — one wired, two embedded constants — so the measurement covers both halves of
     * {@code pullInput}.
     */
    @GameTest(template = "empty", timeoutTicks = 4000)
    @PrefixGameTestTemplate(false)
    public static void lerpChain16(GameTestHelper helper) {
        var g = newGraph();
        NodeModel head = null;
        NodeModel tail = null;
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            var lerp = addNode(g, LerpNode.class);
            setInputConstant(lerp, "b", 1.0f);
            setInputConstant(lerp, "t", 0.5f);
            if (tail == null) {
                setInputConstant(lerp, "a", 0.0f);
                head = lerp;
            } else {
                wire(g, lerp.getInputsById().get("a"), tail.getOutputsById().get("out"));
            }
            tail = lerp;
        }
        if (head == null) { helper.fail("chain not built"); return; }

        PortModel out = tail.getOutputsById().get("out");
        var exec = new GraphExecutor(g);

        // lerp(x, 1, 0.5) iterated 16 times from 0 → 1 - 2^-16.
        float expected = 0f;
        for (int i = 0; i < CHAIN_LENGTH; i++) expected = expected + (1f - expected) * 0.5f;

        Float first = exec.evaluate(out, Float.class);
        assertEq(helper, "lerp chain value", expected, first == null ? Float.NaN : first, 1e-6f);

        Runnable run = () -> {
            exec.clearCache();
            exec.evaluate(out, Float.class);
        };
        var checked = KGBench.measure("lerp-chain-16 (pull)", CHAIN_LENGTH, WARMUP, TIMED, run);
        exec.setGraphFrozen(true);
        var frozen = KGBench.measure("lerp-chain-16 (pull, frozen)", CHAIN_LENGTH, WARMUP, TIMED, run);

        // Guard that the loop kept doing the work. clearCache() first: without it this just
        // re-reads the memo the last benchmark iteration left behind and proves nothing.
        exec.clearCache();
        assertEq(helper, "still computes after bench", expected,
                orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        // The one number here that IS worth asserting. Wall-clock is machine-dependent, but bytes
        // per run for a fixed graph are a property of the code, and this chain is pure arithmetic:
        // it allocated 13,176 B/run before this work. The budget is loose enough to absorb whether
        // the JIT scalar-replaces the single box at the evaluate() boundary, and still catches any
        // regression to boxing per node.
        assertAllocationUnder(helper, "lerp-chain-16", frozen, 64);

        KGBench.reportDigestCost("lerp-chain-16", checked, frozen);
        KGBench.reportRow(frozen);
        KGBench.report("data pull, static ports", checked, frozen);
        helper.succeed();
    }

    // ---- 2. same shape, dynamic-port node --------------------------------------------------

    /**
     * {@link AddNode} chain: {@code out[i] = out[i-1] + 1}. Same topology as {@link #lerpChain16}
     * but the node declares its inputs dynamically from an option and reads them by a
     * <em>constructed</em> id ({@code "in" + i}), which is how much of the library is written.
     */
    @GameTest(template = "empty", timeoutTicks = 4000)
    @PrefixGameTestTemplate(false)
    public static void addChain16(GameTestHelper helper) {
        var g = newGraph();
        NodeModel tail = null;
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            var add = addNode(g, AddNode.class);
            setInputConstant(add, "in2", 1.0f);
            if (tail == null) {
                setInputConstant(add, "in1", 0.0f);
            } else {
                wire(g, add.getInputsById().get("in1"), tail.getOutputsById().get("out"));
            }
            tail = add;
        }
        if (tail == null) { helper.fail("chain not built"); return; }

        PortModel out = tail.getOutputsById().get("out");
        var exec = new GraphExecutor(g);
        assertEq(helper, "add chain value", (float) CHAIN_LENGTH, orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        Runnable run = () -> {
            exec.clearCache();
            exec.evaluate(out, Float.class);
        };
        var checked = KGBench.measure("add-chain-16 (dyn ports)", CHAIN_LENGTH, WARMUP, TIMED, run);
        exec.setGraphFrozen(true);
        var frozen = KGBench.measure("add-chain-16 (dyn, frozen)", CHAIN_LENGTH, WARMUP, TIMED, run);

        exec.clearCache();
        assertEq(helper, "still computes after bench", (float) CHAIN_LENGTH,
                orNaN(exec.evaluate(out, Float.class)), 1e-6f);
        assertAllocationUnder(helper, "add-chain-16", frozen, 64);
        KGBench.reportDigestCost("add-chain-16", checked, frozen);
        KGBench.reportRow(frozen);
        KGBench.report("data pull, dynamic ports", checked, frozen);
        helper.succeed();
    }

    // ---- 3. exec flow: the EntityStudio locomotion Event Graph, rebuilt in KilaGraph --------

    /**
     * Five statements over ~21 nodes, with a Branch and two values that feed back through the
     * variable store frame to frame (the {@code Interp To} pattern):
     *
     * <pre>
     *   speed     = lerp(speed, sqrt(vx*vx + vz*vz), 0.2)
     *   direction = clamp(yaw - facing, -180, 180)
     *   if (speed &gt; 0.1)  lean = lerp(lean, direction * 0.5, 0.3)
     *   else               lean = lerp(lean, 0, 0.3)
     * </pre>
     *
     * Each run is one frame for one entity: {@code clearCache()} then {@code executeFrom(entry)}.
     */
    @GameTest(template = "empty", timeoutTicks = 4000)
    @PrefixGameTestTemplate(false)
    public static void locomotion(GameTestHelper helper) {
        var g = newGraph();

        var vx = varGet(g, "vx", 0f);
        var vz = varGet(g, "vz", 0f);
        var yaw = varGet(g, "yaw", 0f);
        var facing = varGet(g, "facing", 0f);
        var speedIn = varGet(g, "speed", 0f);
        var leanIn = varGet(g, "lean", 0f);

        // --- statement 1: speed = lerp(speed, sqrt(vx*vx + vz*vz), 0.2)
        var vxSq = addNode(g, MultiplyNode.class);
        wire(g, vxSq.getInputsById().get("in1"), vx);
        wire(g, vxSq.getInputsById().get("in2"), vx);
        var vzSq = addNode(g, MultiplyNode.class);
        wire(g, vzSq.getInputsById().get("in1"), vz);
        wire(g, vzSq.getInputsById().get("in2"), vz);
        var sumSq = addNode(g, AddNode.class);
        wire(g, sumSq.getInputsById().get("in1"), vxSq.getOutputsById().get("out"));
        wire(g, sumSq.getInputsById().get("in2"), vzSq.getOutputsById().get("out"));
        var len = addNode(g, SqrtNode.class);
        wire(g, len.getInputsById().get("in"), sumSq.getOutputsById().get("out"));
        var speedLerp = addNode(g, LerpNode.class);
        wire(g, speedLerp.getInputsById().get("a"), speedIn);
        wire(g, speedLerp.getInputsById().get("b"), len.getOutputsById().get("out"));
        setInputConstant(speedLerp, "t", 0.2f);

        // --- statement 2: direction = clamp(yaw - facing, -180, 180)
        var diff = addNode(g, SubtractNode.class);
        wire(g, diff.getInputsById().get("a"), yaw);
        wire(g, diff.getInputsById().get("b"), facing);
        var dirClamp = addNode(g, ClampNode.class);
        wire(g, dirClamp.getInputsById().get("in"), diff.getOutputsById().get("out"));
        setInputConstant(dirClamp, "min", -180f);
        setInputConstant(dirClamp, "max", 180f);

        // --- branch condition: speed > 0.1
        var moving = addNode(g, GreaterThanNode.class);
        wire(g, moving.getInputsById().get("a"), speedLerp.getOutputsById().get("out"));
        setInputConstant(moving, "b", 0.1f);

        // --- statement 4/5: lean
        var leanTarget = addNode(g, MultiplyNode.class);
        wire(g, leanTarget.getInputsById().get("in1"), dirClamp.getOutputsById().get("out"));
        setInputConstant(leanTarget, "in2", 0.5f);
        var leanMoving = addNode(g, LerpNode.class);
        wire(g, leanMoving.getInputsById().get("a"), leanIn);
        wire(g, leanMoving.getInputsById().get("b"), leanTarget.getOutputsById().get("out"));
        setInputConstant(leanMoving, "t", 0.3f);
        var leanStill = addNode(g, LerpNode.class);
        wire(g, leanStill.getInputsById().get("a"), leanIn);
        setInputConstant(leanStill, "b", 0f);
        setInputConstant(leanStill, "t", 0.3f);

        // --- exec line: Entry → set speed → set direction → Branch → set lean (either side)
        var entry = addNode(g, EntryNode.class);
        var setSpeed = setVar(g, "speed", speedLerp.getOutputsById().get("out"));
        var setDir = setVar(g, "direction", dirClamp.getOutputsById().get("out"));
        var branch = addNode(g, BranchNode.class);
        wire(g, branch.getInputsById().get("cond"), moving.getOutputsById().get("out"));
        var setLeanMoving = setVar(g, "lean", leanMoving.getOutputsById().get("out"));
        var setLeanStill = setVar(g, "lean", leanStill.getOutputsById().get("out"));

        wire(g, setSpeed.getInputsById().get("trigger"), entry.getOutputsById().get("next"));
        wire(g, setDir.getInputsById().get("trigger"), setSpeed.getOutputsById().get("next"));
        wire(g, branch.getInputsById().get("in"), setDir.getOutputsById().get("next"));
        wire(g, setLeanMoving.getInputsById().get("trigger"), branch.getOutputsById().get("trueExec"));
        wire(g, setLeanStill.getInputsById().get("trigger"), branch.getOutputsById().get("falseExec"));

        // Live inputs, as a real frame would have: moving at 3/4 blocks per tick, turning.
        var store = new VariableStore();
        store.put("vx", 3.0f);
        store.put("vz", 4.0f);
        store.put("yaw", 30.0f);
        store.put("facing", -15.0f);
        var exec = new GraphExecutor(g, new EvaluationEnvironment(store, OptionalLong.empty()));

        // Converge, then assert the graph really computed the locomotion values.
        for (int i = 0; i < 200; i++) {
            exec.clearCache();
            exec.executeFrom(entry);
        }
        assertEq(helper, "speed converges to |v| = 5", 5.0f, num(store.get("speed")), 1e-3f);
        assertEq(helper, "direction = clamp(30 - -15)", 45.0f, num(store.get("direction")), 1e-3f);
        assertEq(helper, "lean converges to direction * 0.5", 22.5f, num(store.get("lean")), 1e-3f);

        Runnable run = () -> {
            exec.clearCache();
            exec.executeFrom(entry);
        };
        var checked = KGBench.measure("locomotion (5 stmts, exec)", 21, WARMUP, TIMED, run);
        exec.setGraphFrozen(true);
        var frozen = KGBench.measure("locomotion (exec, frozen)", 21, WARMUP, TIMED, run);

        assertEq(helper, "still converged after bench", 5.0f, num(store.get("speed")), 1e-3f);
        // Higher budget than the pure-data chains: SetVar necessarily boxes each value it puts into
        // the VariableStore, which is a Map<String,Object>. Was 10,936 B/run before this work.
        assertAllocationUnder(helper, "locomotion", frozen, 256);
        KGBench.reportDigestCost("locomotion", checked, frozen);
        KGBench.reportRow(frozen);
        KGBench.report("exec flow, EntityStudio locomotion analogue", checked, frozen);
        helper.succeed();
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** A graph variable plus its "get" node; returns the output port to wire from. */
    private static PortModel varGet(BlueprintGraph g, String name, float defaultValue) {
        var decl = (VariableDeclarationModelBase)
                g.graphModel.createVariable(name, float.class, defaultValue, VariableKind.INPUT);
        var node = g.graphModel.createVariableNode(decl, new Vector2f(), null, null);
        return node.getOutputPort();
    }

    /** A {@link SetVarNode} writing {@code source} into the store under {@code name}. */
    private static NodeModel setVar(BlueprintGraph g, String name, PortModel source) {
        var set = addNode(g, SetVarNode.class);
        setOption(set, "varName", name);
        wire(g, set.getInputsById().get("value"), source);
        return set;
    }

    /**
     * Assert a per-run allocation budget. Skipped when the JVM exposes no per-thread counter
     * ({@code -1}); a budget rather than an exact figure because whether the JIT scalar-replaces
     * the box at the {@code evaluate()} boundary varies between runs of the same code.
     */
    private static void assertAllocationUnder(GameTestHelper helper, String label,
                                              KGBench.Result r, double budgetBytesPerRun) {
        double actual = r.bytesPerRun();
        if (actual < 0) return;
        if (actual > budgetBytesPerRun) {
            helper.fail(label + ": allocated " + actual + " B/run, budget " + budgetBytesPerRun
                    + " (total " + r.totalAllocBytes() + " over " + r.allocRuns() + " runs)");
        }
    }

    private static float orNaN(Float f) {
        return f == null ? Float.NaN : f;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }

    // ---- 4. where the remaining cost actually is --------------------------------------------

    /**
     * A shape sweep, so stage 2 optimises what is actually expensive rather than what looks
     * expensive. Each row chains N copies of one node and reports cost per node; the differences
     * between rows isolate one variable at a time:
     *
     * <ul>
     *   <li>{@code AbsNode} — 1 wired input, 1 output, no options, static ports. The floor.</li>
     *   <li>{@code LerpNode} — 3 inputs (1 wired + 2 constants), otherwise identical to Abs, so
     *       {@code Lerp − Abs} is the cost of two extra constant reads.</li>
     *   <li>{@code AddNode} — 2 inputs but dynamic ports, an option read per evaluate, and input
     *       ids built with {@code "in" + i}; {@code Add − Lerp} is what those conventions cost.</li>
     * </ul>
     *
     * Sweeping N separates per-node cost (the slope) from fixed per-run cost (the intercept).
     */
    @GameTest(template = "empty", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void costBreakdown(GameTestHelper helper) {
        int[] lengths = {4, 16, 64};
        for (int n : lengths) {
            measureChain(helper, "abs   (1 in, static)", n, AbsNode.class, "in", node -> {});
            measureChain(helper, "lerp  (3 in, static)", n, LerpNode.class, "a", node -> {
                setInputConstant(node, "b", 1.0f);
                setInputConstant(node, "t", 0.5f);
            });
            measureChain(helper, "add   (2 in, dynamic+option)", n, AddNode.class, "in1", node ->
                    setInputConstant(node, "in2", 0.0f));
            // 1 wired + 4 constants. With abs (0) and lerp (2) this is a three-point line, so the
            // slope is the cost of one embedded-constant read rather than a guess from one pair.
            measureChain(helper, "remap (1 wired + 4 const)", n, RemapNode.class, "in", node -> {
                setInputConstant(node, "fromMin", 0.0f);
                setInputConstant(node, "fromMax", 1.0f);
                setInputConstant(node, "toMin", 0.0f);
                setInputConstant(node, "toMax", 1.0f);
            });
        }
        helper.succeed();
    }

    /** Build an N-long chain of {@code cls} and report its per-node cost. */
    private static void measureChain(GameTestHelper helper, String label, int n,
                                     Class<? extends Node> cls,
                                     String chainInput, Consumer<NodeModel> constants) {
        var g = newGraph();
        NodeModel tail = null;
        for (int i = 0; i < n; i++) {
            var node = addNode(g, cls);
            constants.accept(node);
            if (tail == null) setInputConstant(node, chainInput, 1.0f);
            else wire(g, node.getInputsById().get(chainInput), tail.getOutputsById().get("out"));
            tail = node;
        }
        if (tail == null) { helper.fail("empty chain"); return; }
        PortModel out = tail.getOutputsById().get("out");
        var exec = new GraphExecutor(g);
        exec.setGraphFrozen(true);   // isolate node cost from the staleness digest
        if (exec.evaluate(out, Float.class) == null) { helper.fail(label + ": produced no value"); return; }

        KGBench.measure(String.format("%-28s n=%-3d", label, n), n, 2_000, 8_000, () -> {
            exec.clearCache();
            exec.evaluate(out, Float.class);
        });
    }
}
