package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RandomNode;
import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.OptionalLong;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertEq;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.assertTrue;

/**
 * Two runs of the same graph agree, exactly.
 *
 * <p>This is the property the differential harness is built on: comparing an optimised executor
 * against an unoptimised one only means something if the unoptimised one is reproducible in the
 * first place. So the baseline is established here — bit-identical values, and an identical
 * evaluation trace — before anything is compared against it.</p>
 *
 * <p>Bit-identical rather than within-a-tolerance on purpose. Float arithmetic is deterministic;
 * a tolerance would hide exactly the kind of drift an executor change introduces, such as a value
 * that starts making an extra round trip through {@code double}.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class DeterminismGameTest {

    private DeterminismGameTest() {}

    /** The same seed produces the same random draws, in the same order. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aSeededRunIsReproducible(GameTestHelper helper) {
        float[] a = drawFour(1234L);
        float[] b = drawFour(1234L);
        for (int i = 0; i < a.length; i++) {
            assertTrue(helper, "draw " + i + " is bit-identical (" + a[i] + " vs " + b[i] + ")",
                    Float.floatToRawIntBits(a[i]) == Float.floatToRawIntBits(b[i]));
        }
        helper.succeed();
    }

    /**
     * A different seed produces different draws — without this, {@link #aSeededRunIsReproducible}
     * would pass just as happily against an RNG that always returned the same number.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void adifferentSeedProducesDifferentDraws(GameTestHelper helper) {
        float[] a = drawFour(1234L);
        float[] b = drawFour(9876L);
        boolean anyDifferent = false;
        for (int i = 0; i < a.length; i++) {
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) anyDifferent = true;
        }
        assertTrue(helper, "seeds 1234 and 9876 disagree somewhere", anyDifferent);
        helper.succeed();
    }

    /** Repeating a run on one executor gives bit-identical values and an identical trace. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void repeatedRunsAgreeExactly(GameTestHelper helper) {
        var b = KGGraphFixtures.locomotion();
        var store = seededStore();
        var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.empty()));

        var first = new EvalTrace();
        exec.setTrace(first);
        exec.clearCache();
        exec.executeFrom(b.node("entry"));
        int speedBits = Float.floatToRawIntBits(num(store.get("speed")));

        // Same starting state, so the second run must reproduce the first exactly.
        var store2 = seededStore();
        var exec2 = new GraphExecutor(b.graph(), new EvaluationEnvironment(store2, OptionalLong.empty()));
        var second = new EvalTrace();
        exec2.setTrace(second);
        exec2.clearCache();
        exec2.executeFrom(b.node("entry"));

        assertEq(helper, "speed is bit-identical", speedBits,
                Float.floatToRawIntBits(num(store2.get("speed"))));
        String diff = first.diff(second);
        assertTrue(helper, "traces identical: " + diff, diff == null);
        helper.succeed();
    }

    /**
     * Two executors over one graph model do not interfere, even when their runs are interleaved.
     * They share a {@link com.lowdragmc.kilagraph.graph.exec.PreparedGraph}; the per-run value
     * tables must stay their own.
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void interleavedExecutorsDoNotInterfere(GameTestHelper helper) {
        var b = KGGraphFixtures.locomotion();

        var slowStore = seededStore();
        var fastStore = seededStore();
        fastStore.put("vx", 6.0f);      // |v| = 10 rather than 5
        fastStore.put("vz", 8.0f);
        var slow = new GraphExecutor(b.graph(), new EvaluationEnvironment(slowStore, OptionalLong.empty()));
        var fast = new GraphExecutor(b.graph(), new EvaluationEnvironment(fastStore, OptionalLong.empty()));

        for (int i = 0; i < 200; i++) {
            slow.clearCache();
            slow.executeFrom(b.node("entry"));
            fast.clearCache();
            fast.executeFrom(b.node("entry"));
        }

        assertEq(helper, "first executor converged to its own |v|", 5.0f, num(slowStore.get("speed")), 1e-3f);
        assertEq(helper, "second executor converged to its own |v|", 10.0f, num(fastStore.get("speed")), 1e-3f);
        helper.succeed();
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** Four chained Random draws under {@code seed}, in evaluation order. */
    private static float[] drawFour(long seed) {
        var b = KGGraphBuilder.blueprint();
        for (int i = 0; i < 4; i++) {
            b.add("r" + i, RandomNode.class).constant("r" + i + ".min", 0f).constant("r" + i + ".max", 100f);
        }
        // A sink so all four are demanded, in index order.
        b.add("sink", AddNode.class).option("sink", "inputs", 4);
        for (int i = 0; i < 4; i++) b.wire("sink.in" + (i + 1), "r" + i);

        var exec = new GraphExecutor(b.graph(), EvaluationEnvironment.seeded(seed));
        exec.evaluate(b.outputOf("sink"), Float.class);

        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            Float v = exec.evaluate(b.outputOf("r" + i), Float.class);
            out[i] = v == null ? Float.NaN : v;
        }
        return out;
    }

    private static VariableStore seededStore() {
        var store = new VariableStore();
        store.put("vx", 3.0f);
        store.put("vz", 4.0f);
        store.put("yaw", 30.0f);
        store.put("facing", -15.0f);
        return store;
    }

    private static float num(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.NaN;
    }
}
