package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Angle unit for nodes that take a rotation input. {@link #DEGREES} values are converted to radians
 * (via GLSL {@code radians()}) before the trig math, so the underlying formula always works in
 * radians.
 *
 * <p>The serialized name is an explicit, stored string rather than a computed
 * {@code name().toLowerCase()}: it gives a stable on-disk form decoupled from the Java constant
 * names, and — unlike a freshly-allocated string — stays strongly referenced, which the option
 * serializer relies on when mapping a saved name back to the enum.
 */
public enum AngleUnit implements StringRepresentable {
    RADIANS("radians"),
    DEGREES("degrees");

    private final String id;

    AngleUnit(String id) {
        this.id = id;
    }

    @Override
    public @NotNull String getSerializedName() {
        return id;
    }
}