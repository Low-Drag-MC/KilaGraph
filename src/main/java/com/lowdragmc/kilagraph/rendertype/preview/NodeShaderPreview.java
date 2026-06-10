package com.lowdragmc.kilagraph.rendertype.preview;

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
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.logging.LogUtils;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import org.slf4j.Logger;

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
    private final PortModel outputPort;
    private final Scene scene;

    private RenderTypeGraphMaterial material;
    private boolean lastCompileFailed = false;
    /** The graph change-version last compiled; skip recompiling this thumbnail while it's unchanged. */
    private long lastChangeVersion = Long.MIN_VALUE;

    public NodeShaderPreview(RenderTypeGraph graph, PortModel outputPort) {
        this.graph = graph;
        this.outputPort = outputPort;
        addClass("__kg-node-preview__");
        Style.defaultPipeline(getLayout(), l -> l.width(100).height(100).alignSelf(AlignItems.CENTER));
        Style.defaultPipeline(getStyle(), s -> s.backgroundTexture(IGuiTexture.EMPTY));

        if (Minecraft.getInstance() == null) {
            scene = null;
            return;
        }
        scene = new Scene();
        scene.createScene(new TrackedDummyWorld());
        scene.useOrtho(true);
        scene.setCenter(new Vector3f(0, 0, 0));
        scene.setOrthoRange(0.62f);     // frame the unit quad (half-extent 0.5) with a small margin
        scene.setZoom(1.0f);
        // Camera on the +Z axis looking toward -Z so the +Z-facing quad faces us head-on. With
        // setCameraLookAt's convention pos=(cos(yaw),0,sin(yaw)), yaw=90deg puts the camera at +Z.
        scene.setCameraYawAndPitch(90f, 0f);
        scene.setDraggable(false);
        scene.setScalable(false);
        Style.defaultPipeline(scene.getLayout(), l -> l.widthPercent(100).heightPercent(100));
        scene.<WorldSceneRenderer>getRenderer().setAfterBuiltinSubmit(this::submit);
        addChild(scene);
    }

    private void submit(SceneRenderContext ctx) {
        RenderTypeGraphMaterial mat = updateMaterial();
        if (mat == null) return;
        RenderType renderType = mat.renderType();
        boolean quads = renderType.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
        ctx.submitStorage().submitCustomGeometry(ctx.poseStack(), renderType,
                (pose, buffer) -> PreviewGeometry.quad(pose, buffer, renderType.format(), quads));
    }

    private RenderTypeGraphMaterial updateMaterial() {
        // Skip the per-frame recompile while the graph is unchanged (onGraphChanged bumps the version on
        // any edit). Graph-wide, so any edit re-checks every thumbnail — conservative but correct, and
        // still far cheaper than compiling each node preview every frame.
        long version = graph.getChangeVersion();
        if (material != null && version == lastChangeVersion) return material;

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
