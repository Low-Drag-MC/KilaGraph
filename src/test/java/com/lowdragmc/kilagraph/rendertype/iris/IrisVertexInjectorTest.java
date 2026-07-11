package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-string tests of {@link IrisShaderInjector#injectVertex}: the {@code iris_Position}/{@code iris_Normal}
 * read-hijack that routes a pack vertex shader's position/normal through the {@code kg_surface_id}-dispatched
 * {@code kg_vpos}/{@code kg_vnormal} — mirroring how Iris's transformer emits pack sources
 * ({@code vec4(iris_Position, 1.0)} / {@code vec4(iris_Position + iris_vertex_offset, 1.0)}).
 */
class IrisVertexInjectorTest {

    /** A realistic flattened pack vsh: declarations + both verified gl_Vertex forms + a helper-function read. */
    private static final String PACK_VSH = """
            #version 330 core

            in vec3 iris_Position;
            in vec3 iris_Normal;
            uniform mat4 iris_ModelViewMat;
            uniform mat4 iris_ProjMat;
            out vec3 normal;
            out float dist;

            float distTo(vec3 p) { return length(iris_ModelViewMat * vec4(iris_Position, 1.0)); }

            void main() {
                normal = normalize(mat3(iris_ModelViewMat) * iris_Normal);
                dist = distTo(iris_Position);
                vec4 position = iris_ModelViewMat * vec4(iris_Position + iris_vertex_offset, 1.0);
                gl_Position = iris_ProjMat * iris_ModelViewMat * vec4(iris_Position, 1.0);
            }
            """;

    private static IrisSurfaceRegistry.Surface surface(int id, String vpos, String vnormal) {
        return new IrisSurfaceRegistry.Surface(id, List.of(), List.of(),
                "kg_Surface kg_surface_" + id + "(vec2 kg_uv) { return kg_Surface(vec3(1.0), 1.0,"
                        + " vec3(0.0, 0.0, 1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0); }\n",
                false, false, vpos, vnormal, List.of());
    }

    private static IrisSurfaceRegistry.Surface positionSurface(int id) {
        return surface(id, "vec3 kg_vpos_" + id + "(vec3 kg_pos, vec3 kg_n) {\n"
                + "    return kg_pos + vec3(0.0, 1.0, 0.0);\n}\n", null);
    }

    private static IrisSurfaceRegistry.Surface normalSurface(int id) {
        return surface(id, null, "vec3 kg_vnormal_" + id + "(vec3 kg_pos, vec3 kg_n) {\n"
                + "    return -kg_n;\n}\n");
    }

    @Test
    void rewritesBothVerifiedPositionReadForms() {
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(positionSurface(1)));
        assertTrue(out.contains("vec4(kg_vpos(iris_Position), 1.0)"),
                "the plain vec4(iris_Position, 1.0) form is hijacked");
        assertTrue(out.contains("vec4(kg_vpos(iris_Position) + iris_vertex_offset, 1.0)"),
                "iris_vertex_offset stays OUTSIDE the displacement (mirrors ModelOffset handling)");
        assertTrue(out.contains("dist = distTo(kg_vpos(iris_Position));"),
                "reads inside the pack's own helper calls are hijacked too");
        assertTrue(out.contains("float distTo(vec3 p) { return length(iris_ModelViewMat * vec4(kg_vpos(iris_Position), 1.0)); }"),
                "reads inside pack helper function bodies are hijacked");
    }

    @Test
    void declarationLinesAreNeverRewritten() {
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(positionSurface(1)));
        assertTrue(out.contains("in vec3 iris_Position;"), "the attribute declaration stays raw");
        assertFalse(out.contains("in vec3 kg_vpos(iris_Position);"), "no hijack on the declaration line");

        String layout = PACK_VSH.replace("in vec3 iris_Position;", "layout(location = 0) in vec3 iris_Position;");
        String out2 = IrisShaderInjector.injectVertex(layout, List.of(positionSurface(1)));
        assertTrue(out2.contains("layout(location = 0) in vec3 iris_Position;"),
                "layout-qualified declarations are skipped too");

        // CRLF line endings / a trailing comment must not turn the declaration into a rewrite target.
        String crlf = PACK_VSH.replace("in vec3 iris_Position;", "in vec3 iris_Position; // vertex position")
                .replace("\n", "\r\n");
        String out3 = IrisShaderInjector.injectVertex(crlf, List.of(positionSurface(1)));
        assertTrue(out3.contains("in vec3 iris_Position; // vertex position"),
                "commented CRLF declaration stays raw");
        assertFalse(out3.contains("in vec3 kg_vpos(iris_Position);"), "no hijack on the CRLF declaration line");
    }

    @Test
    void emitsDispatcherWithIdentityFallback() {
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(positionSurface(1), positionSurface(2)));
        assertTrue(out.contains("uniform int kg_surface_id;"), "discriminator declared");
        assertTrue(out.contains("if (kg_surface_id == 1) return kg_vpos_1(kg_p, iris_Normal);"), "surface 1 dispatched");
        assertTrue(out.contains("if (kg_surface_id == 2) return kg_vpos_2(kg_p, iris_Normal);"), "surface 2 dispatched");
        assertTrue(out.contains("return kg_p;"),
                "id 0 / unclaimed ids return the input unchanged (non-KilaGraph draws bit-identical)");
    }

    @Test
    void untouchedWhenNoSurfaceDisplaces() {
        var fragmentOnly = surface(1, null, null);
        assertSame(PACK_VSH, IrisShaderInjector.injectVertex(PACK_VSH, List.of(fragmentOnly)),
                "no displacing surface -> the pack vsh is returned as-is (not even a marker)");
        assertSame(PACK_VSH, IrisShaderInjector.injectVertex(PACK_VSH, List.of()),
                "no surfaces at all -> untouched");
    }

    @Test
    void untouchedWhenSourceReadsNothingWeHijack() {
        String composite = """
                #version 330 core
                in vec2 iris_UV0;
                void main() { gl_Position = vec4(iris_UV0 * 2.0 - 1.0, 0.0, 1.0); }
                """;
        assertSame(composite, IrisShaderInjector.injectVertex(composite, List.of(positionSurface(1))),
                "a composite-style vsh without iris_Position reads stays untouched");
    }

    @Test
    void idempotent() {
        String once = IrisShaderInjector.injectVertex(PACK_VSH, List.of(positionSurface(1)));
        assertSame(once, IrisShaderInjector.injectVertex(once, List.of(positionSurface(1))),
                "a second pass sees the marker and no-ops");
    }

    @Test
    void normalOnlySurfaceLeavesPositionAlone() {
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(normalSurface(1)));
        assertFalse(out.contains("kg_vpos("), "no position hijack for a normal-only surface");
        assertTrue(out.contains("normalize(mat3(iris_ModelViewMat) * kg_vnormal(iris_Normal))"),
                "the normal read is hijacked");
        assertTrue(out.contains("if (kg_surface_id == 1) return kg_vnormal_1(iris_Position, kg_n);"),
                "the normal dispatcher passes the raw position");
        assertTrue(out.contains("return kg_n;"), "identity fallback for the normal dispatcher");
    }

    @Test
    void positionOnlySurfaceLeavesNormalAlone() {
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(positionSurface(1)));
        assertFalse(out.contains("kg_vnormal("), "no normal hijack for a position-only surface");
        assertTrue(out.contains("normal = normalize(mat3(iris_ModelViewMat) * iris_Normal);"),
                "the pack's normal line is untouched");
    }

    @Test
    void missingNormalDeclarationDegradesToConstant() {
        String noNormal = """
                #version 330 core
                in vec3 iris_Position;
                void main() { gl_Position = vec4(iris_Position, 1.0); }
                """;
        String out = IrisShaderInjector.injectVertex(noNormal, List.of(positionSurface(1)));
        assertTrue(out.contains("kg_vpos_1(kg_p, vec3(0.0, 1.0, 0.0))"),
                "no iris_Normal declared -> the dispatcher passes a constant, never an undeclared name");
    }

    @Test
    void sharedDeclarationUnitsAreDedupedAndEmitted() {
        String decl = "layout(std140) uniform KG_Transforms {\n    mat4 ModelViewMat;\n} kg_transforms;\n";
        var a = new IrisSurfaceRegistry.Surface(1, List.of(decl), List.of(), "", false, false,
                "vec3 kg_vpos_1(vec3 kg_pos, vec3 kg_n) { return kg_pos; }\n", null, List.of());
        var b = new IrisSurfaceRegistry.Surface(2, List.of(decl), List.of(), "", false, false,
                "vec3 kg_vpos_2(vec3 kg_pos, vec3 kg_n) { return kg_pos; }\n", null, List.of());
        String out = IrisShaderInjector.injectVertex(PACK_VSH, List.of(a, b));
        assertEquals(1, countOccurrences(out, "uniform KG_Transforms"),
                "an identical declaration shared by surfaces is emitted once");
    }

    @Test
    void guardedInjectionReturnsOriginalOnThrow() {
        var poisoned = new IrisSurfaceRegistry.Surface(1, List.of(), List.of(), "", false, false,
                "vec3 kg_vpos_1(vec3 kg_pos, vec3 kg_n) { return kg_pos; }\n", null, null /* NPE on dedup */);
        assertSame(PACK_VSH, IrisShaderInjector.injectVertexGuarded("gbuffers_entities", PACK_VSH, List.of(poisoned)),
                "any throw hands Iris the untouched source — the pack can never be broken");
    }

    @Test
    void registryBuildAssemblesNamespacedVertexFunctions() {
        var raw = new InjectionSnippet(
                List.of("layout(std140) uniform KG_Material {\n    vec4 kg_amp;\n} kg_material;\n"),
                List.of(), "", "vec3(1.0), 1.0, vec3(0.0, 0.0, 1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0",
                false, false, List.of(), java.util.Map.of(),
                "    vec3 v_0 = kg_material.kg_amp.xyz;\n", "kg_pos + v_0",
                null, null,
                List.of("float kg_wave(float x) { return sin(x); }\n"), false);
        var s = IrisSurfaceRegistry.build(7, raw);
        assertNotNull(s.vertexPositionFunction());
        assertNull(s.vertexNormalFunction());
        assertTrue(s.vertexPositionFunction().startsWith("vec3 kg_vpos_7(vec3 kg_pos, vec3 kg_n) {"),
                "canonical two-parameter signature");
        assertTrue(s.vertexPositionFunction().contains("kg_material_7.kg_amp"),
                "KG_Material accessor namespaced by id");
        assertTrue(s.vertexPositionFunction().contains("return kg_pos + v_0;"), "body + return expression");
        assertTrue(s.declarationUnits().get(0).contains("uniform KG_Material_7"), "block declaration namespaced");
        assertEquals("float kg_wave(float x) { return sin(x); }\n", s.vertexFunctions().get(0),
                "vertex helper functions carried through");
    }

    @Test
    void vertexValidationHarnessCallsTheFunctions() {
        var s = surface(3,
                "vec3 kg_vpos_3(vec3 kg_pos, vec3 kg_n) { return kg_pos; }\n",
                "vec3 kg_vnormal_3(vec3 kg_pos, vec3 kg_n) { return kg_n; }\n");
        String harness = IrisSurfaceRegistry.vertexValidationHarness(s);
        assertTrue(harness.startsWith("#version 330 core\n"), "standalone vertex shader");
        assertTrue(harness.contains("kg_p = kg_vpos_3(kg_p, kg_nrm);"), "position function is CALLED (not stripped)");
        assertTrue(harness.contains("kg_nrm = kg_vnormal_3(kg_p, kg_nrm);"), "normal function is CALLED");
        assertTrue(harness.contains("gl_Position = "), "writes gl_Position so the compiler keeps everything");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
