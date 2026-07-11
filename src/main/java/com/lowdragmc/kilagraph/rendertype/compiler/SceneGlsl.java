package com.lowdragmc.kilagraph.rendertype.compiler;

/**
 * The {@code kg_scene.glsl} depth helpers as {@code addFunction}-registrable Java constants, for
 * <b>Iris-injection mode</b>: an injected shaderpack program can't resolve {@code #moj_import} includes, so
 * the same helpers are emitted inline instead (deduped by name across surfaces by the injector). The bodies
 * mirror {@code assets/kilagraph/shaders/include/kg_scene.glsl} <b>verbatim</b> — {@code SceneGlslTest} pins
 * the two copies together so vanilla and shaderpack rendering can't silently diverge.
 *
 * <p>Register in dependency order ({@code kg_eye_from_ndcz} → {@code kg_eye_depth} →
 * {@code kg_linear01_depth}): the compiler's function map and the injector both preserve insertion order,
 * and GLSL needs a definition before its first use.</p>
 */
public final class SceneGlsl {

    private SceneGlsl() {}

    public static final String FN_EYE_FROM_NDCZ = """
            float kg_eye_from_ndcz(float ndcZ, mat4 iproj) {
                vec4 view = iproj * vec4(0.0, 0.0, ndcZ, 1.0);
                return -(view.z / view.w);
            }
            """;

    public static final String FN_EYE_DEPTH = """
            float kg_eye_depth(float rawDepth, mat4 iproj) {
                return kg_eye_from_ndcz(rawDepth * 2.0 - 1.0, iproj);
            }
            """;

    public static final String FN_LINEAR01_DEPTH = """
            float kg_linear01_depth(float rawDepth, mat4 iproj) {
                float eye = kg_eye_depth(rawDepth, iproj);
                float nearD = kg_eye_from_ndcz(-1.0, iproj);
                float farD  = kg_eye_from_ndcz( 1.0, iproj);
                return (eye - nearD) / (farD - nearD);
            }
            """;

    public static final String FN_CAMERA_NEAR = """
            float kg_camera_near(mat4 iproj) { return kg_eye_from_ndcz(-1.0, iproj); }
            """;

    public static final String FN_CAMERA_FAR = """
            float kg_camera_far(mat4 iproj)  { return kg_eye_from_ndcz( 1.0, iproj); }
            """;

    /**
     * Injection-only (no include counterpart — the vanilla pipeline samples the {@code KG_SceneDepth}
     * capture instead): raw scene depth from Iris's {@code depthtex1}, the pre-translucent opaque depth
     * snapshot Iris auto-binds by name on every gbuffers program. Semantically equivalent to the vanilla
     * capture (both are the opaque scene's depth). The injector declares the sampler iff the pack's
     * flattened source doesn't already.
     */
    public static final String FN_SCENE_DEPTH_RAW = """
            float kg_scene_depth_raw(vec2 kg_uv) {
                return texture(depthtex1, kg_uv).r;
            }
            """;
}
