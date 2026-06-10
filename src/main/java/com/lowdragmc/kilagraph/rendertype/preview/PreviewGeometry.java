package com.lowdragmc.kilagraph.rendertype.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Shared preview meshes (cube for the whole-graph panel, flat quad for per-node previews). Vertices
 * are written with only the attributes the target {@link VertexFormat} declares, so the same code
 * works for any pipeline format — and crucially the emitted layout always matches the buffer the
 * RenderType hands us (deriving from {@code renderType.format()} avoids "Missing elements" crashes).
 */
public final class PreviewGeometry {

    private static final int FULL_BRIGHT = 0x00F000F0;

    private PreviewGeometry() {}

    /** A unit cube centered at the origin, faces CCW-from-outside. {@code quads}: 4 verts/face vs 2 tris. */
    public static void cube(PoseStack.Pose pose, VertexConsumer vc, VertexFormat format, boolean quads) {
        float[][] c = {
                {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
                {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}
        };
        int[][] faces = {{1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {4, 0, 1, 5}, {3, 7, 6, 2}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            float[] n = normals[f];
            int[] order = quads ? new int[]{0, 1, 2, 3} : new int[]{0, 1, 2, 0, 2, 3};
            for (int i : order) vertex(vc, pose, format, c[q[i]], uvs[i], n);
        }
    }

    /** A flat unit quad in the XY plane (z=0), facing +Z, uv 0..1. {@code quads}: 4 verts vs 2 tris. */
    public static void quad(PoseStack.Pose pose, VertexConsumer vc, VertexFormat format, boolean quads) {
        float[][] c = {{-0.5f, -0.5f, 0f}, {0.5f, -0.5f, 0f}, {0.5f, 0.5f, 0f}, {-0.5f, 0.5f, 0f}};
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        float[] n = {0, 0, 1};
        int[] order = quads ? new int[]{0, 1, 2, 3} : new int[]{0, 1, 2, 0, 2, 3};
        for (int i : order) vertex(vc, pose, format, c[i], uvs[i], n);
    }

    /** Emit one vertex, writing only the attributes present in {@code format}. */
    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, VertexFormat format,
                               float[] p, float[] uv, float[] n) {
        vc.addVertex(pose, p[0], p[1], p[2]);
        if (format.contains(VertexFormatElement.COLOR)) vc.setColor(255, 255, 255, 255);
        if (format.contains(VertexFormatElement.UV0)) vc.setUv(uv[0], uv[1]);
        if (format.contains(VertexFormatElement.UV1)) vc.setOverlay(OverlayTexture.NO_OVERLAY);
        if (format.contains(VertexFormatElement.UV2)) vc.setLight(FULL_BRIGHT);
        if (format.contains(VertexFormatElement.NORMAL)) vc.setNormal(pose, n[0], n[1], n[2]);
    }
}
