package com.lowdragmc.kilagraph.graph.mc;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The one place Minecraft's vector types cross into the graph's.
 *
 * <h2>Why there is a boundary at all</h2>
 * The blueprint graph has exactly one vector concept — JOML, via {@code KGTypeHandles.VEC2/3/4} — and
 * {@code net.minecraft.world.phys.Vec3} is deliberately <b>not</b> a pin type. Two vector concepts on
 * the same wires would mean every arithmetic node existing twice, and a user having to know which
 * flavour a given pin wanted. So Minecraft's vectors are converted at the edge of the MC node group
 * and never travel.
 *
 * <p>The cost, stated plainly: {@code Vec3} is {@code double} and {@code Vector3f} is {@code float},
 * so a position more than about 16 million blocks from the origin loses sub-block precision on the way
 * through. The world border is at 30 million, so this is reachable in principle and irrelevant in
 * practice — and it is the price of one vector concept instead of two.
 *
 * <p>There is no {@code Vec3i} pin type either, for the same reason plus a better one: {@code BlockPos}
 * already <em>is</em> the graph's integer triple and has a picker and a codec. A bare {@code Vec3i}
 * (which is what {@code Direction.getNormal()} hands back) is therefore split into three ints rather
 * than given a type of its own.
 */
public final class McConvert {

    private McConvert() {
    }

    /** A Minecraft vector as the graph's vector. Null-safe: null in, null out. */
    public static Vector3f toJoml(Vec3 v) {
        return v == null ? null : new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /** An integer Minecraft vector as the graph's vector. */
    public static Vector3f toJoml(Vec3i v) {
        return v == null ? null : new Vector3f(v.getX(), v.getY(), v.getZ());
    }

    /**
     * The graph's vector as a Minecraft vector, treating an absent value as the origin.
     *
     * <p>Origin rather than null because every consumer here feeds it straight into a constructor or
     * a world query, and {@code Vec3.ZERO} is the answer those give for "nothing supplied" anyway.</p>
     */
    public static Vec3 toVec3(Vector3f v) {
        return v == null ? Vec3.ZERO : new Vec3(v.x, v.y, v.z);
    }
}
