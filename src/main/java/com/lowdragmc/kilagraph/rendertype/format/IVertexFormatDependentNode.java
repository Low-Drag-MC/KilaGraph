package com.lowdragmc.kilagraph.rendertype.format;

/**
 * A node whose meaning depends on a particular {@link KGVertexElement} being present in the graph's
 * composed vertex format (e.g. a raw vertex-attribute reader needs its attribute declared). The graph
 * validates these on change and flags any whose element is absent from the current format.
 *
 * <p>Client-safe: {@link #requiredElementKey()} returns a registry key string, no {@code blaze3d}.</p>
 */
public interface IVertexFormatDependentNode {
    /** The {@link KGVertexElement} key this node needs in the vertex format to compile meaningfully. */
    String requiredElementKey();
}
