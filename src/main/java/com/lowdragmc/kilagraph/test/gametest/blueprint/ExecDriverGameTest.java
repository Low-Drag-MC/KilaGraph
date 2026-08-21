package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * The two exec drivers run the same nodes, the same number of times.
 *
 * <p>{@code runToCompletion()} drains a frame without re-settling the stack between nodes;
 * {@code step()} settles twice per node so a debugger can look in between. That difference is only
 * safe if it is invisible in what actually ran — and the failure it invites is a fused loop that
 * swallows a node when a frame is pushed, or runs one twice when a signal unwinds. Neither changes
 * any value a graph produces in most shapes, so a value assertion would not see it.</p>
 *
 * <p>So both drivers are run over the same graphs and compared on step count and evaluation trace.
 * The shapes cover the transitions the fused loop has to break out on: a loop pushing its body frame,
 * a subgraph entering and returning, a branch, and — in {@code fanOutIntoLoop} — the one case none of
 * the others reach, a frame pushed while its own frame still has work queued behind it.</p>
 *
 * <p>Not covered here: {@code Sequence} re-arming its next output. It has behaviour coverage in
 * {@code ExecSemanticsGameTest.sequenceToCompletion}, but no driver-equivalence case.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class ExecDriverGameTest {

    private ExecDriverGameTest() {}

    private record Case(String name, Supplier<KGGraphBuilder> build) {}

    private static List<Case> cases() {
        return List.of(
                new Case("execChain", () -> KGGraphFixtures.execChain(32)),
                new Case("branchLadder", () -> KGGraphFixtures.branchLadder(16)),
                new Case("accumulatingLoop", () -> KGGraphFixtures.accumulatingLoop(16)),
                new Case("subgraphCalls", () -> KGGraphFixtures.subgraphCalls(4)),
                new Case("varPingPong", () -> KGGraphFixtures.variablePingPong(16)),
                new Case("mixedWorkload", KGGraphFixtures::mixedWorkload),
                new Case("locomotion", KGGraphFixtures::locomotion),
                // The only shape here where a frame is pushed with work still queued behind it.
                new Case("fanOutIntoLoop", () -> KGGraphFixtures.fanOutIntoLoop(4)));
    }

    /** Same nodes, same count, same order — whichever driver ran them. */
    @GameTest(template = "empty", timeoutTicks = 600)
    @PrefixGameTestTemplate(false)
    public static void bothDriversRunTheSameNodes(GameTestHelper helper) {
        for (Case c : cases()) {
            var graph = c.build().get();

            var fusedTrace = new EvalTrace();
            int fusedSteps = run(graph, fusedTrace, true);

            var steppedTrace = new EvalTrace();
            int steppedSteps = run(graph, steppedTrace, false);

            assertEq(helper, c.name() + ": step count", steppedSteps, fusedSteps);
            String diff = steppedTrace.diff(fusedTrace);
            assertTrue(helper, c.name() + ": traces identical: " + diff, diff == null);
            assertTrue(helper, c.name() + ": something actually ran", fusedSteps > 0);
        }
        helper.succeed();
    }

    /**
     * Drive {@code graph} to completion and return the step count.
     *
     * <p>{@code fused} picks the driver: on, {@code runToCompletion} takes its fused path; off, it
     * falls back to calling {@code step()} in a loop, which is what the debugger does.</p>
     */
    private static int run(KGGraphBuilder graph, EvalTrace trace, boolean fused) {
        var store = new VariableStore();
        store.put("vx", 3.0f);
        store.put("vz", 4.0f);
        store.put("yaw", 30.0f);
        store.put("facing", -15.0f);
        var exec = new GraphExecutor(graph.graph(), new EvaluationEnvironment(store, OptionalLong.of(7L)));
        exec.setOptimisationEnabled(GraphExecutor.Opt.FUSED_EXEC_DRIVER, fused);
        exec.setTrace(trace);

        var session = new ExecSession(exec).begin(graph.node("entry"));
        session.runToCompletion();
        return session.stepCount();
    }
}
