package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import javax.annotation.Nonnull;

/**
 * Minimal {@link Editor} subclass that advertises {@link KilaGraphProject} in the File menu.
 * All editing UI (menus, resources panel, graph view, item library, inspector) comes from the
 * LDLib2 base class.
 */
public class KilaGraphEditor extends Editor {

    public KilaGraphEditor() {
    }

    @Override
    protected @Nonnull Editor createNewEditorInstance() {
        return new KilaGraphEditor();
    }

    @Override
    protected void initMenus() {
        super.initMenus();
        fileMenu.addProjectProvider(KilaGraphProject.TYPE);
    }
}
