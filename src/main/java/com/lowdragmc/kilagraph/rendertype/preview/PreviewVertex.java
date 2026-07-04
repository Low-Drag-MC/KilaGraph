package com.lowdragmc.kilagraph.rendertype.preview;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One preview vertex in a format-agnostic, <b>client-safe</b> form: position + the attributes any preview
 * content might supply (uv, normal, color, light/overlay). A {@code PreviewContent} fills these;
 * the client-side writer then emits only the attributes the target {@code VertexFormat} actually declares.
 *
 * <p>No {@code com.mojang.blaze3d} types, so the geometry/mode logic stays headless-testable. The packed
 * defaults mirror the old hardcoded preview values (white / full-bright / no-overlay).</p>
 *
 * <p><b>Custom elements.</b> The fixed fields cover Minecraft's built-in attributes. A mod that registers
 * its own {@link com.lowdragmc.kilagraph.rendertype.format.KGVertexElement} stores per-vertex values for it
 * under its element key via {@link #setAttribute}/{@link #getAttribute}; the matching
 * {@link PreviewVertexWriters} writer reads them (or just writes a constant if the content provides none).</p>
 */
public final class PreviewVertex {
    /** Full-bright lightmap (block=15, sky=15), matching the old preview default. */
    public static final int FULL_BRIGHT = 0x00F000F0;
    /** {@code OverlayTexture.NO_OVERLAY} as a raw int (avoids a client-only import here). */
    public static final int NO_OVERLAY = 655360; // (10 << 16) | 0

    public float x, y, z;
    public float u, v;
    public float nx = 0f, ny = 1f, nz = 0f; // default up
    public int color = 0xFFFFFFFF;          // ARGB white
    public int light = FULL_BRIGHT;
    public int overlay = NO_OVERLAY;

    /** Per-vertex values for mod-registered custom elements, keyed by the element's registry key. Lazily
     *  allocated (most vertices use only the built-in fields). */
    @Nullable
    private Map<String, float[]> custom;

    public PreviewVertex() {}

    public PreviewVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        this.x = x; this.y = y; this.z = z;
        this.u = u; this.v = v;
        this.nx = nx; this.ny = ny; this.nz = nz;
    }

    /** Store a custom element's per-vertex value (keyed by its registry key). */
    public PreviewVertex setAttribute(String elementKey, float... value) {
        if (custom == null) custom = new java.util.HashMap<>();
        custom.put(elementKey, value);
        return this;
    }

    /** The custom value previously set for {@code elementKey}, or {@code null}. */
    @Nullable
    public float[] getAttribute(String elementKey) {
        return custom == null ? null : custom.get(elementKey);
    }

    /** A positional/uv/normal copy (attributes carry over) — used when a vertex is reused across primitives. */
    public PreviewVertex copy() {
        var c = new PreviewVertex(x, y, z, u, v, nx, ny, nz);
        c.color = color; c.light = light; c.overlay = overlay;
        if (custom != null) c.custom = new java.util.HashMap<>(custom);
        return c;
    }
}
