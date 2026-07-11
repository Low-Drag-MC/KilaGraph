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
                "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface(texture(kg_tex_a, kg_uv).rgb, 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n", false, false);
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
    void hijacksExplicitLodAndGradientReads() {
        // Complementary's emission mipmap-fix uses texture2DLod(specular,uv,0); BSL reads albedo/normals via
        // texture2DGradARB(gtexture/normals, uv, dPdx, dPdy) under parallax. Both forms must be hijacked, with
        // the trailing lod/gradient args absorbed by overloads — else the executed read stays un-hijacked.
        String pack = """
                #version 330 compatibility
                uniform sampler2D gtexture;
                uniform sampler2D specular;
                in vec2 texcoord;
                out vec4 fragColor;
                void main() {
                    vec4 a = texture2DGradARB(gtexture, texcoord, vec2(0.0), vec2(0.0));
                    vec4 s0 = texture2DLod(specular, texcoord, 0);
                    fragColor = a + s0;
                }
                """;
        var s1 = new IrisSurfaceRegistry.Surface(1, List.of(), List.of(),
                "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface(vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.5, 1.0, 1.0, 0.0, 0.0);\n}\n", false, false);
        String out = IrisShaderInjector.injectFragment(pack, List.of(s1));

        assertTrue(out.contains("kg_sample_gtexture(texcoord, vec2(0.0), vec2(0.0))"),
                "texture2DGradARB(gtexture,...) is redirected (gradient args kept)");
        assertTrue(out.contains("kg_sample_specular(texcoord, 0)"), "texture2DLod(specular,...) is redirected (lod arg kept)");
        assertTrue(out.contains("vec4 kg_sample_gtexture(vec2 kg_uv, vec2 kg_dx, vec2 kg_dy) { return kg_sample_gtexture(kg_uv); }"),
                "a (vec2,vec2,vec2) overload absorbs the gradient args");
        assertTrue(out.contains("vec4 kg_sample_specular(vec2 kg_uv, float kg_lod) { return kg_sample_specular(kg_uv); }"),
                "a (vec2,float) overload absorbs the lod arg");
    }

    @Test
    void dedupsSharedDeclarationsAndHelpers() {
        // Two surfaces both reference the same shared missing-sampler decl + the same procedural helper.
        String sharedDecl = "uniform sampler2D kg_MissingSampler;\n";
        String sharedFn = "float kg_noise(vec2 p) { return 0.0; }\n";
        var a = new IrisSurfaceRegistry.Surface(1, List.of(sharedDecl), List.of(sharedFn),
                "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface(vec3(kg_noise(kg_uv)), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n", false, false);
        var b = new IrisSurfaceRegistry.Surface(2, List.of(sharedDecl), List.of(sharedFn),
                "kg_Surface kg_surface_2(vec2 kg_uv) {\n    return kg_Surface(vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n", false, false);
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
        var raw = snippet(
                List.of("layout(std140) uniform KG_Material {\n    vec4 kg_c;\n} kg_material;\n"),
                List.of("vec4 kg_grad_0() { return vec4(0.0); }\n"),
                "    vec4 f_0 = kg_material.kg_c * kg_grad_0();\n",
                "f_0.rgb, f_0.a, vec3(0.0,0.0,1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", false, false);
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

    // ---- M3-redux: fragment-side geometry inputs (NO vsh injection) -------------------------------------
    // Geometry/screen inputs are reconstructed in the fragment from gl_FragCoord + our UBOs; only the smooth
    // normal comes from the pack's own `normal` varying (via a per-program kg_normalView global fill).

    /** A surface with usesGeometry=true — the flag alone drives the kg_normalView global + fill; the injector
     *  doesn't parse the body, so its exact contents don't matter here. */
    private static final String GEOM_FN =
            "kg_Surface kg_surface_1(vec2 kg_uv) {\n    return kg_Surface("
            + "vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n";

    private static IrisSurfaceRegistry.Surface surface(int id, boolean usesGeometry) {
        return new IrisSurfaceRegistry.Surface(id, List.of(), List.of(), GEOM_FN, usesGeometry, false);
    }

    /** A surface whose graph samples the scene depth — carries the injection-only depthtex1 helper the
     *  compiler emits, and the usesSceneDepth flag that drives the injector's conditional declaration. */
    private static IrisSurfaceRegistry.Surface depthSurface(int id) {
        return new IrisSurfaceRegistry.Surface(id, List.of(),
                List.of("float kg_scene_depth_raw(vec2 kg_uv) {\n    return texture(depthtex1, kg_uv).r;\n}\n"),
                GEOM_FN, false, true);
    }

    /** A gbuffers fragment that declares a smooth view-space `normal` varying (Complementary/BSL/Solas). */
    private static String fshWithNormal(String normalDecl) {
        return "#version 330 core\n" + normalDecl + "\nuniform sampler2D gtexture;\nin vec2 texcoord;\n"
                + "out vec4 c;\nvoid main() { c = texture(gtexture, texcoord); }\n";
    }

    @Test
    void fillsNormalFromPackVaryingWhenPresent() {
        String out = IrisShaderInjector.injectFragment(fshWithNormal("in vec3 normal;"), List.of(surface(1, true)));
        assertTrue(out.contains("vec3 kg_normalView;"), "declares the per-program normal global");
        assertTrue(out.contains("kg_normalView = normal;"), "fills it from the pack's own view-space normal varying");
    }

    @Test
    void acceptsVaryingKeywordNormal() {
        // BSL declares it the legacy way: `varying vec3 normal;`.
        String out = IrisShaderInjector.injectFragment(fshWithNormal("varying vec3 normal;"), List.of(surface(1, true)));
        assertTrue(out.contains("kg_normalView = normal;"), "detects the `varying vec3 normal;` form too");
    }

    @Test
    void fallsBackToConstantNormalWhenPackHasNone() {
        // photon-like: no `normal` varying — fall back to a constant, never reference an absent name.
        String out = IrisShaderInjector.injectFragment(fshWithNormal("flat in mat3 tbn;"), List.of(surface(1, true)));
        assertTrue(out.contains("vec3 kg_normalView;"), "still declares the global");
        assertTrue(out.contains("kg_normalView = vec3(0.0, 0.0, 1.0);"), "constant fallback when no pack normal");
        assertFalse(out.contains("kg_normalView = normal;"), "must not reference an absent pack varying");
    }

    @Test
    void normalVaryingMatchIsWordExact() {
        // `normalM`/`newNormal` are NOT the smooth `normal` varying — must not be mistaken for one.
        String out = IrisShaderInjector.injectFragment(fshWithNormal("in vec3 normalM;"), List.of(surface(1, true)));
        assertTrue(out.contains("kg_normalView = vec3(0.0, 0.0, 1.0);"), "normalM is not a bare `normal` varying");
    }

    @Test
    void noNormalGlobalWhenNoGeometrySurface() {
        String out = IrisShaderInjector.injectFragment(PBR_PACK_FSH, List.of(surface(1, false)));
        assertFalse(out.contains("kg_normalView"), "no normal global/fill when no surface reads geometry");
    }

    @Test
    void neverEmitsVertexOutput() {
        // The injector transforms fragments only — it must never emit an out-varying or touch the vertex stage.
        String out = IrisShaderInjector.injectFragment(fshWithNormal("in vec3 normal;"), List.of(surface(1, true)));
        assertFalse(out.contains("out vec3 kg_"), "no vertex out-varyings are emitted");
    }

    // ---- hardening: the injection seam must never break the shaderpack ---------------------------------

    @Test
    void injectGuardedReturnsOriginalSourceOnThrow() {
        // A poisoned surface (null functions list) makes injectFragment throw. The guarded entry the mixin
        // uses must swallow ANY Throwable and hand Iris the untouched source — a KilaGraph bug may cost us
        // our shading, but it must never break the shaderpack's own program compilation.
        var poisoned = new IrisSurfaceRegistry.Surface(1, List.of(), null, GEOM_FN, false, false);
        String out = IrisShaderInjector.injectGuarded("entities_solid", PACK_FSH, List.of(poisoned));
        assertEquals(PACK_FSH, out, "a throwing injection must fall back to the original source");
    }

    @Test
    void validationHarnessWrapsASurfaceCompilably() {
        // The registry GL-validates each NEW surface standalone (reject -> passthrough id 0) so one bad
        // graph can't force every program through the whole-source fallback. The harness must contain
        // everything the surface function needs: the struct, its decls/helpers, and a main() that calls it
        // (so the linker can't strip anything).
        var s = new IrisSurfaceRegistry.Surface(3,
                List.of("uniform sampler2D kg_tex_a;\n"),
                List.of("float kg_noise(vec2 p) { return 0.0; }\n"),
                "kg_Surface kg_surface_3(vec2 kg_uv) {\n    return kg_Surface(texture(kg_tex_a, kg_uv).rgb, 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n",
                false, false);
        String harness = IrisSurfaceRegistry.validationHarness(s);
        assertTrue(harness.startsWith("#version 330 core"), "harness compiles as the same version Iris emits");
        assertTrue(harness.contains("struct kg_Surface {"), "harness declares the shared struct");
        assertTrue(harness.contains("uniform sampler2D kg_tex_a;"), "harness carries the surface's declarations");
        assertTrue(harness.contains("float kg_noise(vec2 p)"), "harness carries the surface's helper functions");
        assertTrue(harness.contains("kg_Surface kg_surface_3(vec2 kg_uv)"), "harness carries the surface function");
        assertTrue(harness.contains("kg_surface_3(vec2(0.5))"), "main() must CALL the surface so nothing is stripped");
        assertTrue(harness.contains("void main()"), "harness is a complete fragment shader");
        assertFalse(harness.contains("kg_normalView"), "no geometry global for a non-geometry surface");
    }

    @Test
    void validationHarnessDeclaresNormalGlobalForGeometrySurfaces() {
        String harness = IrisSurfaceRegistry.validationHarness(surface(1, true));
        assertTrue(harness.contains("vec3 kg_normalView;"),
                "a geometry surface references kg_normalView — the harness must declare it");
    }

    @Test
    void registryBuildCarriesUsesGeometry() {
        var geom = snippet(
                List.of(), List.of(), "", "vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", true, false);
        assertTrue(IrisSurfaceRegistry.build(1, geom).usesGeometry(), "build propagates usesGeometry=true");
        var plain = snippet(
                List.of(), List.of(), "", "vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", false, false);
        assertFalse(IrisSurfaceRegistry.build(2, plain).usesGeometry(), "build propagates usesGeometry=false");
    }

    // ---- Scene Depth under shaderpacks: injected depthtex1 (auto-bound by Iris) --------------------------

    @Test
    void declaresDepthtex1WhenAbsentAndSurfaceNeedsIt() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH, List.of(depthSurface(1)));
        assertEquals(1, countOccurrences(out, "uniform sampler2D depthtex1;"),
                "a scene-depth surface needs depthtex1 declared (Iris auto-binds it by name)");
        assertTrue(out.contains("float kg_scene_depth_raw(vec2 kg_uv)"), "the depth helper is emitted");
    }

    @Test
    void doesNotRedeclareDepthtex1WhenPackDeclaresIt() {
        // Complementary's glsl-transformer-flattened source already declares EVERY sampler (lib/uniforms.glsl)
        // — a second declaration is a compile error that would trip the whole-source fallback.
        String pack = PACK_FSH.replace("uniform sampler2D gtexture;",
                "uniform sampler2D gtexture;\nuniform sampler2D depthtex1;");
        String out = IrisShaderInjector.injectFragment(pack, List.of(depthSurface(1)));
        assertEquals(1, countOccurrences(out, "uniform sampler2D depthtex1;"),
                "must not redeclare a sampler the pack already declares");
    }

    @Test
    void depthtex1EmittedOnceForMultipleDepthSurfaces() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH, List.of(depthSurface(1), depthSurface(2)));
        assertEquals(1, countOccurrences(out, "uniform sampler2D depthtex1;"),
                "depthtex1 is a GLOBAL shared declaration — once, not per surface");
        assertEquals(1, countOccurrences(out, "float kg_scene_depth_raw(vec2 kg_uv)"),
                "the identical depth helper dedups across surfaces");
    }

    @Test
    void noDepthtex1WhenNoSurfaceUsesDepth() {
        String out = IrisShaderInjector.injectFragment(PACK_FSH, List.of(surface(1, false)));
        assertFalse(out.contains("depthtex1"), "no depth declaration when no surface samples the scene depth");
    }

    @Test
    void registryBuildCarriesUsesSceneDepth() {
        var depth = snippet(
                List.of(), List.of(), "", "vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", false, true);
        assertTrue(IrisSurfaceRegistry.build(1, depth).usesSceneDepth(), "build propagates usesSceneDepth=true");
        var plain = snippet(
                List.of(), List.of(), "", "vec3(1.0), 1.0, vec3(0,0,1), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0", false, false);
        assertFalse(IrisSurfaceRegistry.build(2, plain).usesSceneDepth(), "build propagates usesSceneDepth=false");
    }

    @Test
    void validationHarnessDeclaresDepthtex1ForSceneDepthSurfaces() {
        String harness = IrisSurfaceRegistry.validationHarness(depthSurface(1));
        assertTrue(harness.contains("uniform sampler2D depthtex1;"),
                "a scene-depth surface's helpers reference depthtex1 — the harness must declare it");
    }

    /** An {@link com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet} with the fragment pieces
     *  under test and empty/absent runtime deps + vertex parts — new record components default here so
     *  ctor growth stops rippling through every test. */
    private static com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet snippet(
            List<String> decls, List<String> fns, String body, String args,
            boolean usesGeometry, boolean usesSceneDepth) {
        return new com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet(decls, fns, body, args,
                usesGeometry, usesSceneDepth, List.of(), java.util.Map.of(),
                null, null, null, null, List.of(), false);
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
