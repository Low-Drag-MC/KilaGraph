package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A pure, reusable shader-logic graph — the rendertype-independent counterpart to
 * {@link RenderTypeGraph}. It has <b>no</b> fixed vsh/fsh stages and no default entity-shader init; its
 * interface is entirely its Blackboard variables (READ = input, WRITE = output). It exists to be used
 * as a subgraph: a {@code RenderTypeGraph} embeds one as a local (or, later, external resource)
 * subgraph, and the shader compiler inlines it — binding the inner READ variables to the outer node's
 * input expressions and the inner WRITE variables to its outputs.
 *
 * <p>It shares the value-node palette and shader types with {@link RenderTypeGraph} (value nodes list
 * {@code ShaderFunctionGraph} in their {@code @NodeAttribute.graphTypes}); only the vsh/fsh stage nodes
 * and semantic stage blocks are RenderTypeGraph-only.</p>
 */
public class ShaderFunctionGraph extends Graph {
    public static final GraphNodeRegistry NODE_REGISTRY =
            GraphNodeRegistry.create(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "shader_function"),
                    ShaderFunctionGraph.class);

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new ShaderFunctionGraphModel(this);
    }

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return NODE_REGISTRY.getNodeClasses();
    }

    @Override
    public List<TypeHandle> getSupportTypes() {
        return RenderTypeGraphTypes.SUPPORT_TYPES;
    }

    @Override
    public List<TypeHandle> getVariableSupportTypes() {
        return RenderTypeGraphTypes.VARIABLE_SUPPORT_TYPES;
    }

    /** Draggable constants are scalars only (vectors come from the Vec2/3/4 nodes). */
    @Override
    public List<TypeHandle> getLibrarySupportTypes() {
        return RenderTypeGraphTypes.CONSTANT_SUPPORT_TYPES;
    }

    /** Allow nesting function subgraphs inside function graphs. */
    @Override
    public boolean acceptsSubgraphGraph(Graph other) {
        return other instanceof ShaderFunctionGraph;
    }
}
