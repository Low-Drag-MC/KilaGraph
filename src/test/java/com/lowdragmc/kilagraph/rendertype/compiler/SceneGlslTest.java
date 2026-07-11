package com.lowdragmc.kilagraph.rendertype.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link SceneGlsl}'s Java constants to {@code kg_scene.glsl}: the same helpers exist in two forms —
 * the {@code #moj_import} include used by the normal pipeline and {@code addFunction} constants used in
 * Iris-injection mode (an injected shaderpack program can't resolve includes). If someone edits the include
 * without updating the constants (or vice versa), vanilla and shaderpack rendering would silently diverge.
 */
class SceneGlslTest {

    private static String includeSource() throws IOException {
        try (InputStream in = SceneGlslTest.class.getResourceAsStream(
                "/assets/kilagraph/shaders/include/kg_scene.glsl")) {
            assertNotNull(in, "kg_scene.glsl must be on the test classpath (main resources)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void constantsMatchTheIncludeVerbatim() throws IOException {
        String include = includeSource();
        assertTrue(include.contains(SceneGlsl.FN_EYE_FROM_NDCZ.strip()),
                "kg_eye_from_ndcz constant must match kg_scene.glsl verbatim");
        assertTrue(include.contains(SceneGlsl.FN_EYE_DEPTH.strip()),
                "kg_eye_depth constant must match kg_scene.glsl verbatim");
        assertTrue(include.contains(SceneGlsl.FN_LINEAR01_DEPTH.strip()),
                "kg_linear01_depth constant must match kg_scene.glsl verbatim");
        assertTrue(include.contains(SceneGlsl.FN_CAMERA_NEAR.strip()),
                "kg_camera_near constant must match kg_scene.glsl verbatim");
        assertTrue(include.contains(SceneGlsl.FN_CAMERA_FAR.strip()),
                "kg_camera_far constant must match kg_scene.glsl verbatim");
    }

    @Test
    void injectionOnlyDepthReadTargetsIrisDepthtex1() {
        // Not mirrored from the include (vanilla samples KG_SceneDepth instead): the injection-mode raw
        // read goes through Iris's depthtex1 — the pre-translucent opaque depth snapshot Iris binds by
        // name on every gbuffers program.
        assertTrue(SceneGlsl.FN_SCENE_DEPTH_RAW.contains("texture(depthtex1, kg_uv).r"),
                "injection raw depth must sample depthtex1");
    }
}
