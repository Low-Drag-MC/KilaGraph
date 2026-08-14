package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.bitwise.BitAndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.flow.SelectNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.PowNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.graph.exec.CycleException;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.PreparedGraph;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleInputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ISingleOutputPortNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalEntryModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.WirePortalExitModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.newGraph;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/**
 * Covers the two things the executor's resolved-graph layer does that nothing else tests.
 *
 * <p>Both were blind spots rather than new behaviour. Cycle detection is exercised only by
 * {@code GraphExecutorTest}, which {@code build.gradle} excludes from the JUnit run because it needs
 * the FML mod environment — so the one path that throws {@code CycleException} had no automated
 * cover at all. Staleness never had any: the executor used to re-read the live model on every step,
 * so there was nothing to go stale.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class PreparedGraphGameTest {

    private PreparedGraphGameTest() {}

    // ---- cycle detection -------------------------------------------------------------------

    /** Two nodes each feeding the other: pulling either one must raise {@link CycleException}. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cycleIsDetected(GameTestHelper helper) {
        var g = newGraph();
        var a = addNode(g, LerpNode.class);
        var b = addNode(g, LerpNode.class);
        setInputConstant(a, "b", 1.0f);
        setInputConstant(a, "t", 0.5f);
        setInputConstant(b, "b", 1.0f);
        setInputConstant(b, "t", 0.5f);
        wire(g, a.getInputsById().get("a"), b.getOutputsById().get("out"));
        wire(g, b.getInputsById().get("a"), a.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        try {
            exec.evaluate(a.getOutputsById().get("out"), Float.class);
            helper.fail("expected CycleException, got a value");
            return;
        } catch (CycleException expected) {
            // The message names the nodes on the path, which is the point of carrying it.
            assertTrue(helper, "cycle names a node on the path",
                    expected.getMessage().contains(a.getUid().toString()));
        }

        // Re-throwing on the SAME executor, then evaluating something acyclic on it. This pins
        // that a repeated cycle stays a CycleException and does not degrade into anything else.
        // It does NOT prove the visiting stack unwinds — see
        // aLeakedVisitingStackWouldResurfaceOnAnUnrelatedBranch for that; every node still on the
        // stack when a cycle throws is by definition a node that leads to the cycle, so a spurious
        // exception from a leak is indistinguishable from the real one here.
        var c = addNode(g, LerpNode.class);
        setInputConstant(c, "a", 0.0f);
        setInputConstant(c, "b", 1.0f);
        setInputConstant(c, "t", 0.25f);
        PortModel cOut = c.getOutputsById().get("out");
        for (int i = 0; i < 50; i++) {
            try {
                exec.clearCache();
                exec.evaluate(a.getOutputsById().get("out"), Float.class);
                helper.fail("expected CycleException on attempt " + i);
                return;
            } catch (CycleException expected) {
                // and now the same executor must still be able to evaluate something acyclic
            }
            exec.clearCache();
            assertEq(helper, "usable after cycle #" + i, 0.25f,
                    orNaN(exec.evaluate(cOut, Float.class)), 1e-6f);
        }
        helper.succeed();
    }


    /**
     * The visiting stack must unwind when a cycle throws, observed through a node that reaches the
     * cycle only <em>conditionally</em>.
     *
     * <p>This is the shape that makes a leak visible at all. {@code Select} pulls only the branch
     * it takes, so it can be made to walk into a cycle once and then never again. Flipping its
     * {@code cond} constant is not a topology change, so the prepared graph is not rebuilt and the
     * executor keeps the very arrays the failed pull touched. If the cycle flag for {@code Select}
     * were left set, the second evaluation would raise a {@code CycleException} down a branch that
     * has no cycle in it.</p>
     *
     * <p>Any test that instead re-pulls the cyclic node itself cannot see this: a node on the stack
     * when the cycle throws is one that leads to the cycle, so the spurious exception and the real
     * one look the same.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aLeakedVisitingStackWouldResurfaceOnAnUnrelatedBranch(GameTestHelper helper) {
        var g = newGraph();

        // a <-> b : a cycle, reachable only through Select's ifTrue branch
        var a = addNode(g, LerpNode.class);
        var b = addNode(g, LerpNode.class);
        setInputConstant(a, "b", 1.0f);
        setInputConstant(a, "t", 0.5f);
        setInputConstant(b, "b", 1.0f);
        setInputConstant(b, "t", 0.5f);
        wire(g, a.getInputsById().get("a"), b.getOutputsById().get("out"));
        wire(g, b.getInputsById().get("a"), a.getOutputsById().get("out"));

        var safe = addNode(g, AddNode.class);
        setInputConstant(safe, "in1", 3.0f);
        setInputConstant(safe, "in2", 4.0f);

        var sel = addNode(g, SelectNode.class);
        setInputConstant(sel, "cond", true);
        wire(g, sel.getInputsById().get("ifTrue"), a.getOutputsById().get("out"));
        wire(g, sel.getInputsById().get("ifFalse"), safe.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        PortModel out = sel.getOutputsById().get("out");
        try {
            exec.evaluate(out, Object.class);
            helper.fail("expected CycleException down the ifTrue branch");
            return;
        } catch (CycleException expected) {
            // now take the other branch on the same executor
        }

        setInputConstant(sel, "cond", false);   // a value edit, not a topology change
        exec.clearCache();
        Object value = exec.evaluate(out, Object.class);
        assertEq(helper, "acyclic branch still evaluable after a cycle threw", Float.valueOf(7.0f), value);
        helper.succeed();
    }

    /** A node wired to itself is the degenerate cycle and must be caught the same way. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void selfCycleIsDetected(GameTestHelper helper) {
        var g = newGraph();
        var a = addNode(g, LerpNode.class);
        setInputConstant(a, "b", 1.0f);
        setInputConstant(a, "t", 0.5f);
        wire(g, a.getInputsById().get("a"), a.getOutputsById().get("out"));

        try {
            new GraphExecutor(g).evaluate(a.getOutputsById().get("out"), Float.class);
            helper.fail("expected CycleException on a self-wired node");
            return;
        } catch (CycleException expected) {
            helper.succeed();
        }
    }

    // ---- staleness -------------------------------------------------------------------------

    /**
     * Growing a node's port set after the executor has already run it must be picked up.
     *
     * <p>This is the case a cheap node-count/wire-count guard cannot see: changing {@code Add.inputs}
     * from 2 to 3 adds a port without adding a node or a wire, and LDLib2 re-uses the existing
     * {@code PortModel} objects across the redefine, so even their identities are unchanged. Only a
     * digest that includes per-node port counts notices.</p>
     *
     * <p>Note what each assertion is worth. The <em>value</em> assertion passes even with the
     * freshness check disabled, because an id the snapshot doesn't know falls back to the live model
     * — that fallback is deliberate, and this pins it. The <em>rebuild</em> assertion is what
     * actually pins the detection: it fails if the digest stops noticing a port-set change.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void portSetGrowthIsNoticed(GameTestHelper helper) {
        var g = newGraph();
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in1", 1.0f);
        setInputConstant(add, "in2", 2.0f);

        var exec = new GraphExecutor(g);
        var out = add.getOutputsById().get("out");
        assertEq(helper, "2 inputs", 3.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        // Same executor, now a third input. setOption redefines the node.
        long buildsBefore = PreparedGraph.buildCount();
        setOption(add, "inputs", 3);
        setInputConstant(add, "in3", 4.0f);
        exec.clearCache();
        assertEq(helper, "3 inputs after redefine", 7.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);
        assertTrue(helper, "redefine forced a re-prepare", PreparedGraph.buildCount() > buildsBefore);
        helper.succeed();
    }

    /**
     * Rewiring an input after the executor has run must be picked up: the resolved form caches which
     * slot an input pulls from, and that is exactly what a new wire changes.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void rewiringIsNoticed(GameTestHelper helper) {
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

        var exec = new GraphExecutor(g);
        var out = sink.getOutputsById().get("out");
        assertEq(helper, "wired to 5", 105.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        // Repoint the same input at a different source, on the same live executor.
        var wires = sink.getInputsById().get("in1").getConnectedWires();
        g.graphModel.deleteWires(java.util.List.copyOf(wires));
        wire(g, sink.getInputsById().get("in1"), nine.getOutputsById().get("out"));

        exec.clearCache();
        assertEq(helper, "rewired to 9", 109.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);
        helper.succeed();
    }

    /**
     * Discovering a new node must not launder an edit that happened before it.
     *
     * <p>Late-discovered nodes are admitted into the prepared graph on demand, which changes the
     * node set and so forces the structural digest to be re-taken. Doing that unconditionally
     * absorbs whatever else had changed since the graph was prepared and marks the instance fresh
     * forever — with a layout that still describes the old wiring. Because prepared graphs are
     * cached and shared, that poisons every later executor over the same graph too.</p>
     *
     * <p>{@code invalidateNode} is the reachable trigger: it is public and resolves the node it is
     * given without a freshness check first.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void admittingANodeDoesNotLaunderAnEdit(GameTestHelper helper) {
        var g = newGraph();
        var five = addNode(g, AddNode.class);
        setInputConstant(five, "in1", 5.0f);
        setInputConstant(five, "in2", 0.0f);
        var sink = addNode(g, AddNode.class);
        setInputConstant(sink, "in2", 100.0f);
        wire(g, sink.getInputsById().get("in1"), five.getOutputsById().get("out"));

        var exec = new GraphExecutor(g);
        var out = sink.getOutputsById().get("out");
        assertEq(helper, "wired to 5", 105.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);

        // Edit the graph, with no run in between — so nothing has re-checked freshness yet.
        g.graphModel.deleteWires(java.util.List.copyOf(sink.getInputsById().get("in1").getConnectedWires()));
        var nine = addNode(g, AddNode.class);
        setInputConstant(nine, "in1", 9.0f);
        setInputConstant(nine, "in2", 0.0f);
        wire(g, sink.getInputsById().get("in1"), nine.getOutputsById().get("out"));

        // This resolves a node the prepared graph has never seen, admitting it.
        exec.invalidateNode(nine);

        exec.clearCache();
        assertEq(helper, "edit survives the admit", 109.0f, orNaN(exec.evaluate(out, Float.class)), 1e-6f);
        helper.succeed();
    }


    // ---- value semantics across the two lanes ----------------------------------------------

    /**
     * A float too large for an {@code int}, read through an {@code int} input.
     *
     * <p>The old executor coerced with {@code Number.intValue()}, which saturates: 1e40f becomes
     * {@code Integer.MAX_VALUE}. Narrowing through a {@code long} instead saturates at
     * {@code Long.MAX_VALUE} first and then keeps the low 32 bits, giving {@code -1}. The numeric
     * lane has to reproduce the coercion the wrapper types performed, not merely "a number".</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void oversizedFloatNarrowsLikeNumberIntValue(GameTestHelper helper) {
        var g = newGraph();
        var pow = addNode(g, PowNode.class);
        setInputConstant(pow, "base", 10.0f);
        setInputConstant(pow, "exp", 40.0f);          // 1e40, far past Integer.MAX_VALUE
        var and = addNode(g, BitAndNode.class);
        wire(g, and.getInputsById().get("a"), pow.getOutputsById().get("out"));
        setInputConstant(and, "b", -1);               // identity mask

        Integer got = new GraphExecutor(g).evaluate(and.getOutputsById().get("out"), Integer.class);
        assertEq(helper, "saturates like Number.intValue()", Integer.valueOf(Integer.MAX_VALUE), got);
        helper.succeed();
    }

    /** The same value read as a float must still be the float, not something narrowed. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void numericLaneRoundTripsThroughTheObjectLane(GameTestHelper helper) {
        var g = newGraph();
        var pow = addNode(g, PowNode.class);
        setInputConstant(pow, "base", 2.0f);
        setInputConstant(pow, "exp", 0.5f);           // sqrt(2), not representable exactly

        // Pulled as Object at the boundary: must come back as a Float, because a node doing
        // Objects.equals against another Float would otherwise silently stop matching.
        Object raw = new GraphExecutor(g).evaluate(pow.getOutputsById().get("out"), null);
        assertTrue(helper, "boxed back as Float, got " + (raw == null ? "null" : raw.getClass()),
                raw instanceof Float);
        assertEq(helper, "value preserved", Float.valueOf((float) Math.sqrt(2)), raw);
        helper.succeed();
    }


    /**
     * A {@code long} past 2^53 read through a {@code float} input must round once, not twice.
     *
     * <p>{@code getFloat} used to be {@code (float) pullDouble(...)}. A long that big is not exactly
     * representable as a double, so that rounds to double and then to float, and the two roundings
     * can land a ULP away from the single rounding {@code Number.floatValue()} performs — which is
     * what the pre-change {@code getInput(id, Float.class, def)} did.</p>
     *
     * <p>The value below is one such long. Random longs essentially never hit this (200M fuzzed
     * without a single divergence, in two independent attempts) because it needs the discarded bits
     * to sit exactly on a halfway point — so it is a case that has to be reasoned about, not
     * searched for.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void largeLongRoundsOnceIntoFloat(GameTestHelper helper) {
        final long big = 9007199791611905L;

        var g = newGraph();
        var decl = (VariableDeclarationModelBase)
                g.graphModel.createVariable("big", long.class, 0L, VariableKind.INPUT);
        var getNode = g.graphModel.createVariableNode(decl, new Vector2f(), null, null);

        var abs = addNode(g, AbsNode.class);
        wire(g, abs.getInputsById().get("in"), getNode.getOutputPort());

        var exec = new GraphExecutor(g, EvaluationEnvironment.with(java.util.Map.of("big", big)));
        Float got = exec.evaluate(abs.getOutputsById().get("out"), Float.class);

        float expected = Math.abs(Long.valueOf(big).floatValue());
        if (got == null || Float.floatToRawIntBits(got) != Float.floatToRawIntBits(expected)) {
            helper.fail("expected " + expected + " (bits " + Float.floatToRawIntBits(expected)
                    + "), got " + got + " (bits " + (got == null ? "null" : Float.floatToRawIntBits(got)) + ")");
            return;
        }
        helper.succeed();
    }


    /**
     * A cycle routed through a wire portal must still raise {@link CycleException}.
     *
     * <p>Whether a graph can cycle is now decided once at prepare time, by walking each input's
     * resolved source. A wire portal is invisible to that walk: an exit portal resolves its value by
     * pulling the <em>entry</em> portal's input, and the exit node has no inputs of its own — the
     * entry↔exit gap deliberately has no wire. So the DFS sees an acyclic graph and, without the
     * bail-out, the executor would skip its runtime cycle check and recurse until the stack died.
     *
     * <p>The failure mode is the point: not a slow path, but {@code StackOverflowError} in place of
     * a catchable exception.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cycleThroughAWirePortalIsStillDetected(GameTestHelper helper) {
        var g = newGraph();
        var decl = g.graphModel.createGraphPortalDeclaration("v", null, null);
        var entry = g.graphModel.createWirePortalNode(WirePortalEntryModel.class, decl,
                TypeHandles.FLOAT, new Vector2f(), null, null, null, null);
        var exit = g.graphModel.createWirePortalNode(WirePortalExitModel.class, decl,
                TypeHandles.FLOAT, new Vector2f(), null, null, null, null);

        // exit.out -> add.in1 -> ... -> entry.in, closing the loop through the portal bridge.
        var add = addNode(g, AddNode.class);
        setInputConstant(add, "in2", 1.0f);
        g.graphModel.createWire(add.getInputsById().get("in1"),
                ((ISingleOutputPortNodeModel) exit).getOutputPort());
        g.graphModel.createWire(((ISingleInputPortNodeModel) entry).getInputPort(),
                add.getOutputsById().get("out"));

        try {
            new GraphExecutor(g).evaluate(add.getOutputsById().get("out"), Float.class);
            helper.fail("expected CycleException through the portal, got a value");
        } catch (CycleException expected) {
            helper.succeed();
        } catch (StackOverflowError e) {
            helper.fail("cycle through a portal was not detected: recursed to StackOverflowError");
        }
    }

    private static float orNaN(Float f) {
        return f == null ? Float.NaN : f;
    }
}
