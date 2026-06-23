package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * A live thumbnail of a single shader-node output port, rendered Unity-style as the port's value
 * across a flat uv 0..1 quad. Compiles the subgraph feeding the port via
 * {@link ShaderGraphCompiler#compilePreview} (which substitutes mesh uv / vertex varyings with
 * preview defaults) and draws it through a real {@link RenderType}, so the preview reflects upstream
 * nodes exactly as they'd shade.
 *
 * <p>Rebuilds its material only when the compiled content hash changes, so editing upstream updates
 * the thumbnail in real time. Frees its material (and the Scene its resources) when removed.</p>
 */
public class NodeShaderPreview extends UIElement {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraph graph;
    /** The owning node + a supplier of its current preview-output port id. The PortModel is re-resolved each
     *  compile (not captured), so a node that recreates its ports on {@code defineNode} (Expression:
     *  rename/retype an output) doesn't freeze the thumbnail on a stale, dead port. */
    private final NodeModel nodeModel;
    private final Supplier<String> previewPortId;
    private final Scene scene;

    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling this thumbnail while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;
    /** Ephemeral (not serialized) preview geometry; the node's preferred default (else a flat quad),
     *  switched via right-click. */
    private KGPreviewContent content;
    /** Last width the height was squared to — guards against a relayout loop in {@link #onLayoutChanged()}. */
    private float lastSquareSize = -1f;

    public NodeShaderPreview(RenderTypeGraph graph, NodeModel nodeModel, Supplier<String> previewPortId,
                             @Nullable KGPreviewContent defaultContent) {
        this.graph = graph;
        this.nodeModel = nodeModel;
        this.previewPortId = previewPortId;
        this.content = defaultContent != null ? defaultContent : KGPreviewContents.QUAD;
        // Restore the persisted shape (survives reopen + undo/redo; stored on the graph model keyed by
        // node UID). Falls back to the node's default if there's no saved key or it no longer resolves.
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            String savedKey = m.getNodePreviewContentKey(nodeModel.getUid());
            if (savedKey != null) {
                KGPreviewContent saved = KGPreviewContents.get(savedKey);
                if (saved != null) this.content = saved;
            }
        }
        addClass("__kg-node-preview__");
        // Fill the column width, never shrink below 100×100, and stay square — height tracks the resolved
        // width in onLayoutChanged (aspect-ratio wouldn't expand the parent's height).
        Style.defaultPipeline(getLayout(), l -> l.minWidth(100).minHeight(100).widthPercent(100).alignSelf(AlignItems.CENTER));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        if (Minecraft.getInstance() == null) {
            scene = null;
            return;
        }
        scene = new Scene();
        scene.createScene(new TrackedDummyWorld());
        scene.useOrtho(true);
        scene.setCenter(new Vector3f(0, 0, 0));
        scene.setOrthoRange(0.5f);      // ortho half-range = the unit quad's half-extent, so it fills the (square) panel
        scene.setZoom(1.0f);
        // Camera on the +Z axis looking toward -Z so the +Z-facing quad faces us head-on. With
        // setCameraLookAt's convention pos=(cos(yaw),0,sin(yaw)), yaw=90deg puts the camera at +Z.
        scene.setCameraYawAndPitch(90f, 0f);
        scene.setDraggable(false);
        scene.setScalable(false);
        scene.setIntractable(false);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        scene.<WorldSceneRenderer>getRenderer().setAfterBuiltinSubmit(this::submit);
        addChild(scene);
        // Right-click the thumbnail → switch its preview geometry: handled by RenderTypeGraphView.createMenu
        // (the unified graph context menu), since GraphView opens its own menu on right-click and would
        // otherwise clobber a local one.
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

    /** The vertex-format keys of the current material (for the geometry picker), or null until it's built. */
    @Nullable
    public Set<String> previewFormatKeys() {
        return material == null ? null : PreviewContentMenu.formatKeys(material.renderType().format());
    }

    /** Switch this preview's geometry — invoked by {@code RenderTypeGraphView.createMenu}'s content items.
     *  Persists the choice on the graph model (keyed by node UID) so it survives reopen + undo/redo. */
    public void setContent(KGPreviewContent content) {
        this.content = content;
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            m.setNodePreviewContentKey(nodeModel.getUid(), content.key());
        }
    }

    private void submit(SceneRenderContext ctx) {
        RenderTypeGraphMaterial mat = updateMaterial();
        if (mat == null) return;
        RenderType renderType = mat.renderType();
        // Node previews compile with PREVIEW_SETTINGS (POSITION_COLOR_TEX, QUADS); render the chosen content
        // (a flat quad by default) through the shared content/tessellator path.
        ctx.submitStorage().submitCustomGeometry(ctx.poseStack(), renderType,
                (pose, buffer) -> PreviewRenderer.render(content, pose, buffer,
                        renderType.format(), RenderTypeGraph.Settings.VertexFormatMode.QUADS));
    }

    private RenderTypeGraphMaterial updateMaterial() {
        // Skip the per-frame recompile while the graph is unchanged (onGraphChanged bumps the version on
        // any edit). Graph-wide, so any edit re-checks every thumbnail — conservative but correct, and
        // still far cheaper than compiling each node preview every frame.
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

        // Re-resolve the live port by the node's current preview id — it's recreated on defineNode (and its
        // id changes when an output is renamed), so a captured instance would be stale.
        String id = previewPortId.get();
        PortModel outputPort = id == null ? null : nodeModel.getOutputsById().get(id);
        if (outputPort == null) return material;

        CompiledShaderGraph compiled;
        try {
            compiled = new ShaderGraphCompiler(graph).compilePreview(outputPort);
        } catch (RuntimeException e) {
            if (!lastCompileFailed) {
                LOGGER.warn("[KilaGraph] node preview failed to compile: {}", e.getMessage());
                lastCompileFailed = true;
            }
            return material;
        }
        lastChangeVersion = version;
        lastCompileFailed = false;
        if (material != null && material.contentHash().equals(compiled.contentHash())) {
            // GLSL unchanged (pipeline reused), but a value-only edit may have changed the baked
            // defaults (texture / sampler params / uniform default) — re-bake them onto the material.
            material.refreshDefaults(compiled);
            return material;
        }
        RenderTypeGraphMaterial rebuilt = RenderTypeFactory.createMaterial(compiled);
        if (rebuilt == null) return material;
        if (material != null) material.close();
        material = rebuilt;
        return material;
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();
        if (material != null) {
            material.close();
            material = null;
        }
    }
}
