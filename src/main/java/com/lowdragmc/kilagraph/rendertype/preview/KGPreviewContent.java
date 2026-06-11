package com.lowdragmc.kilagraph.rendertype.preview;

import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * A piece of preview geometry the RenderType preview can draw (quad / cube / sphere / a JSON model / …),
 * built into format-agnostic primitives via {@link #build}. Registered in {@link KGPreviewContents} and
 * chosen from the preview's right-click menu. Client-safe by default — {@link #build} only emits neutral
 * {@link PreviewVertex} data; a content needing client resources (e.g. a baked JSON model) keeps that in
 * its own client-only class and guards {@link #isCompatible}.
 */
public interface KGPreviewContent {

    /** The registry key (stable id), e.g. {@code "cube"}. */
    String key();

    /** A human label for the right-click menu. */
    Component title();

    /** Emit this content's geometry into {@code mb}. */
    void build(PreviewMeshBuilder mb);

    /**
     * Whether this content can render against a vertex format made of {@code elementKeys}
     * ({@link com.lowdragmc.kilagraph.rendertype.format.KGVertexElement} keys). Default: any format.
     * A JSON model overrides this to require POSITION/COLOR/UV0/UV2 (what baked quads carry).
     */
    default boolean isCompatible(Set<String> elementKeys) {
        return true;
    }
}
