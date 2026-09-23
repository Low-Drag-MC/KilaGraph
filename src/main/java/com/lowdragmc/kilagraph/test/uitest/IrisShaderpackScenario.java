package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings.BlendMode;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GeometrySpaces;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisShaderInjector;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceRegistry;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import com.lowdragmc.kilagraph.rendertype.nodes.input.PositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.AddNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.bare;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.exposedTint;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.settings;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.texture;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.vec3;

/**
 * KilaGraph quads drawn in the world (via {@link SubmitCustomGeometryEvent}) without a pack and under each
 * pack in {@code run/shaderpacks}, checked by pixel colour (by hue under a pack). Skipped without Iris.
 */
@LDLRegisterClient(name = "kg_iris", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class IrisShaderpackScenario implements UIScenario {

    private static final String[] PACKS = {"BSL_v10.1.3.zip", "ComplementaryReimagined_r5.8.1.zip"};

    /** On the superflat surface, looking due south. */
    private static final double FEET_X = 0.5, FEET_Y = -60, FEET_Z = 0.5;
    /** A known backdrop behind the quads. */
    private static final BlockPos WALL_FROM = new BlockPos(-8, -60, 9), WALL_TO = new BlockPos(8, -52, 9);
    /** Eye to quad plane, and a quad's half size, in blocks. */
    private static final double DEPTH = 4, HALF = 0.45;
    /** Quad positions on that plane, in blocks right of / above the view centre. */
    private static final double COL_1 = -3.2, COL_2 = -1.6, COL_3 = 0, COL_4 = 1.6, COL_5 = 3.2, TOP = 1.0, BOTTOM = -0.9;
    /** Where the displacing graph's quad is submitted, and how far down it moves it. */
    private static final double DISPLACED_UP = -0.6;
    private static final float DROP = 0.9f;
    /** Frames for a pack's TAA to settle. */
    private static final int SETTLE_FRAMES = 90;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Identifier CHECKER = Identifier.fromNamespaceAndPath("kilagraph", "uitest/iris_checker");
    private static final Identifier WHITE_CONCRETE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    /** Transparent white: left on the overlay unit by another draw, it paints an unbound material white. */
    private static final Identifier STALE = Identifier.fromNamespaceAndPath("kilagraph", "uitest/iris_stale");

    private static final float[] MAGENTA = {1, 0, 1}, CYAN = {0, 1, 1}, YELLOW = {1, 1, 0}, BLUE = {0, 0, 1},
            RED = {1, 0, 0}, GREEN = {0, 1, 0}, WHITE_RGB = {1, 1, 1};

    /** The scene under test, and the one being drawn (null when none). */
    @Nullable private static Scene built;
    @Nullable private static volatile Scene shown;
    private static boolean listening;

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(0).tags("kilagraph", "rendertype", "iris", "gpu")
                .defaultTimeoutMs(120_000).scenarioTimeoutMs(900_000);
    }

    @Override
    public void define(ScenarioBuilder s) {
        if (!IrisCompat.ENABLED) {
            s.step("Iris is not loaded (Vulkan, or the kill-switch) — nothing to test",
                    ctx -> ctx.log("Iris compatibility needs OpenGL with Iris on the classpath"));
            return;
        }
        s.step("start without a shaderpack", ctx -> {
                    Packs.remember();
                    Packs.use(null);
                })
                .teleportPlayer(FEET_X, FEET_Y, FEET_Z, 0, 0)
                .fill(WALL_FROM, WALL_TO, Blocks.CONCRETE.lightGray().defaultBlockState())
                .awaitClientChunk(WALL_FROM)
                .waitUntil("the player stands in place", ctx -> ctx.mc().player != null
                        && ctx.mc().player.position().distanceToSqr(FEET_X, FEET_Y, FEET_Z) < 0.01)
                .step("build the materials", IrisShaderpackScenario::build)
                // Sodium meshes the terrain, so check the frame rather than vanilla's section state.
                .waitUntil("the wall is on screen", IrisShaderpackScenario::wallVisible)
                .step("show the quads", ctx -> shown = scene(ctx))
                .frames(10)
                .step("without a pack, KilaGraph draws exact colours", IrisShaderpackScenario::checkVanilla)
                .step("screenshot", ctx -> ctx.screenshot("no_pack"));
        for (int i = 0; i < PACKS.length; i++) {
            String pack = PACKS[i];
            int index = i;
            s.step("enable " + pack, ctx -> enable(ctx, pack))
                    .waitUntil(pack + " is running with our surfaces injected", ctx ->
                            IrisCompat.isShaderPackInUse()
                                    && IrisShaderInjector.lastInjectedGeneration() >= IrisSurfaceRegistry.generation())
                    .frames(SETTLE_FRAMES)
                    .step("screenshot", ctx -> ctx.screenshot(tag(pack)))
                    .step(pack + ": every quad shows its graph's colour", ctx -> checkPack(ctx, pack))
                    .step(pack + ": create a graph while the pack runs", ctx -> addLate(ctx, index))
                    .waitUntil("the reload injects it", ctx ->
                            IrisShaderInjector.lastInjectedGeneration() >= IrisSurfaceRegistry.generation())
                    .frames(SETTLE_FRAMES)
                    .step(pack + ": the late graph shades too", ctx -> checkLate(ctx, pack))
                    .step("screenshot", ctx -> ctx.screenshot(tag(pack) + "_late"));
        }
        s.step("turn the shaderpack off", ctx -> Packs.use(null))
                .waitUntil("the pack is gone", ctx -> !IrisCompat.isShaderPackInUse())
                .frames(10)
                .step("after the pack, KilaGraph draws exact colours again", IrisShaderpackScenario::checkVanilla)
                .teardown("turn the shaderpack off, free the materials", ctx -> {
                    Scene scene = built;
                    built = null;
                    shown = null;
                    try {
                        Packs.use(null);
                    } catch (RuntimeException e) {
                        ctx.log("could not turn the shaderpack off: " + e);
                    } finally {
                        Packs.restoreConfig();
                    }
                    if (scene != null) scene.materials.forEach(RenderTypeGraphMaterial::close);
                    Minecraft.getInstance().getTextureManager().release(CHECKER);
                    Minecraft.getInstance().getTextureManager().release(STALE);
                });
    }

    // ---- phases ----------------------------------------------------------------------------------

    private static void build(TestContext ctx) {
        listen();
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(CHECKER, texture(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF));
        var stale = new NativeImage(16, 16, false);
        stale.fillRect(0, 0, 16, 16, 0x00FFFFFF);
        textures.register(STALE, new DynamicTexture(() -> "KilaGraph uitest stale units", stale));
        Scene scene = new Scene(Minecraft.getInstance().gameRenderer.mainCamera());
        built = scene; // before the first add: a failed compile below must still free what was built

        scene.magenta = scene.add(ctx, solid(MAGENTA), COL_1, TOP);
        scene.cyan = scene.add(ctx, solid(CYAN), COL_2, TOP);
        Bare tint = exposedTint();
        scene.tintA = scene.add(ctx, tint, COL_3, TOP);
        scene.tintB = scene.add(ctx, tint, COL_4, TOP);
        scene.tintA.setUniform("Tint", new Vector3f(YELLOW));
        scene.tintB.setUniform("Tint", new Vector3f(BLUE));
        scene.checker = scene.add(ctx, checker(), COL_5, TOP);
        scene.displaced = scene.add(ctx, displaced(), COL_1, DISPLACED_UP);
        scene.translucent = scene.add(ctx, translucent(), COL_4, BOTTOM);
        // Same pack program after ours: must stay red (the surface id is reset).
        scene.slots.add(new Slot(RenderTypes.entitySolid(WHITE_CONCRETE), COL_5, BOTTOM, 0xFFFF0000));
        ctx.check("the tint materials share one pipeline",
                scene.tintA.renderType().pipeline() == scene.tintB.renderType().pipeline());
    }

    /** The wall between the two rows reads as grey. */
    private static boolean wallVisible(TestContext ctx) {
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            float[] px = scene(ctx).sample(frame, COL_3, 0.1);
            float max = Math.max(px[0], Math.max(px[1], px[2])), min = Math.min(px[0], Math.min(px[1], px[2]));
            return max - min < 0.08f && max > 0.3f;
        }
    }

    private static void enable(TestContext ctx, String pack) {
        Path target = Packs.directory().resolve(pack);
        if (!Files.isRegularFile(target)) seedFromSerialRun(ctx, pack, target);
        ctx.require(pack + " is in " + Packs.directory(), Files.isRegularFile(target));
        scene(ctx).drawsAtPackStart = IrisSurfaceUniform.injectedDraws();
        Packs.use(pack);
    }

    /** A {@code runParTest} shard has its own game directory; copy the pack from {@code run/shaderpacks}. */
    private static void seedFromSerialRun(TestContext ctx, String pack, Path target) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        Path runs = gameDir.getParent();
        if (runs == null) return;
        Path source = runs.resolveSibling("run").resolve("shaderpacks").resolve(pack);
        if (!Files.isRegularFile(source)) return;
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            ctx.log("copied " + pack + " from " + source.getParent());
        } catch (IOException e) {
            ctx.log("could not copy " + source + ": " + e);
        }
    }

    private static void checkVanilla(TestContext ctx) {
        Scene scene = scene(ctx);
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            exact(ctx, "magenta constant", scene.sample(frame, COL_1, TOP), MAGENTA);
            exact(ctx, "cyan constant", scene.sample(frame, COL_2, TOP), CYAN);
            exact(ctx, "Tint = yellow", scene.sample(frame, COL_3, TOP), YELLOW);
            exact(ctx, "Tint = blue", scene.sample(frame, COL_4, TOP), BLUE);
            checkerQuadrants(ctx, scene, frame, true);
            exact(ctx, "displaced quad at its new place", scene.sample(frame, COL_1, DISPLACED_UP - DROP), GREEN);
            notHue(ctx, "displaced quad gone from its old place", scene.sample(frame, COL_1, DISPLACED_UP), GREEN);
            hue(ctx, "translucent red", scene.sample(frame, COL_4, BOTTOM), RED);
            hue(ctx, "vanilla entitySolid quad", scene.sample(frame, COL_5, BOTTOM), RED);
        }
    }

    private static void checkPack(TestContext ctx, String pack) {
        Scene scene = scene(ctx);
        ctx.check("Iris compiled " + pack + " without an error", Packs.error() == null, "no error", Packs.error());
        ctx.check("every graph has an injected surface", scene.materials.stream().allMatch(m -> m.irisSurfaceId() != 0));
        ctx.check("different graphs get different surfaces",
                scene.magenta.irisSurfaceId() != scene.cyan.irisSurfaceId());
        ctx.check("one graph's materials share a surface", scene.tintA.irisSurfaceId() == scene.tintB.irisSurfaceId());
        long draws = IrisSurfaceUniform.injectedDraws() - scene.drawsAtPackStart;
        ctx.check("our draws ran inside the pack's own program", draws > 0, "> 0", draws);
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            hue(ctx, pack + ": magenta constant", scene.sample(frame, COL_1, TOP), MAGENTA);
            hue(ctx, pack + ": cyan constant (a second surface in the same program)", scene.sample(frame, COL_2, TOP), CYAN);
            hue(ctx, pack + ": Tint = yellow", scene.sample(frame, COL_3, TOP), YELLOW);
            hue(ctx, pack + ": Tint = blue (same graph, own KG_Material)", scene.sample(frame, COL_4, TOP), BLUE);
            checkerQuadrants(ctx, scene, frame, false);
            hue(ctx, pack + ": displaced quad at its new place", scene.sample(frame, COL_1, DISPLACED_UP - DROP), GREEN);
            notHue(ctx, pack + ": displaced quad gone from its old place", scene.sample(frame, COL_1, DISPLACED_UP), GREEN);
            hue(ctx, pack + ": translucent red", scene.sample(frame, COL_4, BOTTOM), RED);
            hue(ctx, pack + ": vanilla entitySolid after ours keeps its own colour", scene.sample(frame, COL_5, BOTTOM), RED);
        }
    }

    /** A graph new to the registry, created while the pack runs. */
    private static void addLate(TestContext ctx, int index) {
        Scene scene = scene(ctx);
        int before = IrisSurfaceRegistry.generation();
        if (scene.late != null) {
            RenderType previous = scene.late.renderType();
            scene.slots.removeIf(slot -> slot.type() == previous);
        }
        scene.late = scene.add(ctx, solid(new float[]{1, 1, 0.01f * (index + 1)}), COL_3, BOTTOM);
        ctx.check("a new graph bumps the surface generation", IrisSurfaceRegistry.generation() > before,
                "> " + before, IrisSurfaceRegistry.generation());
    }

    private static void checkLate(TestContext ctx, String pack) {
        Scene scene = scene(ctx);
        ctx.check("the late graph has an injected surface", scene.late.irisSurfaceId() != 0);
        try (NativeImage frame = FrameCapture.grab(Minecraft.getInstance().gameRenderer.mainRenderTarget())) {
            hue(ctx, pack + ": graph created mid-session", scene.sample(frame, COL_3, BOTTOM), YELLOW);
            hue(ctx, pack + ": earlier graphs survive the reload", scene.sample(frame, COL_1, TOP), MAGENTA);
        }
    }

    private static void checkerQuadrants(TestContext ctx, Scene scene, NativeImage frame, boolean exact) {
        // v = 0 is the image's top row and the quad's bottom edge, so the image shows upside down.
        double q = HALF / 2;
        float[][] want = {RED, GREEN, BLUE, WHITE_RGB};
        double[][] at = {{COL_5 - q, TOP - q}, {COL_5 + q, TOP - q}, {COL_5 - q, TOP + q}, {COL_5 + q, TOP + q}};
        String[] names = {"bottom-left", "bottom-right", "top-left", "top-right"};
        for (int i = 0; i < 4; i++) {
            float[] px = scene.sample(frame, at[i][0], at[i][1]);
            String what = "texture " + names[i] + " quadrant";
            if (exact) exact(ctx, what, px, want[i]);
            else hue(ctx, what, px, want[i]);
        }
    }

    // ---- graphs ----------------------------------------------------------------------------------

    private static Bare solid(float[] rgb) {
        Bare b = bare();
        wire(b.graph(), b.color(), vec3(b, rgb[0], rgb[1], rgb[2]).getOutputsById().get("out"));
        return b;
    }

    private static Bare checker() {
        Bare b = bare();
        NodeModel source = addNode(b.graph(), TextureNode.class);
        setOption(source, "texture", RenderTypeGraphTypes.Sampler2DValue.defaultValue().withLocation(CHECKER.toString()));
        NodeModel sample = addNode(b.graph(), SamplerTexture2DNode.class);
        wire(b.graph(), sample.getInputsById().get("sampler"), source.getOutputsById().get("sampler"));
        wire(b.graph(), b.color(), sample.getOutputsById().get("color"));
        return b;
    }

    /** Green, moved down by {@link #DROP} in the vertex stage. */
    private static Bare displaced() {
        Bare b = solid(GREEN);
        NodeModel position = addNode(b.graph(), PositionNode.class);
        setOption(position, GeometrySpaces.OPTION, GeometrySpaces.OBJECT);
        NodeModel shift = addNode(b.graph(), AddNode.class);
        wire(b.graph(), shift.getInputsById().get("a"), position.getOutputsById().get("out"));
        wire(b.graph(), shift.getInputsById().get("b"), vec3(b, 0, -DROP, 0).getOutputsById().get("out"));
        wire(b.graph(), b.positionIn(), shift.getOutputsById().get("out"));
        return b;
    }

    private static Bare translucent() {
        Bare b = solid(RED).settings(s -> settings(s, BlendMode.TRANSLUCENT, s.depthTest(), false, false));
        wire(b.graph(), b.alphaIn(), vec3(b, 0.5f, 0, 0).getOutputsById().get("out"));
        return b;
    }

    // ---- the scene -------------------------------------------------------------------------------

    /** One quad: its render type, position on the quad plane and vertex colour. */
    private record Slot(RenderType type, double right, double up, int argb) {}

    private static final class Scene {
        final List<RenderTypeGraphMaterial> materials = new ArrayList<>();
        final List<Slot> slots = new CopyOnWriteArrayList<>();
        /** The quad plane in world space. */
        final Vec3 center, right, up, toCamera;
        RenderTypeGraphMaterial magenta, cyan, tintA, tintB, checker, displaced, translucent, late;
        long drawsAtPackStart;
        /** A vanilla entity draw whose albedo/overlay/lightmap bindings are {@link #STALE}. */
        final RenderType stale = RenderType.create("kilagraph:uitest/stale_units",
                RenderSetup.builder(RenderPipelines.ENTITY_SOLID)
                        .withTexture("Sampler0", STALE)
                        .withTexture("Sampler1", STALE)
                        .withTexture("Sampler2", STALE)
                        .createRenderSetup());

        Scene(Camera camera) {
            Vector3f forward = new Vector3f(camera.forwardVector());
            Vector3f left = new Vector3f(camera.leftVector());
            Vector3f upVector = new Vector3f(camera.upVector());
            this.center = camera.position().add(forward.x * DEPTH, forward.y * DEPTH, forward.z * DEPTH);
            this.right = new Vec3(-left.x, -left.y, -left.z);
            this.up = new Vec3(upVector.x, upVector.y, upVector.z);
            this.toCamera = new Vec3(-forward.x, -forward.y, -forward.z);
        }

        RenderTypeGraphMaterial add(TestContext ctx, Bare graph, double right, double up) {
            RenderTypeGraphMaterial material = RenderTypeFactory.createMaterial(graph.graph());
            ctx.require("the graph compiles", material != null);
            materials.add(material);
            slots.add(new Slot(material.renderType(), right, up, -1));
            return material;
        }

        Vec3 at(double r, double u) {
            return center.add(right.scale(r)).add(up.scale(u));
        }

        void emit(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, int argb) {
            corner(pose, vc, c, -1, -1, 0, 0, argb);
            corner(pose, vc, c, 1, -1, 1, 0, argb);
            corner(pose, vc, c, 1, 1, 1, 1, argb);
            corner(pose, vc, c, -1, 1, 0, 1, argb);
        }

        private void corner(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, int sx, int sy, float u, float v, int argb) {
            Vec3 p = c.add(right.scale(sx * HALF)).add(up.scale(sy * HALF));
            vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                    .setColor(argb)
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

    private static Scene scene(TestContext ctx) {
        Scene scene = built;
        ctx.require("the scene is built", scene != null);
        return scene;
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, IrisShaderpackScenario::submit);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Scene scene = shown;
        if (scene == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        // Hidden behind the wall.
        Vec3 hidden = scene.at(COL_3, 0).add(scene.toCamera.scale(-8)).subtract(camera);
        var collector = event.getSubmitNodeCollector();
        // One submit order per draw (within an order the draw order is hash-dependent), so a STALE draw
        // deterministically precedes every quad.
        int order = 1;
        for (Slot slot : scene.slots) {
            collector.order(order++).submitCustomGeometry(event.getPoseStack(), scene.stale,
                    (pose, vc) -> scene.emit(pose, vc, hidden, -1));
            Vec3 center = scene.at(slot.right(), slot.up()).subtract(camera);
            collector.order(order++).submitCustomGeometry(event.getPoseStack(), slot.type(),
                    (pose, vc) -> scene.emit(pose, vc, center, slot.argb()));
        }
    }

    // ---- colour checks ---------------------------------------------------------------------------

    private static final float EXACT_TOL = 4f / 255f;

    /** Our own program, no pack: the graph's value, within a few 8-bit steps. */
    private static void exact(TestContext ctx, String what, float[] px, float[] want) {
        boolean ok = Math.abs(px[0] - want[0]) <= EXACT_TOL && Math.abs(px[1] - want[1]) <= EXACT_TOL
                && Math.abs(px[2] - want[2]) <= EXACT_TOL;
        ctx.check(what, ok, rgb(want), rgb(px));
    }

    /** Under a pack: the graph's high channels clearly dominate its zero ones and are roughly balanced. */
    private static void hue(TestContext ctx, String what, float[] px, float[] want) {
        float minHigh = Float.MAX_VALUE, maxHigh = 0, maxLow = 0;
        for (int i = 0; i < 3; i++) {
            if (want[i] >= 0.5f) {
                minHigh = Math.min(minHigh, px[i]);
                maxHigh = Math.max(maxHigh, px[i]);
            } else {
                maxLow = Math.max(maxLow, px[i]);
            }
        }
        boolean ok = maxHigh >= 0.1f && minHigh >= 0.45f * maxHigh && minHigh - maxLow >= 0.08f
                && minHigh >= 1.5f * maxLow;
        ctx.check(what, ok, "hue " + rgb(want), rgb(px));
    }

    /** Not dominated by {@code want}'s channels (the quad is not there). */
    private static void notHue(TestContext ctx, String what, float[] px, float[] want) {
        float minHigh = Float.MAX_VALUE, maxLow = 0;
        for (int i = 0; i < 3; i++) {
            if (want[i] >= 0.5f) minHigh = Math.min(minHigh, px[i]);
            else maxLow = Math.max(maxLow, px[i]);
        }
        ctx.check(what, minHigh - maxLow < 0.08f, "not hue " + rgb(want), rgb(px));
    }

    private static String rgb(float[] c) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", c[0], c[1], c[2]);
    }

    private static String tag(String pack) {
        return pack.substring(0, pack.indexOf('_')).toLowerCase(Locale.ROOT);
    }

    // ---- Iris ------------------------------------------------------------------------------------

    /** Every Iris reference, isolated so the scenario class loads without Iris. */
    private static final class Packs {
        /** The pack selection from before the run. */
        @Nullable private static String userPack;
        private static boolean userEnabled;
        private static boolean remembered;

        static void remember() {
            var config = net.irisshaders.iris.Iris.getIrisConfig();
            userPack = config.getShaderPackName().orElse(null);
            userEnabled = config.areShadersEnabled();
            remembered = true;
        }

        /** Write the original selection back to the config (no reload, so the run stays pack-free). */
        static void restoreConfig() {
            if (!remembered) return;
            var config = net.irisshaders.iris.Iris.getIrisConfig();
            config.setShaderPackName(userPack);
            config.setShadersEnabled(userEnabled);
            try {
                config.save();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        /** Select {@code pack} (null = off), save (reload re-reads the file) and reload. */
        static void use(@Nullable String pack) {
            var config = net.irisshaders.iris.Iris.getIrisConfig();
            if (pack == null && !config.areShadersEnabled()) return;
            if (pack != null) config.setShaderPackName(pack);
            config.setShadersEnabled(pack != null);
            try {
                config.save();
                net.irisshaders.iris.Iris.reload();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        static java.nio.file.Path directory() {
            return net.irisshaders.iris.Iris.getShaderpacksDirectory();
        }

        @Nullable
        static String error() {
            return net.irisshaders.iris.Iris.getStoredError().map(Throwable::toString).orElse(null);
        }
    }
}
