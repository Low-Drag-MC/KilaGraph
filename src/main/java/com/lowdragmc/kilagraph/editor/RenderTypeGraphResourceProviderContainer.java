package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphEditorView;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResource;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResourceProviderContainer;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.SubgraphRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class RenderTypeGraphResourceProviderContainer extends GraphResourceProviderContainer<com.lowdragmc.kilagraph.rendertype.RenderTypeGraph> {
    private final RenderTypeGraphResource renderTypeResource;
    private final IResourceProvider<CompoundTag> provider;
    private final Map<UUID, Tuple<IResourcePath, GraphEditorView>> openedViews = new HashMap<>();

    public RenderTypeGraphResourceProviderContainer(RenderTypeGraphResource graphResource,
                                                    IResourceProvider<CompoundTag> provider) {
        super(graphResource, provider);
        this.renderTypeResource = graphResource;
        this.provider = provider;
        this.addDefault = () -> renderTypeResource.serializeGraph(renderTypeResource.createGraph());
        this.onEdit = this::openRenderTypeGraph;
    }

    private void openRenderTypeGraph(com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer<CompoundTag> container,
                                     IResourcePath path) {
        if (openedViews.values().stream().map(Tuple::getA).anyMatch(path::equals)) return;

        var tag = provider.getResource(path);
        if (tag == null) return;

        IGraphReferenceResolver resolver = new IGraphReferenceResolver() {
            @Override
            public Graph resolve(IResourcePath refPath) {
                if (refPath == null) return null;
                var refTag = provider.getResource(refPath);
                if (refTag != null) {
                    return renderTypeResource.deserializeGraph(refTag, this);
                }
                return resolveForeign(container, refPath);
            }

            @Override
            public void save(IResourcePath refPath, CompoundTag refTag) {
                if (refPath == null || refTag == null) return;
                provider.addResource(refPath, refTag);
                container.reloadSpecificResource(refPath);
                SubgraphRegistry.INSTANCE.notifyExternalGraphSaved(refPath);
            }

            @Override
            public GraphResource<?> getSourceResource() {
                return renderTypeResource;
            }
        };

        var graph = renderTypeResource.deserializeGraph(tag, resolver);
        var editor = container.getEditor();
        var uuid = UUID.randomUUID();

        var newView = new RenderTypeGraphEditorView(getGraphViewFactory()).loadGraph(graph, savedTag -> {
            if (!openedViews.containsKey(uuid)) return;
            var realPath = openedViews.get(uuid).getA();
            provider.addResource(realPath, savedTag);
            container.reloadSpecificResource(realPath);
            SubgraphRegistry.INSTANCE.notifyExternalGraphSaved(realPath);
        });
        newView.setRootPath(path);

        AtomicReference<IResourcePath> pathCache = new AtomicReference<>(path);
        newView.addEventListener(UIEvents.ADDED, e -> openedViews.put(uuid, new Tuple<>(pathCache.get(), newView)));
        newView.addEventListener(UIEvents.REMOVED, e -> {
            var pair = openedViews.remove(uuid);
            if (pair != null) {
                pathCache.set(pair.getA());
            }
        });
        newView.setCanRemove(true);
        newView.setIcon(renderTypeResource.getIcon());
        newView.setDynamicName(() -> {
            if (openedViews.containsKey(uuid)) {
                return Component.literal(openedViews.get(uuid).getA().getResourceName());
            }
            return Component.literal(pathCache.get().getResourceName());
        });
        editor.centerWindow.getLeftTop().addView(newView);
    }

    private static Graph resolveForeign(com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer<CompoundTag> container,
                                        IResourcePath path) {
        var editor = container.getEditor();
        if (editor == null) return null;
        for (var entry : editor.resourceView.getResources().entrySet()) {
            if (!(entry.getKey() instanceof GraphResource<?> graphResource)) continue;
            var instance = entry.getValue();
            var refTag = instance.getResource(path);
            if (refTag instanceof CompoundTag tag) {
                if (graphResource instanceof RenderTypeGraphResource renderTypeGraphResource) {
                    return renderTypeGraphResource.deserializeGraph(tag);
                }
                var refGraph = graphResource.createGraph();
                refGraph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), tag);
                return refGraph;
            }
        }
        return null;
    }

    @Override
    protected void onRename(IResourcePath oldPath, IResourcePath newPath) {
        super.onRename(oldPath, newPath);
        for (var openedView : openedViews.values()) {
            if (openedView.getA().equals(oldPath)) {
                openedView.setA(newPath);
                openedView.getB().setRootPath(newPath);
            }
        }
    }
}
