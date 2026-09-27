package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings.BlendMode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.SceneColorNode;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.Bare;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.bare;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.settings;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.vec3;

/**
 * KilaGraph quads drawn into a real world frame (via {@link SubmitCustomGeometryEvent}), so through the level
 * renderer's own passes, against a wall of known colour — with classic transparency, and with improved
 * (order-independent) transparency, where blended custom geometry draws in the OIT phases or, for a blend they
 * can't express, in the solid phase.
 */
@LDLRegisterClient(name = "kg_rt_world", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class RenderTypeWorldScenario implements UIScenario {

    /** On the superflat surface, looking due south. */
    private static final double FEET_X = 0.5, FEET_Y = -60, FEET_Z = 0.5;
    /** A known backdrop behind the quads. */
    private static final BlockPos WALL_FROM = new BlockPos(-8, -60, 9), WALL_TO = new BlockPos(8, -52, 9);
    /** Eye to quad plane, and a quad's half size, in blocks. */
    private static final double DEPTH = 4, HALF = 0.45;
    /** Quad positions on that plane, in blocks right of / above the view centre. */
    private static final double[] COLUMNS = {-3.2, -1.6, 0, 1.6, 3.2};
    private static final double ROW = 1.0;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float TOL = 5f / 255f;
    /** The blended quads' colour, (0.5, 0.25, 0). */
    private static final float[] C = {0.5f, 0.25f, 0f};

    @Nullable private static volatile Scene shown;
    private static boolean listening;
    private static boolean improvedTransparencyBefore;
    @Nullable private static java.util.concurrent.CompletableFuture<Void> reload;

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(0).tags("kilagraph", "rendertype", "gpu")
                .defaultTimeoutMs(120_000).scenarioTimeoutMs(300_000);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("classic transparency", ctx -> {
                    var option = Minecraft.getInstance().options.improvedTransparency();
                    improvedTransparencyBefore = option.get();
                    option.set(false);
                })
                .teleportPlayer(FEET_X, FEET_Y, FEET_Z, 0, 0)
                .fill(WALL_FROM, WALL_TO, Blocks.CONCRETE.lightGray().defaultBlockState())
                .awaitClientChunk(WALL_FROM)
                .waitUntil("the player stands in place", ctx -> ctx.mc().player != null
                        && ctx.mc().player.position().distanceToSqr(FEET_X, FEET_Y, FEET_Z) < 0.01)
                .step("build the materials", RenderTypeWorldScenario::build)
                .waitUntil("the wall is on screen", RenderTypeWorldScenario::wallVisible)
                .frames(10)
                .step("classic: the wall behind each quad", RenderTypeWorldScenario::recordWall)
                .step("show the quads", ctx -> scene(ctx).visible = true)
                .frames(10)
                .step("classic: each quad over the wall", ctx -> check(ctx, "classic"))
                .step("screenshot", ctx -> ctx.screenshot("classic"))
                .step("hide the quads, improved transparency", ctx -> {
                    scene(ctx).visible = false;
                    Minecraft.getInstance().options.improvedTransparency().set(true);
                })
                .waitUntil("the renderer uses it", ctx -> Minecraft.getInstance().gameRenderer.useImprovedTransparency())
                .frames(10)
                .step("improved: the wall behind each quad", RenderTypeWorldScenario::recordWall)
                .step("show the quads", ctx -> scene(ctx).visible = true)
                .frames(10)
                .step("improved: each quad over the wall", ctx -> check(ctx, "improved"))
                .step("screenshot", ctx -> ctx.screenshot("improved"))
                // A reload replaces the pipeline cache, closing the compiled generated pipelines with it.
                .step("reload resources", ctx -> reload = Minecraft.getInstance().reloadResourcePacks())
                .waitUntil("the reload is done", ctx -> reload != null && reload.isDone())
                .frames(10)
                .step("after the reload: each quad over the wall", ctx -> check(ctx, "after a reload"))
                .teardown("restore transparency, free the materials", ctx -> {
                    Scene scene = shown;
                    shown = null;
                    Minecraft.getInstance().options.improvedTransparency().set(improvedTransparencyBefore);
                    if (scene != null) scene.materials.forEach(RenderTypeGraphMaterial::close);
                });
    }

    private static void build(TestContext ctx) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, RenderTypeWorldScenario::submit);
        }
        Scene scene = new Scene(Minecraft.getInstance().gameRenderer.mainCamera());
        shown = scene; // before the first add: a failed compile below must still free what was built
        scene.opaque = scene.add(ctx, solid(1, 0, 1, BlendMode.OPAQUE), 0);
        scene.translucent = scene.add(ctx, blended(BlendMode.TRANSLUCENT, 0.5f), 1);
        scene.additive = scene.add(ctx, blended(BlendMode.ADDITIVE, 1), 2);
        scene.multiply = scene.add(ctx, blended(BlendMode.MULTIPLY, 1), 3);
        scene.sceneColor = scene.add(ctx, sceneColor(), 4);
        ctx.check("a multiply draws in the solid phase under improved transparency",
                scene.multiply.drawsSolidUnderImprovedTransparency());
    }

    private static Scene scene(TestContext ctx) {
        Scene scene = shown;
        ctx.require("the scene is built", scene != null);
        return scene;
    }

    /** The wall shows where the quads go, as light grey. */
    private static boolean wallVisible(TestContext ctx) {
        Scene scene = shown;
        if (scene == null) return false;
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            float[] px = scene.sample(frame, COLUMNS[2], ROW);
            float max = Math.max(px[0], Math.max(px[1], px[2])), min = Math.min(px[0], Math.min(px[1], px[2]));
            return max - min < 0.08f && max > 0.3f;
        }
    }

    /** The wall at each quad's place, while the quads are hidden: what each blends over. */
    private static void recordWall(TestContext ctx) {
        Scene scene = scene(ctx);
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            for (int i = 0; i < COLUMNS.length; i++) scene.wall[i] = scene.sample(frame, COLUMNS[i], ROW);
        }
    }

    private static void check(TestContext ctx, String mode) {
        Scene scene = scene(ctx);
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            float[][] w = scene.wall;
            expect(ctx, mode + ": opaque magenta", scene.sample(frame, COLUMNS[0], ROW), 1, 0, 1);
            expect(ctx, mode + ": translucent over the wall", scene.sample(frame, COLUMNS[1], ROW),
                    C[0] * 0.5f + w[1][0] * 0.5f, C[1] * 0.5f + w[1][1] * 0.5f, C[2] * 0.5f + w[1][2] * 0.5f);
            expect(ctx, mode + ": additive over the wall", scene.sample(frame, COLUMNS[2], ROW),
                    Math.min(1, C[0] + w[2][0]), Math.min(1, C[1] + w[2][1]), Math.min(1, C[2] + w[2][2]));
            expect(ctx, mode + ": multiply over the wall", scene.sample(frame, COLUMNS[3], ROW),
                    C[0] * w[3][0], C[1] * w[3][1], C[2] * w[3][2]);
            expect(ctx, mode + ": Scene Color shows the wall behind it", scene.sample(frame, COLUMNS[4], ROW),
                    w[4][0], w[4][1], w[4][2]);
        }
    }

    private static void expect(TestContext ctx, String what, float[] px, float r, float g, float b) {
        boolean ok = Math.abs(px[0] - r) <= TOL && Math.abs(px[1] - g) <= TOL && Math.abs(px[2] - b) <= TOL;
        ctx.check(what, ok, rgb(new float[]{r, g, b}), rgb(px));
    }

    private static String rgb(float[] c) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", c[0], c[1], c[2]);
    }

    // ---- graphs ----------------------------------------------------------------------------------

    private static Bare solid(float r, float g, float b, BlendMode blend) {
        Bare graph = bare().settings(s -> settings(s, blend, s.depthTest(), blend == BlendMode.OPAQUE, false));
        wire(graph.graph(), graph.color(), vec3(graph, r, g, b).getOutputsById().get("out"));
        return graph;
    }

    /** {@link #C} at {@code alpha}, blended with {@code blend}. */
    private static Bare blended(BlendMode blend, float alpha) {
        Bare graph = solid(C[0], C[1], C[2], blend);
        wire(graph.graph(), graph.alphaIn(), vec3(graph, alpha, 0, 0).getOutputsById().get("out"));
        return graph;
    }

    /** The captured scene at the fragment's own screen position: in front of the wall, it shows the wall. */
    private static Bare sceneColor() {
        Bare graph = bare().settings(s -> settings(s, BlendMode.TRANSLUCENT, s.depthTest(), false, false));
        NodeModel scene = addNode(graph.graph(), SceneColorNode.class);
        wire(graph.graph(), graph.color(), scene.getOutputsById().get("out"));
        return graph;
    }

    // ---- the scene -------------------------------------------------------------------------------

    private static void submit(SubmitCustomGeometryEvent event) {
        Scene scene = shown;
        if (scene == null || !scene.visible) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();
        for (int i = 0; i < scene.materials.size(); i++) {
            Vec3 center = scene.at(COLUMNS[scene.columns.get(i)], ROW).subtract(camera);
            collector.submitCustomGeometry(event.getPoseStack(), scene.materials.get(i).renderType(),
                    (pose, vc) -> scene.emit(pose, vc, center));
        }
    }

    private static final class Scene {
        final List<RenderTypeGraphMaterial> materials = new ArrayList<>();
        final List<Integer> columns = new ArrayList<>();
        /** The quad plane in world space. */
        final Vec3 center, right, up, toCamera;
        RenderTypeGraphMaterial opaque, translucent, additive, multiply, sceneColor;
        /** The wall at each column while the quads are hidden. */
        final float[][] wall = new float[COLUMNS.length][];
        volatile boolean visible;

        Scene(Camera camera) {
            Vector3f forward = new Vector3f(camera.forwardVector());
            Vector3f left = new Vector3f(camera.leftVector());
            Vector3f upVector = new Vector3f(camera.upVector());
            this.center = camera.position().add(forward.x * DEPTH, forward.y * DEPTH, forward.z * DEPTH);
            this.right = new Vec3(-left.x, -left.y, -left.z);
            this.up = new Vec3(upVector.x, upVector.y, upVector.z);
            this.toCamera = new Vec3(-forward.x, -forward.y, -forward.z);
        }

        RenderTypeGraphMaterial add(TestContext ctx, Bare graph, int column) {
            RenderTypeGraphMaterial material = RenderTypeFactory.createMaterial(graph.graph());
            ctx.require("the graph compiles", material != null);
            materials.add(material);
            columns.add(column);
            return material;
        }

        Vec3 at(double r, double u) {
            return center.add(right.scale(r)).add(up.scale(u));
        }

        void emit(PoseStack.Pose pose, VertexConsumer vc, Vec3 c) {
            corner(pose, vc, c, -1, -1, 0, 0);
            corner(pose, vc, c, 1, -1, 1, 0);
            corner(pose, vc, c, 1, 1, 1, 1);
            corner(pose, vc, c, -1, 1, 0, 1);
        }

        private void corner(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, int sx, int sy, float u, float v) {
            Vec3 p = c.add(right.scale(sx * HALF)).add(up.scale(sy * HALF));
            vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                    .setColor(-1)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, (float) toCamera.x, (float) toCamera.y, (float) toCamera.z);
        }

        /** Mean colour of a 9×9 patch around the plane point (r, u). */
        float[] sample(NativeImage frame, double r, double u) {
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            Vec3 rel = at(r, u).subtract(camera.position());
            Vector4f clip = camera.getViewRotationProjectionMatrix(new Matrix4f())
                    .transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1));
            int cx = Math.round((clip.x / clip.w * 0.5f + 0.5f) * frame.getWidth());
            int cy = Math.round((0.5f - clip.y / clip.w * 0.5f) * frame.getHeight());
            float[] sum = new float[4];
            int n = 0;
            for (int y = cy - 4; y <= cy + 4; y++) {
                for (int x = cx - 4; x <= cx + 4; x++) {
                    if (x < 0 || y < 0 || x >= frame.getWidth() || y >= frame.getHeight()) continue;
                    float[] px = ShaderTestKit.rgba(frame, x, y);
                    for (int i = 0; i < 4; i++) sum[i] += px[i];
                    n++;
                }
            }
            for (int i = 0; i < 4; i++) sum[i] /= Math.max(1, n);
            return sum;
        }
    }
}
