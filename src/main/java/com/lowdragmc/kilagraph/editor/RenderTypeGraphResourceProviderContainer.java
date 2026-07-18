package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphEditorView;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResourceProviderContainer;
import net.minecraft.nbt.CompoundTag;

/**
 * The RenderType graph flavor of the shared graph-resource container. Everything RenderType-specific
 * (the {@code {graph, settings}} wrapper, fixed-stage restoration, resolver threading, cross-library
 * saves) now flows through {@code RenderTypeGraphResource.serializeGraphResource /
 * deserializeGraphResource} and the base container's template hooks — the only thing left to
 * customize here is the editor view, whose {@code serializeGraph} must emit the wrapped tag so
 * dirty-detection and saves compare/store the same form.
 */
public class RenderTypeGraphResourceProviderContainer extends GraphResourceProviderContainer<RenderTypeGraph> {

    public RenderTypeGraphResourceProviderContainer(RenderTypeGraphResource graphResource,
                                                    IResourceProvider<CompoundTag> provider) {
        super(graphResource, provider);
    }

    @Override
    protected GraphEditorView createEditorView() {
        return new RenderTypeGraphEditorView(getGraphViewFactory());
    }
}
