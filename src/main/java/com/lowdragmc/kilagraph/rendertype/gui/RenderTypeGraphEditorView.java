package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.editor.RenderTypeGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphEditorView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;

public class RenderTypeGraphEditorView extends GraphEditorView {
    public RenderTypeGraphEditorView(Supplier<? extends GraphView> graphViewFactory) {
        super(graphViewFactory);
    }

    @Override
    public CompoundTag serializeGraph() {
        return getGraph() instanceof RenderTypeGraph renderTypeGraph
                ? RenderTypeGraphResource.INSTANCE.serializeGraph(renderTypeGraph)
                : super.serializeGraph();
    }
}
