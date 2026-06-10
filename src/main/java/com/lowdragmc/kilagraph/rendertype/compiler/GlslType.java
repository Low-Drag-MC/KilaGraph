package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;

import org.jetbrains.annotations.Nullable;

/**
 * Maps a graph {@link TypeHandle} to a concrete GLSL type used during shader code generation.
 *
 * <p>Scalars/vectors carry a {@link #components} count so the compiler can perform the same
 * implicit float&harr;vecN broadcast/swizzle that {@code RenderTypeGraphModel.canAssignTo}
 * permits in the editor. {@link #SAMPLER2D} is an opaque handle type (no component count) — it
 * never participates in arithmetic, only feeds {@code texture(...)} calls.</p>
 */
public enum GlslType {
    FLOAT("float", 1),
    VEC2("vec2", 2),
    VEC3("vec3", 3),
    VEC4("vec4", 4),
    INT("int", 1),
    BOOL("bool", 1),
    MAT4("mat4", 0),
    SAMPLER2D("sampler2D", 0);

    private final String glsl;
    private final int components;

    GlslType(String glsl, int components) {
        this.glsl = glsl;
        this.components = components;
    }

    /** The GLSL type keyword, e.g. {@code "vec4"}. */
    public String glsl() {
        return glsl;
    }

    /** Number of float components (1 for scalar, 2-4 for vectors, 0 for opaque/matrix types). */
    public int components() {
        return components;
    }

    /** Whether this is a float scalar or float vector — i.e. participates in float broadcast rules. */
    public boolean isFloatVector() {
        return this == FLOAT || this == VEC2 || this == VEC3 || this == VEC4;
    }

    /** The float-vector type for a given component count (1&rarr;FLOAT, 2&rarr;VEC2, ...). */
    public static GlslType floatVector(int components) {
        return switch (components) {
            case 1 -> FLOAT;
            case 2 -> VEC2;
            case 3 -> VEC3;
            case 4 -> VEC4;
            default -> throw new IllegalArgumentException("No float-vector GLSL type for " + components + " components");
        };
    }

    /**
     * Resolve the GLSL type for a graph type handle, or {@code null} if the handle has no shader
     * representation (the compiler treats that as an error at the call site).
     */
    @Nullable
    public static GlslType of(TypeHandle type) {
        if (type == null) return null;
        if (type.equals(TypeHandles.FLOAT)) return FLOAT;
        if (type.equals(TypeHandles.INT)) return INT;
        if (type.equals(TypeHandles.BOOL)) return BOOL;
        if (type.equals(RenderTypeGraphTypes.VEC2)) return VEC2;
        if (type.equals(RenderTypeGraphTypes.VEC3)) return VEC3;
        if (type.equals(RenderTypeGraphTypes.VEC4)) return VEC4;
        // COLOR is an ARGB int at the editor surface (color-picker), but a vec4 in the shader.
        if (type.equals(TypeHandles.COLOR)) return VEC4;
        if (type.equals(RenderTypeGraphTypes.MAT4)) return MAT4;
        if (type.equals(RenderTypeGraphTypes.SAMPLER2D)) return SAMPLER2D;
        return null;
    }
}
