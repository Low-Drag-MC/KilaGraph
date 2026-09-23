package com.lowdragmc.kilagraph.rendertype.preview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only registry mapping a {@link VertexFormatElement}'s name to the call that fills it for a preview
 * vertex. {@link PreviewRenderer} writes Position (via {@code addVertex}) then, for every other element the
 * target format declares, invokes the matching writer — so a preview vertex always carries exactly the
 * attributes the buffer expects (no "Missing elements in vertex"). Only built-in attributes can be written
 * ({@code BufferBuilder} writes nothing else).
 */
public final class PreviewVertexWriters {

    /** Fills one element of a preview vertex into the consumer. */
    @FunctionalInterface
    public interface Writer {
        void write(VertexConsumer vc, PoseStack.Pose pose, PreviewVertex v);
    }

    private static final Map<String, Writer> BY_NAME = new HashMap<>();

    static {
        register(DefaultVertexFormat.COLOR_SEMANTIC_NAME, (vc, pose, v) -> vc.setColor(v.color));
        register(DefaultVertexFormat.UV0_SEMANTIC_NAME, (vc, pose, v) -> vc.setUv(v.u, v.v));
        register(DefaultVertexFormat.UV1_SEMANTIC_NAME, (vc, pose, v) -> vc.setOverlay(v.overlay));
        register(DefaultVertexFormat.UV2_SEMANTIC_NAME, (vc, pose, v) -> vc.setLight(v.light));
        register(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, (vc, pose, v) -> vc.setNormal(pose, v.nx, v.ny, v.nz));
        register(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, (vc, pose, v) -> vc.setLineWidth(v.lineWidth));
    }

    private PreviewVertexWriters() {}

    public static void register(String attribName, Writer writer) {
        BY_NAME.put(attribName, writer);
    }

    public static Writer get(VertexFormatElement element) {
        return BY_NAME.get(element.name());
    }
}
