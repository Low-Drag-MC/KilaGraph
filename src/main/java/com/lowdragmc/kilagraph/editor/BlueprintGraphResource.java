package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResource;

/**
 * Resource type that hosts {@link BlueprintGraph} instances inside a KilaGraph project. The
 * editor uses it to list, create, and edit blueprint graphs from the Resources panel.
 */
public class BlueprintGraphResource extends GraphResource<BlueprintGraph> {
    public static final BlueprintGraphResource INSTANCE = new BlueprintGraphResource();

    private BlueprintGraphResource() {}

    @Override
    public BlueprintGraph createGraph() {
        return new BlueprintGraph();
    }

    @Override
    public IGuiTexture getIcon() {
        return Icons.WIDGET_CUSTOM;
    }

    @Override
    public String getName() {
        return "blueprint";
    }
}
