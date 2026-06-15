package com.lowdragmc.kilagraph.rendertype.nodes.artistic;

/**
 * Shared GLSL helper-function definitions for Artistic nodes, registered via {@code ctx.function(name, ...)}
 * (deduped by name, so multiple nodes share one copy). Currently the classic branchless RGB&harr;HSV
 * conversions used by the Hue and Colorspace Conversion nodes.
 */
public final class ArtisticGlsl {
    private ArtisticGlsl() {}

    public static final String RGB2HSV_NAME = "kg_rgb2hsv";
    public static final String RGB2HSV = """
            vec3 kg_rgb2hsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }""";

    public static final String HSV2RGB_NAME = "kg_hsv2rgb";
    public static final String HSV2RGB = """
            vec3 kg_hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }""";
}
