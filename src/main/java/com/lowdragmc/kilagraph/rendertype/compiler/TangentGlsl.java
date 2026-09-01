package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Shared GLSL for the tangent-basis seam ({@link ShaderGraphCompiler#objectTangentBasis()}), registered via
 * {@code addFunction} (deduped by name, so every tangent-reading node shares one copy). Minecraft's vertex
 * formats carry no tangent attribute, so these two functions <em>derive</em> one; both write their result
 * into {@code out} parameters rather than returning a {@code mat3}, because the compiler has no {@code mat3}
 * {@link GlslType} and hoists the columns as plain {@code vec3} temps.
 *
 * <p>Register {@link #FROM_NORMAL} before {@link #FROM_UV} — the uv frame calls the normal frame for its
 * degenerate case, and functions are emitted in registration order.</p>
 */
public final class TangentGlsl {
    private TangentGlsl() {}

    public static final String FROM_NORMAL_NAME = "kg_tangentFrameFromNormal";
    /**
     * A deterministic orthonormal basis from the normal alone (Duff et al. 2017, "Building an Orthonormal
     * Basis, Revisited" — branchless, no normalize, and the {@code sign} trick keeps {@code s + n.z} away
     * from zero at the {@code n.z = -1} pole). The rotation around {@code n} is arbitrary but stable, which
     * is all a graph can ask for when there is no uv to anchor it: fine for procedural bumps, wrong for a
     * baked normal map (whose orientation is defined by the uv layout — see {@link #FROM_UV}).
     * {@code n} must already be unit length.
     */
    public static final String FROM_NORMAL = """
            void kg_tangentFrameFromNormal(vec3 n, out vec3 t, out vec3 b) {
                float s = n.z >= 0.0 ? 1.0 : -1.0;
                float a = -1.0 / (s + n.z);
                float d = n.x * n.y * a;
                t = vec3(1.0 + s * n.x * n.x * a, s * d, -s * n.x);
                b = vec3(d, s + n.y * n.y * a, -n.y);
            }""";

    public static final String FROM_UV_NAME = "kg_tangentFrameFromUv";
    /**
     * The tangent basis implied by the mesh's own uv layout, recovered per-fragment from screen-space
     * derivatives (Schueler's "cotangent frame"): the surface gradients {@code dFdx/dFdy} of the position and
     * the uv are enough to solve for the {@code T}/{@code B} that map uv onto the surface. Because it reads
     * the real uv it reproduces mirrored and rotated uv islands — exactly what block/entity models do — so a
     * normal map sampled with the same uv lands the right way round. Fragment stage only ({@code dFdx} is
     * illegal in a vsh). A degenerate patch (no uv gradient, e.g. a collapsed or untextured triangle) falls
     * back to {@link #FROM_NORMAL} instead of producing a zero basis. {@code n} must already be unit length.
     */
    public static final String FROM_UV = """
            void kg_tangentFrameFromUv(vec3 n, vec3 p, vec2 uv, out vec3 t, out vec3 b) {
                vec3 dp1 = dFdx(p);
                vec3 dp2 = dFdy(p);
                vec2 duv1 = dFdx(uv);
                vec2 duv2 = dFdy(uv);
                vec3 dp2perp = cross(dp2, n);
                vec3 dp1perp = cross(n, dp1);
                vec3 rt = dp2perp * duv1.x + dp1perp * duv2.x;
                vec3 rb = dp2perp * duv1.y + dp1perp * duv2.y;
                float m = max(dot(rt, rt), dot(rb, rb));
                if (m > 0.0) {
                    float invmax = inversesqrt(m);
                    t = rt * invmax;
                    b = rb * invmax;
                } else {
                    kg_tangentFrameFromNormal(n, t, b);
                }
            }""";
}
