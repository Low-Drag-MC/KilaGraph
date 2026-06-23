package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-matrix tests pinning {@link ShaderGraphCompiler#convert}'s type-conversion contract — the
 * float&harr;vecN broadcast / swizzle / pad rules that mirror {@code RenderTypeGraphModel.canAssignTo}.
 *
 * <p>Pure logic (only {@link ShaderExpr} / {@link GlslType}); no Minecraft classes, so it runs as a
 * plain JUnit test. Locks the agreed policy: scalar broadcast via constructor, downsize via swizzle,
 * upsize pads with 0.0 except a vec4 target's last (w) component which pads 1.0.</p>
 */
class ShaderConvertTest {

    private static ShaderExpr expr(String code, GlslType type) {
        return new ShaderExpr(code, type);
    }

    private static String convert(String code, GlslType from, GlslType to) {
        return ShaderGraphCompiler.convert(expr(code, from), to).code();
    }

    private static GlslType typeOf(String code, GlslType from, GlslType to) {
        return ShaderGraphCompiler.convert(expr(code, from), to).type();
    }

    // ---- identity / no-op --------------------------------------------------------------------

    @Test
    void sameTypeReturnsSameInstance() {
        ShaderExpr v = expr("x", GlslType.VEC3);
        assertSame(v, ShaderGraphCompiler.convert(v, GlslType.VEC3), "identical type must be a no-op");
    }

    @Test
    void nullTargetReturnsInput() {
        ShaderExpr v = expr("x", GlslType.VEC3);
        assertSame(v, ShaderGraphCompiler.convert(v, null));
    }

    // ---- the conversion matrix ---------------------------------------------------------------

    static Stream<Arguments> conversions() {
        return Stream.of(
                // scalar broadcast: float -> vecN via constructor
                Arguments.of("a", GlslType.FLOAT, GlslType.VEC2, "vec2(a)"),
                Arguments.of("a", GlslType.FLOAT, GlslType.VEC3, "vec3(a)"),
                Arguments.of("a", GlslType.FLOAT, GlslType.VEC4, "vec4(a)"),

                // upsize: pad 0.0, but vec4 target's last (w) component pads 1.0
                Arguments.of("v", GlslType.VEC2, GlslType.VEC3, "vec3(v, 0.0)"),
                Arguments.of("v", GlslType.VEC2, GlslType.VEC4, "vec4(v, 0.0, 1.0)"),
                Arguments.of("v", GlslType.VEC3, GlslType.VEC4, "vec4(v, 1.0)"),

                // downsize: swizzle
                Arguments.of("v", GlslType.VEC4, GlslType.VEC3, "(v).xyz"),
                Arguments.of("v", GlslType.VEC4, GlslType.VEC2, "(v).xy"),
                Arguments.of("v", GlslType.VEC3, GlslType.VEC2, "(v).xy"),

                // to float: take .x
                Arguments.of("v", GlslType.VEC2, GlslType.FLOAT, "(v).x"),
                Arguments.of("v", GlslType.VEC3, GlslType.FLOAT, "(v).x"),
                Arguments.of("v", GlslType.VEC4, GlslType.FLOAT, "(v).x"),

                // int / bool normalisation
                Arguments.of("i", GlslType.INT, GlslType.FLOAT, "float(i)"),
                Arguments.of("a", GlslType.FLOAT, GlslType.INT, "int(a)"),
                Arguments.of("i", GlslType.INT, GlslType.VEC3, "vec3(float(i))"),
                Arguments.of("b", GlslType.BOOL, GlslType.VEC2, "vec2(float(b))")
        );
    }

    @ParameterizedTest(name = "{1} -> {2} : {3}")
    @MethodSource("conversions")
    void convertMatrix(String code, GlslType from, GlslType to, String expected) {
        assertEquals(expected, convert(code, from, to));
        assertEquals(to, typeOf(code, from, to), "result must be tagged with the target type");
    }

    // ---- opaque types are never wrapped/copied, only retagged --------------------------------

    @Test
    void samplerIsNeverConverted() {
        // sampler -> sampler is identity (same type), and sampler can't be produced as any vec.
        ShaderExpr s = expr("Sampler0", GlslType.SAMPLER2D);
        assertSame(s, ShaderGraphCompiler.convert(s, GlslType.SAMPLER2D));
    }

    @Test
    void mat4KeepsCodeWhenRetagged() {
        // mat4 is not arithmetic-convertible: code is preserved, only the tag changes.
        assertEquals("M", convert("M", GlslType.MAT4, GlslType.VEC4));
    }

    // ---- GlslFormat literal emission (locale-stable, always a decimal point) -----------------

    @Test
    void floatLiteralAlwaysHasDecimalPoint() {
        assertEquals("1.0", GlslFormat.f(1f));
        assertEquals("0.0", GlslFormat.f(0f));
        assertEquals("-2.0", GlslFormat.f(-2f));
    }

    @Test
    void vectorConstantLiterals() {
        assertEquals("vec3(0.25, 0.5, 0.75)",
                GlslFormat.literal(new org.joml.Vector3f(0.25f, 0.5f, 0.75f), GlslType.VEC3));
        assertEquals("vec4(1.0, 0.0, 0.0, 1.0)",
                GlslFormat.literal(new org.joml.Vector4f(1f, 0f, 0f, 1f), GlslType.VEC4));
        // scalar number broadcasts into a vector literal
        assertEquals("vec3(2.0)", GlslFormat.literal(2f, GlslType.VEC3));
    }

    /**
     * Custom vec TypeHandles must carry a non-null default-value supplier. Without it, a constant of
     * that type (port / Constant node / variable) starts null and LDLib2's {@code Vector*fAccessor}
     * NPEs while building its editor — i.e. opening any graph crashes. Guards that regression.
     */
    @Test
    void customVectorTypesHaveNonNullDefaults() {
        assertInstanceOf(org.joml.Vector2f.class, RenderTypeGraphTypes.VEC2.getDefaultValue());
        assertInstanceOf(org.joml.Vector3f.class, RenderTypeGraphTypes.VEC3.getDefaultValue());
        assertInstanceOf(org.joml.Vector4f.class, RenderTypeGraphTypes.VEC4.getDefaultValue());
    }

    // ---- GRADIENT: opaque wire type + std140 packing -----------------------------------------

    @Test
    void gradientTypeResolvesAndIsOpaque() {
        assertEquals(GlslType.GRADIENT, GlslType.of(RenderTypeGraphTypes.GRADIENT));
        assertInstanceOf(RenderTypeGraphTypes.GradientValue.class, RenderTypeGraphTypes.GRADIENT.getDefaultValue());
        // opaque: not arithmetic-convertible, code preserved & only retagged (mirrors sampler/mat4).
        assertEquals("g", convert("g", GlslType.GRADIENT, GlslType.VEC4));
    }

    @Test
    void gradientPacksStd140HeaderAndKeys() {
        // Default gradient: black(0)->white(1), full alpha, BLEND.
        float[] p = GradientGlsl.pack(RenderTypeGraphTypes.GradientValue.defaultValue());
        assertEquals(GradientGlsl.PACKED_FLOATS, p.length);
        assertEquals(68, p.length); // vec4 header + 8 colour vec4 + 8 alpha vec4
        // header = (type, colorsLength, alphasLength, _)
        assertEquals(0f, p[0]); // BLEND
        assertEquals(2f, p[1]); // 2 colour keys
        assertEquals(2f, p[2]); // 2 alpha keys
        // colours[0] = (pos, r, g, b) = (0, 0,0,0); colours[1] = (1, 1,1,1)
        assertEquals(0f, p[4]); assertEquals(0f, p[5]); assertEquals(0f, p[6]); assertEquals(0f, p[7]);
        assertEquals(1f, p[8]); assertEquals(1f, p[9]); assertEquals(1f, p[10]); assertEquals(1f, p[11]);
        // alphas[0] = (pos, alpha, 0, 0) = (0, 1, 0, 0); alphas[1] = (1, 1, 0, 0)
        assertEquals(0f, p[36]); assertEquals(1f, p[37]);
        assertEquals(1f, p[40]); assertEquals(1f, p[41]);
    }

    @Test
    void gradientFixedModePacksTypeOne() {
        var fixed = RenderTypeGraphTypes.GradientValue.defaultValue()
                .withMode(RenderTypeGraphTypes.BlendMode.FIXED);
        assertEquals(1f, GradientGlsl.pack(fixed)[0]);
    }

    @Test
    void gradientBuilderEmitsAllSlots() {
        String glsl = GradientGlsl.builderFunction("kg_test", RenderTypeGraphTypes.GradientValue.defaultValue());
        assertTrue(glsl.contains("KG_Gradient kg_test()"), glsl);
        assertTrue(glsl.contains("g.header = vec4(0.0, 2.0, 2.0, 0.0);"), glsl);
        assertTrue(glsl.contains("g.colors[0] = vec4(0.0, 0.0, 0.0, 0.0);"), glsl);
        assertTrue(glsl.contains("g.colors[1] = vec4(1.0, 1.0, 1.0, 1.0);"), glsl);
        // all 8 slots filled (NaN-safety) and the function returns the struct.
        assertTrue(glsl.contains("g.colors[7] ="), glsl);
        assertTrue(glsl.contains("g.alphas[7] ="), glsl);
        assertTrue(glsl.contains("return g;"), glsl);
    }
}
