package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Client-side bridge from a {@link KGPreviewContent} to a {@code VertexConsumer}: builds the content's mesh,
 * tessellates it for the pipeline's primitive mode ({@link PreviewTessellator}), and writes each vertex —
 * Position via {@code addVertex}, then every other element the {@code VertexFormat} declares via its
 * {@link PreviewVertexWriters} writer. So emitted vertices always match the buffer the RenderType hands us.
 */
public final class PreviewRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Element ids we've already warned about lacking a writer (warn once, not every frame). */
    private static final Set<Integer> WARNED_MISSING_WRITER = ConcurrentHashMap.newKeySet();

    private PreviewRenderer() {}

    /** Build {@code content} and emit it for the given format + mode. */
    public static void render(KGPreviewContent content, PoseStack.Pose pose, VertexConsumer vc,
                              VertexFormat format, RenderTypeGraph.Settings.VertexFormatMode mode) {
        var mesh = new PreviewMeshBuilder();
        content.build(mesh);
        emit(PreviewTessellator.toStream(mesh, mode), pose, vc, format);
    }

    /** Write a tessellated vertex stream into {@code vc}, filling exactly the format's declared elements. */
    public static void emit(List<PreviewVertex> stream, PoseStack.Pose pose, VertexConsumer vc, VertexFormat format) {
        // Resolve a writer for every non-position element up front. If any is missing (a mod registered a
        // custom VertexFormatElement but no PreviewVertexWriters writer for it), skip the whole draw —
        // emitting a partial vertex would make BufferBuilder throw "Missing elements in vertex".
        var writers = new ArrayList<PreviewVertexWriters.Writer>();
        for (VertexFormatElement element : format.getElements()) {
            if (element == VertexFormatElement.POSITION) continue;
            var writer = PreviewVertexWriters.get(element);
            if (writer == null) {
                if (WARNED_MISSING_WRITER.add(element.id())) {
                    LOGGER.warn("[KilaGraph] no preview writer for vertex element id {}; skipping preview. "
                            + "Register one via PreviewVertexWriters.register.", element.id());
                }
                return;
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
