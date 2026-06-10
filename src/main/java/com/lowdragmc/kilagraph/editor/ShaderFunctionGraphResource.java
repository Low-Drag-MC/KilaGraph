package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResource;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResourceProviderContainer;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;

import java.util.function.Supplier;

/**
 * Editor resource for standalone, reusable {@link ShaderFunctionGraph}s — the pure shader-logic
 * counterpart to {@link RenderTypeGraphResource}. A function graph authored here can be dragged from
 * the Resources panel into any {@code RenderTypeGraph} (or another function graph) as an
 * {@code EXTERNAL} subgraph node: the host accepts it via
 * {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraph#acceptsSubgraphGraph}, and the shader
 * compiler inlines it (binding inner READ variables to the node's input expressions and inner WRITE
 * variables to its outputs). This is the cross-graph reuse path that local in-graph subgraphs lacked.
 *
 * <p>Unlike {@link RenderTypeGraphResource}, a function graph carries no extra serialized settings —
 * it is just its node/variable model — so the default {@link GraphResourceProviderContainer} (which
 * already provides editing, external-subgraph reference resolution, and drag-to-import) is sufficient;
 * no custom container or serialize/deserialize overrides are needed.</p>
 */
public class ShaderFunctionGraphResource extends GraphResource<ShaderFunctionGraph> {
    public static final ShaderFunctionGraphResource INSTANCE = new ShaderFunctionGraphResource();

    private ShaderFunctionGraphResource() {}

    @Override
    public ShaderFunctionGraph createGraph() {
        return new ShaderFunctionGraph();
    }

    @Override
    public Supplier<? extends GraphView> getGraphViewFactory() {
        // Reuse the RenderType view: it hides its RenderType-only panels (settings/preview) for any
        // non-RenderType graph, so a function graph edits with just the canvas / blackboard / library —
        // identical to diving into a local subgraph.
        return RenderTypeGraphView::new;
    }

    @Override
    public IGuiTexture getIcon() {
        return Icons.WIDGET_GROUP;
    }

    @Override
    public String getName() {
        return "shader_function";
    }
}
