package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings.VertexFormatMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link PreviewMeshBuilder}'s neutral primitives (quads/tris/lines) into the flat vertex stream a
 * given {@link VertexFormatMode} expects — so the same content renders correctly whatever primitive mode the
 * graph's pipeline was built with. <b>Client-safe</b> (no {@code com.mojang.blaze3d}); the actual
 * {@code VertexConsumer} writes happen in {@code PreviewRenderer}, keeping this ordering logic testable
 * headlessly.
 *
 * <p>Mode mapping (matching how Minecraft indexes each mode): <b>QUADS</b> emits 4-vertex groups (a triangle
 * becomes a degenerate quad {@code a,b,c,c}; MC indexes each group {@code 0,1,2,0,2,3}). <b>TRIANGLES</b>
 * emits 3-vertex groups. <b>TRIANGLE_STRIP</b> stitches the triangle list into one degenerate strip with
 * winding-preserving padding. <b>LINES</b> (MC's line-quad mode, 4 verts/segment) emits each wireframe edge
 * as {@code a,a,b,b}. <b>LINE_STRIP</b> emits each edge as a 2-vertex segment.</p>
 */
public final class PreviewTessellator {

    private PreviewTessellator() {}

    public static List<PreviewVertex> toStream(PreviewMeshBuilder mesh, VertexFormatMode mode) {
        return switch (mode) {
            case QUADS -> quadStream(mesh);
            case TRIANGLES -> flatten(triangles(mesh));
            case TRIANGLE_STRIP -> stitch(triangles(mesh));
            case LINES -> lineQuadStream(edges(mesh));
            case LINE_STRIP -> lineStripStream(edges(mesh));
        };
    }

    // ---- per-mode streams --------------------------------------------------------------------

    /** 4-vertex groups: quads as-is, triangles as a degenerate quad (a,b,c,c). */
    private static List<PreviewVertex> quadStream(PreviewMeshBuilder mesh) {
        var out = new ArrayList<PreviewVertex>();
        for (PreviewVertex[] q : mesh.quads) {
            out.add(q[0]); out.add(q[1]); out.add(q[2]); out.add(q[3]);
        }
        for (PreviewVertex[] t : mesh.tris) {
            out.add(t[0]); out.add(t[1]); out.add(t[2]); out.add(t[2]);
        }
        return out;
    }

    /** The triangle list: quads split into two triangles (a,b,c)+(a,c,d), triangles as-is. */
    static List<PreviewVertex[]> triangles(PreviewMeshBuilder mesh) {
        var tris = new ArrayList<PreviewVertex[]>();
        for (PreviewVertex[] q : mesh.quads) {
            tris.add(new PreviewVertex[]{q[0], q[1], q[2]});
            tris.add(new PreviewVertex[]{q[0], q[2], q[3]});
        }
        tris.addAll(mesh.tris);
        return tris;
    }

    private static List<PreviewVertex> flatten(List<PreviewVertex[]> tris) {
        var out = new ArrayList<PreviewVertex>(tris.size() * 3);
        for (PreviewVertex[] t : tris) {
            out.add(t[0]); out.add(t[1]); out.add(t[2]);
        }
        return out;
    }

    /**
     * Stitch a triangle list into a single GL triangle strip using degenerate (zero-area) bridge vertices.
     * A strip triangle at an odd start index has reversed winding, so each appended triangle is padded to
     * land on an even index — preserving the original winding (front faces stay front faces).
     */
    static List<PreviewVertex> stitch(List<PreviewVertex[]> tris) {
        var strip = new ArrayList<PreviewVertex>();
        for (int i = 0; i < tris.size(); i++) {
            PreviewVertex[] t = tris.get(i);
            if (i == 0) {
                strip.add(t[0]); strip.add(t[1]); strip.add(t[2]);
            } else {
                strip.add(strip.get(strip.size() - 1)); // duplicate the seam (degenerate)
                strip.add(t[0]);                          // jump to the next triangle (degenerate)
                if (strip.size() % 2 != 0) strip.add(t[0]); // parity → keep the real triangle CCW
                strip.add(t[0]); strip.add(t[1]); strip.add(t[2]);
            }
        }
        return strip;
    }

    /** Unique-ish wireframe edges from every primitive (quad → 4 edges, tri → 3, line → 1). */
    static List<PreviewVertex[]> edges(PreviewMeshBuilder mesh) {
        var edges = new ArrayList<PreviewVertex[]>();
        for (PreviewVertex[] q : mesh.quads) {
            edges.add(new PreviewVertex[]{q[0], q[1]});
            edges.add(new PreviewVertex[]{q[1], q[2]});
            edges.add(new PreviewVertex[]{q[2], q[3]});
            edges.add(new PreviewVertex[]{q[3], q[0]});
        }
        for (PreviewVertex[] t : mesh.tris) {
            edges.add(new PreviewVertex[]{t[0], t[1]});
            edges.add(new PreviewVertex[]{t[1], t[2]});
            edges.add(new PreviewVertex[]{t[2], t[0]});
        }
        edges.addAll(mesh.lines);
        return edges;
    }

    /** MC's LINES mode is a line-quad (4 verts indexed 0,1,2,0,2,3); emit a,a,b,b per edge. */
    private static List<PreviewVertex> lineQuadStream(List<PreviewVertex[]> edges) {
        var out = new ArrayList<PreviewVertex>(edges.size() * 4);
        for (PreviewVertex[] e : edges) {
            out.add(e[0]); out.add(e[0].copy()); out.add(e[1]); out.add(e[1].copy());
        }
        return out;
    }

    /** DEBUG_LINE_STRIP is a sequential GL line strip; emit each edge as a 2-vertex segment. */
    private static List<PreviewVertex> lineStripStream(List<PreviewVertex[]> edges) {
        var out = new ArrayList<PreviewVertex>(edges.size() * 2);
        for (PreviewVertex[] e : edges) {
            out.add(e[0]); out.add(e[1]);
        }
        return out;
    }
}
