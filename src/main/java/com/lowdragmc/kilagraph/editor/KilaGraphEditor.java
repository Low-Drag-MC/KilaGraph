package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * All editing UI (menus, resources panel, graph view, item library, inspector) comes from the
 * LDLib2 base class.
 */
public class KilaGraphEditor extends Editor {
    public static final ResourceLocation WINDOW_ID = LDLib2.id("kila_graph");

    public KilaGraphEditor() {
        this.leftWindow.setDisplay(false);
        this.leftWindow.getParentWindow().removeSplitWindow(this.leftWindow);
        initResources();
    }

    private void initResources() {
        this.resourceView.clear();
        this.resourceView.loadResources(Resources.of(
                BlueprintGraphResource.INSTANCE,
                RenderTypeGraphResource.INSTANCE,
                ShaderFunctionGraphResource.INSTANCE
        ));
    }

    @Override
    protected @Nonnull Editor createNewEditorInstance() {
        return new KilaGraphEditor();
    }

    @Override
    protected void initMenus() {
        super.initMenus();
    }

    @Override
    protected void closeCurrentProject() {
        super.closeCurrentProject();
        initResources();
    }
}
