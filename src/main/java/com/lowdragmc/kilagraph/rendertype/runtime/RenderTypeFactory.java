package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat;
import com.lowdragmc.lowdraglib2.client.shader.management.Shader;
import com.lowdragmc.lowdraglib2.client.shader.management.ShaderProgram;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * Turns a {@link CompiledShaderGraph} into a drawable {@link RenderTypeGraphMaterial} for the 1.21.1 backport:
 * compiles the generated GLSL into an LDLib2 raw {@link ShaderProgram} ({@link Shader#loadShader}), then wraps
 * it in a material. Everything is individual uniforms (no UBO); the material's {@link RenderTypeGraphMaterial#apply()}
 * binds them at draw. {@code #moj_import} directives (1.21.1 {@code fog/light/projection/matrix.glsl}) are inlined
 * by {@link GlslImportProcessor} before compilation (the GL driver can't resolve them).
 *
 * <p>TODO(1.21-backport milestone 2): vanilla RenderType integration + pipeline caching/refcounting (26.1 cached
 * pipelines by content hash and drew through a real {@code RenderType}); for the current preview slice each call
 * compiles a fresh program and the material is short-lived (the preview rebuilds only on content-hash change).</p>
 */
public final class RenderTypeFactory {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RenderTypeFactory() {}

    /** Compile {@code graph} and build a material from it. Returns {@code null} if the GLSL fails to compile. */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(RenderTypeGraph graph) {
        return createMaterial(new ShaderGraphCompiler(graph).compile());
    }

    /** Build a material from an already-compiled graph. Returns {@code null} on stage errors or GLSL compile
     *  failure (the caller keeps its last good material rather than drawing a broken one). */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled) {
        if (compiled.hasStageErrors()) {
            for (var e : compiled.stageErrors()) LOGGER.warn("[KilaGraph] stage error: {}", e.message());
            return null;
        }
        // Inline #moj_import the same way vanilla does at shader load — the GL driver doesn't understand it.
        String vsh = GlslImportProcessor.process(compiled.vertexSource());
        String fsh = GlslImportProcessor.process(compiled.fragmentSource());
        ShaderProgram program = new ShaderProgram();
        try {
            program.attach(Shader.loadShader(Shader.ShaderType.VERTEX, vsh));
            program.attach(Shader.loadShader(Shader.ShaderType.FRAGMENT, fsh));
            program.linkProgram();
        } catch (RuntimeException e) {
            LOGGER.warn("[KilaGraph] shader compile/link failed for hash {}: {}\n--- VERTEX ---\n{}\n--- FRAGMENT ---\n{}",
                    compiled.contentHash(), e.getMessage(), vsh, fsh);
            program.delete();
            return null;
        }
        return new RenderTypeGraphMaterial(program, compiled);
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
