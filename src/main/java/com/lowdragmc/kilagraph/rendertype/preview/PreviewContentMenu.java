package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.format.KGVertexElement;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexElements;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Vector2f;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared right-click menu for choosing a {@link KGPreviewContent} — used by both the whole-graph preview
 * ({@code ShaderPreviewTool}) and per-node previews ({@code NodeShaderPreview}) so they switch geometry the
 * same way. Client-only (opens an LDLib2 {@link Menu}). Lists every registered content compatible with the
 * given vertex format.
 */
public final class PreviewContentMenu {

    private PreviewContentMenu() {}

    /** Append a leaf per registered content compatible with {@code formatKeys} (each invoking
     *  {@code onSelect}), into an existing menu builder. Shared by the standalone {@link #open} popup and
     *  the graph-view context menu so both list the same geometry choices. */
    public static void appendContentItems(TreeBuilder.Menu menu, Set<String> formatKeys,
                                          Consumer<KGPreviewContent> onSelect) {
        for (KGPreviewContent content : KGPreviewContents.all()) {
            if (!content.isCompatible(formatKeys)) continue;
            menu.leaf(content.title(), () -> onSelect.accept(content));
        }
    }

    /** Open the content picker at the event's position, invoking {@code onSelect} with the chosen content. */
    public static void open(UIElement host, UIEvent event, Set<String> formatKeys, Consumer<KGPreviewContent> onSelect) {
        var mui = host.getModularUI();
        if (mui == null) return;
        var menu = TreeBuilder.Menu.start();
        appendContentItems(menu, formatKeys, onSelect);
        var root = mui.ui.rootElement;
        var offset = root.worldToLocalLayoutOffset(new Vector2f(event.x, event.y));
        root.addChild(new Menu<>(menu.build(), TreeBuilder.Menu::uiProvider)
                .setHoverTextureProvider(TreeBuilder.Menu::hoverTextureProvider)
                .setOnNodeClicked(TreeBuilder.Menu::handle)
                .layout(layout -> {
                    layout.left(offset.x);
                    layout.top(offset.y);
                }));
    }

    /** The {@link KGVertexElement} keys a format is composed of, for filtering compatible contents. */
    public static Set<String> formatKeys(VertexFormat format) {
        Set<String> keys = new LinkedHashSet<>();
        for (var element : format.getElements()) {
            KGVertexElement desc = KGVertexElements.byMcId(element.id());
            if (desc != null) keys.add(desc.key());
        }
        return keys;
    }
}
