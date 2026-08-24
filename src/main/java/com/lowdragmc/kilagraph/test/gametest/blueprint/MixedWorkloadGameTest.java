package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * The golden test: one graph that uses most of the executor at once.
 *
 * <p>Every other test in the suite isolates one mechanism. This one runs them together — two levels
 * of function call, from inside a loop, with two loop-carried accumulators, followed by a stage over
 * vectors, NBT, numeric conversion and string formatting — because the failures that survive a suite
 * of isolated tests are the ones that only appear when mechanisms interact. A subgraph that is
 * correct alone and a loop that is correct alone can still be wrong together.</p>
 *
 * <p>The values are chained: the loop's total feeds the vector, whose length feeds the NBT tag and
 * the label. A stage that silently stopped running therefore changes an asserted value instead of
 * going unnoticed, which is what makes this usable as the reference shape for the benchmark suite
 * and the differential harness.</p>
 */
public final class MixedWorkloadGameTest {
    private static final String COMPUTES_EVERY_STAGE = "mixed_workload_computes_every_stage";
    private static final String IS_REPRODUCIBLE = "mixed_workload_is_reproducible";
    private static final String IS_ACTUALLY_A_MIXED_GRAPH = "mixed_workload_is_actually_a_mixed_graph";

    private MixedWorkloadGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(COMPUTES_EVERY_STAGE, MixedWorkloadGameTest::computesEveryStage);
        KGGameTests.registerFunction(IS_REPRODUCIBLE, MixedWorkloadGameTest::isReproducible);
        KGGameTests.registerFunction(IS_ACTUALLY_A_MIXED_GRAPH, MixedWorkloadGameTest::isActuallyAMixedGraph);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        TestData<Holder<TestEnvironmentDefinition<?>>> d = KGGameTests.defaultTestData(environment, "empty");
        for (String p : new String[]{
                COMPUTES_EVERY_STAGE, IS_REPRODUCIBLE, IS_ACTUALLY_A_MIXED_GRAPH
        }) {
            KGGameTests.registerFunctionTest(event, p, KGGameTests.functionKey(p), d);
        }
    }

    public static void computesEveryStage(GameTestHelper helper) {
        var b = KGGraphFixtures.mixedWorkload();
        var exec = new GraphExecutor(b.graph());
        exec.executeFrom(b.node("entry"));

        var vars = exec.getEnvironment().variables();

        // scores are 1*2+0, 2*2+0, 3*2+10, 4*2+10 = 2, 4, 16, 18
        assertEq(helper, "loop accumulated every score", 40f, num(vars.get("total")), 1e-4f);
        assertEq(helper, "peak score", 18f, num(vars.get("peak")), 1e-4f);

        Object tag = vars.get("tag");
        assertTrue(helper, "the NBT stage produced a compound", tag instanceof CompoundTag);
        if (tag instanceof CompoundTag t) {
            assertEq(helper, "tag.total", 40, t.getIntOr("total", 0));
            assertEq(helper, "tag.len — |(24, 32, 0)|", 40, t.getIntOr("len", 0));
            assertEq(helper, "tag.peak", 18, t.getIntOr("peak", 0));
        }
        assertEq(helper, "formatted label", "total=40.0 len=40.0", vars.get("label"));
        helper.succeed();
    }

    /**
     * Re-running on the same executor reproduces the run exactly — same values, same evaluation
     * order and count. This is the shape the differential harness compares an optimised executor
     * against, so its reproducibility has to hold first.
     */
    public static void isReproducible(GameTestHelper helper) {
        var b = KGGraphFixtures.mixedWorkload();

        var first = new EvalTrace();
        var execA = new GraphExecutor(b.graph());
        execA.setTrace(first);
        execA.executeFrom(b.node("entry"));
        Object labelA = execA.getEnvironment().variables().get("label");

        var second = new EvalTrace();
        var execB = new GraphExecutor(b.graph());
        execB.setTrace(second);
        execB.executeFrom(b.node("entry"));

        assertEq(helper, "same label", labelA, execB.getEnvironment().variables().get("label"));
        String diff = first.diff(second);
        assertTrue(helper, "identical evaluation traces: " + diff, diff == null);
        helper.succeed();
    }

    /**
     * The graph really is the size this test claims. A fixture that quietly shrank — a stage
     * dropped during a refactor — would keep passing the value assertions if the dropped stage
     * happened to be the last one, so the shape itself is asserted.
     */
    public static void isActuallyAMixedGraph(GameTestHelper helper) {
        var b = KGGraphFixtures.mixedWorkload();
        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        exec.executeFrom(b.node("entry"));

        // Two levels of function call, a loop, and the post-loop stage all left marks.
        assertTrue(helper, "the inner function ran on every iteration",
                trace.countByLabel("GreaterThanNode") >= 4);
        assertTrue(helper, "the loop ran four iterations",
                trace.countByLabel("MaxNode") == 4);
        assertTrue(helper, "the vector stage ran", trace.countByLabel("Length") == 1);
        assertTrue(helper, "the NBT stage ran", trace.countByLabel("NbtSetNode") == 3);
        assertTrue(helper, "the string stage ran", trace.countByLabel("FormatNode") == 1);
        helper.succeed();
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
