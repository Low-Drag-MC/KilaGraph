package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * A live thumbnail of a single shader-node output port, rendered Unity-style as the port's value across a
 * flat uv 0..1 quad.
 *
 * <p>TODO(1.21-backport milestone 2): the live rendering — an LDLib2 {@code Scene} +
 * {@code WorldSceneRenderer.setAfterBuiltinSubmit(SceneRenderContext)} that compiles the subgraph feeding
 * the port (via {@code ShaderGraphCompiler.compilePreview}) and draws it through a
 * {@code net.minecraft.client.renderer.rendertype.RenderType}/{@code RenderTypeGraphMaterial} — is stubbed:
 * {@code SceneRenderContext}, the per-{@code RenderType} submit API, and the material runtime do not exist in
 * 1.21.1. The UI shell (layout + content-shape persistence) is kept so the editor still builds;
 * {@link #previewFormatKeys()} returns {@code null} (no live material) so the geometry menu falls back to its
 * default. Reimplement the thumbnail rendering against the 1.21.1 scene/RenderType model.</p>
 */
public class NodeShaderPreview extends UIElement {

    private final RenderTypeGraph graph;
    private final NodeModel nodeModel;
    @SuppressWarnings("unused") // kept for milestone 2 (re-resolving the live preview port each compile)
    private final Supplier<String> previewPortId;
    @SuppressWarnings("unused") // kept for milestone 2 (the geometry the thumbnail renders)
    private KGPreviewContent content;
    /** Last width the height was squared to — guards against a relayout loop in {@link #onLayoutChanged()}. */
    private float lastSquareSize = -1f;

    public NodeShaderPreview(RenderTypeGraph graph, NodeModel nodeModel, Supplier<String> previewPortId,
                             @Nullable KGPreviewContent defaultContent) {
        this.graph = graph;
        this.nodeModel = nodeModel;
        this.previewPortId = previewPortId;
        this.content = defaultContent != null ? defaultContent : KGPreviewContents.QUAD;
        // Restore the persisted shape (survives reopen + undo/redo; stored on the graph model keyed by node UID).
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            String savedKey = m.getNodePreviewContentKey(nodeModel.getUid());
            if (savedKey != null) {
                KGPreviewContent saved = KGPreviewContents.get(savedKey);
                if (saved != null) this.content = saved;
            }
        }
        addClass("__kg-node-preview__");
        Style.defaultPipeline(getLayout(), l -> l.minWidth(100).minHeight(100).widthPercent(100).alignSelf(AlignItems.CENTER));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));
        // TODO(1.21-backport milestone 2): create the LDLib2 Scene and submit the compiled RenderType.
    }

    /** Keep the thumbnail square: height tracks the resolved width (aspect-ratio can't expand the parent). */
    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        float w = getSizeWidth();
        if (w > 0 && Math.abs(w - lastSquareSize) > 0.5f) {
            lastSquareSize = w;
            Style.importantPipeline(getLayout(), l -> l.height(w));
        }
    }

    // TODO(1.21-backport milestone 2): derive from the live material's RenderType vertex format.
    @Nullable
    public Set<String> previewFormatKeys() {
        return null;
    }

    /** Switch this preview's geometry — persisted on the graph model (keyed by node UID). */
    public void setContent(KGPreviewContent content) {
        this.content = content;
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            m.setNodePreviewContentKey(nodeModel.getUid(), content.key());
        }
    }
}
