package com.lowdragmc.kilagraph.rendertype.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link FogGlsl}/{@link LightGlsl} constants (the injection-safe inline mirrors) to Minecraft's
 * own {@code fog.glsl}/{@code light.glsl} includes, read from the Minecraft jar on the test classpath. If
 * a Minecraft update changes the function bodies, these fail and the mirrors must be re-synced — otherwise
 * vanilla and shaderpack rendering would silently diverge.
 */
class McGlslMirrorTest {

    private static String mcInclude(String name) throws IOException {
        String path = "assets/minecraft/shaders/include/" + name;
        // The test runs in a module layer that may isolate the Minecraft jar's resources — try the usual
        // loaders first, then fall back to locating the jar on java.class.path and reading it directly.
        for (ClassLoader loader : new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                McGlslMirrorTest.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()}) {
            if (loader == null) continue;
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            if (!entry.endsWith(".jar") || !entry.toLowerCase().contains("minecraft")) continue;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(entry)) {
                java.util.zip.ZipEntry ze = zip.getEntry(path);
                if (ze == null) continue;
                try (InputStream in = zip.getInputStream(ze)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // not a readable jar — keep looking
            }
        }
        assertNotNull(null, "minecraft:" + name + " not reachable from the test classpath");
        throw new AssertionError();
    }

    @Test
    void fogFunctionsMatchTheIncludeVerbatim() throws IOException {
        String fog = mcInclude("fog.glsl");
        assertTrue(fog.contains(FogGlsl.FN_LINEAR_FOG_VALUE.strip()), "linear_fog_value must match fog.glsl");
        assertTrue(fog.contains(FogGlsl.FN_TOTAL_FOG_VALUE.strip()), "total_fog_value must match fog.glsl");
        assertTrue(fog.contains(FogGlsl.FN_APPLY_FOG.strip()), "apply_fog must match fog.glsl");
        assertTrue(fog.contains(FogGlsl.FN_FOG_SPHERICAL_DISTANCE.strip()), "fog_spherical_distance must match");
        assertTrue(fog.contains(FogGlsl.FN_FOG_CYLINDRICAL_DISTANCE.strip()), "fog_cylindrical_distance must match");
    }

    @Test
    void lightFunctionsMatchTheIncludeShapeAndConstants() throws IOException {
        String light = mcInclude("light.glsl");
        // The macros are inlined as literals in LightGlsl (an injected snippet must not #define names a
        // pack might also define) — pin the macro VALUES and the function shapes instead of verbatim text.
        assertTrue(light.contains("MINECRAFT_LIGHT_POWER   (" + LightGlsl.LIGHT_POWER + ")"),
                "MINECRAFT_LIGHT_POWER value drifted from LightGlsl.LIGHT_POWER");
        assertTrue(light.contains("MINECRAFT_AMBIENT_LIGHT (" + LightGlsl.AMBIENT_LIGHT + ")"),
                "MINECRAFT_AMBIENT_LIGHT value drifted from LightGlsl.AMBIENT_LIGHT");
        assertTrue(LightGlsl.FN_MIX_LIGHT_SEPARATE.contains(
                        "* " + LightGlsl.LIGHT_POWER + " + " + LightGlsl.AMBIENT_LIGHT),
                "FN_MIX_LIGHT_SEPARATE must inline the pinned literals");
        assertTrue(light.contains(LightGlsl.FN_COMPUTE_LIGHT.strip()), "minecraft_compute_light must match light.glsl");
        assertTrue(light.contains("vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color)"),
                "minecraft_mix_light signature must match light.glsl");
    }

    @Test
    void fogUboLayoutMatchesTheInclude() throws IOException {
        String fog = mcInclude("fog.glsl");
        // KG_Fog binds Minecraft's own Fog buffer — the declared field ORDER must match fog.glsl exactly.
        String kg = com.lowdragmc.kilagraph.rendertype.runtime.KGFogUniforms.declareGlsl();
        int last = -1;
        for (String field : new String[]{"vec4 FogColor;", "float FogEnvironmentalStart;",
                "float FogEnvironmentalEnd;", "float FogRenderDistanceStart;", "float FogRenderDistanceEnd;",
                "float FogSkyEnd;", "float FogCloudsEnd;"}) {
            assertTrue(fog.contains(field), "fog.glsl lost field: " + field);
            int at = kg.indexOf(field);
            assertTrue(at > last, "KG_Fog field order diverged at: " + field);
            last = at;
        }
    }

    @Test
    void mcGlobalsLayoutMatchesTheInclude() throws IOException {
        String globals = mcInclude("globals.glsl");
        String kg = com.lowdragmc.kilagraph.rendertype.runtime.KGMcGlobalsUniforms.declareGlsl();
        int last = -1;
        for (String field : new String[]{"ivec3 CameraBlockPos;", "vec3 CameraOffset;", "vec2 ScreenSize;",
                "float GlintAlpha;", "float GameTime;", "int MenuBlurRadius;", "int UseRgss;"}) {
            assertTrue(globals.contains(field), "globals.glsl lost field: " + field);
            int at = kg.indexOf(field);
            assertTrue(at > last, "KG_McGlobals field order diverged at: " + field);
            last = at;
        }
    }

    @Test
    void lightingLayoutMatchesTheInclude() throws IOException {
        String light = mcInclude("light.glsl");
        String kg = com.lowdragmc.kilagraph.rendertype.runtime.KGLightingUniforms.declareGlsl();
        assertTrue(light.contains("vec3 Light0_Direction;") && light.contains("vec3 Light1_Direction;"),
                "light.glsl lost the light-direction fields");
        assertTrue(kg.indexOf("Light0_Direction") < kg.indexOf("Light1_Direction"),
                "KG_Lighting field order diverged");
    }
}
