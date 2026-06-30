package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Self-contained in-world test for the Iris path: {@code /kgiris draw} toggles a 1×1×1 cube rendered at the
 * player's block position with the <b>default KilaGraph material</b> ({@code new RenderTypeGraph()} — the
 * dirt-textured lit entity shader), drawn through the ordinary world pass ({@link RenderLevelStageEvent})
 * and the standard {@code MultiBufferSource} → {@code RenderType.draw} path. This is the same pipeline a
 * block-entity renderer uses, so it exercises the exact Iris routing ({@code assignPipeline} + the injected
 * gbuffers program). If this cube shows under a shaderpack but a host's hologram doesn't, the difference is
 * the host's draw path; if neither shows, the bug is reproducible here in code we control.
 */
@EventBusSubscriber(modid = "kilagraph", value = Dist.CLIENT)
public final class IrisTestRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Full-brightness packed lightmap (block=15, sky=15) — same literal the preview uses. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    /** Dirt texture used by the vanilla-RenderType comparison cube. */
    private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");

    private static boolean enabled = false;
    /** When true, draw with a vanilla {@code RenderType.entitySolid} instead of our KilaGraph material —
     *  bisects "our custom pipeline" vs "the draw stage/path" as the cause of invisibility under Iris. */
    private static boolean vanilla = false;
    private static BlockPos pos = BlockPos.ZERO;
    private static RenderTypeGraphMaterial material;
    private static boolean errored = false;

    private IrisTestRenderer() {}

    /** Toggle the test cube, anchoring it at {@code at}. {@code useVanilla} picks the vanilla-RenderType
     *  comparison cube. Returns the new enabled state. */
    public static boolean toggle(BlockPos at, boolean useVanilla) {
        // Switching mode while on restarts cleanly.
        if (enabled && vanilla != useVanilla) {
            vanilla = useVanilla;
            pos = at;
            return true;
        }
        enabled = !enabled;
        vanilla = useVanilla;
        pos = at;
        return enabled;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        if (!enabled) return;
        try {
            RenderType renderType;
            if (vanilla) {
                renderType = RenderTypes.entitySolid(DIRT);
            } else {
                if (material == null) {
                    material = RenderTypeFactory.createMaterial(new RenderTypeGraph());
                    if (material == null) {
                        LOGGER.warn("[KilaGraph][Iris] test cube material failed to compile");
                        enabled = false;
                        return;
                    }
                }
                renderType = material.renderType();
            }
            renderCube(event, renderType);
        } catch (Throwable t) {
            if (!errored) {
                errored = true;
                LOGGER.error("[KilaGraph][Iris] test cube render failed", t);
            }
            enabled = false;
        }
    }

    private static void renderCube(RenderLevelStageEvent event, RenderType renderType) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // World pose is camera-relative: translate the block's world position into camera space.
        poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        Matrix4f m = poseStack.last().pose();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(renderType);

        // Unit cube corners.
        float x0 = 0, y0 = 0, z0 = 0, x1 = 1, y1 = 1, z1 = 1;
        // Each face: 4 corners (CCW) + outward normal.
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);   // +Z
        quad(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);  // -Z
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);  // -X
        quad(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);   // +X
        quad(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);   // +Y
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);  // -Y

        buffers.endBatch(renderType);
        poseStack.popPose();
    }

    /** Emit one quad (4 vertices) with the full ENTITY vertex format the default graph expects. */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz) {
        vertex(vc, m, ax, ay, az, 0, 0, nx, ny, nz);
        vertex(vc, m, bx, by, bz, 1, 0, nx, ny, nz);
        vertex(vc, m, cx, cy, cz, 1, 1, nx, ny, nz);
        vertex(vc, m, dx, dy, dz, 0, 1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
