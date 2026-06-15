package com.lowdragmc.kilagraph.rendertype.compiler;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A node that contributes its own editor-surfaced validation errors. {@code RenderTypeGraph.validateGraph}
 * (run on every graph change via {@code onGraphChanged}) collects these and reports each through the editor's
 * {@code GraphLogger}, keyed to the node — so issues a node can detect statically (e.g. the Expression node's
 * reserved-word / duplicate port names, which would otherwise only surface as a GPU compile error) appear in
 * the editor's error panel next to the offending node.
 */
public interface INodeValidator {
    /** Human-readable error messages for the node's current state, or empty when valid. */
    List<Component> validationErrors();
}
