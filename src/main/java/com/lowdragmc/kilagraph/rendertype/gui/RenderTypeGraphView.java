package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.editor.ExportShaderFunction;
import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.preview.NodeShaderPreview;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewContentMenu;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.DockSlot;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@MethodsReturnNonnullByDefault
public class RenderTypeGraphView extends GraphView {
    @Getter
    private final RenderTypeSettingsTool settingsTool;
    @Getter
    private final ShaderPreviewTool previewTool;
    /** The RenderType-only tool panels — hidden when the view shows a non-RenderType (sub)graph. */
    private final GraphPanel settingsPanel;
    private final GraphPanel previewPanel;

    public RenderTypeGraphView() {
        super();
        settingsTool = new RenderTypeSettingsTool(this);
        settingsPanel = new GraphPanel(this, settingsTool);
        getPanelLayer().addChild(settingsPanel);
        dockManager.register(settingsPanel, DockSlot.BOTTOM_LEFT);

        previewTool = new ShaderPreviewTool(this);
        previewPanel = new GraphPanel(this, previewTool);
        getPanelLayer().addChild(previewPanel);
        dockManager.register(previewPanel, DockSlot.BOTTOM_RIGHT);
        Optional.ofNullable(dockManager.getCornerPanel(DockSlot.BOTTOM_RIGHT)).ifPresent(panel -> panel.selectTool(previewTool));
    }

    @Override
    public GraphView loadGraph(@Nullable Graph graph) {
        super.loadGraph(graph);
        // The settings + preview tools are RenderType-specific. When diving into a ShaderFunctionGraph
        // (or any non-RenderType sub-graph) they have no meaning, so hide their panels.
        boolean isRenderType = graph instanceof RenderTypeGraph;
        setPanelVisible(settingsPanel, isRenderType && shouldShowSettingsPanel(graph));
        setPanelVisible(previewPanel, isRenderType);
        if (isRenderType) settingsTool.refreshFromGraph();
        return this;
    }

    /**
     * Whether the render-settings panel should be shown for {@code graph}. Subclasses whose render
     * settings are forced/locked (e.g. a SlideShow-compat view that must match the slide pipeline) can
     * override this to hide the panel entirely. Default: shown for any {@link RenderTypeGraph}.
     */
    protected boolean shouldShowSettingsPanel(@Nullable Graph graph) {
        return true;
    }

    private static void setPanelVisible(GraphPanel panel, boolean visible) {
        Style.importantPipeline(panel.getLayout(),
                l -> l.display(visible ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
    }

    public @Nullable RenderTypeGraph getRenderTypeGraph() {
        return getGraph() instanceof RenderTypeGraph renderTypeGraph ? renderTypeGraph : null;
    }

    /**
     * GraphView opens its own context menu on right-click (clobbering any a child element tries to open),
     * so the per-node preview's geometry picker is routed through here: when the right-click lands on a
     * {@link NodeShaderPreview}, return that preview's content choices instead of the default add-node menu.
     */
    @Override
    protected TreeBuilder.Menu createMenu(float mouseX, float mouseY) {
        var hoveredPreview = findHoveredPreview();
        if (hoveredPreview != null) {
            // A preview is under the cursor: show ITS geometry picker (never the add-node menu). An empty
            // menu (material not built yet) simply opens nothing.
            var menu = TreeBuilder.Menu.start();
            Set<String> keys = hoveredPreview.previewFormatKeys();
            if (keys != null) PreviewContentMenu.appendContentItems(menu, keys, hoveredPreview::setContent);
            return menu;
        }
        var menu = super.createMenu(mouseX, mouseY);
        appendExportShaderFunction(menu);
        return menu;
    }

    /**
     * Append an "Export as Shader Function" action when nodes are selected: builds a standalone
     * {@link com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph} from the selection
     * (non-destructively — the current graph is untouched) and adds it as a new entry in the editor's
     * Resources panel, ready to drag back in as an external subgraph.
     */
    private void appendExportShaderFunction(TreeBuilder.Menu menu) {
        if (selectedElementModels().isEmpty()) return;
        menu.leaf("rendertypegraph.export_shader_function", this::exportSelectionAsShaderFunction);
    }

    private void exportSelectionAsShaderFunction() {
        Graph graph = getGraph();
        if (graph == null) return;
        var selection = selectedElementModels();
        var fn = ExportShaderFunction.build(graph.graphModel, selection, Platform.getFrozenRegistry());
        if (fn == null) return;
        CompoundTag tag = ExportShaderFunction.serialize(fn);

        Editor editor = getFirstAncestorOfType(Editor.class);
        if (editor == null) return;
        editor.resourceView.selectResourceInstance(ShaderFunctionGraphResource.INSTANCE);
        // All resource tabs' containers are built eagerly (just hidden when inactive), so the ShaderFunction
        // container — and its auto-selected provider container — already exist in the UI tree.
        var rc = findDescendant(editor.resourceView, ResourceContainer.class,
                c -> c.resourceInstance.resource == ShaderFunctionGraphResource.INSTANCE);
        if (rc == null) return;
        @SuppressWarnings("unchecked")
        ResourceProviderContainer<CompoundTag> rpc = findDescendant(rc.providerContainer,
                ResourceProviderContainer.class, c -> true);
        if (rpc != null) rpc.addNewResource(tag);
    }

    private java.util.List<GraphElementModel> selectedElementModels() {
        return getSelected().stream()
                .filter(GraphElementModel.class::isInstance)
                .map(GraphElementModel.class::cast)
                .toList();
    }

    /** Depth-first search for the first descendant of {@code root} of {@code type} matching {@code filter}. */
    @Nullable
    @SuppressWarnings("unchecked")
    private static <T extends UIElement> T findDescendant(UIElement root, Class<? super T> type, Predicate<T> filter) {
        for (var child : root.getChildren()) {
            if (type.isInstance(child)) {
                T t = (T) child;
                if (filter.test(t)) return t;
            }
            T found = findDescendant(child, type, filter);
            if (found != null) return found;
        }
        return null;
    }

    /** Depth-first search for the hovered {@link NodeShaderPreview} under {@code element}, or null. */
    @Nullable
    protected NodeShaderPreview findHoveredPreview() {
        var mui = getModularUI();
        if (mui == null) return null;
        var hovered = mui.getLastHoveredElement();
        if (hovered == null) return null;
        for (var element : hovered.getStructurePath()) {
            if (element instanceof NodeShaderPreview found) return found;
        }
        return null;
    }
}
