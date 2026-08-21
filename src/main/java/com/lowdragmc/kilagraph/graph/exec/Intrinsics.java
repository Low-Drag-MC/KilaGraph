package com.lowdragmc.kilagraph.graph.exec;

import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.NotEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.logic.NotNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.Atan2Node;
import com.lowdragmc.kilagraph.blueprint.nodes.math.DivideNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ExpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.FractNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LogBaseNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LogNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MaxNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MinNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ModuloNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.NegateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.PowNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.RemapNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SignNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.BranchNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.EntryNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.GateNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.NoopNode;
import com.lowdragmc.kilagraph.blueprint.nodes.exec.SetVarNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;

import java.util.Map;

/**
 * Arithmetic nodes the executor can evaluate without calling them.
 *
 * <h2>What this is</h2>
 * Each entry is a <em>transcription</em> of one node's {@code evaluate} body into a {@code switch}
 * case in {@link GraphExecutor}: the same input reads, in the same order, with the same default
 * literals, writing the same output. What it skips is the machinery around the body — binding a
 * pooled {@link EvalContext}, the megamorphic {@code evaluable.evaluate(ctx)} call site that every
 * node in the process shares, an id scan per input, and the staging-and-flush round trip.
 *
 * <h2>What it deliberately is not</h2>
 * It is <b>not</b> a scheduler and it does not decide when or how often a node runs. Every case pulls
 * its inputs through the same {@code pullFloat} the node would have used, at the same point, so
 * evaluation order and count are unchanged by construction. That is why this needs no notion of
 * purity and why a node that short-circuits ({@code And}, {@code Or}, {@code Select}) or memoises
 * ({@code Cache}) could be transcribed here too without changing its meaning — those are simply not
 * worth the risk yet.
 *
 * <h2>Why a table and not an annotation</h2>
 * An {@code @Intrinsic(ADD)} copy-pasted onto {@code SubtractNode} is invisible in review; a row in
 * this file sits next to the case it claims to match. A table is also enumerable, which is what lets
 * {@code IntrinsicParityGameTest} iterate it and fail if an opcode has no coverage. And a node not in
 * the table — every third-party node, and every node nobody has got to yet — takes the normal path,
 * so the safe outcome is the default rather than something to remember.
 *
 * <h2>Size discipline</h2>
 * The dispatch method must stay well under HotSpot's {@code DontCompileHugeMethods} limit of 8000
 * bytecodes, or it stops being JIT-compiled at all and the "optimisation" is a large regression that
 * looks like nothing. Split the switch by group before it gets close; do not merge the groups back
 * together to tidy up.
 */
public final class Intrinsics {

    private Intrinsics() {}

    static final int NONE = 0;

    // ---- arithmetic, dispatched by evalArithmetic ----
    static final int OP_ABS = 1;
    static final int OP_SQRT = 2;
    static final int OP_CLAMP = 3;
    static final int OP_SUB = 4;
    static final int OP_LERP = 5;
    static final int OP_NEGATE = 6;
    /** Variadic: reads {@code in1..inN} and its {@code inputs} option. */
    static final int OP_ADD = 7;
    /** Variadic, as {@link #OP_ADD}. */
    static final int OP_MUL = 8;
    static final int OP_SIGN = 9;
    static final int OP_FRACT = 10;
    static final int OP_EXP = 11;
    static final int OP_DIV = 12;
    static final int OP_MOD = 13;
    static final int OP_POW = 14;
    static final int OP_ATAN2 = 15;
    static final int OP_LOG = 16;
    static final int OP_LOGBASE = 17;
    static final int OP_REMAP = 18;
    /** Variadic, as {@link #OP_ADD}. */
    static final int OP_MIN = 19;
    /** Variadic, as {@link #OP_ADD}. */
    static final int OP_MAX = 20;

    /**
     * Opcodes at or above this are predicates and dispatch through a second method.
     *
     * <p>Two methods rather than one, because the dispatch has to stay well inside HotSpot's
     * {@code DontCompileHugeMethods} limit — see the class javadoc. This is the split, and it is a
     * numeric range so adding an opcode to the wrong side is a compile-time-visible mistake rather
     * than a silent one.</p>
     */
    static final int OP_PREDICATE_BASE = 50;

    static final int OP_GT = 50;
    static final int OP_GE = 51;
    static final int OP_LT = 52;
    static final int OP_LE = 53;
    static final int OP_NEQ = 54;
    static final int OP_NOT = 55;

    // ---- exec-side opcodes, dispatched by executeStep ----
    /** Unconditional flow along one exec output — {@code Entry}, {@code Noop}. */
    static final int XOP_FLOW = 1;
    /** {@code Branch}: one of two exec outputs, on a bool input. */
    static final int XOP_BRANCH = 2;
    /** {@code Gate}: one exec output, or none, on a bool input. */
    static final int XOP_GATE = 3;
    /** {@code SetVar}: write a variable, then flow. */
    static final int XOP_SETVAR = 4;

    /**
     * Node class → opcode. Every entry must have a matching case in
     * {@code GraphExecutor.evalIntrinsic} and a row in {@code IntrinsicParityGameTest}.
     */
    private static final Map<Class<?>, Integer> TABLE = Map.ofEntries(
            Map.entry(AbsNode.class, OP_ABS),
            Map.entry(SqrtNode.class, OP_SQRT),
            Map.entry(ClampNode.class, OP_CLAMP),
            Map.entry(SubtractNode.class, OP_SUB),
            Map.entry(LerpNode.class, OP_LERP),
            Map.entry(NegateNode.class, OP_NEGATE),
            Map.entry(AddNode.class, OP_ADD),
            Map.entry(MultiplyNode.class, OP_MUL),
            Map.entry(SignNode.class, OP_SIGN),
            Map.entry(FractNode.class, OP_FRACT),
            Map.entry(ExpNode.class, OP_EXP),
            Map.entry(DivideNode.class, OP_DIV),
            Map.entry(ModuloNode.class, OP_MOD),
            Map.entry(PowNode.class, OP_POW),
            Map.entry(Atan2Node.class, OP_ATAN2),
            Map.entry(LogNode.class, OP_LOG),
            Map.entry(LogBaseNode.class, OP_LOGBASE),
            Map.entry(RemapNode.class, OP_REMAP),
            Map.entry(MinNode.class, OP_MIN),
            Map.entry(MaxNode.class, OP_MAX),
            Map.entry(GreaterThanNode.class, OP_GT),
            Map.entry(GreaterEqualNode.class, OP_GE),
            Map.entry(LessThanNode.class, OP_LT),
            Map.entry(LessEqualNode.class, OP_LE),
            Map.entry(NotEqualsNode.class, OP_NEQ),
            Map.entry(NotNode.class, OP_NOT));

    /**
     * Node class → exec opcode. Same rules as {@link #TABLE}: a transcription of the node's
     * {@code execute} body, and anything not listed keeps its own path.
     */
    private static final Map<Class<?>, Integer> EXEC_TABLE = Map.of(
            EntryNode.class, XOP_FLOW,
            NoopNode.class, XOP_FLOW,
            BranchNode.class, XOP_BRANCH,
            GateNode.class, XOP_GATE,
            SetVarNode.class, XOP_SETVAR);

    /**
     * Resolve {@code n}'s exec opcode, the flow outputs it fires and the input it reads.
     *
     * <p>A missing port leaves it unbound rather than approximated. That matters more here than on
     * the data side: {@code ctx.flow(id)} <em>throws</em> for an unknown output, so an intrinsic that
     * guessed would turn a diagnostic into silence.</p>
     */
    static void bindExec(PreparedGraph.Node n) {
        if (n.asNodeModel == null || n.annotated == null) return;
        Integer op = EXEC_TABLE.get(n.annotated.getClass());
        if (op == null) return;

        int flowA;
        int flowB = -1;
        int in = -1;
        int aux = -1;
        switch (op) {
            case XOP_FLOW -> flowA = n.flowIndex(n.annotated instanceof EntryNode ? "next" : "out");
            case XOP_BRANCH -> {
                flowA = n.flowIndex("trueExec");
                flowB = n.flowIndex("falseExec");
                in = n.inputIndex("cond");
                if (flowB < 0 || in < 0) return;
            }
            case XOP_GATE -> {
                flowA = n.flowIndex("out");
                in = n.inputIndex("enabled");
                if (in < 0) return;
            }
            case XOP_SETVAR -> {
                flowA = n.flowIndex("next");
                in = n.inputIndex("value");
                aux = n.optionIndex("varName");
                if (in < 0 || aux < 0) return;
            }
            default -> {
                return;
            }
        }
        if (flowA < 0) return;

        n.execOp = op;
        n.execFlowA = flowA;
        n.execFlowB = flowB;
        n.execIn = in;
        n.execAux = aux;
    }

    /** Every node class with an intrinsic — what the parity test enumerates to prove coverage. */
    public static java.util.Set<Class<?>> classes() {
        return TABLE.keySet();
    }

    /** Every node class with an exec intrinsic. @see #classes() */
    public static java.util.Set<Class<?>> execClasses() {
        return EXEC_TABLE.keySet();
    }

    /**
     * Resolve {@code n}'s opcode and operand indices, or leave it on the normal path.
     *
     * <p>The port layout is checked here rather than assumed. A node whose ports are not what its
     * class implies — a redefine mid-edit, a shape this table has not been told about — simply does
     * not bind, and runs the way it always did.</p>
     */
    static void bind(PreparedGraph.Node n) {
        if (n.asNodeModel == null || n.evaluable == null) return;
        Integer op = TABLE.get(n.evaluable.getClass());
        if (op == null) return;

        int outSlot = soleOutputSlot(n, "out");
        if (outSlot < 0) return;

        int[] in;
        int aux = -1;
        switch (op) {
            case OP_ABS, OP_SQRT, OP_NEGATE, OP_SIGN, OP_FRACT, OP_EXP, OP_NOT -> in = inputs(n, "in");
            case OP_CLAMP -> in = inputs(n, "in", "min", "max");
            case OP_SUB, OP_GT, OP_GE, OP_LT, OP_LE, OP_NEQ, OP_DIV, OP_MOD -> in = inputs(n, "a", "b");
            case OP_LERP -> in = inputs(n, "a", "b", "t");
            case OP_POW -> in = inputs(n, "base", "exp");
            case OP_ATAN2 -> in = inputs(n, "y", "x");
            case OP_LOG -> in = inputs(n, "in", "base");
            case OP_LOGBASE -> in = inputs(n, "value", "base");
            case OP_REMAP -> in = inputs(n, "in", "fromMin", "fromMax", "toMin", "toMax");
            case OP_ADD, OP_MUL, OP_MIN, OP_MAX -> {
                in = numberedInputs(n);
                aux = n.optionIndex("inputs");
                // The node's arity comes from that option; without it there is nothing to agree with.
                if (aux < 0) in = null;
            }
            default -> in = null;
        }
        if (in == null) return;

        n.op = op;
        n.opIn = in;
        n.opOutSlot = outSlot;
        n.opAux = aux;
    }

    /** The slot of the one output called {@code portId}, or -1 if there is not exactly one. */
    private static int soleOutputSlot(PreparedGraph.Node n, String portId) {
        int found = -1;
        for (int k = 0; k < n.outputIds.length; k++) {
            if (!n.outputIds[k].equals(portId)) continue;
            if (found >= 0) return -1;   // two ports share the id; staging writes both, so bail
            found = n.outputSlots[k];
        }
        return found;
    }

    /** Input indices for {@code ids} in order, or null if any is missing. */
    private static int[] inputs(PreparedGraph.Node n, String... ids) {
        int[] out = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = n.inputIndex(ids[i]);
            if (out[i] < 0) return null;
        }
        return out;
    }

    /** Input indices for {@code in1..inN}, stopping at the first gap. Null if there are none. */
    private static int[] numberedInputs(PreparedGraph.Node n) {
        int count = 0;
        while (n.inputIndex(com.lowdragmc.kilagraph.graph.core.PortIds.in(count + 1)) >= 0) count++;
        if (count == 0) return null;   // no in1 at all: not the shape this opcode expects
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = n.inputIndex(com.lowdragmc.kilagraph.graph.core.PortIds.in(i + 1));
        }
        return out;
    }
}
