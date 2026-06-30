package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side bridge from a {@link KGPreviewContent} to a {@code VertexConsumer}: builds the content's mesh,
 * tessellates it for the pipeline's primitive mode ({@link PreviewTessellator}), and writes each vertex —
 * Position via {@code addVertex}, then every other element the {@code VertexFormat} declares via its
 * {@link PreviewVertexWriters} writer. So emitted vertices always match the buffer the RenderType hands us.
 */
public final class PreviewRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Element ids we've already warned about lacking a writer (warn once, not every frame). */
    private static final java.util.Set<Integer> WARNED_MISSING_WRITER = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PreviewRenderer() {}

    /** Build {@code content} and emit it for the given format + mode. */
    public static void render(KGPreviewContent content, PoseStack.Pose pose, VertexConsumer vc,
                              VertexFormat format, RenderTypeGraph.Settings.VertexFormatMode mode) {
        var mesh = new PreviewMeshBuilder();
        content.build(mesh);
        emit(PreviewTessellator.toStream(mesh, mode), pose, vc, format);
    }

    /** Write a tessellated vertex stream into {@code vc}, filling the format's declared elements that we have
     *  writers for. Elements without a writer are left unwritten — an <em>extending</em> {@code BufferBuilder}
     *  (e.g. Iris, which swaps the format to {@code IrisVertexFormats.ENTITY} and auto-fills its extra
     *  {@code iris_Entity}/{@code mc_midTexCoord}/{@code at_tangent} attributes) populates them itself, exactly
     *  as it does for vanilla geometry. Aborting the whole draw here instead (the old behaviour) is what made
     *  graph models invisible under a shaderpack. */
    public static void emit(List<PreviewVertex> stream, PoseStack.Pose pose, VertexConsumer vc, VertexFormat format) {
        var writers = new ArrayList<PreviewVertexWriters.Writer>();
        for (VertexFormatElement element : format.getElements()) {
            if (element == VertexFormatElement.POSITION) continue;
            var writer = PreviewVertexWriters.get(element);
            if (writer == null) {
                // Unknown element (e.g. an Iris-extended attribute, or a mod's custom element). Skip it and
                // rely on the buffer to fill it; warn once so a genuinely-missing writer is still diagnosable.
                if (WARNED_MISSING_WRITER.add(element.id())) {
                    LOGGER.warn("[KilaGraph] no preview writer for vertex element id {}; leaving it for the "
                            + "buffer to fill (Iris-extended attribute?). Register one via PreviewVertexWriters.register "
                            + "if it should be written explicitly.", element.id());
                }
                continue;
            }
            writers.add(writer);
        }
        for (PreviewVertex v : stream) {
            vc.addVertex(pose, v.x, v.y, v.z); // POSITION (always first / always present)
            for (PreviewVertexWriters.Writer w : writers) {
                w.write(vc, pose, v);
            }
        }
    }
}
