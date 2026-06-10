package com.lowdragmc.kilagraph.client;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Client-only validation harness for the RenderType pipeline. Not part of the public feature set —
 * it exists so the GPU path (which can't be exercised headlessly) can be checked in a running client:
 *
 * <ul>
 *   <li>{@code /kilagraph_shadertest precompile} — compiles the default entity graph, builds its
 *       pipeline, and runs {@code GpuDevice.precompilePipeline} (driver compile via the
 *       {@code ShaderManager} mixin). Reports {@code isValid()} in chat and logs the generated GLSL,
 *       so a driver compile error is immediately visible.</li>
 *   <li>{@code /kilagraph_shadertest draw} — toggles drawing a textured quad above the player using
 *       the generated {@link RenderTypeGraphMaterial}, exercising {@code RenderType.draw} and the
 *       custom-uniform mixin end-to-end.</li>
 * </ul>
 */
public final class RenderTypeGraphDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean drawing = false;
    private static RenderTypeGraphMaterial material;
    private static boolean materialInvalid = false;

    private RenderTypeGraphDebug() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RenderTypeGraphDebug::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RenderTypeGraphDebug::onRenderLevel);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("kilagraph_shadertest")
                        .then(Commands.literal("precompile").executes(ctx -> {
                            precompileSelfCheck(ctx.getSource());
                            return 1;
                        }))
                        .then(Commands.literal("draw").executes(ctx -> {
                            drawing = !drawing;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("KilaGraph shader test draw: " + (drawing ? "ON" : "OFF")),
                                    false);
                            return 1;
                        }))
        );
    }

    /** Compile + build + driver-precompile the default entity graph; report validity. */
    private static void precompileSelfCheck(net.minecraft.commands.CommandSourceStack source) {
        try {
            RenderTypeGraph graph = new RenderTypeGraph();
            CompiledShaderGraph compiled = new ShaderGraphCompiler(graph).compile();
            LOGGER.info("[KilaGraph] generated vertex shader:\n{}", compiled.vertexSource());
            LOGGER.info("[KilaGraph] generated fragment shader:\n{}", compiled.fragmentSource());

            RenderPipeline pipeline = RenderTypeFactory.getOrBuildPipeline(compiled);
            CompiledRenderPipeline cp = RenderSystem.getDevice().precompilePipeline(pipeline);
            boolean valid = cp.isValid();
            source.sendSuccess(() -> Component.literal(
                    "KilaGraph precompile: " + (valid ? "VALID" : "INVALID (see log)")
                            + " [hash " + compiled.contentHash() + "]"), false);
            if (!valid) {
                source.sendFailure(Component.literal("Generated pipeline failed to compile on the GPU — see latest.log"));
            }
        } catch (Throwable t) {
            LOGGER.error("[KilaGraph] precompile self-check failed", t);
            source.sendFailure(Component.literal("KilaGraph precompile threw: " + t.getMessage()));
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (!drawing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        RenderTypeGraphMaterial mat = material();
        if (mat == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 base = mc.player.position().add(0, 2.0, 0);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
        Matrix4f m = ps.last().pose();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(mat.renderType());
        int light = 0x00F000F0; // full-bright (sky=240, block=240)
        int overlay = OverlayTexture.NO_OVERLAY;
        quadVertex(vc, m, -0.5f, 0f, -0.5f, 0f, 0f, light, overlay);
        quadVertex(vc, m, -0.5f, 0f, 0.5f, 0f, 1f, light, overlay);
        quadVertex(vc, m, 0.5f, 0f, 0.5f, 1f, 1f, light, overlay);
        quadVertex(vc, m, 0.5f, 0f, -0.5f, 1f, 0f, light, overlay);
        buffers.endBatch(mat.renderType());

        ps.popPose();
    }

    private static void quadVertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                                   float u, float v, int light, int overlay) {
        vc.addVertex(m, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0f, 1f, 0f);
    }

    private static RenderTypeGraphMaterial material() {
        if (materialInvalid) return null;
        if (material == null) {
            RenderSystem.assertOnRenderThread();
            // createMaterial validates the pipeline on the GPU and returns null if it can't compile,
            // so we never draw with a broken pipeline (which would crash the render loop).
            material = RenderTypeFactory.createMaterial(new RenderTypeGraph());
            if (material == null) {
                LOGGER.error("[KilaGraph] test material pipeline is invalid; refusing to draw. Run /kilagraph_shadertest precompile for the GLSL + driver log.");
                materialInvalid = true;
                drawing = false;
            }
        }
        return material;
    }
}
