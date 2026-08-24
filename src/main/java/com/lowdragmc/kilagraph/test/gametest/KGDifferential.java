package com.lowdragmc.kilagraph.test.gametest;

import com.lowdragmc.kilagraph.graph.exec.EvalTrace;
import com.lowdragmc.kilagraph.graph.exec.EvaluationEnvironment;
import com.lowdragmc.kilagraph.graph.exec.ExecSession;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.VariableStore;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs one graph under two executor configurations and compares everything observable about the two
 * runs — values bit-for-bit, evaluation order and count, and the number of exec steps.
 *
 * <h2>What this is for</h2>
 * Each planned optimisation of the executor comes with a switch that turns it off. This harness runs
 * the same fixtures with the switches off and with them on, and demands the two runs be
 * indistinguishable. That makes the safety argument the same for every stage instead of a bespoke
 * one per change, and it catches the failure mode a value assertion cannot: an optimisation that
 * evaluates a different set of nodes, or a different number of times, while still producing the
 * right answer — until the day one of those nodes has a side effect, draws from the shared RNG, or
 * costs a world lookup.
 *
 * <h2>Why all three signals</h2>
 * <ul>
 *   <li><b>Values, bit-exact.</b> A tolerance would hide the drift an executor change actually
 *       causes, such as a float that starts making an extra round trip through {@code double}. The
 *       rendering carries the runtime class too, so {@code Integer 1} and {@code Float 1.0} differ.</li>
 *   <li><b>Evaluation trace.</b> The only way to see laziness. See {@link EvalTrace}.</li>
 *   <li><b>Step count.</b> Specifically for the fused exec driver: a loop that swallowed or
 *       duplicated a step would otherwise be invisible.</li>
 * </ul>
 *
 * <h2>Modes</h2>
 * {@link Mode#FROZEN} skips the staleness digest, generalising the single-graph
 * {@code ExecutorEdgeCaseGameTest.frozenAgreesWithUnfrozen} to every fixture — subgraphs, loops and
 * NBT included. {@link Mode#UNOPTIMISED} turns off every {@link GraphExecutor.Opt}, which is how each
 * optimisation stage was admitted: it has to be indistinguishable from the path it replaced.
 *
 * <p>A new optimisation needs no change here — it adds a constant to {@code Opt} and
 * {@code UNOPTIMISED} picks it up. It does need a scenario that reaches it, which is a separate
 * thing and not automatic.
 */
public final class KGDifferential {

    private KGDifferential() {}

    /**
     * An executor configuration to compare.
     *
     * <p>New optimisations extend {@link #configure}. Keep every mode's <em>observable</em> behaviour
     * identical by construction — a mode that is allowed to differ is not a mode, it is a bug this
     * harness would report forever.</p>
     */
    public enum Mode {
        /** Stock configuration: every optimisation on, which is what ships. */
        DEFAULT,
        /** {@code setGraphFrozen(true)} — skips the per-run structural freshness digest. */
        FROZEN,
        /** Every optimisation switched off: the paths they replaced, which is the reference. */
        UNOPTIMISED;

        void configure(GraphExecutor exec) {
            if (this == FROZEN) exec.setGraphFrozen(true);
            if (this == UNOPTIMISED) {
                for (GraphExecutor.Opt o : GraphExecutor.Opt.values()) {
                    exec.setOptimisationEnabled(o, false);
                }
            }
        }
    }

    /**
     * A graph plus how to drive it and what to look at afterwards.
     *
     * @param builder     builds a fresh graph; called once per run so the two runs cannot share state
     * @param entry       name of the exec entry node, or {@code null} for a pure data pull
     * @param runs        how many times to drive the entry (a converging graph needs several)
     * @param seed        seeds the variable store before the run
     * @param outputRefs  {@link KGGraphBuilder} references to evaluate at the end
     * @param variables   variable-store keys to read at the end
     */
    public record Scenario(String name,
                           Supplier<KGGraphBuilder> builder,
                           @Nullable String entry,
                           int runs,
                           Consumer<VariableStore> seed,
                           List<String> outputRefs,
                           List<String> variables) {

        public static Scenario of(String name, Supplier<KGGraphBuilder> builder, String entry,
                                  List<String> variables) {
            return new Scenario(name, builder, entry, 1, store -> {}, List.of(), variables);
        }

        public static Scenario data(String name, Supplier<KGGraphBuilder> builder, List<String> outputRefs) {
            return new Scenario(name, builder, null, 1, store -> {}, outputRefs, List.of());
        }

        public Scenario repeated(int n) {
            return new Scenario(name, builder, entry, n, seed, outputRefs, variables);
        }

        public Scenario seededWith(Consumer<VariableStore> s) {
            return new Scenario(name, builder, entry, runs, s, outputRefs, variables);
        }

        public Scenario reading(String... refs) {
            return new Scenario(name, builder, entry, runs, seed, List.of(refs), variables);
        }
    }

    /** Everything observable about one run. */
    public record RunResult(List<String> values, EvalTrace trace, int stepCount) {}

    /** Build {@code scenario}'s graph and run it under {@code mode}. */
    public static RunResult run(Scenario scenario, Mode mode) {
        return run(scenario.builder().get(), scenario, mode);
    }

    /**
     * Run {@code scenario} under {@code mode} against an already-built graph.
     *
     * <p>Comparisons must go through this form with one shared graph. A trace identifies nodes by
     * uid, and rebuilding the graph mints new uids, so two runs over two separately-built copies
     * would report every event as divergent. Sharing the graph is also the honest arrangement: it is
     * exactly what happens in practice, where many executors run over one model. Nothing here
     * mutates the graph, and each run gets its own store and executor.</p>
     */
    public static RunResult run(KGGraphBuilder b, Scenario scenario, Mode mode) {
        var store = new VariableStore();
        scenario.seed().accept(store);

        var exec = new GraphExecutor(b.graph(), new EvaluationEnvironment(store, OptionalLong.of(20260814L)));
        mode.configure(exec);
        var trace = new EvalTrace();
        exec.setTrace(trace);

        int steps = 0;
        if (scenario.entry() != null) {
            for (int i = 0; i < scenario.runs(); i++) {
                exec.clearCache();
                // Driven through an explicit session rather than executeFrom, because the step count
                // is part of what is being compared and executeFrom does not expose it.
                var session = new ExecSession(exec).begin(b.node(scenario.entry()));
                session.runToCompletion();
                steps += session.stepCount();
            }
        }

        List<String> values = new ArrayList<>();
        for (String ref : scenario.outputRefs()) {
            values.add(ref + "=" + render(exec.evaluate(b.outputOf(ref), Object.class)));
        }
        for (String name : scenario.variables()) {
            values.add("$" + name + "=" + render(store.get(name)));
        }
        return new RunResult(values, trace, steps);
    }

    /**
     * Compare two runs, returning the first difference or {@code null} if they are indistinguishable.
     */
    @Nullable
    public static String compare(RunResult a, RunResult b) {
        if (a.stepCount() != b.stepCount()) {
            return "step count " + a.stepCount() + " vs " + b.stepCount();
        }
        if (a.values().size() != b.values().size()) {
            return "different number of observed values";
        }
        for (int i = 0; i < a.values().size(); i++) {
            if (!Objects.equals(a.values().get(i), b.values().get(i))) {
                return "value differs: " + a.values().get(i) + " vs " + b.values().get(i);
            }
        }
        return a.trace().diff(b.trace());
    }

    /** Run {@code scenario} under both modes over one shared graph, and report the first difference. */
    @Nullable
    public static String compareModes(Scenario scenario, Mode left, Mode right) {
        KGGraphBuilder b = scenario.builder().get();
        String diff = compare(run(b, scenario, left), run(b, scenario, right));
        return diff == null ? null : scenario.name() + " [" + left + " vs " + right + "]: " + diff;
    }

    /**
     * A value rendered so that two renderings are equal only when the values are truly identical.
     *
     * <p>Floats and doubles go through their raw bits: {@code 0.0} and {@code -0.0} are different
     * values that {@code equals} calls different but {@code ==} calls the same, and two NaNs with
     * different payloads are worth telling apart when the question is whether an arithmetic path
     * changed. The runtime class is included because a wrapper type change — an {@code Integer}
     * where a {@code Float} used to be — reaches {@code toString}, {@code equals} and every
     * downstream node, and is exactly the kind of drift a numeric lane can introduce.</p>
     */
    private static String render(@Nullable Object v) {
        if (v == null) return "null";
        String type = v.getClass().getSimpleName();
        if (v instanceof Float f) return type + ":" + Float.floatToRawIntBits(f);
        if (v instanceof Double d) return type + ":" + Double.doubleToRawLongBits(d);
        if (v instanceof CompoundTag t) return type + ":" + t;
        return type + ":" + v;
    }
}
