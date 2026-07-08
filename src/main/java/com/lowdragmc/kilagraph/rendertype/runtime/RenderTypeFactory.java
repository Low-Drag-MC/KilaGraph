package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turns a {@link CompiledShaderGraph} into a drawable {@link RenderTypeGraphMaterial} for the 1.21.1 backport:
 * builds a vanilla {@link ShaderInstance} from the generated GLSL + manifest via
 * {@link KGShaderResourceProvider#createShaderInstance} (the provider serves the synthetic shader assets and lets
 * vanilla resolve the {@code #moj_import} includes normally), then wraps it in a material. Everything is individual
 * uniforms (no UBO); the material's {@link RenderTypeGraphMaterial#applyUniforms()} stages the KG-managed + EXPOSED
 * ones at draw while {@code ShaderInstance.setDefaultUniforms} handles the vanilla builtins.
 *
 * <p>The material exposes a vanilla {@code RenderType} for in-world rendering ({@link RenderTypeGraphMaterial#renderType()}
 * — verified drawing through a {@code MultiBufferSource}). TODO(1.21-backport milestone 2): material
 * caching/refcounting by content hash (26.1 cached pipelines); each call currently builds a fresh ShaderInstance and
 * the material is short-lived (the preview rebuilds only on content-hash change).</p>
 */
public final class RenderTypeFactory {

    private RenderTypeFactory() {}

    /** Compile {@code graph} and build a material from it. Returns {@code null} if the GLSL fails to compile. */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(RenderTypeGraph graph) {
        return createMaterial(graph.createCompiler().compile());
    }

    /** Build a material from an already-compiled graph. Returns {@code null} on stage errors or GLSL compile
     *  failure (the caller keeps its last good material rather than drawing a broken one). Must run on the render
     *  thread — {@link KGShaderResourceProvider#createShaderInstance} compiles/links GL programs. */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled) {
        VertexFormat format = vertexFormat(compiled.settings().vertexFormatElements());
        LDShaderInstance shader = KGShaderResourceProvider.createShaderInstance(compiled, format);
        if (shader == null) return null; // stage errors / compile failure already logged
        return new RenderTypeGraphMaterial(shader, compiled);
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
