package com.lowdragmc.kilagraph.blueprint;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.graph.type.KGGraphModel;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.ui.KGUITypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphNodeRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        // LDLib2 UI. All wire-only, so they belong here (a port can carry them) but not in
        // getLibrarySupportTypes() (none can be authored as a literal).
        types.addAll(KGUITypeHandles.all());
        return List.copyOf(types);
    }

    /**
     * Types the library will not offer even though {@link TypeHandleHelpers#canAuthorLiteral} says a
     * constant of them would hold a value. Two entries, each for a reason no predicate states:
     *
     * <ul>
     *   <li>{@link KGTypeHandles#VECTOR} is width-polymorphic on purpose — a VECTOR pin takes 2, 3
     *       or 4 and answers in kind — and a constant has to commit to a width. Drag VEC2/VEC3/VEC4
     *       instead.</li>
     *   <li>{@link KGTypeHandles#CHUNK_POS} has a syncdata accessor and a default value but no
     *       {@code ConfiguratorAccessor}, so its constant renders an empty inspector row. That is
     *       the one case the predicate structurally cannot see: whether a widget exists is a
     *       client-only fact ({@code LDLib2Registries.CONFIGURATOR_ACCESSORS} is
     *       {@code @OnlyIn(Dist.CLIENT)}), and this list is read on both sides. Delete this entry
     *       the day LDLib2 gains a ChunkPos configurator accessor.</li>
     * </ul>
     */
    private static final Set<TypeHandle> LIBRARY_EXCLUDED =
            Set.of(KGTypeHandles.VECTOR, KGTypeHandles.CHUNK_POS);

    /**
     * The types the item library offers as draggable Constant nodes: every supported type a literal
     * can be authored of, minus {@link #LIBRARY_EXCLUDED}.
     *
     * <p>The hand-written list is also what let {@code CHUNK_POS} ship as a constant that renders
     * nothing — it was on the list precisely because a person put it there.</p>
     */
    @Override
    public List<TypeHandle> getLibrarySupportTypes() {
        return TypeHandleHelpers.authorableTypes(graphModel.getSupportTypes()).stream()
                .filter(type -> !LIBRARY_EXCLUDED.contains(type))
                .toList();
    }
}
