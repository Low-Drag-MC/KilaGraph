package com.lowdragmc.kilagraph.rendertype.iris;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many surfaces a pack program has room for: past a stage's uniform block or sampler limit the program wouldn't
 * link, and Iris would drop the whole pack.
 */
class IrisInjectionFitTest {

    private static final String PACK = """
            #version 330 core
            layout(std140) uniform iris_Fog { vec4 iris_FogColor; };
            uniform sampler2D gtexture;
            uniform sampler2D lightmap;
            uniform sampler2D unreadNoise;
            void main() { gl_FragColor = texture(gtexture, vec2(0.0)) * texture(lightmap, vec2(0.0)) * iris_FogColor; }
            """;
    private static final String GLOBALS = "layout(std140) uniform KG_Globals { float Time; } kg_globals;\n";

    private static IrisSurfaceRegistry.Surface surface(int id, boolean sceneDepth, String... units) {
        return new IrisSurfaceRegistry.Surface(id, List.of(units), List.of(), "", false, sceneDepth, null, null, List.of());
    }

    private static String material(int id) {
        return "layout(std140) uniform KG_Material_" + id + " { vec4 kg_c; } kg_material_" + id + ";\n";
    }

    private static List<Integer> fit(String source, IrisShaderInjector.Limits limits, IrisSurfaceRegistry.Surface... surfaces) {
        return IrisShaderInjector.fit(source, List.of(surfaces), limits, true).stream()
                .map(IrisSurfaceRegistry.Surface::id).toList();
    }

    @Test
    void aSharedDeclarationTakesItsBlockOnce() {
        // pack 1 + globals 1 + two materials = 4
        var limits = new IrisShaderInjector.Limits(4, 16);
        assertEquals(List.of(1, 2), fit(PACK, limits, surface(1, false, material(1), GLOBALS),
                surface(2, false, material(2), GLOBALS), surface(3, false, material(3))));
    }

    @Test
    void aSurfaceThatDoesNotFitIsLeftOutForALaterOneThatDoes() {
        var limits = new IrisShaderInjector.Limits(16, 3);
        String twoSamplers = "uniform sampler2D kg_a;\nuniform sampler2D kg_b;\n";
        assertEquals(List.of(2), fit(PACK, limits, surface(1, false, twoSamplers),
                surface(2, false, "uniform sampler2D kg_c;\n")));
    }

    @Test
    void aSamplerThePackDoesNotReadTakesNoUnit() {
        // gtexture + lightmap read, unreadNoise not: one unit left of 3
        var limits = new IrisShaderInjector.Limits(16, 3);
        assertEquals(List.of(1), fit(PACK, limits, surface(1, false, "uniform sampler2D kg_a;\n"),
                surface(2, false, "uniform sampler2D kg_b;\n")));
    }

    @Test
    void aSamplerArrayTakesAUnitPerElement() {
        var limits = new IrisShaderInjector.Limits(16, 5);
        assertEquals(List.of(), fit(PACK, limits, surface(1, false, "uniform sampler2D kg_a[4];\n")));
        assertEquals(List.of(1), fit(PACK, limits, surface(1, false, "uniform sampler2D kg_a[3];\n")));
    }

    @Test
    void sceneDepthTakesTheDepthSamplerUnlessThePackDeclaresIt() {
        var limits = new IrisShaderInjector.Limits(16, 2);
        assertEquals(List.of(), fit(PACK, limits, surface(1, true)));
        String withDepth = PACK.replace("void main()", "uniform sampler2D depthtex1;\nvoid main()");
        assertEquals(List.of(1), fit(withDepth, limits, surface(1, true)));
    }

    @Test
    void theVertexStageTakesNoDepthSampler() {
        var limits = new IrisShaderInjector.Limits(16, 2);
        assertEquals(List.of(1), IrisShaderInjector.fit(PACK, List.of(surface(1, true)), limits, false).stream()
                .map(IrisSurfaceRegistry.Surface::id).toList());
    }

    @Test
    void withoutALimitEverySurfaceFits() {
        assertEquals(List.of(1, 2, 3), fit(PACK, IrisShaderInjector.Limits.NONE, surface(1, true, material(1)),
                surface(2, false, material(2)), surface(3, false, "uniform sampler2D kg_a[64];\n")));
    }
}
