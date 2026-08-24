package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.OptionalLong;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Tests for the test infrastructure. {@link KGGraphBuilder} is what every later complex-graph test
 * is written in, so a bug in it would show up as a mysterious failure in an unrelated behaviour
 * test — cheaper to catch it here.
 *
 * <p>The load-bearing one is {@link #locomotionMatchesHandBuilt}: it runs the fixture built through
 * the DSL and asserts the values {@code ExecutorBenchGameTest.locomotion} asserts for the same graph
 * built by hand. That is the only evidence that the DSL wires what it appears to wire.</p>
 */
public final class KGGraphBuilderGameTest {
    private static final String LOCOMOTION_MATCHES_HAND_BUILT = "kg_graph_builder_locomotion_matches_hand_built";
    private static final String AMBIGUOUS_BARE_REFERENCE_IS_REJECTED = "kg_graph_builder_ambiguous_bare_reference_is_rejected";
    private static final String BAD_NAMES_FAIL_FAST = "kg_graph_builder_bad_names_fail_fast";
    private static final String ADD_MANY_BUILDS_A_CHAIN = "kg_graph_builder_add_many_builds_a_chain";

    private KGGraphBuilderGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(LOCOMOTION_MATCHES_HAND_BUILT, KGGraphBuilderGameTest::locomotionMatchesHandBuilt);
        KGGameTests.registerFunction(AMBIGUOUS_BARE_REFERENCE_IS_REJECTED, KGGraphBuilderGameTest::ambiguousBareReferenceIsRejected);
        KGGameTests.registerFunction(BAD_NAMES_FAIL_FAST, KGGraphBuilderGameTest::badNamesFailFast);
        KGGameTests.registerFunction(ADD_MANY_BUILDS_A_CHAIN, KGGraphBuilderGameTest::addManyBuildsAChain);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                LOCOMOTION_MATCHES_HAND_BUILT, AMBIGUOUS_BARE_REFERENCE_IS_REJECTED, BAD_NAMES_FAIL_FAST,
                ADD_MANY_BUILDS_A_CHAIN
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    /** The DSL-built locomotion graph converges to the same values as the hand-built one. */
    public static void locomotionMatchesHandBuilt(GameTestHelper helper) {
        var b = KGGraphFixtures.locomotion();

        var store = new VariableStore();
        store.put("vx", 3.0f);
        store.put("vz", 4.0f);
        store.put("yaw", 30.0f);
        store.put("facing", -15.0f);
        var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.empty()));

        for (int i = 0; i < 200; i++) {
            exec.clearCache();
            exec.executeFrom(b.node("entry"));
        }

        assertEq(helper, "speed converges to |v| = 5", 5.0f, num(store.get("speed")), 1e-3f);
        assertEq(helper, "direction = clamp(30 - -15)", 45.0f, num(store.get("direction")), 1e-3f);
        assertEq(helper, "lean converges to direction * 0.5", 22.5f, num(store.get("lean")), 1e-3f);
        helper.succeed();
    }

    /**
     * A bare reference to a node with two candidate ports is rejected rather than resolved to the
     * first. Guessing here would silently wire a Branch to its true side and leave a graph that
     * looks correct in the source and tests nothing.
     */
    public static void ambiguousBareReferenceIsRejected(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("cond", BranchNode.class);
        b.add("sink", LerpNode.class);

        // Branch has trueExec + falseExec: two exec outputs, so `then` must not pick one.
        assertTrue(helper, "two exec outputs are ambiguous",
                throwsIllegalArgument(() -> b.then("cond", "sink")));
        // Lerp has a/b/t: three data inputs, so a bare destination is ambiguous too.
        assertTrue(helper, "three data inputs are ambiguous",
                throwsIllegalArgument(() -> b.constant("sink", 1f)));
        helper.succeed();
    }

    /** Unknown names and duplicates fail at build time, naming what was actually registered. */
    public static void badNamesFailFast(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("a", AddNode.class);

        assertTrue(helper, "unknown node name", throwsIllegalArgument(() -> b.node("nope")));
        assertTrue(helper, "duplicate node name", throwsIllegalArgument(() -> b.add("a", AddNode.class)));
        assertTrue(helper, "unknown port id", throwsIllegalArgument(() -> b.constant("a.nope", 1f)));
        helper.succeed();
    }

    /** {@code addMany} + explicit wiring builds a chain whose value is the chain's arithmetic. */
    public static void addManyBuildsAChain(GameTestHelper helper) {
        int n = 8;
        var b = KGGraphBuilder.blueprint();
        b.addMany("add", AddNode.class, n);
        b.constant("add0.in1", 0f);
        for (int i = 0; i < n; i++) {
            b.constant("add" + i + ".in2", 1f);
            if (i > 0) b.wire("add" + i + ".in1", "add" + (i - 1));
        }

        var exec = new GraphExecutor(b.graph());
        Float out = exec.evaluate(b.node("add" + (n - 1)).getOutputsById().get("out"), Float.class);
        assertEq(helper, "chain of " + n + " increments", (float) n, out == null ? Float.NaN : out, 1e-6f);
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static boolean throwsIllegalArgument(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
