package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.IGraphTool;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * hash), giving real-time updates as the user edits without recompiling every frame. A graph that
 * fails to compile or whose pipeline is rejected by the driver simply skips drawing that frame. The
 * cube geometry is emitted in whatever vertex format/mode the graph's {@code Settings} specify, so
 * the preview matches the real pipeline's expectations.</p>
 */
public class ShaderPreviewTool extends UIElement implements IGraphTool {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final RenderTypeGraphView graphView;
    @Nullable
    private final Scene scene;

    @Nullable
    private final Label errorLabel;
    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;

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

        // Overlays the scene; shows stage-affinity errors when the current graph has any.
        errorLabel = new Label();
        Style.defaultPipeline(errorLabel.getLayout(),
                l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).top(2).left(2));
        errorLabel.setValue(Component.empty());
        addChild(errorLabel);
    }

    @Override
    public Component getTitle() {
        return Component.literal("Preview");
    }

    /** Free the per-instance material (GpuBuffer) when the panel goes away. The Scene releases its
     * own renderer resources via its {@code onRemoved}. */
    @Override
    protected void onRemoved() {
        super.onRemoved();
        if (material != null) {
            material.close();
            material = null;
        }
    }

    /** Runs inside the scene's render (render thread): (re)build the material and submit the cube. */
    private void submitPreview(SceneRenderContext ctx) {
        RenderTypeGraph graph = graphView.getRenderTypeGraph();
        if (graph == null) return;

        RenderTypeGraphMaterial mat = updateMaterial(graph);
        if (mat == null) return;

        // Emit geometry matching the RenderType we are actually drawing with — never a parallel
        // assumption from Settings. (When a graph is invalid for its vertex format, updateMaterial
        // keeps the last good material; deriving the format from that material avoids any desync
        // between the buffer's format and what we write.)
        RenderType renderType = mat.renderType();
        VertexFormat format = renderType.format();
        boolean quads = renderType.mode() == VertexFormat.Mode.QUADS;

        ctx.submitStorage().submitCustomGeometry(ctx.poseStack(), renderType,
                (pose, buffer) -> emitCube(pose, buffer, format, quads));
    }

    /** Recompile + rebuild the material when the graph changed; null if it can't be rendered. */
    private RenderTypeGraphMaterial updateMaterial(RenderTypeGraph graph) {
        // Skip the per-frame recompile while the graph hasn't changed. onGraphChanged bumps the version
        // on any structural OR value/option edit, so a stale render is impossible. (We still recompile
        // when we have no material yet, e.g. after a compile/pipeline failure, to keep retrying.)
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

        CompiledShaderGraph compiled;
        try {
            compiled = new ShaderGraphCompiler(graph).compile();
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
            // GLSL unchanged (pipeline reused), but a value-only edit (texture / sampler params / uniform
            // default) may have changed the baked defaults — re-bake them onto the existing material.
            material.refreshDefaults(compiled);
            return material;
        }
        // Graph changed — createMaterial validates the pipeline on the GPU and returns null if the
        // edit produced an invalid shader; keep the last good material rather than crashing the draw.
        RenderTypeGraphMaterial rebuilt = RenderTypeFactory.createMaterial(compiled);
        if (rebuilt == null) return material;
        if (material != null) material.close();
        material = rebuilt;
        return material;
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

    // ---- preview geometry --------------------------------------------------------------------

    /**
     * A unit cube centered at the origin. Faces wind counter-clockwise when viewed from outside, so
     * back-face culling keeps the outer surfaces. Vertices carry only the attributes present in
     * {@code format}. {@code quads} chooses 4-vertex faces (QUADS mode) vs two triangles per face.
     */
    private static void emitCube(PoseStack.Pose pose, VertexConsumer vc, VertexFormat format, boolean quads) {
        // 8 corners
        float[][] c = {
                {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}, // back  (-Z)
                {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}      // front (+Z)
        };
        // 6 faces as CCW-from-outside corner indices + outward normal
        int[][] faces = {
                {1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {4, 0, 1, 5}, {3, 7, 6, 2}
        };
        float[][] normals = {
                {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}
        };
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            float[] n = normals[f];
            if (quads) {
                for (int i = 0; i < 4; i++) vertex(vc, pose, format, c[q[i]], uvs[i], n);
            } else {
                int[] tri = {0, 1, 2, 0, 2, 3};
                for (int i : tri) vertex(vc, pose, format, c[q[i]], uvs[i], n);
            }
        }
    }

    /** Emit one vertex, writing only the attributes the format declares. */
    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, VertexFormat format,
                               float[] p, float[] uv, float[] n) {
        vc.addVertex(pose, p[0], p[1], p[2]);
        if (format.contains(VertexFormatElement.COLOR)) vc.setColor(255, 255, 255, 255);
        if (format.contains(VertexFormatElement.UV0)) vc.setUv(uv[0], uv[1]);
        if (format.contains(VertexFormatElement.UV1)) vc.setOverlay(OverlayTexture.NO_OVERLAY);
        if (format.contains(VertexFormatElement.UV2)) vc.setLight(FULL_BRIGHT);
        if (format.contains(VertexFormatElement.NORMAL)) vc.setNormal(pose, n[0], n[1], n[2]);
    }
}
