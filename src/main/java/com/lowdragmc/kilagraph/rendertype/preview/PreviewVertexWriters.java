package com.lowdragmc.kilagraph.rendertype.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only registry mapping a {@link VertexFormatElement}'s id to the call that fills it for a preview
 * vertex. {@link PreviewRenderer} writes Position (via {@code addVertex}) then, for every other element the
 * target format declares, invokes the matching writer — so a preview vertex always carries exactly the
 * attributes the buffer expects (no "Missing elements in vertex"). Seeded with the built-ins; a mod that
 * registers a custom {@link com.lowdragmc.kilagraph.rendertype.format.KGVertexElement} should also
 * {@link #register} a writer for it, or preview of a format containing it can't be completed.
 */
public final class PreviewVertexWriters {

    /** Fills one element of a preview vertex into the consumer. */
    @FunctionalInterface
    public interface Writer {
        void write(VertexConsumer vc, PoseStack.Pose pose, PreviewVertex v);
    }

    private static final Map<Integer, Writer> BY_ID = new HashMap<>();

    static {
        register(VertexFormatElement.COLOR, (vc, pose, v) -> vc.setColor(v.color));
        register(VertexFormatElement.UV0, (vc, pose, v) -> vc.setUv(v.u, v.v));
        register(VertexFormatElement.UV1, (vc, pose, v) -> vc.setOverlay(v.overlay));
        register(VertexFormatElement.UV2, (vc, pose, v) -> vc.setLight(v.light));
        register(VertexFormatElement.NORMAL, (vc, pose, v) -> vc.setNormal(pose, v.nx, v.ny, v.nz));
        // TODO(1.21-backport milestone 2): VertexFormatElement.LINE_WIDTH / VertexConsumer.setLineWidth do
        // not exist in 1.21.1 — the line-width preview writer is omitted until the format is available again.
    }

    private PreviewVertexWriters() {}

    public static void register(VertexFormatElement element, Writer writer) {
        BY_ID.put(element.id(), writer);
    }

    public static Writer get(VertexFormatElement element) {
        return BY_ID.get(element.id());
    }
}
