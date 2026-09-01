package com.lowdragmc.kilagraph.rendertype.nodes.uv;

/**
 * Shared GLSL for the parallax nodes. Both shift the uv along the tangent-space view direction so a flat
 * quad's texture appears to have depth; the difference is how hard they look for the right shift.
 *
 * <p>Both read <b>height</b> from the sampled red channel with {@code 1} = the surface and {@code 0} = the
 * bottom of the groove (the usual height-map convention), and both divide by the view direction's {@code z}
 * so a grazing view shifts further than a head-on one — clamped away from zero, because at the silhouette
 * {@code z → 0} and the offset would blow up.</p>
 */
public final class ParallaxGlsl {
    private ParallaxGlsl() {}

    public static final String OFFSET_NAME = "kg_parallaxOffset";
    /**
     * Unity's Parallax Mapping: one sample, one shift. Cheap and stable, but it only approximates — the
     * height it reads is the one at the <em>original</em> uv, not at the point the eye really hits, so it
     * skews badly on steep slopes and at grazing angles. Fine for subtle surface relief.
     */
    public static final String OFFSET = """
            vec2 kg_parallaxOffset(sampler2D heightMap, vec2 uv, vec3 viewTS, float amplitude) {
                float h = texture(heightMap, uv).r;
                vec2 dir = viewTS.xy / max(abs(viewTS.z), 1.0e-4);
                return uv - dir * (1.0 - h) * amplitude;
            }""";

    public static final String OCCLUSION_NAME = "kg_parallaxOcclusion";
    /**
     * Unity's Parallax Occlusion Mapping: march along the view ray in {@code steps} equal depth slices until
     * the ray falls below the height field, then linearly interpolate between the last two samples for the
     * crossing point. Costs up to {@code steps + 2} texture fetches but actually finds the intersection, so
     * steep relief and grazing angles hold up where {@link #OFFSET} smears.
     *
     * <p>{@code steps} is a compile-time constant (the node's dropdown), so the loop unrolls. The march uses
     * an explicit-LOD fetch: inside a non-uniform loop the implicit derivative is undefined, and asking for
     * it would make neighbouring pixels that took different step counts sample different mip levels.</p>
     */
    public static final String OCCLUSION = """
            vec2 kg_parallaxOcclusion(sampler2D heightMap, vec2 uv, vec3 viewTS, float amplitude, int steps) {
                float layerDepth = 1.0 / float(steps);
                vec2 delta = (viewTS.xy / max(abs(viewTS.z), 1.0e-4)) * amplitude * layerDepth;
                vec2 currentUv = uv;
                float currentLayer = 0.0;
                float currentDepth = 1.0 - textureLod(heightMap, currentUv, 0.0).r;
                for (int i = 0; i < steps; i++) {
                    if (currentLayer >= currentDepth) break;
                    currentUv -= delta;
                    currentDepth = 1.0 - textureLod(heightMap, currentUv, 0.0).r;
                    currentLayer += layerDepth;
                }
                // Refine: the crossing lies between this sample and the previous one.
                vec2 prevUv = currentUv + delta;
                float after = currentDepth - currentLayer;
                float before = (1.0 - textureLod(heightMap, prevUv, 0.0).r) - currentLayer + layerDepth;
                float denom = after - before;
                float weight = abs(denom) < 1.0e-6 ? 0.0 : after / denom;
                return mix(currentUv, prevUv, clamp(weight, 0.0, 1.0));
            }""";
}
