package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;

/**
 * The {@link RenderTypeGraph}'s graph model. Inherits the shader vec-assign rule from
 * {@link ShaderGraphModelBase}, and redirects inline subgraph creation to {@link ShaderFunctionGraph}
 * so "create subgraph from selection" yields a pure shader-function graph (no fixed stages / entity
 * shader init) instead of cloning the whole RenderTypeGraph.
 */
public class RenderTypeGraphModel extends ShaderGraphModelBase {
    public RenderTypeGraphModel(Graph graph) {
        super(graph);
    }

    /**
     * No-arg subgraph creation (used by {@code extractSelectionToLocalSubgraph} and the editor's
     * "create subgraph from selection") produces a {@link ShaderFunctionGraph}. Accepted because
     * {@link RenderTypeGraph#acceptsSubgraphGraph} opts into it.
     */
    @Override
    public CustomGraphModelImpl createLocalSubgraphInstance() {
        return createLocalSubgraphInstance(ShaderFunctionGraph.class);
    }
}
