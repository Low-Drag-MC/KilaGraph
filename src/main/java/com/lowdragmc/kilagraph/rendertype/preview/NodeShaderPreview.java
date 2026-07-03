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
import com.lowdragmc.lowdraglib2.math.Size;
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
 * A live thumbnail of a single shader-node output port, rendered Unity-style as the port's value across a flat
 * uv 0..1 quad. Compiles the subgraph feeding the port via {@link ShaderGraphCompiler#compilePreview} (forces
 * {@code PREVIEW_SETTINGS}) and draws it with the generated {@link RenderTypeGraphMaterial}'s {@code ShaderInstance}.
 *
 * <p>Hosts a tiny ortho, non-interactive LDLib2 {@code Scene} in <b>FBO mode</b>: the node lives inside the graph
 * canvas, which renders with its own pan/zoom pose — an <i>immediate</i> scene draws with window-coordinate
 * viewport/GL calls that don't composite there (unlike the docked whole-graph preview, which draws straight to the
 * main framebuffer). The FBO renderer draws the scene into an off-screen texture that the Scene element then blits
 * as a GUI quad under the current pose, so it composites in the node.</p>
 */
public class NodeShaderPreview extends UIElement {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraph graph;
    /** The owning node + a supplier of its current preview-output port id (re-resolved each compile, not captured). */
    private final NodeModel nodeModel;
    private final Supplier<String> previewPortId;
    @Nullable
    private final Scene scene;

    @Nullable
    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    private long lastChangeVersion = Long.MIN_VALUE;
    private KGPreviewContent content;
    private float lastSquareSize = -1f;

    public NodeShaderPreview(RenderTypeGraph graph, NodeModel nodeModel, Supplier<String> previewPortId,
                             @Nullable KGPreviewContent defaultContent) {
        this.graph = graph;
        this.nodeModel = nodeModel;
        this.previewPortId = previewPortId;
        this.content = defaultContent != null ? defaultContent : KGPreviewContents.QUAD;
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

        if (Minecraft.getInstance() == null) {
            scene = null;
            return;
        }
        scene = new Scene();
        // FBO renderer: renders to a square off-screen texture the Scene composites into the node under the GUI pose.
        scene.createScene(new TrackedDummyWorld(), true, Size.of(256, 256));
        scene.useOrtho(true);
        scene.setCenter(new Vector3f(0, 0, 0));
        scene.setOrthoRange(0.5f);      // ortho half-range = the unit quad's half-extent, so it fills the square FBO
        scene.setZoom(1.0f);
        scene.setCameraYawAndPitch(90f, 0f); // camera on +Z looking at the +Z-facing quad head-on
        scene.setDraggable(false);
        scene.setScalable(false);
        scene.setIntractable(false);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        WorldSceneRenderer renderer = scene.getRenderer();
        if (renderer != null) {
            renderer.setAfterWorldRender(r -> submit());
        }
        addChild(scene);
    }

    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        float w = getSizeWidth();
        if (w > 0 && Math.abs(w - lastSquareSize) > 0.5f) {
            lastSquareSize = w;
            Style.importantPipeline(getLayout(), l -> l.height(w));
        }
    }

    @Nullable
    public Set<String> previewFormatKeys() {
        return material == null ? null : PreviewContentMenu.formatKeys(material.format());
    }

    public void setContent(KGPreviewContent content) {
        this.content = content;
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            m.setNodePreviewContentKey(nodeModel.getUid(), content.key());
        }
    }

    /** Runs inside the FBO scene's render (afterWorldRender, FBO bound): (re)build the material and draw the quad. */
    private void submit() {
        RenderTypeGraphMaterial mat = updateMaterial();
        if (mat == null) return;
        BufferBuilder buffer = Tesselator.getInstance().begin(mat.mode(), mat.format());
        PreviewRenderer.render(content, new PoseStack().last(), buffer, mat.format(),
                RenderTypeGraph.Settings.VertexFormatMode.QUADS);
        MeshData mesh = buffer.build();
        if (mesh == null) return;
        mat.applyRenderState();
        RenderSystem.setShader(mat::shader);
        mat.applyUniforms();
        BufferUploader.drawWithShader(mesh);
    }

    private RenderTypeGraphMaterial updateMaterial() {
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

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
