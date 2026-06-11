package com.lowdragmc.kilagraph.rendertype.preview;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects a preview content's geometry as format-agnostic primitives: quads (4 verts, CCW from the front),
 * triangles (3 verts, CCW), and lines (2 verts). {@link PreviewTessellator} turns these into the vertex
 * stream a specific {@code VertexFormat.Mode} expects. Client-safe (no GL).
 */
public final class PreviewMeshBuilder {
    public final List<PreviewVertex[]> quads = new ArrayList<>();
    public final List<PreviewVertex[]> tris = new ArrayList<>();
    public final List<PreviewVertex[]> lines = new ArrayList<>();

    public void quad(PreviewVertex a, PreviewVertex b, PreviewVertex c, PreviewVertex d) {
        quads.add(new PreviewVertex[]{a, b, c, d});
    }

    public void tri(PreviewVertex a, PreviewVertex b, PreviewVertex c) {
        tris.add(new PreviewVertex[]{a, b, c});
    }

    public void line(PreviewVertex a, PreviewVertex b) {
        lines.add(new PreviewVertex[]{a, b});
    }

    /** A quad from raw corner positions + a shared normal; uvs default to the unit square (0..1). */
    public void quad(float[] p0, float[] p1, float[] p2, float[] p3, float[] n) {
        quad(
                new PreviewVertex(p0[0], p0[1], p0[2], 0, 0, n[0], n[1], n[2]),
                new PreviewVertex(p1[0], p1[1], p1[2], 1, 0, n[0], n[1], n[2]),
                new PreviewVertex(p2[0], p2[1], p2[2], 1, 1, n[0], n[1], n[2]),
                new PreviewVertex(p3[0], p3[1], p3[2], 0, 1, n[0], n[1], n[2]));
    }

    public boolean isEmpty() {
        return quads.isEmpty() && tris.isEmpty() && lines.isEmpty();
    }
}
