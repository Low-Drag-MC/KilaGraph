package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContents;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewContentMenu;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewMaterialSlot;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewRenderer;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
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
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A graph editor panel that renders the live result of the current {@link RenderTypeGraph}. It hosts
 * an LDLib2 {@link Scene} and, each frame, compiles the graph and submits a unit cube using the
 * generated {@link RenderTypeGraphMaterial} through the vanilla submit pipeline — so the preview is
 * exactly what the compiled {@code RenderType} produces in-world (shader, blend/depth, cull, fog,
 * lighting).
 *
 * <p>The material is rebuilt only when the graph's content hash changes (pipelines are cached by
 * hash), giving real-time updates as the user edits without recompiling every frame — and in the background
 * ({@link PreviewMaterialSlot}), the last material drawing until the new one is ready. A graph that fails to
 * compile or whose pipeline is rejected by the driver keeps the last good material. The cube geometry is emitted
 * in whatever vertex format/mode the graph's {@code Settings} specify, so the preview matches the real pipeline's
 * expectations.</p>
 */
public class ShaderPreviewTool extends UIElement implements IGraphTool {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RenderTypeGraphView graphView;
    @Nullable
    private final Scene scene;

    @Nullable
    private final Label errorLabel;
    /** The material being drawn: {@code null} until the first successful build (a failed one keeps the last). */
    private final PreviewMaterialSlot material = new PreviewMaterialSlot();
    /** The graph last compiled (its stage errors, if any, are what the error label shows). */
    @Nullable
    private CompiledShaderGraph lastCompiled;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;
    /** Ephemeral (not serialized) choice of what geometry to preview; switched via right-click. */
    private KGPreviewContent content = KGPreviewContents.CUBE;

    public ShaderPreviewTool(RenderTypeGraphView graphView) {
        this.graphView = graphView;
        addClass("__rendertype-preview-tool__");
        Style.defaultPipeline(getLayout(), l -> l.widthPercent(100).heightPercent(100));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        // The Scene touches the client (Minecraft/GL); guard so the view can still be constructed
        // headlessly (e.g. server-side GameTests that exercise the settings tool).
        if (Minecraft.getInstance() == null) {
            scene = null;
            errorLabel = null;
            return;
        }
        scene = new Scene();
        // Immediate renderer (no FBO): renders straight into the element's rect, so the camera's
        // aspect matches the panel and the result isn't stretched. (A fixed-size FBO would be
        // square and get squashed into a non-square dock.)
        scene.createScene(new TrackedDummyWorld());
        scene.setCenter(new org.joml.Vector3f(0, 0, 0));
        scene.setZoom(2.5f);
        scene.setCameraYawAndPitch(45f, 25f);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        scene.<WorldSceneRenderer>getRenderer().setAfterBuiltinSubmit(this::submitPreview);
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

    /** Free the per-instance material (GpuBuffer) when the panel goes away. The Scene releases its
     * own renderer resources via its {@code onRemoved}. */
    @Override
    protected void onRemoved() {
        super.onRemoved();
        material.close();
    }

    /** The material being drawn; {@code null} until the first successful build. */
    @Nullable
    public RenderTypeGraphMaterial getMaterial() {
        return material.material();
    }

    /** Runs inside the scene's render (render thread): (re)build the material and submit the cube. */
    private void submitPreview(SceneRenderContext ctx) {
        RenderTypeGraph graph = graphView.getRenderTypeGraph();
        if (graph == null) return;

        RenderTypeGraphMaterial mat = updateMaterial(graph);
        if (mat == null) return;

        // Restore/track the persisted shape (survives reopen + undo/redo; stored on the graph model).
        // Written through on right-click below, so reading it back here is stable. A saved key that no
        // longer resolves or is incompatible with the current vertex format is ignored (keeps current).
        if (graph.graphModel instanceof RenderTypeGraphModel m) {
            String savedKey = m.getPreviewToolContentKey();
            if (savedKey != null) {
                KGPreviewContent saved = KGPreviewContents.get(savedKey);
                if (saved != null && saved != content
                        && saved.isCompatible(PreviewContentMenu.formatKeys(mat.renderType().format()))) {
                    content = saved;
                }
            }
        }

        // Emit geometry matching the RenderType we are actually drawing with — never a parallel
        // assumption from Settings. (When a graph is invalid for its vertex format, updateMaterial
        // keeps the last good material; deriving the format from that material avoids any desync
        // between the buffer's format and what we write.)
        RenderType renderType = mat.renderType();
        VertexFormat format = renderType.format();
        var mode = modeOf(renderType.primitiveTopology());

        ctx.submitStorage().submitCustomGeometry(ctx.poseStack(), renderType,
                (pose, buffer) -> PreviewRenderer.render(content, pose, buffer, format, mode));
    }

    /** Map the pipeline's primitive topology back to the graph's {@link RenderTypeGraph.Settings.VertexFormatMode}
     * (the inverse of {@code RenderTypeFactory.primitiveTopology}), so the tessellator emits a matching stream. */
    private static RenderTypeGraph.Settings.VertexFormatMode modeOf(PrimitiveTopology topology) {
        return switch (topology) {
            case TRIANGLES -> RenderTypeGraph.Settings.VertexFormatMode.TRIANGLES;
            case TRIANGLE_STRIP, TRIANGLE_FAN -> RenderTypeGraph.Settings.VertexFormatMode.TRIANGLE_STRIP;
            case LINES -> RenderTypeGraph.Settings.VertexFormatMode.LINES;
            case DEBUG_LINES, DEBUG_LINE_STRIP -> RenderTypeGraph.Settings.VertexFormatMode.LINE_STRIP;
            case QUADS, POINTS -> RenderTypeGraph.Settings.VertexFormatMode.QUADS;
        };
    }

    // ---- right-click content switch ----------------------------------------------------------

    private void onMouseDown(UIEvent event) {
        RenderTypeGraphMaterial current = material.material();
        if (event.button != 1 || current == null) return; // right-click only
        var formatKeys = PreviewContentMenu.formatKeys(current.renderType().format());
        PreviewContentMenu.open(this, event, formatKeys, c -> {
            content = c;
            // Persist the choice on the graph model so it survives reopen + undo/redo.
            RenderTypeGraph graph = graphView.getRenderTypeGraph();
            if (graph != null && graph.graphModel instanceof RenderTypeGraphModel m) {
                m.setPreviewToolContentKey(c.key());
            }
        });
    }

    /** Recompile the graph when it changed, and draw the latest material built; null while there is none. */
    @Nullable
    private RenderTypeGraphMaterial updateMaterial(RenderTypeGraph graph) {
        boolean wasFailed = material.failed();
        // Skip the per-frame recompile while the graph hasn't changed. onGraphChanged bumps the version
        // on any structural OR value/option edit, so a stale render is impossible.
        long version = graph.getChangeVersion();
        boolean edited = version != lastChangeVersion;
        if (edited) {
            lastChangeVersion = version; // a graph that doesn't compile is retried on the next edit
            try {
                // editorPreview(): screen-space defaults (Scene Color/Depth's unconnected UV) map the whole capture
                // onto the cube instead of the panel's screen sub-rect — otherwise the preview would only show the
                // small on-screen rectangle it occupies. In-world materials still use true screen-space.
                lastCompiled = graph.createCompiler().editorPreview().compile();
                lastCompileFailed = false;
                // Built in the background; the last good material keeps drawing until then — and for good,
                // should the edit produce an invalid shader.
                material.request(lastCompiled);
            } catch (RuntimeException e) {
                if (!lastCompileFailed) {
                    LOGGER.warn("[KilaGraph] preview graph failed to compile: {}", e.getMessage());
                    lastCompileFailed = true;
                }
                edited = false;
            }
        }
        material.update();
        if (edited || material.failed() != wasFailed) showErrors();
        return material.material();
    }

    /** Show the last compile's stage errors, else whether its build failed on the GPU. */
    private void showErrors() {
        if (lastCompiled == null) return;
        if (lastCompiled.hasStageErrors() || !material.failed()) showStageErrors(lastCompiled);
        else showCompileError();
    }

    /** Show a generic "shader failed to compile" message (the driver's detailed GLSL error is logged). GLSL/pipeline
     *  compile failures happen at the GPU layer, not the graph-validation layer, so they never reach the
     *  GraphLogger. */
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
