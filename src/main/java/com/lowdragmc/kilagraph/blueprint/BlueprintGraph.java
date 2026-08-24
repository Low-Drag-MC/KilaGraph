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
import net.minecraft.resources.Identifier;

import java.util.HashSet;
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
        types.add(KGTypeHandles.CONTAINER);
        types.add(KGTypeHandles.FLUID_CONTAINER);
        types.add(KGTypeHandles.NBT_COMPOUND);
        types.add(KGTypeHandles.RESOURCE_LOCATION);
        types.add(KGTypeHandles.AABB);
        types.add(KGTypeHandles.CHUNK_POS);
        types.add(KGTypeHandles.TEXT);
        types.add(KGTypeHandles.ROTATION);
        types.add(KGTypeHandles.MIRROR);
        types.add(KGTypeHandles.AXIS);
        types.add(KGTypeHandles.EQUIPMENT_SLOT);
        return List.copyOf(types);
    }

    /**
     * The types the item library offers as draggable Constant nodes.
     *
     * <p>Overridden because the default is {@link #getSupportTypes()}, which is the wrong list for
     * this question. A type belongs in the type-picker dropdowns as soon as a port can carry it, but
     * it only belongs here if a literal of it can be authored: {@code Level}, {@code Entity},
     * {@code Player} and {@code BlockEntity} are deliberately wire-only — they have no
     * {@code AccessorRegistries} entry, so their constant node renders an empty inspector row and
     * emits {@code null}. Offering four nodes that cannot do anything is worse than not offering
     * them.</p>
     *
     * <p>{@code LIST}, {@code MAP} and {@code NODE_REF} are excluded for the same reason — they are
     * registered without a default constant on purpose (see {@link KGTypeHandles}).</p>
     */
    @Override
    public List<TypeHandle> getLibrarySupportTypes() {
        KGTypeHandles.init();
        return List.of(
                TypeHandles.BOOL, TypeHandles.INT, TypeHandles.LONG, TypeHandles.FLOAT,
                TypeHandles.DOUBLE, TypeHandles.STRING,
                KGTypeHandles.VEC2, KGTypeHandles.VEC3, KGTypeHandles.VEC4,
                // Minecraft (LDLib2-provided) — all accessor-backed, all have pickers
                TypeHandles.DIRECTION, TypeHandles.BLOCK, TypeHandles.ITEM, TypeHandles.FLUID,
                TypeHandles.ENTITY_TYPE, TypeHandles.ITEM_STACK, TypeHandles.FLUID_STACK,
                // Minecraft (KilaGraph-provided)
                KGTypeHandles.BLOCK_POS, KGTypeHandles.BLOCK_STATE, KGTypeHandles.NBT_COMPOUND,
                KGTypeHandles.RESOURCE_LOCATION, KGTypeHandles.AABB, KGTypeHandles.CHUNK_POS,
                KGTypeHandles.TEXT, KGTypeHandles.ROTATION, KGTypeHandles.MIRROR,
                KGTypeHandles.AXIS, KGTypeHandles.EQUIPMENT_SLOT);
    }
}
