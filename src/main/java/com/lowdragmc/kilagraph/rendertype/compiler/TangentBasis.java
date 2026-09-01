package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * A surface tangent basis as three GLSL expressions — the columns of the {@code mat3} that maps a
 * tangent-space vector into the space the three vectors themselves live in ({@code T·v.x + B·v.y + N·v.z}).
 * Unity's Tangent/Bitangent/Normal triple, minus the {@code mat3} (the compiler has no {@code mat3}
 * {@link GlslType}; the columns are hoisted as plain {@code vec3} temps).
 *
 * <p>Produced by {@link ShaderGraphCompiler#tangentBasis(String)}, which builds the object-space basis once
 * per stage and rotates it into view/world. All three are unit length, and {@code N} is the same normal the
 * Normal node reports for that space; {@code T}/{@code B} are only exactly orthogonal to each other when the
 * uv layout is (a scale of) orthogonal — the usual normal-mapping approximation.</p>
 *
 * @param tangent   {@code T} — the +u direction of the uv layout across the surface
 * @param bitangent {@code B} — the +v direction (handedness already applied)
 * @param normal    {@code N} — the surface normal
 */
public record TangentBasis(ShaderExpr tangent, ShaderExpr bitangent, ShaderExpr normal) {
}
