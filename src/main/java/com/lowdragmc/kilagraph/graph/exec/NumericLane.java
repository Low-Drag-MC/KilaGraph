package com.lowdragmc.kilagraph.graph.exec;

import java.util.Objects;

/**
 * Which width a numeric node should do its arithmetic in, decided from the values that actually
 * arrive rather than from the types its ports declare.
 *
 * <h2>The problem this exists for</h2>
 * Every math and comparison node in the library declares {@code float} ports, and
 * {@code KGGraphModel.canAssignTo} deliberately lets any {@link Number} wire into any {@code Number}
 * port. So a {@code long} reaching a math node was silently converted, and a float carries only 24
 * bits of mantissa: past 2^24 consecutive whole numbers start sharing one float. A world's
 * {@code gameTime} passes 2^24 after about 9.7 days of runtime, at which point {@code gameTime % 40}
 * answers the same constant forever and every "is it time yet" test downstream freezes with it.
 *
 * <h2>The rule</h2>
 * Four lanes, {@link #INT} &lt; {@link #LONG} &lt; {@link #FLOAT} &lt; {@link #DOUBLE}; a node works in
 * the widest lane any of its inputs asks for. What each input asks for depends on how it is fed:
 * <ul>
 *   <li><b>A connected input names the lane.</b> Its runtime type decides: {@code Integer} and
 *       narrower ask for {@code INT}, a {@code Long} for {@code LONG}, a {@code Float} for
 *       {@code FLOAT}, anything else numeric for {@code DOUBLE}. A non-number asks for nothing.</li>
 *   <li><b>An unconnected constant is weak</b> — see {@link #ofConstant}. It adopts whatever lane the
 *       wires chose instead of forcing one, because the constant's <em>type</em> is an artifact of
 *       the port it sits on rather than something the user chose. Typing 40 into
 *       {@code Modulo}'s {@code b} field stores a {@code Float} only because that port is declared
 *       {@code float}; letting it drag {@code gameTime % 40} back into the float lane would undo the
 *       whole fix.</li>
 *   <li><b>Nothing asked for anything → {@code FLOAT}</b> (see {@link #resolve}). That is what keeps
 *       every graph built before this existed answering exactly what it used to: a node fed only
 *       float constants stays float.</li>
 * </ul>
 *
 * <p>Arithmetic in the {@code LONG} lane is Java's, so it wraps on overflow rather than drifting into
 * an approximation the way the float lane did. That is the trade: exact up to 2^63 and then wrong
 * loudly, instead of approximate from 2^24 and wrong quietly.</p>
 *
 * <h2>What the fold costs</h2>
 * The first implementation walked a node's operands <em>twice</em>: once through {@code pullLane} to
 * decide the lane, then again through {@code pullFloat} to read them. Measured against itself through
 * {@code Opt.NUMERIC_PROMOTION}, that cost <b>+3.6 to +4.4 ns per node step</b> on a chain of
 * {@code Add}s with one wired input and one embedded float constant each, and <b>+1.2 to +2.5</b> on a
 * chain of {@code Abs} with a wired input and no constant — the constant path being dearer because
 * {@code Constant.getValue()} was being called twice per evaluation.
 *
 * <p>It now reads each operand once and folds the lane as it goes ({@code GraphExecutor.pullFloatLane},
 * {@code Opt.SINGLE_PASS_LANE}). One pass beat two on every one of six measurements, sign-stable.
 * Against a baseline that does no classification at all, what is left is:
 * <ul>
 *   <li><b>~+0.4 to +0.7 ns per node step</b> on the {@code Add} chain — roughly 5% of that node's
 *       ~11 ns step, down from 35-40%;</li>
 *   <li><b>~+0.1 to +0.4 ns per node step</b> on the {@code Abs} chain — roughly 2%.</li>
 * </ul>
 * No single one of those ten measurements clears {@code Comparison.conclusive()}, because the delta is
 * now smaller than the harness's own spread; all ten are positive, which is the evidence there is.
 * Treat them as slight <em>under</em>-estimates: the off arm still writes {@code lastLane} and the
 * cases still fold it, so a little of the cost is on both sides. See
 * {@code ExecutorBenchShapesGameTest.numericPromotionCost}.
 *
 * <p><b>Two things were tried and do not work.</b> A per-input memo of the constant's lane, keyed by
 * the constant's value identity — sound, since an unedited constant hands back the same box — measured
 * as <em>nothing</em> against its own switch (-0.19 ns per node step, sign unstable) while making the
 * whole check about 40% <em>slower</em>: {@code pullLane} grew past what the inliner would take and
 * both sides of its own comparison regressed together. And there is nothing for a memo to save in the
 * first place, because a node is already memoised per generation — the fold runs once per node per
 * run either way. If the cost has to go further, the direction that can reach zero is inferring at
 * prepare time which nodes can never see a non-float value, not making the runtime check cleverer.</p>
 *
 * <p>The node-side path ({@link EvalContext#lane}) is still two-pass. It is reached only when
 * intrinsics are off or when the lane is not float, so it is off the hot path in both cases.</p>
 *
 * <h2>Where it is applied</h2>
 * Only to the operations that are <em>closed over whole numbers</em> — add, subtract, multiply,
 * negate, abs, sign, min, max, clamp, modulo, and the four order comparisons. Everything real-valued
 * ({@code Sqrt}, {@code Exp}, {@code Log}, {@code Trig}, {@code Atan2}, {@code Lerp}, {@code Fract},
 * {@code Remap}, {@code Pow}) stays in the float lane whatever it is fed, because promoting it would
 * buy nothing. {@code Divide} stays float on purpose: silently turning {@code 7 / 2} into {@code 3}
 * is a worse trap than the one being fixed.
 */
public final class NumericLane {

    private NumericLane() {}

    /** No opinion — the input is not a number, or is a weak constant. Lower than every real lane. */
    public static final byte NONE = -1;
    /**
     * Whole numbers that fit 32 bits.
     *
     * <p>The arithmetic nodes do not distinguish this from {@link #LONG} — they work in {@code long}
     * for both, since a wider result is never a worse one. It exists for the <em>bitwise</em> nodes,
     * where the width is the meaning rather than a container: {@code 1 << 35} is 8 in 32 bits and
     * 34359738368 in 64, and both are correct answers to different questions.</p>
     */
    public static final byte INT = 0;
    /** Whole numbers, carried as {@code long}. */
    public static final byte LONG = 1;
    /** {@code float}. The lane everything used to be in, and the fallback when nothing asks. */
    public static final byte FLOAT = 2;
    /** {@code double} — for a producer that is genuinely double-precision. */
    public static final byte DOUBLE = 3;

    /** The wider of two lanes. {@link #NONE} loses to everything, which is what makes it the identity. */
    public static byte widen(byte a, byte b) {
        return a > b ? a : b;
    }

    /** A folded lane as a lane to actually compute in: nothing asked for anything means {@link #FLOAT}. */
    public static byte resolve(byte lane) {
        return lane == NONE ? FLOAT : lane;
    }

    /**
     * The lane a value on a wire asks for.
     *
     * <p>{@code Byte}/{@code Short}/{@code Integer}/{@code Long} are the whole-number types the
     * executor's numeric lane and {@code EvalContext.coerce} can produce, so those ask for a
     * whole-number lane. Any other {@link Number} — {@code BigInteger}, {@code BigDecimal}, a third
     * party's own — asks for {@link #DOUBLE}: that is where {@code Number.doubleValue()} puts it, and
     * claiming otherwise would be claiming a precision this class cannot deliver through
     * {@code longValue()}.</p>
     */
    public static byte of(Object value) {
        if (value instanceof Long) return LONG;
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) return INT;
        if (value instanceof Float) return FLOAT;
        if (value instanceof Number) return DOUBLE;
        return NONE;
    }

    /**
     * The lane an <em>unconnected</em> input's embedded constant asks for — usually nothing.
     *
     * <p>A float-typed constant holding a whole number is exactly the case the weak rule is for: the
     * user typed 40 and the port stored {@code 40.0f}, and there is no evidence they wanted float
     * arithmetic. One holding {@code 0.5} is different — a fraction is only expressible in the float
     * lanes, so it asks for one, and {@code longWire - 0.5} stays fractional as it should.</p>
     *
     * <p>A constant whose type is already whole is not weak: nothing about it is an artifact, so it
     * names {@link #LONG} the way a wire would.</p>
     */
    public static byte ofConstant(Object value) {
        if (value instanceof Float f) return isWhole(f) ? NONE : FLOAT;
        if (value instanceof Double d) return isWhole(d) ? NONE : DOUBLE;
        return of(value);
    }

    /** The lane a computed slot's {@code GraphExecutor.KIND_*} tag asks for. */
    static byte ofKind(byte kind) {
        return switch (kind) {
            case GraphExecutor.KIND_INT -> INT;
            case GraphExecutor.KIND_LONG -> LONG;
            case GraphExecutor.KIND_FLOAT -> FLOAT;
            case GraphExecutor.KIND_DOUBLE -> DOUBLE;
            // KIND_OBJECT — the caller inspects the boxed value with of(Object) instead.
            default -> NONE;
        };
    }

    /**
     * Whether {@code d} is a whole number that a {@code long} can hold exactly.
     *
     * <p>NaN fails the first test ({@code NaN != NaN}), and both infinities fail the range test, so
     * all three are treated as fractional and keep their float lane — which is right: none of them
     * survives a trip through {@code long}.</p>
     *
     * <p>The bounds are the {@code double}s nearest ±2^63. {@code -9.223372036854776E18} <em>is</em>
     * {@code Long.MIN_VALUE} exactly, so it is included; the positive bound is 2^63, which is one
     * past {@code Long.MAX_VALUE}, so it is not.</p>
     */
    private static boolean isWhole(double d) {
        return d == Math.rint(d) && d >= -9.223372036854776E18 && d < 9.223372036854776E18;
    }

    /** Whether {@code n} is one of the whole-number types {@link #of} maps to {@link #INT}/{@link #LONG}. */
    public static boolean isIntegral(Number n) {
        return n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte;
    }

    // ---- equality ---------------------------------------------------------------------------

    /**
     * Equality for the {@code Equals} / {@code NotEquals} nodes: numbers compare by value, everything
     * else by {@link Objects#equals}.
     *
     * <p>{@code Objects.equals} alone was the mirror image of the precision bug. {@code Long.equals}
     * requires the other side to be a {@code Long}, so a 5 that arrived as an {@code Integer} and a 5
     * that arrived as a {@code Float} compared <em>unequal</em> — and which of those a wire carries is
     * an implementation detail of whichever node produced it. Players were being asked to know it.</p>
     */
    public static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) return numbersEqual(na, nb);
        return Objects.equals(a, b);
    }

    /**
     * Whether two numbers denote the same value, exactly — including across the whole/fractional
     * divide.
     *
     * <p>Two whole numbers compare as {@code long}, two fractional ones as {@code double}. Mixed is
     * the case worth spelling out: comparing as {@code double} would make
     * {@code 9007199254740993 == 9007199254740992.0} true, because the long does not survive the
     * conversion. Going the other way does survive — a {@code double} that is whole and in range
     * converts to a {@code long} losslessly — so that is the direction this takes. A fractional
     * value (or a NaN, or an infinity) can never equal a whole one, hence the early {@code false}.</p>
     */
    public static boolean numbersEqual(Number a, Number b) {
        boolean wholeA = isIntegral(a);
        boolean wholeB = isIntegral(b);
        if (wholeA && wholeB) return a.longValue() == b.longValue();
        if (wholeA || wholeB) {
            double d = (wholeA ? b : a).doubleValue();
            if (!isWhole(d)) return false;
            return (wholeA ? a : b).longValue() == (long) d;
        }
        return a.doubleValue() == b.doubleValue();
    }
}
