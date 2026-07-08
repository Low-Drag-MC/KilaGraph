package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewContentMenu;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewRenderer;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.IGraphTool;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A graph editor panel that renders the live result of the current {@link RenderTypeGraph}. It hosts an LDLib2
 * {@link Scene} and, each frame, compiles the graph and draws a unit cube with the generated
 * {@link RenderTypeGraphMaterial}'s {@code ShaderInstance} — so the preview is exactly what the compiled shader
 * produces (shader + KG/exposed uniforms + samplers). Drawing happens in the scene renderer's
 * {@code afterWorldRender} hook, where {@code RenderSystem}'s model-view/projection are the scene camera, so
 * {@code BufferUploader.drawWithShader} places the cube in the scene's 3D space.
 *
 * <p>The material is rebuilt only when the graph's content hash changes, giving real-time updates as the user
 * edits without recompiling every frame. A graph that fails to compile (or whose GLSL the driver rejects) simply
 * keeps rendering the last good material. The cube geometry is emitted in whatever vertex format/mode the material
 * uses ({@link PreviewRenderer}), so the preview matches the pipeline's expectations.</p>
 *
 * <p>The draw honours the graph {@code Settings}' blend/cull/depth via {@link RenderTypeGraphMaterial#applyRenderState()}
 * (the immediate-mode equivalent of the {@link RenderTypeGraphMaterial#renderType()} export's {@code RenderStateShard}s,
 * from the same mapping — so preview and in-world render match).</p>
 */
public class ShaderPreviewTool extends UIElement implements IGraphTool {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraphView graphView;
    @Nullable
    private final Scene scene;
    @Nullable
    private final Label errorLabel;

    @Nullable
    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;
    /** Ephemeral choice of what geometry to preview; switched via right-click, persisted on the graph model. */
    private KGPreviewContent content = KGPreviewContents.CUBE;

    public ShaderPreviewTool(RenderTypeGraphView graphView) {
        this.graphView = graphView;
        addClass("__rendertype-preview-tool__");
        Style.defaultPipeline(getLayout(), l -> l.widthPercent(100).heightPercent(100));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        // The Scene touches the client (Minecraft/GL); guard so the view can still be constructed headlessly
        // (e.g. server-side GameTests that exercise the settings tool).
        if (Minecraft.getInstance() == null) {
            scene = null;
            errorLabel = null;
            return;
        }
        scene = new Scene();
        // Immediate renderer (no FBO): renders straight into the element's rect, so the camera's aspect matches
        // the panel and the result isn't stretched.
        scene.createScene(new TrackedDummyWorld());
        scene.setCenter(new org.joml.Vector3f(0, 0, 0));
        scene.setZoom(2.5f);
        scene.setCameraYawAndPitch(45f, 25f);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        // Draw the cube after the (empty) world render, where the scene camera is the active RenderSystem matrix.
        // Registered on the renderer directly rather than Scene.setAfterWorldRender, which is only invoked when the
        // scene has core blocks (this preview has none).
        WorldSceneRenderer renderer = scene.getRenderer();
        if (renderer != null) {
            renderer.setAfterWorldRender(r -> submitPreview());
        }
        addChild(scene);

        // Right-click anywhere in the panel → choose the preview geometry (Quad / Cube / Sphere / …).
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);

        // Overlays the scene; shows stage-affinity errors when the current graph has any.
        errorLabel = new Label();
        Style.defaultPipeline(errorLabel.getLayout(),
                l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).top(2).left(2));
        errorLabel.setValue(Component.empty());
        addChild(errorLabel);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rendertypegraph.preview.title");
    }

    /** Free the per-instance material (ShaderInstance) when the panel goes away. The Scene releases its own
     * renderer resources via its {@code onRemoved}. */
    @Override
    protected void onRemoved() {
        super.onRemoved();
        if (material != null) {
            material.close();
            material = null;
        }
    }

    /** Runs inside the scene's render (render thread): (re)build the material and draw the cube with it. */
    private void submitPreview() {
        RenderTypeGraph graph = graphView.getRenderTypeGraph();
        if (graph == null) return;

        RenderTypeGraphMaterial mat = updateMaterial(graph);
        if (mat == null) return;

        // Restore/track the persisted shape (survives reopen + undo/redo; stored on the graph model). A saved key
        // that no longer resolves or is incompatible with the current vertex format is ignored (keeps current).
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            String savedKey = m.getPreviewToolContentKey();
            if (savedKey != null) {
                KGPreviewContent saved = KGPreviewContents.get(savedKey);
                if (saved != null && saved != content
                        && saved.isCompatible(PreviewContentMenu.formatKeys(mat.format()))) {
                    content = saved;
                }
            }
        }

        VertexFormat format = mat.format();
        VertexFormat.Mode mode = mat.mode();

        // Build the cube mesh in the material's own format/mode, then draw it with the material's ShaderInstance.
        BufferBuilder buffer = Tesselator.getInstance().begin(mode, format);
        PreviewRenderer.render(content, new PoseStack().last(), buffer, format, modeOf(mode));
        MeshData mesh = buffer.build();
        if (mesh == null) return; // no geometry emitted (e.g. no compatible vertex writers)

        // Apply the graph Settings' blend/cull/depth (the immediate-mode equivalent of the RenderType's shards).
        mat.applyRenderState();

        // A lightmap-sampling shader (e.g. the block-style vertex colour) needs Sampler2 bound — the
        // RenderType path gets it from the LIGHTMAP shard; this immediate draw must enable it itself,
        // else the preview renders black.
        var lightTexture = net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture();
        if (mat.usesLightmap()) lightTexture.turnOnLightLayer();

        RenderSystem.setShader(mat::shader);
        mat.applyUniforms();
        // drawWithShader sets the vanilla builtins (setDefaultUniforms), applies, draws, and closes the mesh.
        BufferUploader.drawWithShader(mesh);

        if (mat.usesLightmap()) lightTexture.turnOffLightLayer();
    }

    /** Map the pipeline's primitive mode back to the graph's {@link RenderTypeGraph.Settings.VertexFormatMode}
     * (the inverse of {@code RenderTypeFactory.vertexMode}), so the tessellator emits a matching stream. */
    private static RenderTypeGraph.Settings.VertexFormatMode modeOf(VertexFormat.Mode mode) {
        return switch (mode) {
            case TRIANGLES -> RenderTypeGraph.Settings.VertexFormatMode.TRIANGLES;
            case TRIANGLE_STRIP, TRIANGLE_FAN -> RenderTypeGraph.Settings.VertexFormatMode.TRIANGLE_STRIP;
            case LINES -> RenderTypeGraph.Settings.VertexFormatMode.LINES;
            case LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP -> RenderTypeGraph.Settings.VertexFormatMode.LINE_STRIP;
            case QUADS -> RenderTypeGraph.Settings.VertexFormatMode.QUADS;
        };
    }

    // ---- right-click content switch ----------------------------------------------------------

    private void onMouseDown(UIEvent event) {
        if (event.button != 1 || material == null) return; // right-click only
        var formatKeys = PreviewContentMenu.formatKeys(material.format());
        PreviewContentMenu.open(this, event, formatKeys, c -> {
            content = c;
            // Persist the choice on the graph model so it survives reopen + undo/redo.
            RenderTypeGraph graph = graphView.getRenderTypeGraph();
            if (graph != null && graph.graphModel instanceof RenderTypeGraphModel m) {
                m.setPreviewToolContentKey(c.key());
            }
        });
    }

    /** Recompile + rebuild the material when the graph changed; null if it can't be rendered. */
    @Nullable
    private RenderTypeGraphMaterial updateMaterial(RenderTypeGraph graph) {
        // Skip the per-frame recompile while the graph hasn't changed (the change-version bumps on any structural
        // or value/option edit). Still recompile when we have no material yet (e.g. after a failure) to keep retrying.
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

        CompiledShaderGraph compiled;
        try {
            // editorPreview(): screen-space defaults (Scene Color/Depth's unconnected UV) map the whole capture onto
            // the cube instead of the panel's screen sub-rect. In-world materials still use true screen-space.
            compiled = graph.createCompiler().editorPreview().compile();
        } catch (RuntimeException e) {
            if (!lastCompileFailed) {
                LOGGER.warn("[KilaGraph] preview graph failed to compile: {}", e.getMessage());
                lastCompileFailed = true;
            }
            return material; // keep last good material rendering
        }
        lastChangeVersion = version;
        lastCompileFailed = false;
        showStageErrors(compiled);

        if (material != null && material.contentHash().equals(compiled.contentHash())) {
            // GLSL unchanged (shader reused), but a value-only edit may have changed the baked defaults — re-bake.
            material.refreshDefaults(compiled);
            return material;
        }
        // Graph changed — createMaterial builds+compiles the ShaderInstance and returns null if the edit produced
        // invalid GLSL; keep the last good material rather than crashing the draw.
        RenderTypeGraphMaterial rebuilt = RenderTypeFactory.createMaterial(compiled);
        if (rebuilt == null) {
            // GLSL compile failures happen at the GL layer (not graph validation), so they never reach the
            // GraphLogger — surface a generic message here (the detailed driver error is logged). Don't clobber a
            // stage-error message already shown above.
            if (!compiled.hasStageErrors()) showCompileError();
            return material;
        }
        if (material != null) material.close();
        material = rebuilt;
        return material;
    }

    /** Show a generic "shader failed to compile" message (the driver's detailed GLSL error is logged). */
    private void showCompileError() {
        if (errorLabel == null) return;
        errorLabel.setValue(Component.translatable("rendertypegraph.preview.compile_failed")
                .withStyle(s -> s.withColor(0xFF5555)));
    }

    /** Show stage-affinity violations (one per line, red) over the scene, or clear when none. */
    private void showStageErrors(CompiledShaderGraph compiled) {
        if (errorLabel == null) return;
        if (!compiled.hasStageErrors()) {
            errorLabel.setValue(Component.empty());
            return;
        }
        var text = Component.literal(compiled.stageErrors().stream()
                .map(com.lowdragmc.kilagraph.rendertype.compiler.StageError::message)
                .reduce((a, b) -> a + "\n" + b).orElse(""));
        errorLabel.setValue(text.withStyle(s -> s.withColor(0xFF5555)));
    }
}
