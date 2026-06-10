package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;

/**
 * The {@link ShaderFunctionGraph}'s graph model — just the shared shader vec-assign rule from
 * {@link ShaderGraphModelBase}. No subgraph-creation redirect: a function graph's inline subgraphs
 * stay function graphs (same-type).
 */
public class ShaderFunctionGraphModel extends ShaderGraphModelBase {
    public ShaderFunctionGraphModel(Graph graph) {
        super(graph);
    }
}
