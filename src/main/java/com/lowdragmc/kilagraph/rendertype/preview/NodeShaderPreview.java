package com.lowdragmc.kilagraph.rendertype.preview;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * A live thumbnail of a single shader-node output port, rendered Unity-style as the port's value across a
 * flat uv 0..1 quad. Compiles the subgraph feeding the port via {@link ShaderGraphCompiler#compilePreview}
 * (which substitutes mesh uv / vertex varyings with preview defaults, and forces {@code PREVIEW_SETTINGS}:
 * POSITION_COLOR_TEX / QUADS) and draws it with the generated {@link RenderTypeGraphMaterial}'s
 * {@code ShaderInstance}, so the thumbnail reflects upstream nodes exactly as they'd shade.
 *
 * <p>Hosts a tiny ortho, non-interactive LDLib2 {@code Scene} looking head-on at the +Z-facing quad; the draw
 * happens in the scene renderer's {@code afterWorldRender} hook (same contract as {@code ShaderPreviewTool}).
 * Rebuilds its material only when the compiled content hash changes, so editing upstream updates the thumbnail
 * in real time. Frees its material (and the Scene its resources) when removed.</p>
 */
public class NodeShaderPreview extends UIElement {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraph graph;
    /** The owning node + a supplier of its current preview-output port id. The PortModel is re-resolved each
     *  compile (not captured), so a node that recreates its ports on {@code defineNode} (e.g. Expression:
     *  rename/retype an output) doesn't freeze the thumbnail on a stale, dead port. */
    private final NodeModel nodeModel;
    private final Supplier<String> previewPortId;
    @Nullable
    private final Scene scene;

    @Nullable
    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling this thumbnail while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;
    /** Ephemeral preview geometry; the node's preferred default (else a flat quad), switched via right-click. */
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
        // Fill the column width, never shrink below 100×100, and stay square — height tracks the resolved width
        // in onLayoutChanged (aspect-ratio wouldn't expand the parent's height).
        Style.defaultPipeline(getLayout(), l -> l.minWidth(100).minHeight(100).widthPercent(100).alignSelf(AlignItems.CENTER));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        // The Scene touches the client (Minecraft/GL); guard so the node can still be constructed headlessly.
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
        // Camera on the +Z axis looking toward -Z so the +Z-facing quad faces us head-on. With setCameraLookAt's
        // convention pos=(cos(yaw),0,sin(yaw)), yaw=90deg puts the camera at +Z.
        scene.setCameraYawAndPitch(90f, 0f);
        scene.setDraggable(false);
        scene.setScalable(false);
        scene.setIntractable(false);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        // Draw in the renderer's afterWorldRender hook (the scene has no core blocks, so Scene.setAfterWorldRender
        // would never fire — register on the renderer directly, as ShaderPreviewTool does).
        WorldSceneRenderer renderer = scene.getRenderer();
        if (renderer != null) {
            renderer.setAfterWorldRender(r -> submit());
        }
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
        return material == null ? null : PreviewContentMenu.formatKeys(material.format());
    }

    /** Switch this preview's geometry — invoked by {@code RenderTypeGraphView.createMenu}'s content items.
     *  Persists the choice on the graph model (keyed by node UID) so it survives reopen + undo/redo. */
    public void setContent(KGPreviewContent content) {
        this.content = content;
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            m.setNodePreviewContentKey(nodeModel.getUid(), content.key());
        }
    }

    /** Runs inside the scene's render (render thread): (re)build the material and draw the content with it. */
    private void submit() {
        RenderTypeGraphMaterial mat = updateMaterial();
        if (mat == null) return;

        // compilePreview forces PREVIEW_SETTINGS (POSITION_COLOR_TEX / QUADS); build+draw the chosen content
        // (a flat quad by default) in the material's own format/mode through the shared draw contract.
        BufferBuilder buffer = Tesselator.getInstance().begin(mat.mode(), mat.format());
        PreviewRenderer.render(content, new PoseStack().last(), buffer, mat.format(),
                RenderTypeGraph.Settings.VertexFormatMode.QUADS);
        MeshData mesh = buffer.build();
        if (mesh == null) return; // no geometry emitted (e.g. no compatible vertex writers)

        mat.applyRenderState();
        RenderSystem.setShader(mat::shader);
        mat.applyUniforms();
        BufferUploader.drawWithShader(mesh);
    }

    private RenderTypeGraphMaterial updateMaterial() {
        // Skip the per-frame recompile while the graph is unchanged (any edit bumps the version). Graph-wide, so
        // any edit re-checks every thumbnail — conservative but correct, and far cheaper than compiling each frame.
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

        // Re-resolve the live port by the node's current preview id — it's recreated on defineNode (and its id
        // changes when an output is renamed), so a captured instance would be stale.
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
            // GLSL unchanged (shader reused), but a value-only edit may have changed the baked defaults — re-bake.
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
