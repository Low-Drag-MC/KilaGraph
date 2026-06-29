package com.lowdragmc.kilagraph.rendertype.iris;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link IrisShaderInjector#injectFragment}'s GLSL source transform — the game-independent core of
 * the Iris compatibility PoC. Pure string logic, runs as a plain JUnit test (no Minecraft/Iris classes).
 */
class IrisShaderInjectorTest {

    private static final String PACK_FSH = """
            #version 330 compatibility
            #extension GL_ARB_shader_texture_lod : enable

            uniform sampler2D gtexture;
            in vec2 texcoord;
            out vec4 fragColor;

            vec3 helper() {
                return texture(gtexture, texcoord).rgb;
            }

            void main() {
                vec4 col = texture(gtexture, texcoord);
                fragColor = vec4(helper() * col.rgb, col.a);
            }
            """;

    @Test
    void rewritesEveryGtextureReadToTheGatedHelper() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH);
        // No raw gtexture sampling left in the *body* — the only real texture(gtexture,...) is inside the
        // appended helper definition. So exactly one such call should remain.
        int rawReads = countOccurrences(out, "texture(gtexture,");
        assertEquals(1, rawReads, "exactly one real gtexture read should remain (the helper body)");
        assertTrue(out.contains("kg_sample_gtexture(texcoord)"),
                "shaderpack reads should be redirected to the helper");
    }

    @Test
    void declaresUniformAndHelper() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH);
        assertTrue(out.contains("uniform int kg_surface_id;"), "must declare the discriminator uniform");
        assertTrue(out.contains("vec4 kg_sample_gtexture(vec2 kg_uv);"), "must forward-declare the helper");
        assertTrue(out.contains("vec4 kg_sample_gtexture(vec2 kg_uv) {"), "must define the helper");
    }

    @Test
    void declarationsComeAfterTheLeadingPreprocessorBlock() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH);
        int versionIdx = out.indexOf("#version");
        int extensionIdx = out.indexOf("#extension");
        int uniformIdx = out.indexOf("uniform int kg_surface_id;");
        assertTrue(versionIdx >= 0 && extensionIdx >= 0);
        assertTrue(uniformIdx > extensionIdx,
                "our declarations must sit after #version/#extension, not between them");
    }

    @Test
    void isIdempotent() {
        String once = IrisShaderInjector.injectFragment(PACK_FSH);
        String twice = IrisShaderInjector.injectFragment(once);
        assertEquals(once, twice, "re-injecting an already-injected source must be a no-op");
    }

    @Test
    void leavesSourcesWithoutGtextureUntouched() {
        String basic = """
                #version 330
                out vec4 fragColor;
                void main() { fragColor = vec4(1.0); }
                """;
        assertEquals(basic, IrisShaderInjector.injectFragment(basic));
        assertFalse(IrisShaderInjector.injectFragment(basic).contains(IrisShaderInjector.MARKER));
    }

    @Test
    void hijacksTexSampler() {
        // Complementary's Iris-patched gbuffers source names the albedo sampler `tex`, not `gtexture`.
        String pack = """
                #version 330 compatibility
                uniform sampler2D tex;
                in vec2 texcoord;
                out vec4 fragColor;
                void main() { fragColor = texture(tex, texcoord); }
                """;
        String out = IrisShaderInjector.injectFragment(pack);
        assertTrue(out.contains("kg_sample_tex(texcoord)"), "texture(tex,...) should be redirected");
        assertTrue(out.contains("vec4 kg_sample_tex(vec2 kg_uv) {"), "must define the tex helper");
        assertTrue(out.contains("return texture(tex, kg_uv);"), "helper must fall back to the real tex sampler");
        // `texcoord` (which contains the substring "tex") must NOT be rewritten.
        assertTrue(out.contains("texcoord"), "texcoord must be left intact (word-boundary match)");
    }

    /** A pack fragment that reads all three LabPBR samplers (albedo + normals + specular). */
    private static final String PBR_PACK_FSH = """
            #version 330 compatibility
            uniform sampler2D tex;
            uniform sampler2D normals;
            uniform sampler2D specular;
            in vec2 texcoord;
            out vec4 fragData[3];
            void main() {
                fragData[0] = texture(tex, texcoord);
                fragData[1] = texture(normals, texcoord);
                fragData[2] = texture(specular, texcoord);
            }
            """;

    @Test
    void dispatchesToRegisteredSurfacesAsStruct() {
        var s1 = new IrisSurfaceRegistry.Surface(1,
                List.of("uniform sampler2D kg_tex_a;\n"), List.of(),
                "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface(texture(kg_tex_a, kg_uv).rgb, 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n");
        String out = IrisShaderInjector.injectFragment(PBR_PACK_FSH, List.of(s1));

        assertTrue(out.contains("struct kg_Surface {"), "shared struct declared");
        assertTrue(out.contains("kg_Surface kg_surface(int kg_id, vec2 kg_uv) {"), "struct-returning dispatch");
        assertTrue(out.contains("if (kg_id == 1) return kg_surface_1(kg_uv);"), "dispatch to surface 1");
        // three hijack families:
        assertTrue(out.contains("kg_sample_tex(texcoord)") && out.contains("vec4 kg_sample_tex(vec2 kg_uv) {"), "albedo hijacked");
        assertTrue(out.contains("kg_sample_normals(texcoord)") && out.contains("vec4 kg_sample_normals(vec2 kg_uv) {"), "normals hijacked");
        assertTrue(out.contains("kg_sample_specular(texcoord)") && out.contains("vec4 kg_sample_specular(vec2 kg_uv) {"), "specular hijacked");
        // encoders present and used by the helpers:
        assertTrue(out.contains("vec4 kg_encodeNormal(vec3"), "normal encoder emitted");
        assertTrue(out.contains("vec4 kg_encodeSpecular(float"), "specular encoder emitted");
        assertTrue(out.contains("return kg_encodeNormal(s.normalTS, s.ao, s.height);"), "normals helper encodes the struct");
        assertTrue(out.contains("return kg_encodeSpecular(s.smoothness, s.metallic, s.porosity, s.sss, s.emission);"), "specular helper encodes the struct");
        assertTrue(out.contains("return vec4(s.albedo, s.alpha);"), "albedo helper returns albedo+alpha");
    }

    @Test
    void dedupsSharedDeclarationsAndHelpers() {
        // Two surfaces both reference the same shared missing-sampler decl + the same procedural helper.
        String sharedDecl = "uniform sampler2D kg_MissingSampler;\n";
        String sharedFn = "float kg_noise(vec2 p) { return 0.0; }\n";
        var a = new IrisSurfaceRegistry.Surface(1, List.of(sharedDecl), List.of(sharedFn),
                "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface(vec3(kg_noise(kg_uv)), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n");
        var b = new IrisSurfaceRegistry.Surface(2, List.of(sharedDecl), List.of(sharedFn),
                "kg_Surface kg_surface_2(vec2 kg_uv) {\n    return kg_Surface(vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n");
        String out = IrisShaderInjector.injectFragment(PACK_FSH, List.of(a, b));

        assertEquals(1, countOccurrences(out, "uniform sampler2D kg_MissingSampler;"),
                "an identical declaration shared by surfaces is emitted once");
        assertEquals(1, countOccurrences(out, "float kg_noise(vec2 p)"),
                "an identical helper shared by surfaces is emitted once");
    }

    @Test
    void registryNamespacesCollisionProneIdentifiers() {
        // Two distinct graphs both mint KG_Material/kg_material and a kg_grad_0 builder — they must not
        // collide once the registry suffixes them with the surface id.
        var raw = new com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet(
                List.of("layout(std140) uniform KG_Material {\n    vec4 kg_c;\n} kg_material;\n"),
                List.of("vec4 kg_grad_0() { return vec4(0.0); }\n"),
                "    vec4 f_0 = kg_material.kg_c * kg_grad_0();\n",
                "f_0.rgb, f_0.a, vec3(0.0,0.0,1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0");
        var one = IrisSurfaceRegistry.build(1, raw);
        var two = IrisSurfaceRegistry.build(2, raw);

        assertTrue(one.declarationUnits().get(0).contains("uniform KG_Material_1"), "block namespaced by id 1");
        assertTrue(one.surfaceFunction().contains("kg_material_1.kg_c"), "accessor namespaced by id 1");
        assertTrue(one.functions().get(0).contains("kg_grad_1_0()"), "gradient builder namespaced by id 1");
        assertTrue(one.surfaceFunction().startsWith("kg_Surface kg_surface_1(vec2 kg_uv) {"),
                "function returns the shared kg_Surface struct");
        assertTrue(one.surfaceFunction().contains("return kg_Surface(f_0.rgb, f_0.a,"),
                "struct constructed from the namespaced body + args");
        assertTrue(two.surfaceFunction().contains("kg_material_2.kg_c") && two.functions().get(0).contains("kg_grad_2_0()"),
                "id 2 gets a distinct namespace, so the two surfaces never collide");
    }

    @Test
    void handlesTexture2DSpelling() {
        String legacy = """
                #version 120
                uniform sampler2D gtexture;
                void main() { gl_FragColor = texture2D(gtexture, gl_TexCoord[0].st); }
                """;
        String out = IrisShaderInjector.injectFragment(legacy);
        assertTrue(out.contains("kg_sample_gtexture(gl_TexCoord[0].st)"),
                "texture2D(gtexture,...) should also be redirected");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
