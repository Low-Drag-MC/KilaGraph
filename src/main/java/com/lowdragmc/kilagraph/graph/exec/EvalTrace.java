package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The order and count of node evaluations during a run.
 *
 * <h2>Why the final value is not enough</h2>
 * {@link PreparedGraph} states the invariant this class exists to check: preparation may change
 * addressing, never evaluation order or count, <em>because laziness is observable</em>. {@code And}
 * and {@code Or} short-circuit, {@code Select} pulls only the taken branch, {@code Cache} pulls once
 * ever, and {@code Random} draws from a shared RNG.
 *
 * <p>Every one of those can be broken without changing the answer of the graph under test. An
 * optimisation that evaluates both sides of a {@code Select} still produces the right value — until
 * the untaken side is a {@code Random}, or reads a variable a loop is about to overwrite, or costs a
 * world lookup. A test that only asserts the output is green for all of them. So the executor is
 * asked what it actually did, and two runs are compared on that.
 *
 * <h2>Cost</h2>
 * A trace is opt-in: {@link GraphExecutor#setTrace} defaults to {@code null} and the executor's hot
 * paths pay one null check against a field that is null for the whole run — perfectly predicted, and
 * nothing is allocated. It is not free, it is just far below the noise floor of anything measurable.
 * Nothing outside tests turns it on.
 *
 * <h2>Scope</h2>
 * A trace follows subgraph entry into child executors (see {@code GraphExecutor.subgraphEnter}), so
 * one trace covers a whole call tree rather than only the outermost graph. That is deliberate: the
 * subgraph path is where evaluation count is easiest to get wrong and hardest to see.
 */
public final class EvalTrace {

    /** What the executor did. {@code EVAL} is a data-node evaluation, {@code EXEC} an exec step. */
    public enum Kind { EVAL, EXEC }

    /**
     * One thing the executor did.
     *
     * @param label the node implementation's simple class name, carried so a failure message reads
     *              {@code MultiplyNode} rather than a bare UUID
     */
    public record Event(Kind kind, UUID node, String label) {
        /**
         * {@code EXEC SetVarNode#3f2a}.
         *
         * <p>The uid fragment is there because the label alone is not a diagnostic: a graph with two
         * {@code SetVar}s in it reports a divergence as "SetVarNode vs SetVarNode", which says only
         * that something is wrong. Four hex digits are enough to tell two nodes apart in a message
         * and short enough not to bury the label.</p>
         */
        @Override
        public String toString() {
            String uid = node.toString();
            return kind + " " + label + "#" + uid.substring(0, Math.min(4, uid.length()));
        }
    }

    private final List<Event> events = new ArrayList<>();

    /**
     * Record a data-node evaluation.
     *
     * <p>Called from {@code evaluateNode}, which the memo in {@code ensureComputed} reaches exactly
     * once per node per generation — so the number of {@code EVAL} events for a node <em>is</em> the
     * number of times it was really evaluated, which is the quantity under test.</p>
     */
    void recordEval(AbstractNodeModel node) {
        events.add(new Event(Kind.EVAL, node.getUid(), labelOf(node)));
    }

    /** Record an exec step. */
    void recordExec(AbstractNodeModel node) {
        events.add(new Event(Kind.EXEC, node.getUid(), labelOf(node)));
    }

    private static String labelOf(AbstractNodeModel node) {
        if (node instanceof ICustomNodeModel custom && custom.getNode() != null) {
            return custom.getNode().getClass().getSimpleName();
        }
        return node.getClass().getSimpleName();
    }

    // ---- reading -----------------------------------------------------------------------------

    /** Every event, in the order it happened. */
    public List<Event> events() {
        return List.copyOf(events);
    }

    public int size() {
        return events.size();
    }

    /** How many times {@code nodeUid} was evaluated as a data node. */
    public int evalCount(UUID nodeUid) {
        return count(Kind.EVAL, nodeUid);
    }

    /** How many times {@code nodeUid} ran as an exec step. */
    public int execCount(UUID nodeUid) {
        return count(Kind.EXEC, nodeUid);
    }

    private int count(Kind kind, UUID nodeUid) {
        int n = 0;
        for (Event e : events) {
            if (e.kind == kind && e.node.equals(nodeUid)) n++;
        }
        return n;
    }

    /** How many events name a node of this implementation class. Handy when uids are not to hand. */
    public int countByLabel(String label) {
        int n = 0;
        for (Event e : events) {
            if (e.label.equals(label)) n++;
        }
        return n;
    }

    public void clear() {
        events.clear();
    }

    // ---- comparison --------------------------------------------------------------------------

    /**
     * The first position at which this trace differs from {@code other}, or {@code null} if they are
     * identical — the message names the position and both sides, because "traces differ" on a
     * hundred-event run is not something a human can act on.
     */
    @Nullable
    public String diff(EvalTrace other) {
        int n = Math.min(events.size(), other.events.size());
        for (int i = 0; i < n; i++) {
            Event a = events.get(i);
            Event b = other.events.get(i);
            if (a.kind != b.kind || !a.node.equals(b.node)) {
                return "traces diverge at #" + i + ": " + a + " vs " + b + context(other, i);
            }
        }
        if (events.size() != other.events.size()) {
            return "traces have different lengths: " + events.size() + " vs " + other.events.size()
                    + context(other, n);
        }
        return null;
    }

    /** A few events either side of {@code at}, so a divergence can be located in the graph. */
    private String context(EvalTrace other, int at) {
        int from = Math.max(0, at - 3);
        return "\n  this : " + window(events, from, at + 4)
                + "\n  other: " + window(other.events, from, at + 4);
    }

    private static String window(List<Event> list, int from, int to) {
        return list.subList(Math.min(from, list.size()), Math.min(to, list.size())).toString();
    }

    @Override
    public String toString() {
        return "EvalTrace(" + events.size() + " events)";
    }
}
