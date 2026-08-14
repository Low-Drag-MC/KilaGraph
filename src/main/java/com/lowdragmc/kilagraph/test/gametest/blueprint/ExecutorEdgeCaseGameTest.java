package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitAndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToStringNode;
import com.lowdragmc.kilagraph.blueprint.nodes.list.ListGetNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.PowNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RandomNode;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Edge cases of the two-lane value table that ordinary node tests never reach.
 *
 * <p>The existing suite establishes that each node computes the right thing. What it does not touch
 * is what happens <em>between</em> nodes now that numbers travel unboxed: the values that survive a
 * raw-bits round trip badly if you get it wrong (NaN, -0.0, infinities), reads that cross between
 * the numeric and object lanes, and anything involving an executor that lives longer than one
 * evaluation — which is every test in the suite, because they all build a graph, run it once and
 * drop the executor.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecutorEdgeCaseGameTest {

    private ExecutorEdgeCaseGameTest() {}

    // ---- special float values through the numeric lane --------------------------------------

    /**
     * NaN, the infinities and -0.0 must survive a round trip through the unboxed lane.
     *
     * <p>These are exactly the values where storing "the bits" and storing "the number" diverge:
     * {@code floatToIntBits} collapses every NaN to one canonical pattern while
     * {@code floatToRawIntBits} does not, and -0.0 is only distinguishable from 0.0 by its sign
     * bit. {@code Pow} is unguarded, so it is the way to produce them from a real node.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void specialFloatsSurviveTheNumericLane(GameTestHelper helper) {
        // pow(0, -1) = +Inf ; pow(-1, 0.5) = NaN -- and note WHICH NaN: Math.pow returns a
        // *negative* NaN here (0xffc00000), not the canonical Float.NaN (0x7fc00000). Comparing
        // against Float.NaN would be asserting the wrong thing; the property that matters is that
        // whatever the node produced comes back bit-identical. Storing floatToIntBits instead of
        // floatToRawIntBits would canonicalise that sign bit away and this is what would catch it.
        checkPow(helper, 0f, -1f, "+Inf");
        checkPow(helper, -1f, 0.5f, "NaN");

        // 1 * 0 * -1 = -0.0, which must not come back as +0.0
        var g = newGraph();
        var mul = addNode(g, MultiplyNode.class);
        setInputConstant(mul, "in1", 0.0f);
        setInputConstant(mul, "in2", -1.0f);
        Float negZero = new GraphExecutor(g).evaluate(mul.getOutputsById().get("out"), Float.class);
        if (negZero == null || Float.floatToRawIntBits(negZero) != Float.floatToRawIntBits(-0.0f)) {
            helper.fail("-0.0 lost its sign: got " + negZero);
            return;
        }
        helper.succeed();
    }

    /** Expected value computed exactly as the node computes it, then compared bit for bit. */
    private static void checkPow(GameTestHelper helper, float base, float exp, String label) {
        float expected = (float) Math.pow(base, exp);
        var g = newGraph();
        var pow = addNode(g, PowNode.class);
        setInputConstant(pow, "base", base);
        setInputConstant(pow, "exp", exp);
        Float got = new GraphExecutor(g).evaluate(pow.getOutputsById().get("out"), Float.class);
        if (got == null || Float.floatToRawIntBits(got) != Float.floatToRawIntBits(expected)) {
            helper.fail(label + ": expected bits " + Integer.toHexString(Float.floatToRawIntBits(expected))
                    + ", got " + (got == null ? "null"
                    : Integer.toHexString(Float.floatToRawIntBits(got))));
        }
    }

    // ---- crossing between the lanes ---------------------------------------------------------

    /**
     * A number written to the numeric lane and read as an {@code Object} must come back as the same
     * wrapper the old single-lane executor held — {@code Float}, not {@code Double} — because
     * {@code toString} is observable through {@code ToString}, and {@code Objects.equals} through
     * {@code Equals}. A float must render "2.5", an int "3".
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void numericLaneRendersAsTheRightType(GameTestHelper helper) {
        var g = newGraph();

        var add = addNode(g, AddNode.class);          // float producer
        setInputConstant(add, "in1", 1.0f);
        setInputConstant(add, "in2", 1.5f);
        var floatStr = addNode(g, ToStringNode.class);
        wire(g, floatStr.getInputsById().get("in"), add.getOutputsById().get("out"));

        var and = addNode(g, BitAndNode.class);       // int producer
        setInputConstant(and, "a", 7);
        setInputConstant(and, "b", 3);
        var intStr = addNode(g, ToStringNode.class);
        wire(g, intStr.getInputsById().get("in"), and.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        assertEq(helper, "float renders as a float", "2.5",
                exec.evaluate(floatStr.getOutputsById().get("out"), String.class));
        assertEq(helper, "int renders as an int", "3",
                exec.evaluate(intStr.getOutputsById().get("out"), String.class));
        helper.succeed();
    }

    /** An int-producing node feeding a float input, and a float-producing node feeding an int input. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void valuesCrossBetweenIntAndFloatPorts(GameTestHelper helper) {
        var g = newGraph();

        // int -> float port
        var and = addNode(g, BitAndNode.class);
        setInputConstant(and, "a", 6);
        setInputConstant(and, "b", 5);                // 6 & 5 = 4
        var abs = addNode(g, AbsNode.class);
        wire(g, abs.getInputsById().get("in"), and.getOutputsById().get("out"));

        // float -> int port (2.9 must truncate toward zero, like Number.intValue())
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 2.9f);
        setInputConstant(add, "in2", 0.0f);
        var and2 = addNode(g, BitAndNode.class);
        wire(g, and2.getInputsById().get("a"), add.getOutputsById().get("out"));
        setInputConstant(and2, "b", -1);              // identity mask

        var exec = new GraphExecutor(g);
        assertEq(helper, "int read through a float port", 4.0f,
                orNaN(exec.evaluate(abs.getOutputsById().get("out"), Float.class)), 1e-6f);
        assertEq(helper, "float truncates into an int port", Integer.valueOf(2),
                exec.evaluate(and2.getOutputsById().get("out"), Integer.class));
        helper.succeed();
    }

    // ---- memoisation ------------------------------------------------------------------------

    /**
     * A node reached through two different inputs must still evaluate once.
     *
     * <p>{@code Random} makes that observable: it draws from the executor's shared RNG, so a second
     * evaluation would return a different number. Its own javadoc relies on the port cache for
     * "same evaluator, same roll", which makes it the right probe for the slot table's memoisation.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aDiamondEvaluatesTheSharedNodeOnce(GameTestHelper helper) {
        var g = newGraph();
        var rand = addNode(g, RandomNode.class);
        setInputConstant(rand, "min", 1.0f);
        setInputConstant(rand, "max", 2.0f);
        var add = addNode(g, AddNode.class);
        wire(g, add.getInputsById().get("in1"), rand.getOutputsById().get("out"));
        wire(g, add.getInputsById().get("in2"), rand.getOutputsById().get("out"));

        var exec = new GraphExecutor(g, EvaluationEnvironment.seeded(1234L));
        Float roll = exec.evaluate(rand.getOutputsById().get("out"), Float.class);
        Float sum = exec.evaluate(add.getOutputsById().get("out"), Float.class);
        if (roll == null || sum == null) { helper.fail("null result"); return; }
        assertEq(helper, "both inputs saw the same roll", roll * 2f, sum, 1e-6f);

        // ...and a new generation really does re-roll, so the assertion above is not vacuous.
        exec.clearCache();
        Float reroll = exec.evaluate(rand.getOutputsById().get("out"), Float.class);
        assertTrue(helper, "clearCache re-rolls", reroll != null && !reroll.equals(roll));
        helper.succeed();
    }

    // ---- long-lived executors ---------------------------------------------------------------

    /**
     * Editing a constant must be visible on the next generation.
     *
     * <p>The prepared graph caches the {@code Constant} <em>reference</em> per input so a read is
     * not a name-keyed map lookup. Caching the <em>value</em> instead would pass every other test in
     * the suite and break exactly this.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anEditedConstantIsVisibleAfterClearCache(GameTestHelper helper) {
        var g = newGraph();
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 1.0f);
        setInputConstant(add, "in2", 2.0f);
        PortModel out = add.getOutputsById().get("out");

        var exec = new GraphExecutor(g);
        assertEq(helper, "before edit", 3.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        setInputConstant(add, "in2", 40.0f);
        exec.clearCache();
        assertEq(helper, "after edit", 41.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);
        helper.succeed();
    }

    /**
     * Two executors over one graph share a single prepared form. Neither may be left holding a
     * stale view after the other triggers a rebuild.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void twoExecutorsOverOneGraphBothSeeAnEdit(GameTestHelper helper) {
        var g = newGraph();
        var five = addNode(g, AddNode.class);
        setInputConstant(five, "in1", 5.0f);
        setInputConstant(five, "in2", 0.0f);
        var nine = addNode(g, AddNode.class);
        setInputConstant(nine, "in1", 9.0f);
        setInputConstant(nine, "in2", 0.0f);
        var sink = addNode(g, AddNode.class);
        setInputConstant(sink, "in2", 100.0f);
        wire(g, sink.getInputsById().get("in1"), five.getOutputsById().get("out"));
        PortModel out = sink.getOutputsById().get("out");

        var a = new GraphExecutor(g);
        var b = new GraphExecutor(g);
        assertEq(helper, "A before", 105.0f, orNaN(a.evaluate(out, Float.class)), 1e-6f);
        assertEq(helper, "B before", 105.0f, orNaN(b.evaluate(out, Float.class)), 1e-6f);

        g.graphModel.deleteWires(java.util.List.copyOf(
                sink.getInputsById().get("in1").getConnectedWires()));
        wire(g, sink.getInputsById().get("in1"), nine.getOutputsById().get("out"));

        // A rebuilds first; B must not keep serving the old layout out of its own cached reference.
        a.clearCache();
        assertEq(helper, "A after", 109.0f, orNaN(a.evaluate(out, Float.class)), 1e-6f);
        b.clearCache();
        assertEq(helper, "B after", 109.0f, orNaN(b.evaluate(out, Float.class)), 1e-6f);
        helper.succeed();
    }

    /** A frozen executor must agree with an unfrozen one on a graph nobody edited. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void frozenAgreesWithUnfrozen(GameTestHelper helper) {
        var g = newGraph();
        NodeModel tail = null;
        for (int i = 0; i < 8; i++) {
            var add = addNode(g, AddNode.class);
            setInputConstant(add, "in2", 1.5f);
            if (tail == null) setInputConstant(add, "in1", 0.5f);
            else wire(g, add.getInputsById().get("in1"), tail.getOutputsById().get("out"));
            tail = add;
        }
        if (tail == null) { helper.fail("chain not built"); return; }
        PortModel out = tail.getOutputsById().get("out");

        var checked = new GraphExecutor(g);
        var frozen = new GraphExecutor(g);
        frozen.setGraphFrozen(true);
        for (int run = 0; run < 3; run++) {
            checked.clearCache();
            frozen.clearCache();
            assertEq(helper, "run " + run, orNaN(checked.evaluate(out, Float.class)),
                    orNaN(frozen.evaluate(out, Float.class)), 1e-6f);
        }
        assertEq(helper, "value is right at all", 0.5f + 8 * 1.5f,
                orNaN(checked.evaluate(out, Float.class)), 1e-6f);
        helper.succeed();
    }

    // ---- growth and failure -----------------------------------------------------------------

    /**
     * A chain deep enough to grow every parallel array at least once: the slot/stamp/kind/written
     * tables, the cycle-detection stack, and the depth-indexed context pools. They are grown in
     * separate places, so growing one and forgetting another is the shape of bug this catches.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    @PrefixGameTestTemplate(false)
    public static void aDeepChainGrowsEveryTable(GameTestHelper helper) {
        final int depth = 300;
        var g = newGraph();
        NodeModel tail = null;
        for (int i = 0; i < depth; i++) {
            var add = addNode(g, AddNode.class);
            setInputConstant(add, "in2", 1.0f);
            if (tail == null) setInputConstant(add, "in1", 0.0f);
            else wire(g, add.getInputsById().get("in1"), tail.getOutputsById().get("out"));
            tail = add;
        }
        if (tail == null) { helper.fail("chain not built"); return; }
        PortModel out = tail.getOutputsById().get("out");

        var exec = new GraphExecutor(g);
        for (int run = 0; run < 3; run++) {
            exec.clearCache();
            assertEq(helper, "deep chain run " + run, (float) depth,
                    orNaN(exec.evaluate(out, Float.class)), 1e-3f);
        }
        helper.succeed();
    }

    /**
     * A node that throws part-way through a pull must leave the executor usable.
     *
     * <p>The pull path now keeps depth counters for the pooled contexts and a flag array for cycle
     * detection. If an exception skipped either unwind, the damage would not show at the throw — it
     * would show on the next unrelated evaluation, which is what this checks.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void anExecutorSurvivesANodeThatThrows(GameTestHelper helper) {
        var g = newGraph();
        var get = addNode(g, ListGetNode.class);      // list defaults to empty
        setInputConstant(get, "index", 5);            // out of bounds -> throws
        var ok = addNode(g, AddNode.class);
        setInputConstant(ok, "in1", 20.0f);
        setInputConstant(ok, "in2", 22.0f);

        var exec = new GraphExecutor(g);
        for (int i = 0; i < 3; i++) {
            try {
                exec.evaluate(get.getOutputsById().get("value"), Object.class);
                helper.fail("expected the node to throw");
                return;
            } catch (IndexOutOfBoundsException expected) {
                // the point is what happens next
            }
            exec.clearCache();
            assertEq(helper, "still usable after throw " + i, 42.0f,
                    orNaN(exec.evaluate(ok.getOutputsById().get("out"), Float.class)), 1e-6f);
        }
        helper.succeed();
    }


    // ---- the exec-side staging path -----------------------------------------------------------

    /**
     * An exec node that also publishes data outputs, which nothing in the shipped library does.
     *
     * <p>Deliberately not {@code @NodeAttribute}-annotated: {@code addNode} instantiates the class
     * directly, while the item library is driven by {@code getSupportNodes()}, so this stays out of
     * the node palette.</p>
     */
    public static final class StagingProbeNode extends AnnotatedNode {
        @ExecInputPort public ExecutionFlow in;
        @OutputPort public float staged;
        @OutputPort public float untouched;

        @Override
        public void execute(ExecContext ctx) {
            ctx.setOutput("staged", 11.0f);      // primitive overload -> numeric lane
            // "untouched" is deliberately never staged
        }

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("staged", -1.0f);      // marker: only visible if exec did NOT publish
            ctx.setOutput("untouched", -2.0f);   // marker: proves evaluate() ran
        }
    }

    /**
     * The exec flush leaves an unset output alone; the data flush publishes it as null. That
     * asymmetry is inherited from the pre-rewrite executor ({@code if (v != null)} on the exec side,
     * an unconditional put on the data side) and nothing else covers it, because no shipped exec
     * node writes a data output at all.
     *
     * <p>Observable because a slot the exec flush did not stamp is simply not computed yet, so
     * pulling it falls through to {@code evaluate()} — which writes a different marker. Order
     * matters and is part of the semantics: once {@code evaluate()} runs it publishes <em>all</em>
     * outputs, so the exec-staged one has to be read first.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void execStagedOutputsPublishAndUnsetOnesFallThrough(GameTestHelper helper) {
        var g = newGraph();
        var entry = addNode(g, EntryNode.class);
        var probe = addNode(g, StagingProbeNode.class);
        wire(g, probe.getInputsById().get("in"), entry.getOutputsById().get("next"));

        var exec = new GraphExecutor(g);
        exec.executeFrom(entry);

        // read the staged one first: exec published it, so evaluate() must not have run
        assertEq(helper, "exec-staged output published", 11.0f,
                orNaN(exec.evaluate(probe.getOutputsById().get("staged"), Float.class)), 1e-6f);
        // the unset one was left alone, so pulling it now runs evaluate()
        assertEq(helper, "unset output falls through to evaluate", -2.0f,
                orNaN(exec.evaluate(probe.getOutputsById().get("untouched"), Float.class)), 1e-6f);
        helper.succeed();
    }

    private static float orNaN(Float f) {
        return f == null ? Float.NaN : f;
    }
}
