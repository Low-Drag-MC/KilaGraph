package com.lowdragmc.kilagraph.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.type.KGGraphModel;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;

/**
 * The first KilaGraph graph: a pure data-flow graph (no exec ports yet) used to validate the
 * annotation framework and {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor}. Eventually
 * subsumes the Minecraft-facing blueprint semantics.
 */
public class BlueprintGraph extends Graph {

    public static final GraphNodeRegistry NODE_REGISTRY =
            GraphNodeRegistry.create(ResourceLocation.fromNamespaceAndPath(Kilagraph.MODID, "blueprint"),
                    BlueprintGraph.class);

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return NODE_REGISTRY.getNodeClasses();
    }

    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new KGGraphModel(this);
    }

    /**
     * Surface the common scalar + Minecraft handles in type-picker dropdowns (the editor uses this
     * to populate {@code typeHandlePickerOption} candidates). Default {@code null} would auto-detect
     * only types already used by ports in the graph; we want MC types available up front.
     */
    @Override
    public List<TypeHandle> getSupportTypes() {
        KGTypeHandles.init();
        var types = new HashSet<>(CustomGraphModelImpl.detectSupportedTypes(graphModel));
        // scalars
        types.add(TypeHandles.BOOL);
        types.add(TypeHandles.INT);
        types.add(TypeHandles.LONG);
        types.add(TypeHandles.FLOAT);
        types.add(TypeHandles.DOUBLE);
        types.add(TypeHandles.STRING);
        // KilaGraph collections
        types.add(KGTypeHandles.LIST);
        types.add(KGTypeHandles.MAP);
        // Minecraft (LDLib2-provided)
        types.add(TypeHandles.DIRECTION);
        types.add(TypeHandles.BLOCK);
        types.add(TypeHandles.ITEM);
        types.add(TypeHandles.FLUID);
        types.add(TypeHandles.ENTITY_TYPE);
        types.add(TypeHandles.ITEM_STACK);
        types.add(TypeHandles.FLUID_STACK);
        // Minecraft (KilaGraph-provided)
        types.add(KGTypeHandles.BLOCK_POS);
        types.add(KGTypeHandles.BLOCK_STATE);
        types.add(KGTypeHandles.LEVEL);
        types.add(KGTypeHandles.ENTITY);
        types.add(KGTypeHandles.PLAYER);
        types.add(KGTypeHandles.BLOCK_ENTITY);
        return List.copyOf(types);
    }
}
