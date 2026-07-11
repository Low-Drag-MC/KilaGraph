package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * Minecraft's {@code fog.glsl} <b>functions</b> as {@code addFunction}-registrable constants (the
 * {@code Fog} UBO itself is exposed as the {@code KG_Fog} slice-view block — see
 * {@code KGFogUniforms}). Emitting the functions inline instead of {@code #moj_import}-ing the include
 * keeps fog-using graphs injectable under an Iris shaderpack (a Minecraft include in the fragment stage
 * rejects the whole graph). Bodies mirror the include <b>verbatim</b> — {@code FogGlslTest} pins the two
 * copies together. Register in dependency order ({@code linear_fog_value} first).
 */
public final class FogGlsl {

    private FogGlsl() {}

    public static final String FN_LINEAR_FOG_VALUE = """
            float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
                if (vertexDistance <= fogStart) {
                    return 0.0;
                } else if (vertexDistance >= fogEnd) {
                    return 1.0;
                }

                return (vertexDistance - fogStart) / (fogEnd - fogStart);
            }
            """;

    public static final String FN_TOTAL_FOG_VALUE = """
            float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
                return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
            }
            """;

    public static final String FN_APPLY_FOG = """
            vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
                float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
                return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
            }
            """;

    public static final String FN_FOG_SPHERICAL_DISTANCE = """
            float fog_spherical_distance(vec3 pos) {
                return length(pos);
            }
            """;

    public static final String FN_FOG_CYLINDRICAL_DISTANCE = """
            float fog_cylindrical_distance(vec3 pos) {
                float distXZ = length(pos.xz);
                float distY = abs(pos.y);
                return max(distXZ, distY);
            }
            """;
}
