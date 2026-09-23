package com.lowdragmc.kilagraph.rendertype.format;

/**
 * A registry-friendly, <b>client-safe</b> description of a vertex attribute the RenderType graph can
 * compose into a {@link com.mojang.blaze3d.vertex.VertexFormat}. It deliberately holds <em>no</em>
 * {@code com.mojang.blaze3d} types (those are {@code @OnlyIn(CLIENT)}) so it can be referenced from the
 * shader compiler and graph {@code Settings}, which are exercised on the dedicated GameTest server.
 *
 * <p>The bridge to the real Minecraft attribute is {@link #gpuFormat()}, resolved only on the client when
 * an actual {@code VertexFormat} is built ({@link KGVertexFormat}).</p>
 *
 * @param key        registry key, e.g. {@code "position"}, {@code "uv0"}
 * @param attribName the GLSL/{@code VertexFormat} binding name, e.g. {@code "Position"} — vertex
 *                   attributes bind to shader inputs <b>by this name</b>, so the same value drives both
 *                   the built format and the generated {@code in …;} declaration
 * @param glslType   the GLSL input type, e.g. {@code "vec3"}, {@code "vec4"}, {@code "ivec2"}
 * @param gpuFormat  name of the {@code com.mojang.blaze3d.GpuFormat} constant, e.g. {@code "RGBA8_UNORM"};
 *                   its component type must match {@code glslType} (int/_SINT, uint/_UINT, float/the rest)
 */
public record KGVertexElement(String key, String attribName, String glslType, String gpuFormat) {
}
