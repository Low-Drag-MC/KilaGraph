package com.lowdragmc.kilagraph.rendertype.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named {@link KGVertexElement} key lists used as quick-fill shortcuts in the Settings UI and as the
 * graph default. These carry the attribute sets the old {@code VertexFormatPreset} enum mapped to (in
 * canonical order, so only {@link #ENTITY} builds a stock {@code DefaultVertexFormat} exactly).
 *
 * <p>Client-safe (plain string keys).</p>
 */
public final class VertexFormatPresets {

    /** {@code DefaultVertexFormat.ENTITY}: Position, Color, UV0, UV1, UV2, Normal. */
    public static final List<String> ENTITY = List.of("position", "color", "uv0", "uv1", "uv2", "normal");
    /** Position, Color, UV0, UV2, Normal: {@code DefaultVertexFormat.BLOCK} plus the Normal that Fresnel /
     *  normal defaults need. */
    public static final List<String> BLOCK = List.of("position", "color", "uv0", "uv2", "normal");
    /** Position, Color, UV0 ({@code DefaultVertexFormat.POSITION_TEX_COLOR}'s attributes). */
    public static final List<String> POSITION_COLOR_TEX = List.of("position", "color", "uv0");
    /** Position, Color, UV0, Normal. Used by the node-preview (so previews carry a real surface normal for
     *  Fresnel/normal nodes). */
    public static final List<String> POSITION_COLOR_TEX_NORMAL = List.of("position", "color", "uv0", "normal");

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
