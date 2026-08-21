package com.lowdragmc.kilagraph.test.gametest.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.GreaterThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessEqualNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.LessThanNode;
import com.lowdragmc.kilagraph.blueprint.nodes.compare.NotEqualsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.convert.ToStringNode;
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
import com.lowdragmc.kilagraph.blueprint.nodes.math.AbsNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.AddNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.ClampNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.LerpNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.MultiplyNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SqrtNode;
import com.lowdragmc.kilagraph.blueprint.nodes.math.SubtractNode;
import com.lowdragmc.kilagraph.graph.exec.GraphExecutor;
import com.lowdragmc.kilagraph.graph.exec.Intrinsics;
import com.lowdragmc.kilagraph.test.gametest.KGGraphBuilder;
import com.lowdragmc.kilagraph.test.gametest.KGGraphFixtures;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every intrinsic answers bit-for-bit what its node answers.
 *
 * <p>An intrinsic is a hand transcription of a node's {@code evaluate} body, so the way it goes wrong
 * is a transcription slip: an operand swapped, an operator inverted, a default literal copied from
 * the wrong line. None of those are visible in review with any reliability, and most of them agree
 * with the original on the values a normal test would use — {@code max(lo, min(hi, v))} against
 * {@code min(hi, max(lo, v))} differ only when {@code lo > hi}.</p>
 *
 * <p>So every entry in {@link Intrinsics#classes()} is run both ways over a cross product of edge
 * values, and the results are compared as raw bits. Raw bits rather than {@code equals}: {@code 0.0}
 * and {@code -0.0} are equal under {@code ==} and different values, and two NaNs are unequal under
 * {@code ==} and the same value. Only the bits say what actually came out.</p>
 *
 * <p>Each node is tested twice over: once with its inputs fed by wires, once with them as embedded
 * constants. Those are different code paths inside the pull — a wired input reads a producing slot,
 * an unwired one reads a {@code Constant} — and an intrinsic that resolved its operands wrongly can
 * easily pass one and fail the other.</p>
 *
 * <p>{@link #everyIntrinsicIsCovered} fails if a class is added to the table without being added
 * here, so an opcode cannot ship untested.</p>
 */
@GameTestHolder(Kilagraph.MODID)
public final class IntrinsicParityGameTest {

    private IntrinsicParityGameTest() {}

    /**
     * Values chosen so a slip shows up: the signed zeroes separate {@code ==} from bit equality, the
     * infinities and NaN travel differently through {@code Math.sqrt} and comparisons, and the
     * extremes catch a default literal borrowed from the wrong input.
     */
    private static final float[] VALUES = {
            0f, -0f, 1f, -1f, 0.5f, -3.7f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.MAX_VALUE, Float.MIN_VALUE, 1e30f,
    };

    /**
     * A narrower set for nodes with more than three inputs.
     *
     * <p>{@code Remap} takes five, and the full cross product of twelve values over five inputs is a
     * quarter of a million evaluations per variant. These six keep both signs, both zeroes, a NaN
     * and an infinity, which is what the arithmetic actually branches on.</p>
     */
    private static final float[] WIDE_VALUES = {0f, -0f, 1f, -3.7f, Float.NaN, Float.POSITIVE_INFINITY};

    /** Booleans, for the nodes whose inputs are boolean rather than numeric. */
    private static final Object[] BOOL_VALUES = {Boolean.TRUE, Boolean.FALSE};

    /**
     * One node under test.
     *
     * @param logical  inputs are boolean, so the numeric value table would only ever exercise the
     *                 default. Such a node is not run through the wired variant: producing a boolean
     *                 on a wire needs a second node, and the value it would carry is already covered
     *                 by the constant and defaulted variants.
     * @param wiredOnly the inputs are {@code UNKNOWN}-typed and so carry no embedded constant —
     *                 LDLib2 only creates one for a port with a concrete type. The constant and
     *                 defaulted variants cannot be built for such a node at all; there is nothing to
     *                 set. {@code NotEquals} compares arbitrary objects, so both its inputs are.
     */
    private record Spec(Class<? extends Node> nodeClass, List<String> inputs,
                        boolean logical, boolean wiredOnly) {
        Spec(Class<? extends Node> nodeClass, List<String> inputs) {
            this(nodeClass, inputs, false, false);
        }

        Spec(Class<? extends Node> nodeClass, List<String> inputs, boolean logical) {
            this(nodeClass, inputs, logical, false);
        }

        Object[] values() {
            if (logical) return BOOL_VALUES;
            float[] src = inputs.size() > 3 ? WIDE_VALUES : VALUES;
            Object[] out = new Object[src.length];
            for (int i = 0; i < src.length; i++) out[i] = src[i];
            return out;
        }
    }

    private static List<Spec> specs() {
        return List.of(
                // one numeric input
                new Spec(AbsNode.class, List.of("in")),
                new Spec(SqrtNode.class, List.of("in")),
                new Spec(NegateNode.class, List.of("in")),
                new Spec(SignNode.class, List.of("in")),
                new Spec(FractNode.class, List.of("in")),
                new Spec(ExpNode.class, List.of("in")),
                // two numeric inputs
                new Spec(SubtractNode.class, List.of("a", "b")),
                new Spec(DivideNode.class, List.of("a", "b")),
                new Spec(ModuloNode.class, List.of("a", "b")),
                new Spec(PowNode.class, List.of("base", "exp")),
                new Spec(Atan2Node.class, List.of("y", "x")),
                new Spec(LogNode.class, List.of("in", "base")),
                new Spec(LogBaseNode.class, List.of("value", "base")),
                // variadic
                new Spec(AddNode.class, List.of("in1", "in2")),
                new Spec(MultiplyNode.class, List.of("in1", "in2")),
                new Spec(MinNode.class, List.of("in1", "in2")),
                new Spec(MaxNode.class, List.of("in1", "in2")),
                // three or more
                new Spec(ClampNode.class, List.of("in", "min", "max")),
                new Spec(LerpNode.class, List.of("a", "b", "t")),
                new Spec(RemapNode.class, List.of("in", "fromMin", "fromMax", "toMin", "toMax")),
                // predicates
                new Spec(GreaterThanNode.class, List.of("a", "b")),
                new Spec(GreaterEqualNode.class, List.of("a", "b")),
                new Spec(LessThanNode.class, List.of("a", "b")),
                new Spec(LessEqualNode.class, List.of("a", "b")),
                new Spec(NotEqualsNode.class, List.of("a", "b"), false, true),
                new Spec(NotNode.class, List.of("in"), true));
    }

    /** Inputs fed by wires — the producing-slot path. */
    @GameTest(template = "empty", timeoutTicks = 2000)
    @PrefixGameTestTemplate(false)
    public static void intrinsicsMatchTheirNodesWhenWired(GameTestHelper helper) {
        for (Spec spec : specs()) {
            if (spec.logical()) continue;   // see Spec.logical
            String failure = check(spec, true);
            if (failure != null) {
                helper.fail(failure);
                return;
            }
        }
        helper.succeed();
    }

    /** Inputs as embedded constants — the {@code Constant} path. */
    @GameTest(template = "empty", timeoutTicks = 2000)
    @PrefixGameTestTemplate(false)
    public static void intrinsicsMatchTheirNodesWhenConstant(GameTestHelper helper) {
        for (Spec spec : specs()) {
            if (spec.wiredOnly()) continue;   // see Spec.wiredOnly
            String failure = check(spec, false);
            if (failure != null) {
                helper.fail(failure);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * One input at a time fed something that is not a number, so the reader falls back to its
     * <em>default literal</em> — the line most likely to be copied from the wrong place.
     *
     * <p>Neither of the other two variants reaches it. They give every input a value, so the default
     * is never taken and {@code Lerp}'s {@code b} could default to {@code 0f} instead of {@code 1f}
     * with nothing noticing. A {@code String} on the wire is the reachable way to get there:
     * {@code pullFloat} uses its default for anything that is not a {@link Number}.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 2000)
    @PrefixGameTestTemplate(false)
    public static void intrinsicsMatchTheirNodesOnDefaultedInputs(GameTestHelper helper) {
        for (Spec spec : specs()) {
            if (spec.wiredOnly()) continue;   // see Spec.wiredOnly
            for (int skipped = 0; skipped < spec.inputs().size(); skipped++) {
                String failure = checkDefaulted(spec, skipped);
                if (failure != null) {
                    helper.fail(failure);
                    return;
                }
            }
        }
        helper.succeed();
    }

    /** As {@link #check}, but input {@code skipped} carries a String so its default is used. */
    private static String checkDefaulted(Spec spec, int skipped) {
        var b = KGGraphBuilder.blueprint();
        b.add("node", spec.nodeClass());
        for (int i = 0; i < spec.inputs().size(); i++) {
            String id = spec.inputs().get(i);
            if (i == skipped) {
                // ToString's input is UNKNOWN-typed and so carries no embedded constant; it has to
                // be fed by a wire, like every other UNKNOWN port in the library.
                b.add("num", AddNode.class).constant("num.in1", 7f).constant("num.in2", 0f);
                b.add("text", ToStringNode.class).wire("text.in", "num");
                b.wire("node." + id, "text.out");
            } else {
                b.constant("node." + id, spec.values()[0]);
            }
        }

        var on = new GraphExecutor(b.graph());
        var off = new GraphExecutor(b.graph());
        on.setGraphFrozen(true);
        off.setGraphFrozen(true);
        off.setOptimisationEnabled(GraphExecutor.Opt.INTRINSICS, false);
        var out = b.outputOf("node.out");

        for (Object v : spec.values()) {
            for (int i = 0; i < spec.inputs().size(); i++) {
                if (i != skipped) b.constant("node." + spec.inputs().get(i), v);
            }
            on.clearCache();
            off.clearCache();
            Object a = on.evaluate(out, Object.class);
            Object c = off.evaluate(out, Object.class);
            if (!identical(a, c)) {
                return spec.nodeClass().getSimpleName() + " [default on '"
                        + spec.inputs().get(skipped) + "', others=" + v + "]: intrinsic gave "
                        + render(a) + ", node gave " + render(c);
            }
        }
        return null;
    }

    /** No opcode ships without a row above. */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyIntrinsicIsCovered(GameTestHelper helper) {
        Set<Class<?>> covered = new HashSet<>();
        for (Spec s : specs()) covered.add(s.nodeClass());
        for (Class<?> c : Intrinsics.classes()) {
            if (!covered.contains(c)) {
                helper.fail("intrinsic for " + c.getSimpleName() + " has no parity coverage; add a "
                        + "Spec for it in IntrinsicParityGameTest");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Every exec intrinsic appears in the graph the differential harness compares both ways.
     *
     * <p>The exec side has no value-level parity test of its own — an exec node produces control
     * flow rather than a value, so the thing to compare is which nodes ran, which is what
     * {@code DifferentialGameTest} and {@code ExecDriverGameTest} already do over their traces. What
     * was missing was any guarantee that all five opcodes actually appear in something they compare:
     * {@code Gate} did not, until {@code execIntrinsicSampler} was written for it.</p>
     */
    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void everyExecIntrinsicIsCovered(GameTestHelper helper) {
        var sampler = KGGraphFixtures.execIntrinsicSampler();
        Set<String> present = new HashSet<>();
        for (var node : sampler.allNodes()) {
            if (node instanceof ICustomNodeModel custom && custom.getNode() != null) {
                present.add(custom.getNode().getClass().getSimpleName());
            }
        }
        for (Class<?> c : Intrinsics.execClasses()) {
            if (!present.contains(c.getSimpleName())) {
                helper.fail("exec intrinsic for " + c.getSimpleName() + " appears in no differential "
                        + "scenario; add it to KGGraphFixtures.execIntrinsicSampler");
                return;
            }
        }
        helper.succeed();
    }

    // ---- the comparison ----------------------------------------------------------------------

    /**
     * Drive every combination of {@link #VALUES} across {@code spec}'s inputs, with intrinsics on and
     * off, and return the first disagreement — or null.
     *
     * <p>One graph, two executors. Building a graph per combination would dominate the runtime and,
     * worse, would give each side its own node objects: the point is that the same graph produces
     * the same answer down two paths.</p>
     */
    private static String check(Spec spec, boolean wired) {
        var b = KGGraphBuilder.blueprint();
        b.add("node", spec.nodeClass());
        for (String id : spec.inputs()) {
            if (wired) {
                String src = "src_" + id;
                b.add(src, AddNode.class).constant(src + ".in1", 0f).constant(src + ".in2", 0f);
                b.wire("node." + id, src);
            } else {
                b.constant("node." + id, spec.values()[0]);
            }
        }

        var on = new GraphExecutor(b.graph());
        var off = new GraphExecutor(b.graph());
        on.setGraphFrozen(true);
        off.setGraphFrozen(true);
        off.setOptimisationEnabled(GraphExecutor.Opt.INTRINSICS, false);
        var out = b.outputOf("node.out");

        Object[] values = spec.values();
        int n = spec.inputs().size();
        int[] at = new int[n];
        List<String> combo = new ArrayList<>(n);
        while (true) {
            for (int i = 0; i < n; i++) {
                String id = spec.inputs().get(i);
                Object v = values[at[i]];
                if (wired) b.constant("src_" + id + ".in1", v);
                else b.constant("node." + id, v);
            }
            on.clearCache();
            off.clearCache();
            Object a = on.evaluate(out, Object.class);
            Object c = off.evaluate(out, Object.class);
            if (!identical(a, c)) {
                combo.clear();
                for (int i = 0; i < n; i++) combo.add(spec.inputs().get(i) + "=" + values[at[i]]);
                return spec.nodeClass().getSimpleName() + (wired ? " [wired] " : " [constant] ")
                        + combo + ": intrinsic gave " + render(a) + ", node gave " + render(c);
            }
            // odometer over the value table
            int i = n - 1;
            while (i >= 0 && ++at[i] == values.length) at[i--] = 0;
            if (i < 0) break;
        }
        return null;
    }

    /**
     * Bit equality, and the same runtime type — a {@code Float} where an {@code Integer} was is a bug.
     *
     * <p>With one exception: <b>all NaNs count as equal</b>. Java does not specify which NaN an
     * operation produces, only that it produces one, and the two paths genuinely differ here — this
     * test's first run reported {@code Lerp(∞, ∞, NaN)} giving {@code 0x7FC00000} one way and
     * {@code 0xFFC00000} the other. Both are NaN; {@code ∞ - ∞} yields the sign-set quiet NaN on SSE
     * and the canonical one elsewhere, so which appears depends on whether that code was compiled
     * yet. Demanding bit equality there would be demanding something the language does not promise,
     * and the test would pass or fail on JIT state.</p>
     *
     * <p>Signed zeroes are <em>not</em> given the same treatment: {@code 0.0} and {@code -0.0} are
     * distinct values Java does specify, they are equal under {@code ==}, and only the bits tell
     * them apart — which is most of why this compares bits at all.</p>
     */
    private static boolean identical(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof Float f) {
            float g = (Float) b;
            if (Float.isNaN(f) && Float.isNaN(g)) return true;
            return Float.floatToRawIntBits(f) == Float.floatToRawIntBits(g);
        }
        if (a instanceof Double d) {
            double e = (Double) b;
            if (Double.isNaN(d) && Double.isNaN(e)) return true;
            return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(e);
        }
        return a.equals(b);
    }

    private static String render(Object v) {
        if (v instanceof Float f) return "Float:" + f + "(bits " + Float.floatToRawIntBits(f) + ")";
        return v == null ? "null" : v.getClass().getSimpleName() + ":" + v;
    }
}
