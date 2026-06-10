package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Which shader stage(s) a node may legally be compiled into. The compiler infers the actual stage a
 * node is used in (by which stage block ultimately pulls it) and flags a {@link StageError} when that
 * conflicts with the node's affinity — e.g. a node reading the {@code Normal} vertex attribute
 * ({@link #VERTEX_ONLY}) pulled into the fragment stage, where that attribute doesn't exist.
 */
public enum StageAffinity {
    /** Usable in either stage (default): plain math, vectors, constants, samplers, material uniforms. */
    ANY,
    /** Only valid in the vertex shader (reads vertex attributes like Position/Normal). */
    VERTEX_ONLY,
    /** Only valid in the fragment shader (e.g. screen-space derivatives, discard). */
    FRAGMENT_ONLY;

    /** Whether this affinity permits use in {@code stage}. */
    public boolean allows(ShaderStage stage) {
        return switch (this) {
            case ANY -> true;
            case VERTEX_ONLY -> stage == ShaderStage.VERTEX;
            case FRAGMENT_ONLY -> stage == ShaderStage.FRAGMENT;
        };
    }
}
