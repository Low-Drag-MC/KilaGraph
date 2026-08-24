package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.test.gametest.KGDifferential;
import com.lowdragmc.kilagraph.test.gametest.KGDifferential.Mode;
import com.lowdragmc.kilagraph.test.gametest.KGDifferential.Scenario;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import com.lowdragmc.kilagraph.test.gametest.KGGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * The guard rail every executor optimisation is checked against.
 *
 * <p>Each scenario is run under two executor configurations and the runs are required to be
 * indistinguishable — same values bit-for-bit, same evaluation order and count, same number of exec
 * steps. See {@link KGDifferential} for why all three signals are needed.</p>
 *
 * <p>Two pairs are compared. {@code DEFAULT} against {@code FROZEN} covers the staleness digest;
 * {@code UNOPTIMISED} against {@code DEFAULT} covers every {@link GraphExecutor.Opt} at once, which
 * is the assertion each optimisation stage was admitted by.</p>
 *
 * <p>Adding an optimisation means adding its switch to {@code Opt}; {@code UNOPTIMISED} turns off
 * whatever is there, so the scenarios below cover it with no further work. What does <em>not</em>
 * happen automatically is a scenario that actually exercises it —
 * {@code IntrinsicParityGameTest.everyExecIntrinsicIsCovered} exists because {@code Gate} had a
 * switch and appeared in no graph here.</p>
 */
public final class DifferentialGameTest {
    private static final String EVERY_SCENARIO_AGREES_ACROSS_MODES = "differential_every_scenario_agrees_across_modes";
    private static final String OPTIMISATIONS_AGREE_WITH_THE_UNOPTIMISED_PATHS = "differential_optimisations_agree_with_the_unoptimised_paths";
    private static final String THE_HARNESS_ACTUALLY_OBSERVES_SOMETHING = "differential_the_harness_actually_observes_something";
    private static final String THE_HARNESS_DETECTS_A_REAL_DIFFERENCE = "differential_the_harness_detects_a_real_difference";

    private DifferentialGameTest() {}


    public static void registerFunctions() {
        KGGameTests.registerFunction(EVERY_SCENARIO_AGREES_ACROSS_MODES, DifferentialGameTest::everyScenarioAgreesAcrossModes);
        KGGameTests.registerFunction(OPTIMISATIONS_AGREE_WITH_THE_UNOPTIMISED_PATHS, DifferentialGameTest::optimisationsAgreeWithTheUnoptimisedPaths);
        KGGameTests.registerFunction(THE_HARNESS_ACTUALLY_OBSERVES_SOMETHING, DifferentialGameTest::theHarnessActuallyObservesSomething);
        KGGameTests.registerFunction(THE_HARNESS_DETECTS_A_REAL_DIFFERENCE, DifferentialGameTest::theHarnessDetectsARealDifference);
    }

    public static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {
        KGGameTests.registerFunctionTest(event, EVERY_SCENARIO_AGREES_ACROSS_MODES, KGGameTests.functionKey(EVERY_SCENARIO_AGREES_ACROSS_MODES),
                KGGameTests.defaultTestData(environment, "empty", 600));
        KGGameTests.registerFunctionTest(event, OPTIMISATIONS_AGREE_WITH_THE_UNOPTIMISED_PATHS, KGGameTests.functionKey(OPTIMISATIONS_AGREE_WITH_THE_UNOPTIMISED_PATHS),
                KGGameTests.defaultTestData(environment, "empty", 600));
        KGGameTests.registerFunctionTest(event, THE_HARNESS_ACTUALLY_OBSERVES_SOMETHING, KGGameTests.functionKey(THE_HARNESS_ACTUALLY_OBSERVES_SOMETHING),
                KGGameTests.defaultTestData(environment, "empty", 600));
        KGGameTests.registerFunctionTest(event, THE_HARNESS_DETECTS_A_REAL_DIFFERENCE, KGGameTests.functionKey(THE_HARNESS_DETECTS_A_REAL_DIFFERENCE),
                KGGameTests.defaultTestData(environment, "empty", 600));
    }

    /** The graphs every mode is compared on. Shared with the behaviour tests and the benchmarks. */
    private static List<Scenario> scenarios() {
        return List.of(
                Scenario.of("locomotion", KGGraphFixtures::locomotion, "entry",
                                List.of("speed", "direction", "lean"))
                        .repeated(200)
                        .seededWith(store -> {
                            store.put("vx", 3.0f);
                            store.put("vz", 4.0f);
                            store.put("yaw", 30.0f);
                            store.put("facing", -15.0f);
                        }),
                Scenario.of("mixedWorkload", KGGraphFixtures::mixedWorkload, "entry",
                        List.of("total", "peak", "tag", "label")),
                Scenario.of("subgraphCalls", () -> KGGraphFixtures.subgraphCalls(4), "entry",
                                List.of("result"))
                        .reading("c0.y", "c1.y", "c2.y", "c3.y"),
                Scenario.of("accumulatingLoop", () -> KGGraphFixtures.accumulatingLoop(32), "entry",
                        List.of("acc")),
                Scenario.of("execChain", () -> KGGraphFixtures.execChain(32), "entry", List.of()),
                Scenario.of("execIntrinsicSampler", KGGraphFixtures::execIntrinsicSampler, "entry",
                        List.of("taken", "untaken", "passed", "blocked")),
                Scenario.data("deepChain", () -> KGGraphFixtures.chainOfAdds(64), List.of("n63"))
        );
    }

    /** Every scenario agrees between a stock executor and a frozen one. */
    public static void everyScenarioAgreesAcrossModes(GameTestHelper helper) {
        for (Scenario s : scenarios()) {
            String diff = KGDifferential.compareModes(s, Mode.DEFAULT, Mode.FROZEN);
            if (diff != null) {
                helper.fail(diff);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Every optimisation, together, is indistinguishable from the paths it replaced. This is the
     * assertion each optimisation stage is admitted by.
     */
    public static void optimisationsAgreeWithTheUnoptimisedPaths(GameTestHelper helper) {
        for (Scenario s : scenarios()) {
            String diff = KGDifferential.compareModes(s, Mode.UNOPTIMISED, Mode.DEFAULT);
            if (diff != null) {
                helper.fail(diff);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The harness observes something. Without this, {@link #everyScenarioAgreesAcrossModes} would
     * pass just as happily if every scenario produced an empty value list and an empty trace — which
     * is precisely how a guard rail quietly stops guarding.
     */
    public static void theHarnessActuallyObservesSomething(GameTestHelper helper) {
        for (Scenario s : scenarios()) {
            var result = KGDifferential.run(s.builder().get(), s, Mode.DEFAULT);
            assertTrue(helper, s.name() + " produced a trace", result.trace().size() > 0);
            assertEq(helper, s.name() + " observed value count",
                    s.outputRefs().size() + s.variables().size(), result.values().size());
            if (s.entry() != null) {
                assertTrue(helper, s.name() + " took exec steps", result.stepCount() > 0);
            }
        }
        helper.succeed();
    }

    /**
     * The harness can tell two runs apart. A comparator that returned "no difference" unconditionally
     * would make every mode agree forever, so it is checked against a pair that really does differ.
     */
    public static void theHarnessDetectsARealDifference(GameTestHelper helper) {
        // Both sides share one graph, so the difference is genuinely in what the executor did rather
        // than in which node objects it did it to.
        var graph = KGGraphFixtures.accumulatingLoop(8);
        var once = Scenario.of("loopOnce", () -> KGGraphFixtures.accumulatingLoop(8), "entry", List.of("acc"));
        var twice = once.repeated(2);

        String diff = KGDifferential.compare(
                KGDifferential.run(graph, once, Mode.DEFAULT),
                KGDifferential.run(graph, twice, Mode.DEFAULT));
        assertTrue(helper, "one run and two runs must not compare equal", diff != null);

        // ... and identical runs still compare equal, so it is not simply always reporting one.
        String same = KGDifferential.compare(
                KGDifferential.run(graph, once, Mode.DEFAULT),
                KGDifferential.run(graph, once, Mode.DEFAULT));
        assertTrue(helper, "identical runs compare equal, got: " + same, same == null);
        helper.succeed();
    }
}
