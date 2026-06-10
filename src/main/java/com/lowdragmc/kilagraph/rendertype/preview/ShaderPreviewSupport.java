package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodePreviewContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;

/**
 * Shared wiring for per-node shader previews, used by both {@code ShaderNode} (top-level value nodes)
 * and {@code ShaderBlockNode} (stage blocks) since they live on different base classes but expose the
 * same {@code onBuildNodePreview} hook. Adds a {@link NodeShaderPreview} thumbnail for the node's
 * chosen output port into the preview container.
 */
public final class ShaderPreviewSupport {

    private ShaderPreviewSupport() {}

    public static void build(NodePreviewContext context, String previewPortId) {
        if (previewPortId == null) return;
        if (!(context.nodeModel() instanceof NodeModel nm)) return;
        if (context.graphView() == null || !(context.graphView().getGraph() instanceof RenderTypeGraph graph)) return;
        PortModel port = nm.getOutputsById().get(previewPortId);
        if (port == null) return;
        context.container().addChild(new NodeShaderPreview(graph, port));
    }
}
