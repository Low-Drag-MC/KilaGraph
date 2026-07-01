package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turns a {@link CompiledShaderGraph} into a usable Minecraft {@code RenderType}.
 *
 * <p>TODO(1.21-backport milestone 2): the pipeline-building path is entirely 1.21.5+ blaze3d
 * ({@code RenderPipeline}/{@code RenderSetup}, {@code net.minecraft.client.renderer.rendertype.RenderType},
 * {@code com.mojang.blaze3d.pipeline.*}, {@code UniformType}, {@code GpuSampler}, refcounted pipeline
 * cache, GPU precompile), none of which exist in 1.21.1 (which uses {@code ShaderInstance} +
 * {@code RenderStateShard}-composed {@code RenderType}s). {@link #createMaterial} was stubbed to return
 * {@code null} for the compile-only milestone; only the version-portable vertex-format/mode mapping is
 * kept. Reimplement material/pipeline creation against the 1.21.1 rendering model.</p>
 */
public final class RenderTypeFactory {

    private RenderTypeFactory() {}

    // TODO(1.21-backport milestone 2): compile the graph + build a RenderType-backed material.
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(RenderTypeGraph graph) {
        return null;
    }

    // TODO(1.21-backport milestone 2): build a material from an already-compiled graph.
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled) {
        return null;
    }

    // ---- Settings -> vertex format mapping (version-portable) --------------------------------

    public static VertexFormat vertexFormat(List<String> elementKeys) {
        return KGVertexFormat.of(elementKeys);
    }

    public static VertexFormat.Mode vertexMode(RenderTypeGraph.Settings.VertexFormatMode mode) {
        return switch (mode) {
            case QUADS -> VertexFormat.Mode.QUADS;
            case TRIANGLES -> VertexFormat.Mode.TRIANGLES;
            case TRIANGLE_STRIP -> VertexFormat.Mode.TRIANGLE_STRIP;
            case LINES -> VertexFormat.Mode.LINES;
            case LINE_STRIP -> VertexFormat.Mode.DEBUG_LINE_STRIP;
        };
    }
}
