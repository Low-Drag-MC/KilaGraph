package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;

/**
 * An extra fragment output (MRT) at {@code location} 1..{@value #MAX_LOCATION}; location 0 is the main target.
 * Every target blends with the graph's blend mode: Minecraft's GL backend keeps one blend state per pipeline.
 */
public record ColorTarget(int location, RenderTypeGraph.Settings.ColorFormat format) {
    public static final int MAX_LOCATION = 7;
}
