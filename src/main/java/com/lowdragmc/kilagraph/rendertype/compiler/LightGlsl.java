package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Minecraft's {@code light.glsl} <b>functions</b> as {@code addFunction}-registrable constants (the
 * {@code Lighting} UBO is exposed as the {@code KG_Lighting} slice-view block — see
 * {@code KGLightingUniforms}); rationale as {@link FogGlsl}. The include's
 * {@code MINECRAFT_LIGHT_POWER (0.6)} / {@code MINECRAFT_AMBIENT_LIGHT (0.4)} macros are inlined as
 * literals — an injected snippet must not {@code #define} names a shaderpack might also define.
 * {@code LightGlslTest} pins the literals and function shapes to the include.
 */
public final class LightGlsl {

    private LightGlsl() {}

    /** {@code MINECRAFT_LIGHT_POWER} in {@code light.glsl}. */
    public static final String LIGHT_POWER = "0.6";
    /** {@code MINECRAFT_AMBIENT_LIGHT} in {@code light.glsl}. */
    public static final String AMBIENT_LIGHT = "0.4";

    public static final String FN_COMPUTE_LIGHT = """
            vec2 minecraft_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {
                return vec2(dot(lightDir0, normal), dot(lightDir1, normal));
            }
            """;

    public static final String FN_MIX_LIGHT_SEPARATE = """
            vec4 minecraft_mix_light_separate(vec2 light, vec4 color) {
                vec2 lightValue = max(vec2(0.0), light);
                float lightAccum = min(1.0, (lightValue.x + lightValue.y) * 0.6 + 0.4);
                return vec4(color.rgb * lightAccum, color.a);
            }
            """;

    public static final String FN_MIX_LIGHT = """
            vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
                vec2 light = minecraft_compute_light(lightDir0, lightDir1, normal);
                return minecraft_mix_light_separate(light, color);
            }
            """;
}
