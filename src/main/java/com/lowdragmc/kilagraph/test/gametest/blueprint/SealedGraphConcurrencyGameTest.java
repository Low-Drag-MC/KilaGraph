package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.PreparedGraph;
import com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;

/**
 * One {@link PreparedGraph}, several threads, no admission.
 *
 * <h2>What is actually being pinned</h2>
 * A prepared graph is shared by every executor over the same graph model, and it used to <em>grow</em>
 * during execution: a node the build pass never reached was admitted on first sight, which appends to
 * an {@code ArrayList}, puts into an {@code IdentityHashMap} and re-derives the cycle flag. A second
 * thread reading that map while it rehashes does not get an exception, it gets a wrong answer or a
 * spin. {@link PreparedGraph#seal()} removes the write rather than guarding it — a sealed graph
 * answers null for an unknown node and raises {@link PreparedGraph#sealBreached()}, so the outcome is
 * one the caller can detect instead of one it cannot.
 *
 * <p>The concurrent test below cannot <em>prove</em> the absence of a race — no test can. It is here
 * because it does catch the admitting version, which was checked rather than assumed: with the seal
 * guard removed and every thread given its own orphan node to admit, it failed. <b>It failed on
 * {@link PreparedGraph#admitCount()}, not on a wrong value</b> — eight threads mutated the shared
 * {@code IdentityHashMap} and every computed result still came back correct that run. So the counter
 * is not a redundant extra assertion next to the value check; it is the one with detection power, and
 * the value check is the one that happens to be quiet. Do not delete it as noise.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class SealedGraphConcurrencyGameTest {

    private SealedGraphConcurrencyGameTest() {}

    private static final int THREADS = 8;
    private static final int ITERATIONS = 400;
    private static final int CHAIN = 32;

    /**
     * A graph the build pass fully discovers admits nothing when run — the precondition sealing is
     * built on, checked rather than assumed.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aFullyWiredGraphAdmitsNothing(GameTestHelper helper) {
        var b = KGGraphFixtures.chainOfAdds(CHAIN);
        var exec = new GraphExecutor(b.graph());
        exec.evaluate(b.outputOf("n" + (CHAIN - 1)), Float.class);

        PreparedGraph prepared = exec.preparedGraph();
        if (prepared == null) {
            helper.fail("a run should have resolved a prepared graph");
            return;
        }
        assertEq(helper, "build() saw every node; nothing was admitted at run time",
                0, prepared.admitCount());
        helper.succeed();
    }

    /**
     * Sealed, an unknown node is refused rather than admitted — and says so.
     *
     * <p>The orphan node is the case that reaches {@code admit()} in the first place: it is in no
     * wire and in {@code getNodeModels()} for nobody, so the build pass cannot see it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sealingRefusesAdmissionAndRecordsIt(GameTestHelper helper) {
        var graph = KGGameTestHelpers.newGraph();
        var reached = KGGameTestHelpers.addRegisteredNode(graph, AbsNode.class);
        KGGameTestHelpers.setInputConstant(reached, "in", -3f);

        var exec = new GraphExecutor(graph);
        assertEq(helper, "the wired node evaluates", 3f,
                exec.evaluate(reached.getOutputsById().get("out"), Float.class), 1e-5f);

        PreparedGraph prepared = exec.preparedGraph();
        if (prepared == null) {
            helper.fail("a run should have resolved a prepared graph");
            return;
        }
        int admittedBeforeSeal = prepared.admitCount();
        prepared.seal();
        assertEq(helper, "sealed", true, prepared.isSealed());
        assertEq(helper, "nothing breached yet", false, prepared.sealBreached());

        // An orphan the build pass never saw. Unsealed this would be admitted mid-run.
        var orphan = KGGameTestHelpers.addNode(graph, AbsNode.class);
        KGGameTestHelpers.setInputConstant(orphan, "in", -9f);
        Object out = exec.evaluate(orphan.getOutputsById().get("out"), Object.class);

        assertEq(helper, "a sealed graph answers null rather than growing", null, out);
        assertEq(helper, "and records that it was asked to", true, prepared.sealBreached());
        assertEq(helper, "without having admitted anything", admittedBeforeSeal, prepared.admitCount());

        // Unsealing is what makes the serial retry work — without it the fallback answers null too.
        prepared.unseal();
        assertEq(helper, "the breach flag survives unsealing", true, prepared.sealBreached());
        assertEq(helper, "unsealed, the orphan is admitted and evaluates", 9f,
                new GraphExecutor(graph).evaluate(orphan.getOutputsById().get("out"), Float.class), 1e-5f);
        helper.succeed();
    }

    /**
     * The real shape: N executors, one prepared graph, all frozen, all warmed up before the fork.
     *
     * <p>Every executor is created <em>and run</em> on this thread first. That is not tidiness: it is
     * the documented precondition twice over — it proves the node set is complete, and it is what
     * makes the instance shared at all, since {@code PreparedGraph.of} reads and fills its cache in
     * two steps and two first-time entries racing can each build their own.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 2000)
    @PrefixGameTestTemplate(false)
    public static void oneSealedGraphDrivesManyThreads(GameTestHelper helper) throws Exception {
        var b = KGGraphFixtures.chainOfAdds(CHAIN);
        var out = b.outputOf("n" + (CHAIN - 1));

        var executors = new GraphExecutor[THREADS];
        for (int i = 0; i < THREADS; i++) {
            executors[i] = new GraphExecutor(b.graph());
            executors[i].setGraphFrozen(true);
            executors[i].evaluate(out, Float.class);          // warm up on this thread
        }

        PreparedGraph prepared = executors[0].preparedGraph();
        if (prepared == null) {
            helper.fail("warm-up should have resolved a prepared graph");
            return;
        }
        for (int i = 1; i < THREADS; i++) {
            if (executors[i].preparedGraph() != prepared) {
                helper.fail("executor " + i + " resolved a different PreparedGraph — the instance is "
                        + "supposed to be shared per graph model, so this test is not testing sharing");
                return;
            }
        }
        assertEq(helper, "nothing admitted during warm-up", 0, prepared.admitCount());

        float expected = executors[0].evaluate(out, Float.class);
        prepared.seal();

        var start = new CountDownLatch(1);
        var done = new CountDownLatch(THREADS);
        var failure = new AtomicReference<String>();
        var threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    var exec = executors[id];
                    for (int k = 0; k < ITERATIONS; k++) {
                        exec.clearCache();
                        Float v = exec.evaluate(out, Float.class);
                        if (v == null || Math.abs(v - expected) > 1e-4f) {
                            failure.compareAndSet(null, "thread " + id + " iteration " + k
                                    + " got " + v + ", expected " + expected);
                            return;
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, "thread " + id + " threw " + t);
                } finally {
                    done.countDown();
                }
            }, "kg-sealed-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        start.countDown();
        if (!done.await(60, TimeUnit.SECONDS)) {
            helper.fail("threads did not finish — a concurrent read of the prepared graph is stuck");
            return;
        }
        for (Thread t : threads) t.join(1000);

        if (failure.get() != null) {
            helper.fail(failure.get());
            return;
        }
        // The load-bearing assertions: the structure was never asked to change, so nothing wrote to
        // it while all those threads were reading it.
        assertEq(helper, "no thread was refused a node", false, prepared.sealBreached());
        assertEq(helper, "no thread admitted a node", 0, prepared.admitCount());
        assertEq(helper, "still sealed", true, prepared.isSealed());
        prepared.unseal();   // leave the shared instance as this test found it
        helper.succeed();
    }

    /**
     * {@code seal()} is idempotent per root, and {@code unseal()} releases exactly what that root took.
     *
     * <p>Both halves matter because the seal is <em>counted</em> rather than a flag — a function graph
     * called by two blueprints sits in two sealed trees, and the second one unsealing must not clear
     * the seal out from under the first one's threads. Counting is what makes that work, and
     * idempotency is what stops a defensive second {@code seal()} from leaking a count that no
     * {@code unseal()} will ever balance.</p>
     *
     * <p>What this does <b>not</b> cover: the two-different-roots case itself. A local subgraph belongs
     * to one parent, so the test builder cannot express one callee shared by two root graphs; that path
     * is reasoned about in {@code PreparedGraph}, not demonstrated here.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void sealIsIdempotentAndUnsealIsPaired(GameTestHelper helper) {
        var b = KGGraphFixtures.chainOfAdds(CHAIN);
        var exec = new GraphExecutor(b.graph());
        exec.evaluate(b.outputOf("n" + (CHAIN - 1)), Float.class);
        PreparedGraph prepared = exec.preparedGraph();
        if (prepared == null) {
            helper.fail("a run should have resolved a prepared graph");
            return;
        }

        assertEq(helper, "starts unsealed", false, prepared.isSealed());
        assertEq(helper, "unsealing what was never sealed is a no-op", false, prepared.isSealed());
        prepared.unseal();
        assertEq(helper, "still unsealed", false, prepared.isSealed());

        prepared.seal();
        prepared.seal();   // a second seal from the same root must not leak a count
        assertEq(helper, "sealed", true, prepared.isSealed());
        prepared.unseal();
        assertEq(helper, "one unseal balances any number of seals from the same root",
                false, prepared.isSealed());
        helper.succeed();
    }
}
