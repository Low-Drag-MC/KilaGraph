package com.lowdragmc.kilagraph.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.type.KGGraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The first KilaGraph graph: a pure data-flow graph (no exec ports yet) used to validate the
 * annotation framework and {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor}. Eventually
 * subsumes the Minecraft-facing blueprint semantics.
 */
public class BlueprintGraph extends Graph {

    public static final GraphNodeRegistry NODE_REGISTRY =
            GraphNodeRegistry.create(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "blueprint"),
                    BlueprintGraph.class);

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return NODE_REGISTRY.getNodeClasses();
    }

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new KGGraphModel(this);
    }
}
