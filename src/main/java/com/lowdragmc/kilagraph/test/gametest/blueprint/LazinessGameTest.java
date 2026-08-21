package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.CacheNode;
import com.lowdragmc.kilagraph.blueprint.nodes.flow.SelectNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.AndNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.OrNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;

/**
 * Which nodes a pull actually evaluates, and how many times.
 *
 * <p>{@code PreparedGraph} states that evaluation order and count are observable and must not
 * change. These tests are that statement made executable. They exist <em>before</em> the executor
 * optimisations that could break them, because every one of these can be broken while every value
 * in the graph stays correct: evaluating both sides of a {@code Select} still yields the right
 * answer, and a test that only checks the answer stays green.</p>
 *
 * <p>The evaluation counts come from {@link EvalTrace} rather than a purpose-built probe node —
 * the executor already knows what it evaluated, so asking it is both cheaper and harder to fool
 * than a counter wired into the graph.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class LazinessGameTest {

    private LazinessGameTest() {}

    /** {@code And} stops at the first false input; the rest are never evaluated. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void andShortCircuits(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("and", AndNode.class).constant("and.in1", false);
        b.add("gt", GreaterThanNode.class).constant("gt.a", 1f).constant("gt.b", 0f);
        b.wire("and.in2", "gt");

        var trace = run(b, "and");
        assertEq(helper, "And result", 1, trace.countByLabel("AndNode"));
        assertEq(helper, "in1=false must not evaluate in2's producer",
                0, trace.evalCount(b.node("gt").getUid()));
        helper.succeed();
    }

    /** {@code Or} stops at the first true input. The mirror of {@link #andShortCircuits}. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void orShortCircuits(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("or", OrNode.class).constant("or.in1", true);
        b.add("gt", GreaterThanNode.class).constant("gt.a", 1f).constant("gt.b", 0f);
        b.wire("or.in2", "gt");

        var trace = run(b, "or");
        assertEq(helper, "in1=true must not evaluate in2's producer",
                0, trace.evalCount(b.node("gt").getUid()));
        helper.succeed();
    }

    /** {@code Select} pulls the branch it returns and only that branch. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void selectEvaluatesOnlyTheTakenBranch(GameTestHelper helper) {
        for (boolean cond : new boolean[]{true, false}) {
            var b = KGGraphBuilder.blueprint();
            b.add("sel", SelectNode.class).constant("sel.cond", cond);
            b.add("whenTrue", AddNode.class).constant("whenTrue.in1", 1f).constant("whenTrue.in2", 0f);
            b.add("whenFalse", AddNode.class).constant("whenFalse.in1", 2f).constant("whenFalse.in2", 0f);
            b.wire("sel.ifTrue", "whenTrue").wire("sel.ifFalse", "whenFalse");

            var trace = run(b, "sel");
            String taken = cond ? "whenTrue" : "whenFalse";
            String untaken = cond ? "whenFalse" : "whenTrue";
            assertEq(helper, "cond=" + cond + " evaluates " + taken,
                    1, trace.evalCount(b.node(taken).getUid()));
            assertEq(helper, "cond=" + cond + " must NOT evaluate " + untaken,
                    0, trace.evalCount(b.node(untaken).getUid()));
        }
        helper.succeed();
    }

    /**
     * A node feeding two consumers is evaluated once, not twice — the memo in {@code ensureComputed}.
     * {@code ExecutorEdgeCaseGameTest.aDiamondEvaluatesTheSharedNodeOnce} asserts the same property
     * through its arithmetic; this asserts it directly, so a future scheduler that recomputes a
     * shared node fails here with the count rather than silently costing twice the work.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void diamondEvaluatesTheSharedNodeOnce(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("shared", AddNode.class).constant("shared.in1", 3f).constant("shared.in2", 4f);
        b.add("left", MultiplyNode.class).wire("left.in1", "shared").constant("left.in2", 2f);
        b.add("right", MultiplyNode.class).wire("right.in1", "shared").constant("right.in2", 5f);
        b.add("sum", AddNode.class).wire("sum.in1", "left").wire("sum.in2", "right");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        Float out = exec.evaluate(b.node("sum").getOutputsById().get("out"), Float.class);

        assertEq(helper, "7*2 + 7*5", 49f, out == null ? Float.NaN : out, 1e-6f);
        assertEq(helper, "shared node evaluated exactly once",
                1, trace.evalCount(b.node("shared").getUid()));
        helper.succeed();
    }

    /**
     * {@code Cache} memoises in per-node state, which {@code clearCache()} does not touch — so its
     * upstream is pulled on the first evaluation and never again, even across generations.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void cachePullsItsSourceOnlyOnce(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("src", AddNode.class).constant("src.in1", 6f).constant("src.in2", 1f);
        b.add("cache", CacheNode.class).wire("cache.value", "src");

        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        var cached = b.node("cache").getOutputsById().get("cached");

        Float first = exec.evaluate(cached, Float.class);
        exec.clearCache();
        Float second = exec.evaluate(cached, Float.class);

        assertEq(helper, "first read", 7f, first == null ? Float.NaN : first, 1e-6f);
        assertEq(helper, "second read is the memo", 7f, second == null ? Float.NaN : second, 1e-6f);
        assertEq(helper, "Cache evaluated twice (its own memo check runs each time)",
                2, trace.evalCount(b.node("cache").getUid()));
        assertEq(helper, "but its source was pulled only once",
                1, trace.evalCount(b.node("src").getUid()));
        helper.succeed();
    }

    /** A node nothing demands is never evaluated, however reachable it looks in the model. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void undemandedNodesAreNotEvaluated(GameTestHelper helper) {
        var b = KGGraphBuilder.blueprint();
        b.add("wanted", AddNode.class).constant("wanted.in1", 1f).constant("wanted.in2", 2f);
        b.add("orphan", MultiplyNode.class).constant("orphan.in1", 9f).constant("orphan.in2", 9f);

        var trace = run(b, "wanted");
        assertEq(helper, "demanded node evaluated", 1, trace.evalCount(b.node("wanted").getUid()));
        assertEq(helper, "unrelated node not evaluated", 0, trace.evalCount(b.node("orphan").getUid()));
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Evaluate {@code nodeName}'s {@code out} port under a fresh trace, and return the trace. */
    private static EvalTrace run(KGGraphBuilder b, String nodeName) {
        var exec = new GraphExecutor(b.graph());
        var trace = new EvalTrace();
        exec.setTrace(trace);
        exec.evaluate(b.node(nodeName).getOutputsById().get("out"), Object.class);
        return trace;
    }
}
