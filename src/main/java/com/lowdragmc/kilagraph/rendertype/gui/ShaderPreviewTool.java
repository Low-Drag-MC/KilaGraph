package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.IGraphTool;
import net.minecraft.network.chat.Component;

/**
 * A graph editor panel that renders the live result of the current {@link RenderTypeGraph}.
 *
 * <p>TODO(1.21-backport milestone 2): stubbed — the live preview hosted an LDLib2 {@code Scene} and each
 * frame compiled the graph + submitted a cube through the generated {@code RenderTypeGraphMaterial}/
 * {@code net.minecraft.client.renderer.rendertype.RenderType} via
 * {@code WorldSceneRenderer.setAfterBuiltinSubmit(SceneRenderContext)}. That scene-submit path + material
 * runtime don't exist in 1.21.1. The tool shell (title + empty panel, so the editor still builds) is kept.
 * Reimplement the live render (Scene host, per-frame recompile, RenderType submit, stage/compile error
 * overlay, right-click geometry menu) against the 1.21.1 scene/RenderType model.</p>
 */
public class ShaderPreviewTool extends UIElement implements IGraphTool {

    @SuppressWarnings("unused") // kept for milestone 2 (source of the RenderTypeGraph to preview)
    private final RenderTypeGraphView graphView;

    public ShaderPreviewTool(RenderTypeGraphView graphView) {
        this.graphView = graphView;
        addClass("__rendertype-preview-tool__");
        Style.defaultPipeline(getLayout(), l -> l.widthPercent(100).heightPercent(100));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));
        // TODO(1.21-backport milestone 2): host the LDLib2 Scene + submit the compiled RenderType each frame.
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rendertypegraph.preview.title");
    }
}
