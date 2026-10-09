package com.lowdragmc.kilagraph.rendertype.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code KG_Material} packing against std140 as GLSL lays the block out. A member one slot off still compiles
 * and draws — just with the neighbouring value — so nothing but these offsets would notice.
 */
class MaterialUniformLayoutTest {

    private static MaterialUniformLayout layout(GlslType... types) {
        var layout = new MaterialUniformLayout();
        for (int i = 0; i < types.length; i++) layout.addField("f" + i, types[i]);
        return layout;
    }

    @Test
    void aScalarAfterAVec3SitsInItsFourthSlot() {
        var layout = layout(GlslType.VEC3, GlslType.FLOAT, GlslType.FLOAT);
        assertArrayEquals(new int[]{0, 12, 16}, layout.std140Offsets());
        assertEquals(32, layout.std140Size());
    }

    @Test
    void vectorsAlignToTheirBaseAlignment() {
        // float@0, vec2@8, vec3@16 (12 bytes), vec4@32, int@48, vec2@56, vec3@64, bool@76
        var layout = layout(GlslType.FLOAT, GlslType.VEC2, GlslType.VEC3, GlslType.VEC4, GlslType.INT, GlslType.VEC2,
                GlslType.VEC3, GlslType.BOOL);
        assertArrayEquals(new int[]{0, 8, 16, 32, 48, 56, 64, 76}, layout.std140Offsets());
        assertEquals(80, layout.std140Size());
    }

    @Test
    void matricesAndStructsStartOnAVec4() {
        var layout = layout(GlslType.FLOAT, GlslType.MAT4, GlslType.FLOAT, GlslType.GRADIENT, GlslType.FLOAT,
                GlslType.CURVE);
        int gradient = (1 + GradientGlsl.MAX_KEYS * 2) * 16;
        assertArrayEquals(new int[]{0, 16, 80, 96, 96 + gradient, 112 + gradient}, layout.std140Offsets());
        assertEquals(112 + gradient + (1 + CurveGlsl.MAX_SEGMENTS * 2) * 16, layout.std140Size());
    }

    @Test
    void theBlockIsDeclaredInTheSameOrder() {
        var layout = layout(GlslType.VEC3, GlslType.FLOAT);
        assertEquals("""
                layout(std140) uniform KG_Material {
                    vec3 f0;
                    float f1;
                } kg_material;
                """, layout.blockGlsl());
    }
}
