package com.lowdragmc.kilagraph.graph.type;

import com.mojang.serialization.Codec;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector values, independent of any node or graph — the component arithmetic behind
 * {@link KGTypeHandles#VECTOR} and the whole {@code vector} node group.
 *
 * <p>It lives in the type layer rather than beside the nodes because {@link KGTypeHandles} needs it:
 * the VECTOR handle's serialization ({@link #CODEC}) and its editor both have to know how a value's
 * width is read and rebuilt, and the type layer cannot depend on the node layer.</p>
 *
 * <p><b>Width is a property of the value, not of the port.</b> Everything here reads however many
 * components arrived and answers in kind. That is the whole model: a pin names a type, but the
 * number that matters travels with the JOML object on the wire.</p>
 */
public final class Vectors {

    /** The widths the graph carries. A vector is never 1 wide — that is a number. */
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 4;
    /** What an unwired VECTOR pin offers, and what {@code KGTypeHandles.VECTOR}'s default value is. */
    public static final int DEFAULT_WIDTH = 3;

    /**
     * Below this a length counts as zero.
     *
     * <p>One constant because every node that divides by a length has to agree: normalize, the
     * length clamp, the projection, the reflection and the axis rotation each guard the same
     * division, and a second threshold would mean two of them disagreeing about which vectors have
     * a direction.</p>
     */
    public static final float EPSILON = 1e-6f;

    /**
     * How a VECTOR port's embedded constant is stored: the components, in order.
     *
     * <p>The width is the list's length, which is the entire reason this exists. The default path —
     * {@code AccessorRegistries.findByType} on the port's declared Java type — would resolve
     * {@code Vector3f} and write exactly three floats, so a Vector2 constant would come back as a
     * Vector3 and a Vector4 would lose its w. {@code Constant.customCodec} takes precedence over
     * that path, and {@code IInputPortBuilder.withCodec} is how a port asks for it.</p>
     */
    public static final Codec<Object> CODEC = Codec.FLOAT.listOf()
            .xmap(Vectors::fromList, Vectors::toList);

    private Vectors() {
    }

    /**
     * The components behind a value, whatever width it carries.
     *
     * <p>Reading components rather than casting is what lets one node serve every width: a port can
     * hold a constant of a different width than it declares, and {@link KGGraphModel} lets any
     * vector wire reach any vector pin.</p>
     *
     * <p>A {@link Number} answers one component so that a scalar reaching a vector pin is arithmetic
     * rather than zero; anything else answers three zeroes, which is the shape a Vector3 pin would
     * have had anyway.</p>
     */
    public static float[] components(Object raw) {
        if (raw instanceof Vector4f v) {
            return new float[] {v.x, v.y, v.z, v.w};
        }
        if (raw instanceof Vector3f v) {
            return new float[] {v.x, v.y, v.z};
        }
        if (raw instanceof Vector2f v) {
            return new float[] {v.x, v.y};
        }
        if (raw instanceof Number n) {
            return new float[] {n.floatValue()};
        }
        return new float[] {0f, 0f, 0f};
    }

    /** The JOML shape for a width, so a value keeps its type across a wire. */
    public static Object carrier(float[] v) {
        return switch (v.length) {
            case 2 -> new Vector2f(v[0], v[1]);
            case 4 -> new Vector4f(v[0], v[1], v[2], v[3]);
            case 1 -> (Object) v[0];
            default -> new Vector3f(at(v, 0), at(v, 1), at(v, 2));
        };
    }

    /**
     * Component {@code index}, or zero when the value does not have one.
     *
     * <p>The negative half of the guard is not theoretical: {@code vector_get_component} takes its
     * index off a pin, so a graph can hand this any int it likes.</p>
     */
    public static float at(float[] v, int index) {
        return index >= 0 && index < v.length ? v[index] : 0f;
    }

    public static float lengthSquared(float[] v) {
        float sum = 0f;
        for (float c : v) {
            sum += c * c;
        }
        return sum;
    }

    /** The dot product of two component arrays, over the wider one's width. */
    public static float dot(float[] p, float[] q) {
        float sum = 0f;
        for (int i = 0, w = Math.max(p.length, q.length); i < w; i++) {
            sum += at(p, i) * at(q, i);
        }
        return sum;
    }

    /** The squared distance between two points, over the wider one's width. */
    public static float distanceSquared(float[] p, float[] q) {
        float sum = 0f;
        for (int i = 0, w = Math.max(p.length, q.length); i < w; i++) {
            float d = at(p, i) - at(q, i);
            sum += d * d;
        }
        return sum;
    }

    /** The same components at a new width: extra ones read zero, surplus ones are dropped. */
    public static float[] resize(float[] v, int width) {
        int w = clampWidth(width);
        float[] out = new float[w];
        for (int i = 0; i < w; i++) out[i] = at(v, i);
        return out;
    }

    /** A copy with one component replaced; an index the value does not have changes nothing. */
    public static float[] withComponent(float[] v, int index, float value) {
        float[] out = v.clone();
        if (index >= 0 && index < out.length) out[index] = value;
        return out;
    }

    public static int clampWidth(int width) {
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width));
    }

    /**
     * A stored component list back into a JOML vector.
     *
     * <p>Clamped to a real vector width: a one-element list would otherwise decode to a bare
     * {@code Float} through {@link #carrier}, which is not a value any vector port can hold, and a
     * longer one to nothing at all. Both are only reachable from hand-edited or corrupt NBT, so they
     * are widened/truncated rather than treated as a decode failure — the alternative disconnects
     * the port's wires (see {@code Constant.deserializeFailed}) over a recoverable value.</p>
     */
    private static Object fromList(List<Float> list) {
        float[] v = new float[clampWidth(list.size())];
        for (int i = 0; i < v.length; i++) v[i] = i < list.size() ? list.get(i) : 0f;
        return carrier(v);
    }

    private static List<Float> toList(Object value) {
        float[] raw = components(value);
        // resize with the array's own length, which is how the [2,4] clamp gets applied on the way
        // out as well as on the way in — a scalar that reached a vector port is stored as (x, 0).
        float[] v = resize(raw, raw.length);
        List<Float> out = new ArrayList<>(v.length);
        for (float c : v) out.add(c);
        return out;
    }
}
