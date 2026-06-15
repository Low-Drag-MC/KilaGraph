package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodePreviewContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Shared wiring for per-node shader previews, used by both {@code ShaderNode} (top-level value nodes)
 * and {@code ShaderBlockNode} (stage blocks) since they live on different base classes but expose the
 * same {@code onBuildNodePreview} hook. Adds a {@link NodeShaderPreview} thumbnail for the node's
 * chosen output port into the preview container.
 *
 * <p>The preview port is identified by a {@link Supplier} (not a captured {@code PortModel}) because a
 * node that re-defines itself ({@code Expression}: rename/retype an output) recreates its PortModels —
 * a captured instance would go stale and the thumbnail would freeze on a dead port. The preview
 * re-resolves the live port by the node's current preview id each frame.</p>
 */
public final class ShaderPreviewSupport {

    private ShaderPreviewSupport() {}

    public static void build(NodePreviewContext context, Supplier<String> previewPortId,
                             @Nullable String defaultContentKey) {
        if (previewPortId == null) return;
        if (!(context.nodeModel() instanceof NodeModel nm)) return;
        if (context.graphView() == null || !(context.graphView().getGraph() instanceof RenderTypeGraph graph)) return;
        // Only add the thumbnail if there's a port to preview right now; it re-resolves dynamically after.
        String id = previewPortId.get();
        if (id == null || nm.getOutputsById().get(id) == null) return;
        KGPreviewContent defaultContent = defaultContentKey == null ? null : KGPreviewContents.get(defaultContentKey);
        context.container().addChild(new NodeShaderPreview(graph, nm, previewPortId, defaultContent));
    }
}
