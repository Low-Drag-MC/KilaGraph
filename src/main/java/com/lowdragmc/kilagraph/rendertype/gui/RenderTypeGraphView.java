package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.DockSlot;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

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
    }

    @Override
    public GraphView loadGraph(@Nullable Graph graph) {
        super.loadGraph(graph);
        // The settings + preview tools are RenderType-specific. When diving into a ShaderFunctionGraph
        // (or any non-RenderType sub-graph) they have no meaning, so hide their panels.
        boolean isRenderType = graph instanceof RenderTypeGraph;
        setPanelVisible(settingsPanel, isRenderType);
        setPanelVisible(previewPanel, isRenderType);
        if (isRenderType) settingsTool.refreshFromGraph();
        return this;
    }

    private static void setPanelVisible(GraphPanel panel, boolean visible) {
        Style.importantPipeline(panel.getLayout(),
                l -> l.display(visible ? TaffyDisplay.FLEX : TaffyDisplay.NONE));
    }

    public @Nullable RenderTypeGraph getRenderTypeGraph() {
        return getGraph() instanceof RenderTypeGraph renderTypeGraph ? renderTypeGraph : null;
    }
}
