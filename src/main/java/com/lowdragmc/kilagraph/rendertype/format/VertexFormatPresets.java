package com.lowdragmc.kilagraph.rendertype.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named {@link KGVertexElement} key lists used as quick-fill shortcuts in the Settings UI and as the
 * graph default. These reproduce the formats the old {@code VertexFormatPreset} enum mapped to, so a
 * graph built from a preset yields the same Minecraft {@code VertexFormat} (see
 * {@link KGVertexFormat}, which reuses the matching {@code DefaultVertexFormat}).
 *
 * <p>Client-safe (plain string keys).</p>
 */
public final class VertexFormatPresets {

    /** {@code DefaultVertexFormat.ENTITY}: Position, Color, UV0, UV1, UV2, Normal. */
    public static final List<String> ENTITY = List.of("position", "color", "uv0", "uv1", "uv2", "normal");
    /** {@code DefaultVertexFormat.BLOCK}: Position, Color, UV0, UV2, Normal. (The stock BLOCK format
     *  really does carry a Normal — 3 bytes + 1 padding; omitting it here made {@code hasAttribute(NORMAL)}
     *  fail for block-format graphs, degrading Fresnel/normal defaults to a constant up vector.) */
    public static final List<String> BLOCK = List.of("position", "color", "uv0", "uv2", "normal");
    /** {@code DefaultVertexFormat.POSITION_TEX_COLOR}: Position, UV0, Color. */
    public static final List<String> POSITION_COLOR_TEX = List.of("position", "uv0", "color");
    /** {@code DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL}: Position, UV0, Color, Normal. Used by the
     *  node-preview (so previews carry a real surface normal for Fresnel/normal nodes). */
    public static final List<String> POSITION_COLOR_TEX_NORMAL = List.of("position", "uv0", "color", "normal");

    /** Ordered name -> key list, for the UI's preset menu. */
    public static final Map<String, List<String>> ALL = new LinkedHashMap<>();

    static {
        ALL.put("Entity", ENTITY);
        ALL.put("Block", BLOCK);
        ALL.put("Position Color Tex", POSITION_COLOR_TEX);
    }

    private VertexFormatPresets() {}

    public static List<String> defaults() {
        return ENTITY;
    }
}
