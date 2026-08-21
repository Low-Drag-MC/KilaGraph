package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.test.gametest.KGDifferential;
import com.lowdragmc.kilagraph.test.gametest.KGDifferential.Mode;
import com.lowdragmc.kilagraph.test.gametest.KGDifferential.Scenario;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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
@GameTestHolder(Kilagraph.MODID)
public final class DifferentialGameTest {

    private DifferentialGameTest() {}

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
    @GameTest(template = "empty", timeoutTicks = 600)
    @PrefixGameTestTemplate(false)
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
    @GameTest(template = "empty", timeoutTicks = 600)
    @PrefixGameTestTemplate(false)
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
    @GameTest(template = "empty", timeoutTicks = 600)
    @PrefixGameTestTemplate(false)
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
    @GameTest(template = "empty", timeoutTicks = 600)
    @PrefixGameTestTemplate(false)
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
