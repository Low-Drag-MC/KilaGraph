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

    /**
     * Every {@code deserialize} rebuilds the node models into fresh instances — including undo/redo,
     * which round-trip the whole model through NBT ({@code UndoableGraphCommand}) directly on this model
     * (not via the resource load that calls {@link RenderTypeGraph#restoreFixedStagesAfterDeserialize()}).
     * Re-resolve the graph's cached fixed vertex/fragment stage references here, the single point all
     * deserialize paths funnel through, so the whole-graph compiler walks the live stages. Otherwise it
     * keeps the old, now-orphaned stage refs and emits an empty (fully transparent) shader — e.g. the
     * graph-tool preview going blank after deleting a node and undoing.
     */
    @Override
    public void afterDeserialize() {
        super.afterDeserialize();
        if (getGraph() instanceof RenderTypeGraph renderTypeGraph) {
            renderTypeGraph.restoreFixedStagesAfterDeserialize();
        }
    }
}
