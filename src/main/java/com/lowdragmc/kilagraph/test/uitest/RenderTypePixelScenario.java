package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings.BlendMode;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings.DepthTest;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GeometrySpaces;
import com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.CombineNode;
import com.lowdragmc.kilagraph.rendertype.nodes.channel.SplitNode;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaDiscardBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentColorTargetBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.input.PositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.InstanceDataNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.vertex.InstanceIdNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.matrix.Mat4TransformNode;
import com.lowdragmc.kilagraph.rendertype.runtime.KGInstanceBuffer;
import com.lowdragmc.kilagraph.rendertype.runtime.KGMesh;
import com.lowdragmc.kilagraph.rendertype.nodes.input.UVNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.VertexColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec3Node;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.BranchNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.CompareNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.AbsNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.ExpNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.InverseSqrtNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.LengthNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.LogNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.ModNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.NegateNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.NormalizeNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.advanced.ReciprocalNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.AddNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.DivideNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.MultiplyNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.PowNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.SqrtNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.basic.SubtractNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation.InverseLerpNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation.LerpNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.interpolation.SmoothstepNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.ClampNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.FractNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.MaxNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.MinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.OneMinusNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.RemapNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.range.SaturateNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.round.CeilNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.round.FloorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.round.RoundNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.round.StepNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.ArccosineNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.ArcsineNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.ArctangentNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.Atan2Node;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.CosNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.DegreesToRadiansNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.RadiansToDegreesNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.SinNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.SinhNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.TanNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.trigonometry.TanhNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.CrossNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.DistanceNode;
import com.lowdragmc.kilagraph.rendertype.nodes.math.vector.DotNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.SceneColorNode;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.ScreenPositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.SamplerTexture2DNode;
import com.lowdragmc.kilagraph.rendertype.nodes.texture.TextureNode;
import com.lowdragmc.kilagraph.rendertype.nodes.transform.CameraNode;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraph.rendertype.runtime.SceneCaptureManager;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture;
import com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.Bare;
import com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.Canvas;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setOption;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.bare;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.exposedTint;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.fmt;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.quad;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.rgba;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.settings;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.texture;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.vec3;
import static com.lowdragmc.kilagraph.test.uitest.ShaderTestKit.withTarget;

/**
 * Draws small graphs into a 32×32 target through the real material path and compares pixels with values
 * computed on the CPU (tolerance: a few 8-bit steps).
 */
@LDLRegisterClient(name = "kg_rt_pixels", group = "kilagraph", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class RenderTypePixelScenario implements UIScenario {

    private static final float TOL = 3f / 255f;
    private static final int CLEAR = 0xFF333333; // (0.2, 0.2, 0.2, 1)
    private static final String CANVAS = "canvas";
    private static final int C = Canvas.SIZE / 2;

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(0).tags("kilagraph", "rendertype", "gpu").guiScale(2)
                .scenarioTimeoutMs(180_000);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("make the canvas", ctx -> ctx.put(CANVAS, new Canvas()))
                .step("a constant reaches the target exactly", RenderTypePixelScenario::constantColor)
                .step("math nodes compute the right values", RenderTypePixelScenario::mathNodes)
                .step("logic selects the right branch", RenderTypePixelScenario::logic)
                .step("an exposed variable is a per-material uniform", RenderTypePixelScenario::materialUniform)
                .step("two materials of one graph are not batched together", RenderTypePixelScenario::noCrossMaterialBatching)
                .step("a value set during the submit reaches that draw", RenderTypePixelScenario::uniformSetDuringSubmit)
                .step("textures sample the right texels, and can be swapped per material", RenderTypePixelScenario::textureSampling)
                .step("uv runs left-to-right and bottom-to-top", RenderTypePixelScenario::uvDirection)
                .step("blend modes combine with what is already there", RenderTypePixelScenario::blending)
                .step("the depth test keeps what is nearer (26.2 is reversed-Z)", RenderTypePixelScenario::depthTest)
                .step("a depth offset pulls a coplanar surface forward", RenderTypePixelScenario::depthOffset)
                .step("a float colour target keeps values above 1", RenderTypePixelScenario::floatColorTarget)
                .step("colour targets are written in the same draw (MRT)", RenderTypePixelScenario::colorTargets)
                .step("colour targets blend like the main target", RenderTypePixelScenario::colorTargetBlend)
                .step("alpha discard drops what is below the cutoff", RenderTypePixelScenario::alphaDiscard)
                .step("camera planes and eye depth are reconstructed from the projection", RenderTypePixelScenario::depthReconstruction)
                .step("the vertex stage moves geometry", RenderTypePixelScenario::vertexStage)
                .step("custom vertex layouts deliver their attributes", RenderTypePixelScenario::vertexLayouts)
                .step("an instanced draw places and colours each instance", RenderTypePixelScenario::instancedData)
                .step("instance transforms and the instance id", RenderTypePixelScenario::instanceTransformAndId)
                .step("Scene Color samples the captured frame", RenderTypePixelScenario::sceneColor)
                .teardown("free the canvas", ctx -> {
                    Canvas canvas = ctx.get(CANVAS, null);
                    if (canvas != null) canvas.close();
                });
    }

    // ---- checks ----------------------------------------------------------------------------------

    private static void constantColor(TestContext ctx) {
        Bare b = bare();
        wire(b.graph(), b.color(), vec3(b, 0.2f, 0.4f, 0.6f).getOutputsById().get("out"));
        expect(ctx, "vec3(0.2, 0.4, 0.6) as base colour", drawCenter(ctx, b), 0.2f, 0.4f, 0.6f);
    }

    private static void mathNodes(TestContext ctx) {
        float sqrt2 = (float) Math.sqrt(2);
        scalar(ctx, AddNode.class, 0.75f, "a", 0.25f, "b", 0.5f);
        scalar(ctx, SubtractNode.class, 0.25f, "a", 0.75f, "b", 0.5f);
        scalar(ctx, MultiplyNode.class, 0.3f, "a", 0.5f, "b", 0.6f);
        scalar(ctx, DivideNode.class, 0.25f, "a", 0.5f, "b", 2f);
        scalar(ctx, PowNode.class, 0.125f, "a", 0.5f, "b", 3f);
        scalar(ctx, ModNode.class, 0.25f, "a", 0.75f, "b", 0.5f);
        scalar(ctx, MinNode.class, 0.3f, "a", 0.3f, "b", 0.6f);
        scalar(ctx, MaxNode.class, 0.6f, "a", 0.3f, "b", 0.6f);
        // Operand order is the classic codegen bug: step(edge, x) and atan(y, x) are not symmetric.
        scalar(ctx, StepNode.class, 1f, "a", 0.5f, "b", 0.7f);
        scalar(ctx, StepNode.class, 0f, "a", 0.5f, "b", 0.3f);
        scalar(ctx, Atan2Node.class, (float) Math.atan2(0.5, 1.0), "a", 0.5f, "b", 1f);
        scalar(ctx, LerpNode.class, 0.35f, "a", 0.2f, "b", 0.8f, "t", 0.25f);
        scalar(ctx, ClampNode.class, 0.4f, "value", 1.5f, "min", 0f, "max", 0.4f);
        scalar(ctx, SmoothstepNode.class, 0.15625f, "edge0", 0f, "edge1", 1f, "x", 0.25f);
        scalar(ctx, InverseLerpNode.class, 0.75f, "a", 0.2f, "b", 0.6f, "t", 0.5f);
        scalar(ctx, RemapNode.class, 0.3f, "in", 0.5f, "inMin", 0f, "inMax", 1f, "outMin", 0.2f, "outMax", 0.4f);
        scalar(ctx, OneMinusNode.class, 0.7f, "a", 0.3f);
        scalar(ctx, SaturateNode.class, 1f, "a", 1.5f);
        scalar(ctx, AbsNode.class, 0.4f, "a", -0.4f);
        scalar(ctx, NegateNode.class, 0.4f, "a", -0.4f);
        scalar(ctx, FractNode.class, 0.25f, "a", 1.25f);
        scalar(ctx, FloorNode.class, 0f, "a", 0.7f);
        scalar(ctx, CeilNode.class, 1f, "a", 0.3f);
        scalar(ctx, RoundNode.class, 1f, "a", 0.6f);
        scalar(ctx, RoundNode.class, 0f, "a", 0.4f);
        scalar(ctx, SqrtNode.class, 0.5f, "a", 0.25f);
        scalar(ctx, ReciprocalNode.class, 0.25f, "a", 4f);
        scalar(ctx, InverseSqrtNode.class, 0.5f, "a", 4f);
        scalar(ctx, ExpNode.class, (float) Math.exp(-1), "a", -1f);
        scalar(ctx, LogNode.class, (float) Math.log(2), "a", 2f);
        scalar(ctx, SinNode.class, (float) Math.sin(0.5), "a", 0.5f);
        scalar(ctx, CosNode.class, (float) Math.cos(0.5), "a", 0.5f);
        scalar(ctx, TanNode.class, (float) Math.tan(0.5), "a", 0.5f);
        scalar(ctx, ArcsineNode.class, (float) Math.asin(0.5), "a", 0.5f);
        scalar(ctx, ArccosineNode.class, (float) Math.acos(0.8), "a", 0.8f);
        scalar(ctx, ArctangentNode.class, (float) Math.atan(0.5), "a", 0.5f);
        scalar(ctx, SinhNode.class, (float) Math.sinh(0.5), "a", 0.5f);
        scalar(ctx, TanhNode.class, (float) Math.tanh(0.5), "a", 0.5f);
        scalar(ctx, DegreesToRadiansNode.class, (float) Math.toRadians(30), "a", 30f);
        scalar(ctx, RadiansToDegreesNode.class, (float) Math.toDegrees(0.01), "a", 0.01f);
        scalar(ctx, DistanceNode.class, 0.5f, "a", 0.2f, "b", 0.7f);
        scalar(ctx, LengthNode.class, 0.5f, "v", new Vector3f(0.3f, 0.4f, 0f));
        scalar(ctx, DotNode.class, 0.5f, "a", new Vector3f(1f, 0f, 0f), "b", new Vector3f(0.5f, 0.25f, 0f));
        scalar(ctx, SqrtNode.class, 1f / sqrt2, "a", 0.5f);

        // Vector results, one channel each.
        vector(ctx, CrossNode.class, new float[]{0, 0, 1}, "a", new Vector3f(1, 0, 0), "b", new Vector3f(0, 1, 0));
        vector(ctx, NormalizeNode.class, new float[]{0.6f, 0, 0.8f}, "v", new Vector3f(3, 0, 4));
    }

    private static void logic(TestContext ctx) {
        for (var op : new String[][]{{"less", "0.9"}, {"greater", "0.1"}, {"lessEqual", "0.9"}, {"equal", "0.1"}}) {
            Bare b = bare();
            NodeModel compare = addNode(b.graph(), CompareNode.class);
            setOption(compare, "op", op[0]);
            setInputConstant(compare, "a", 0.2f);
            setInputConstant(compare, "b", 0.5f);
            NodeModel branch = addNode(b.graph(), BranchNode.class);
            setInputConstant(branch, "t", 0.9f);
            setInputConstant(branch, "f", 0.1f);
            wire(b.graph(), branch.getInputsById().get("predicate"), compare.getOutputsById().get("out"));
            wire(b.graph(), b.color(), branch.getOutputsById().get("out"));
            float v = Float.parseFloat(op[1]);
            expect(ctx, "0.2 " + op[0] + " 0.5 ? 0.9 : 0.1", drawCenter(ctx, b), v, v, v);
        }
    }

    private static void materialUniform(TestContext ctx) {
        Bare b = exposedTint();
        RenderTypeGraphMaterial material = material(ctx, b);
        try {
            expect(ctx, "the variable's default reaches the shader", draw(ctx, material, C, C), 1, 0, 0);
            ctx.check("setUniform finds the variable by its display name",
                    material.setUniform("Tint", new Vector3f(0, 1, 0)));
            expect(ctx, "setUniform changes the drawn colour", draw(ctx, material, C, C), 0, 1, 0);
        } finally {
            material.close();
        }
    }

    /** Two materials of one graph in one dispatch keep their own uniforms (guards vanilla's merge of equal
     *  prepared render types, which never fires today because {@code ScissorState} has no equals). */
    private static void noCrossMaterialBatching(TestContext ctx) {
        Bare graph = exposedTint();
        try (RenderTypeGraphMaterial red = material(ctx, graph);
             RenderTypeGraphMaterial blue = material(ctx, graph)) {
            ctx.check("both materials share one pipeline",
                    red.renderType().pipeline() == blue.renderType().pipeline());
            red.setUniform("Tint", new Vector3f(1, 0, 0));
            blue.setUniform("Tint", new Vector3f(0, 0, 1));
            var pose = new PoseStack();
            try (NativeImage img = dispatch(ctx, storage -> {
                // A, B, A: the third submit is non-consecutive, so it reaches the reorder-merge lookup too.
                storage.submitCustomGeometry(pose, red.renderType(), (p, vc) -> quad(vc, -1, -1, -0.5f, 1, 0.5f, -1));
                storage.submitCustomGeometry(pose, blue.renderType(), (p, vc) -> quad(vc, 0, -1, 1, 1, 0.5f, -1));
                storage.submitCustomGeometry(pose, red.renderType(), (p, vc) -> quad(vc, -0.5f, -1, 0, 1, 0.5f, -1));
            })) {
                expect(ctx, "left quarter drawn with the red material", rgba(img, C / 4, C), 1, 0, 0);
                expect(ctx, "second quarter (non-consecutive red submit) is red", rgba(img, C - 3, C), 1, 0, 0);
                expect(ctx, "right half drawn with the blue material", rgba(img, C + C / 2, C), 0, 0, 1);
            }
        }
    }

    /** A value set in the geometry callback (which runs after {@code prepare()}) reaches that same draw. */
    private static void uniformSetDuringSubmit(TestContext ctx) {
        try (RenderTypeGraphMaterial material = material(ctx, exposedTint())) {
            material.setUniform("Tint", new Vector3f(1, 0, 0));
            try (NativeImage img = dispatch(ctx, storage -> storage.submitCustomGeometry(new PoseStack(),
                    material.renderType(), (p, vc) -> {
                        material.setUniform("Tint", new Vector3f(0, 1, 0));
                        quad(vc, -1, -1, 1, 1, 0.5f, -1);
                    }))) {
                expect(ctx, "the value set in the geometry callback is the one drawn", rgba(img, C, C), 0, 1, 0);
            }
        }
    }

    private static void textureSampling(TestContext ctx) {
        // 2x2 texture, image row 0 at the top: red green / blue white.
        Identifier checker = Identifier.fromNamespaceAndPath("kilagraph", "uitest/checker");
        Identifier magenta = Identifier.fromNamespaceAndPath("kilagraph", "uitest/magenta");
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(checker, texture(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF));
        textures.register(magenta, texture(0xFFFF00FF, 0xFFFF00FF, 0xFFFF00FF, 0xFFFF00FF));
        try {
            Bare b = bare();
            NodeModel source = addNode(b.graph(), TextureNode.class);
            setOption(source, "texture", RenderTypeGraphTypes.Sampler2DValue.defaultValue().withLocation(checker.toString()));
            NodeModel sample = addNode(b.graph(), SamplerTexture2DNode.class);
            wire(b.graph(), sample.getInputsById().get("sampler"), source.getOutputsById().get("sampler"));
            wire(b.graph(), b.color(), sample.getOutputsById().get("color"));
            RenderTypeGraphMaterial material = material(ctx, b);
            try {
                // v = 0 is the image's top row but the quad's bottom edge, so the image shows upside down.
                try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1))) {
                    int lo = Canvas.SIZE / 4, hi = Canvas.SIZE * 3 / 4;
                    expect(ctx, "uv (0.25, 0.25) samples the image's top-left texel", rgba(img, lo, hi), 1, 0, 0);
                    expect(ctx, "uv (0.75, 0.25) samples the image's top-right texel", rgba(img, hi, hi), 0, 1, 0);
                    expect(ctx, "uv (0.25, 0.75) samples the image's bottom-left texel", rgba(img, lo, lo), 0, 0, 1);
                    expect(ctx, "uv (0.75, 0.75) samples the image's bottom-right texel", rgba(img, hi, lo), 1, 1, 1);
                }
                String sampler = material.managedSamplerNames().iterator().next();
                ctx.check("setTexture swaps the bound texture", material.setTexture(sampler, magenta));
                expect(ctx, "the swapped texture is what draws", draw(ctx, material, C, C), 1, 0, 1);
                var view = textures.getTexture(checker).getTextureView();
                ctx.check("setTextureView binds a raw view",
                        material.setTextureView(sampler, view, RenderSystem.getSamplerCache().getClampToEdge(
                                com.mojang.blaze3d.textures.FilterMode.NEAREST)));
                try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1))) {
                    expect(ctx, "the raw view wins over the Identifier binding",
                            rgba(img, Canvas.SIZE / 4, Canvas.SIZE * 3 / 4), 1, 0, 0);
                }
            } finally {
                material.close();
            }
        } finally {
            textures.release(checker);
            textures.release(magenta);
        }
    }

    private static void uvDirection(TestContext ctx) {
        Bare b = bare();
        wire(b.graph(), b.color(), addNode(b.graph(), UVNode.class).getOutputsById().get("out"));
        RenderTypeGraphMaterial material = material(ctx, b);
        try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1))) {
            float lo = 0.5f / Canvas.SIZE, hi = 1 - lo;
            int last = Canvas.SIZE - 1;
            expect(ctx, "bottom-left pixel is uv (0, 0)", rgba(img, 0, last), lo, lo, 0);
            expect(ctx, "bottom-right pixel is uv (1, 0)", rgba(img, last, last), hi, lo, 0);
            expect(ctx, "top-left pixel is uv (0, 1)", rgba(img, 0, 0), lo, hi, 0);
            expect(ctx, "top-right pixel is uv (1, 1)", rgba(img, last, 0), hi, hi, 0);
        } finally {
            material.close();
        }
    }

    private static void blending(TestContext ctx) {
        // dst = (0.2, 0.2, 0.2), src = (0.5, 0.25, 0.0) with alpha 0.5
        float[] d = {0.2f, 0.2f, 0.2f};
        float[] c = {0.5f, 0.25f, 0f};
        float a = 0.5f;
        blend(ctx, BlendMode.OPAQUE, c[0], c[1], c[2]);
        blend(ctx, BlendMode.TRANSLUCENT, c[0] * a + d[0] * (1 - a), c[1] * a + d[1] * (1 - a), c[2] * a + d[2] * (1 - a));
        blend(ctx, BlendMode.ADDITIVE, c[0] + d[0], c[1] + d[1], c[2] + d[2]);
        blend(ctx, BlendMode.LIGHTNING, c[0] * a + d[0], c[1] * a + d[1], c[2] * a + d[2]);
        blend(ctx, BlendMode.TRANSLUCENT_PREMULTIPLIED_ALPHA, c[0] + d[0] * (1 - a), c[1] + d[1] * (1 - a), c[2] + d[2] * (1 - a));
        blend(ctx, BlendMode.INVERT, (1 - d[0]) * c[0] + d[0] * (1 - c[0]), (1 - d[1]) * c[1] + d[1] * (1 - c[1]),
                (1 - d[2]) * c[2] + d[2] * (1 - c[2]));
        blend(ctx, BlendMode.MULTIPLY, c[0] * d[0], c[1] * d[1], c[2] * d[2]);
        blend(ctx, BlendMode.SUBTRACT, Math.max(0, d[0] - c[0]), Math.max(0, d[1] - c[1]), Math.max(0, d[2] - c[2]));
        blend(ctx, BlendMode.MIN, Math.min(c[0], d[0]), Math.min(c[1], d[1]), Math.min(c[2], d[2]));
        blend(ctx, BlendMode.MAX, Math.max(c[0], d[0]), Math.max(c[1], d[1]), Math.max(c[2], d[2]));
    }

    /** A coplanar green quad under a strict "nearer" test shows over red only when offset toward the camera. */
    private static void depthOffset(TestContext ctx) {
        for (float units : new float[]{0, 1000, -1000}) {
            Bare green = solid(0, 1, 0, DepthTest.LESS, true);
            green.settings(s -> withTarget(s, s.colorFormat(), 0, units));
            try (RenderTypeGraphMaterial first = material(ctx, solid(1, 0, 0, DepthTest.LEQUAL, true));
                 RenderTypeGraphMaterial second = material(ctx, green)) {
                Canvas canvas = canvas(ctx);
                canvas.clear(CLEAR);
                canvas.draw(first, vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1));
                canvas.draw(second, vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1));
                try (NativeImage img = canvas.read()) {
                    float g = units > 0 ? 1 : 0;
                    expect(ctx, "depth offset units " + units, rgba(img, C, C), 1 - g, g, 0);
                }
            }
        }
    }

    /** A material with an RGBA16F target keeps values above 1: write 2.0 there, sample it back at a quarter. */
    private static void floatColorTarget(TestContext ctx) {
        Bare bright = bare();
        bright.settings(s -> withTarget(s, RenderTypeGraph.Settings.ColorFormat.RGBA16F, 0, 0));
        wire(bright.graph(), bright.color(), vec3(bright, 2f, 1.5f, 0.25f).getOutputsById().get("out"));
        try (Canvas hdr = new Canvas(GpuFormat.RGBA16_FLOAT);
             RenderTypeGraphMaterial write = material(ctx, bright)) {
            hdr.clear(0);
            hdr.draw(write, vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1));
            expect(ctx, "2.0 survives an RGBA16F target", sampleQuarter(ctx, hdr.target.getColorTextureView()),
                    0.5f, 0.375f, 0.0625f);
        }
    }

    /** Colour targets 1 and 3 (2 left empty) fill their textures in the same draw as the main target. */
    private static void colorTargets(TestContext ctx) {
        Bare b = bare();
        wire(b.graph(), b.color(), vec3(b, 1, 0, 0).getOutputsById().get("out"));
        colorTarget(b, 1, "RGBA8", new Vector4f(0, 1, 0, 1));
        colorTarget(b, 3, "RGBA16F", new Vector4f(2, 1, 0.5f, 1));
        try (RenderTypeGraphMaterial material = material(ctx, b);
             Canvas second = new Canvas();
             Canvas hdr = new Canvas(GpuFormat.RGBA16_FLOAT)) {
            ctx.check("the material has both targets", material.colorTargets().size() == 2);
            expect(ctx, "none bound: the main target still draws", draw(ctx, material, C, C), 1, 0, 0);
            material.setColorTarget(1, second.target.getColorTextureView());
            material.setColorTarget(3, hdr.target.getColorTextureView());
            second.clear(CLEAR);
            hdr.clear(0);
            expect(ctx, "the main target", draw(ctx, material, C, C), 1, 0, 0);
            try (NativeImage img = second.read()) {
                expect(ctx, "target 1", rgba(img, C, C), 0, 1, 0);
            }
            expect(ctx, "target 3 keeps 2.0", sampleQuarter(ctx, hdr.target.getColorTextureView()), 0.5f, 0.25f, 0.125f);
            boolean refused = false;
            try {
                material.setColorTarget(1, hdr.target.getColorTextureView());
            } catch (IllegalArgumentException e) {
                refused = true;
            }
            ctx.check("a texture of another format is refused", refused);
            var small = new TextureTarget("KilaGraph small target", Canvas.SIZE / 2, Canvas.SIZE / 2, false,
                    GpuFormat.RGBA8_UNORM);
            try {
                material.setColorTarget(1, small.getColorTextureView());
                expect(ctx, "a target of another size is skipped", draw(ctx, material, C, C), 1, 0, 0);
            } finally {
                small.destroyBuffers();
            }
        }
    }

    /** Blend ADDITIVE over (0.2, 0.2, 0.2) adds on the colour target too; the next opaque draw overwrites again. */
    private static void colorTargetBlend(TestContext ctx) {
        Bare b = bare().settings(s -> settings(s, BlendMode.ADDITIVE, s.depthTest(), s.depthWrite(), false));
        wire(b.graph(), b.color(), vec3(b, 0.5f, 0, 0).getOutputsById().get("out"));
        colorTarget(b, 1, "RGBA8", new Vector4f(0, 0.5f, 0, 1));
        try (RenderTypeGraphMaterial material = material(ctx, b);
             Canvas second = new Canvas()) {
            material.setColorTarget(1, second.target.getColorTextureView());
            second.clear(CLEAR);
            expect(ctx, "the main target adds", draw(ctx, material, C, C), 0.7f, 0.2f, 0.2f);
            try (NativeImage img = second.read()) {
                expect(ctx, "the colour target adds", rgba(img, C, C), 0.2f, 0.7f, 0.2f);
            }
        }
        Bare opaque = bare();
        wire(opaque.graph(), opaque.color(), vec3(opaque, 0.5f, 0, 0).getOutputsById().get("out"));
        expect(ctx, "blending is off again for the next opaque draw", drawCenter(ctx, opaque), 0.5f, 0, 0);
    }

    private static void colorTarget(Bare b, int location, String format, Vector4f value) {
        NodeModel block = addBlock(b.graph(), b.graph().getFragmentStageModel(), FragmentColorTargetBlock.class);
        setOption(block, FragmentColorTargetBlock.OPTION_TARGET, String.valueOf(location));
        setOption(block, FragmentColorTargetBlock.OPTION_FORMAT, format);
        setInputConstant(block, "color", value);
    }

    /** The centre of {@code view} scaled by 1/4 (so float values above 1 fit RGBA8), read through a sampling material. */
    private static float[] sampleQuarter(TestContext ctx, GpuTextureView view) {
        Bare reader = bare();
        NodeModel source = addNode(reader.graph(), TextureNode.class);
        setOption(source, "texture", RenderTypeGraphTypes.Sampler2DValue.defaultValue()
                .withLocation("kilagraph:textures/misc/white.png"));
        NodeModel sample = addNode(reader.graph(), SamplerTexture2DNode.class);
        wire(reader.graph(), sample.getInputsById().get("sampler"), source.getOutputsById().get("sampler"));
        NodeModel quarter = addNode(reader.graph(), MultiplyNode.class);
        wire(reader.graph(), quarter.getInputsById().get("a"), sample.getOutputsById().get("color"));
        setInputConstant(quarter, "b", 0.25f);
        wire(reader.graph(), reader.color(), quarter.getOutputsById().get("out"));

        try (RenderTypeGraphMaterial read = material(ctx, reader)) {
            ctx.check("the float texture binds", read.setTextureView(read.managedSamplerNames().iterator().next(),
                    view, RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST)));
            return draw(ctx, read, C, C);
        }
    }

    private static void blend(TestContext ctx, BlendMode mode, float r, float g, float b) {
        Bare graph = bare().settings(s -> settings(s, mode, s.depthTest(), s.depthWrite(), false));
        wire(graph.graph(), graph.color(), vec3(graph, 0.5f, 0.25f, 0f).getOutputsById().get("out"));
        NodeModel half = addNode(graph.graph(), Vec3Node.class);
        setInputConstant(half, "x", 0.5f);
        wire(graph.graph(), graph.alphaIn(), half.getOutputsById().get("out"));
        expect(ctx, mode + " over (0.2, 0.2, 0.2)", drawCenter(ctx, graph), r, g, b);
    }

    private static void depthTest(TestContext ctx) {
        // Identity projection, reversed-Z: z = 0.8 is nearer than z = 0.3.
        float near = 0.8f, far = 0.3f;
        depth(ctx, DepthTest.LEQUAL, DepthTest.LEQUAL, true, near, far, 1, 0, "nearer drawn first survives a farther one");
        depth(ctx, DepthTest.LEQUAL, DepthTest.LEQUAL, true, far, near, 0, 1, "nearer drawn second covers a farther one");
        depth(ctx, DepthTest.LESS, DepthTest.LESS, true, near, far, 1, 0, "LESS keeps the nearer one too");
        depth(ctx, DepthTest.LESS, DepthTest.LESS, true, 0.5f, 0.5f, 1, 0, "LESS rejects an equal depth");
        depth(ctx, DepthTest.LEQUAL, DepthTest.LEQUAL, false, near, far, 0, 1, "without depth writes the later draw wins");
        depth(ctx, DepthTest.ALWAYS, DepthTest.ALWAYS, true, near, far, 0, 1, "ALWAYS ignores depth");
        // EQUAL needs something already in the depth buffer to be equal to.
        depth(ctx, DepthTest.LEQUAL, DepthTest.EQUAL, true, 0.5f, 0.5f, 0, 1, "EQUAL passes at the same depth");
        depth(ctx, DepthTest.LEQUAL, DepthTest.EQUAL, true, 0.5f, 0.4f, 1, 0, "EQUAL fails at a different depth");
    }

    /** Red at {@code z1} with {@code firstTest}, then green at {@code z2} with {@code secondTest}; which shows? */
    private static void depth(TestContext ctx, DepthTest firstTest, DepthTest secondTest, boolean write,
                              float z1, float z2, float red, float green, String what) {
        try (RenderTypeGraphMaterial first = material(ctx, solid(1, 0, 0, firstTest, write));
             RenderTypeGraphMaterial second = material(ctx, solid(0, 1, 0, secondTest, write))) {
            Canvas canvas = canvas(ctx);
            canvas.clear(CLEAR);
            canvas.draw(first, vc -> quad(vc, -1, -1, 1, 1, z1, -1));
            canvas.draw(second, vc -> quad(vc, -1, -1, 1, 1, z2, -1));
            try (NativeImage img = canvas.read()) {
                expect(ctx, secondTest + (write ? "" : " (no write)") + ": " + what, rgba(img, C, C), red, green, 0);
            }
        }
    }

    private static void alphaDiscard(TestContext ctx) {
        for (float alpha : new float[]{0.25f, 0.75f}) {
            Bare b = bare();
            wire(b.graph(), b.color(), vec3(b, 1, 1, 1).getOutputsById().get("out"));
            NodeModel alphaValue = addNode(b.graph(), Vec3Node.class);
            setInputConstant(alphaValue, "x", alpha);
            wire(b.graph(), b.alphaIn(), alphaValue.getOutputsById().get("out"));
            NodeModel discard = com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock(
                    b.graph(), b.graph().getFragmentStageModel(), FragmentAlphaDiscardBlock.class);
            setInputConstant(discard, "cutoff", 0.5f);
            float v = alpha < 0.5f ? 0.2f : 1f;
            expect(ctx, "alpha " + alpha + " against cutoff 0.5", drawCenter(ctx, b), v, v, v);
        }
    }

    /** Camera near/far and eye depth (near 0.5, far 64, quad at 8 → (0.5, 0.64, 0.8)) under a reversed-Z and a
     *  conventional projection; Z Buffer Sign -1 and +1 respectively. */
    private static void depthReconstruction(TestContext ctx) {
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        float fov = (float) Math.toRadians(90);
        // Reversed-Z is near and far swapped, as Projection#getMatrix does.
        Matrix4f reversed = new Matrix4f().setPerspective(fov, 1, 64f, 0.5f, zeroToOne);
        Matrix4f conventional = new Matrix4f().setPerspective(fov, 1, 0.5f, 64f, zeroToOne);
        Matrix4f orthoReversed = new Matrix4f().setOrtho(-16, 16, -16, 16, 64f, 0.5f, zeroToOne);
        Matrix4f ortho = new Matrix4f().setOrtho(-16, 16, -16, 16, 0.5f, 64f, zeroToOne);
        var cameras = new LinkedHashMap<String, Matrix4f>();
        cameras.put("reversed-Z", reversed);
        cameras.put("conventional", conventional);
        cameras.put("ortho reversed-Z", orthoReversed);
        cameras.put("ortho", ortho);
        for (var entry : cameras.entrySet()) {
            Bare b = bare();
            NodeModel camera = addNode(b.graph(), CameraNode.class);
            NodeModel combine = addNode(b.graph(), CombineNode.class);
            wire(b.graph(), combine.getInputsById().get("r"), camera.getOutputsById().get("NearPlane"));
            wire(b.graph(), combine.getInputsById().get("g"), divided(b, camera.getOutputsById().get("FarPlane"), 100));
            NodeModel screen = addNode(b.graph(), ScreenPositionNode.class);
            setOption(screen, "mode", "raw");
            NodeModel split = addNode(b.graph(), SplitNode.class);
            wire(b.graph(), split.getInputsById().get("in"), screen.getOutputsById().get("out"));
            wire(b.graph(), combine.getInputsById().get("b"), divided(b, split.getOutputsById().get("a"), 10));
            wire(b.graph(), b.color(), combine.getOutputsById().get("rgb"));
            RenderTypeGraphMaterial material = material(ctx, b);
            try (NativeImage img = drawImage(ctx, material, entry.getValue(), vc -> quad(vc, -16, -16, 16, 16, -8, -1))) {
                expect(ctx, entry.getKey() + ": (near, far/100, eye/10)", rgba(img, C, C), 0.5f, 0.64f, 0.8f);
            } finally {
                material.close();
            }

            Bare sign = bare();
            NodeModel cam = addNode(sign.graph(), CameraNode.class);
            // (sign + 1) / 2: 0 for reversed, 1 for conventional
            NodeModel add = addNode(sign.graph(), AddNode.class);
            wire(sign.graph(), add.getInputsById().get("a"), cam.getOutputsById().get("ZBufferSign"));
            setInputConstant(add, "b", 1f);
            wire(sign.graph(), sign.color(), divided(sign, add.getOutputsById().get("out"), 2));
            RenderTypeGraphMaterial signMaterial = material(ctx, sign);
            float expected = entry.getKey().contains("reversed") ? 0 : 1;
            try (NativeImage img = drawImage(ctx, signMaterial, entry.getValue(), vc -> quad(vc, -16, -16, 16, 16, -8, -1))) {
                expect(ctx, entry.getKey() + ": Z Buffer Sign", rgba(img, C, C), expected, expected, expected);
            } finally {
                signMaterial.close();
            }
        }
    }

    /** A quad over the left half, pushed right by a whole half-canvas in the vertex stage. */
    private static void vertexStage(TestContext ctx) {
        Bare b = bare();
        wire(b.graph(), b.color(), vec3(b, 1, 1, 1).getOutputsById().get("out"));
        NodeModel position = addNode(b.graph(), PositionNode.class);
        setOption(position, GeometrySpaces.OPTION, GeometrySpaces.OBJECT);
        NodeModel shift = addNode(b.graph(), AddNode.class);
        wire(b.graph(), shift.getInputsById().get("a"), position.getOutputsById().get("out"));
        wire(b.graph(), shift.getInputsById().get("b"), vec3(b, 1, 0, 0).getOutputsById().get("out"));
        wire(b.graph(), b.positionIn(), shift.getOutputsById().get("out"));
        RenderTypeGraphMaterial material = material(ctx, b);
        try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 0, 1, 0.5f, -1))) {
            expect(ctx, "the left half, where the quad was, is empty", rgba(img, C / 2, C), 0.2f, 0.2f, 0.2f);
            expect(ctx, "the right half, where it moved to, is drawn", rgba(img, C + C / 2, C), 1, 1, 1);
        } finally {
            material.close();
        }
    }

    /** Vertex colour through each preset layout — a wrong offset or stride shows up as the wrong colour. */
    private static void vertexLayouts(TestContext ctx) {
        int argb = 0xFF3399E6; // (0.2, 0.6, 0.9)
        var layouts = new LinkedHashMap<String, java.util.List<String>>();
        layouts.put("Entity", VertexFormatPresets.ENTITY);
        layouts.put("Block", VertexFormatPresets.BLOCK);
        layouts.put("Position Color Tex", VertexFormatPresets.POSITION_COLOR_TEX);
        layouts.put("Position Color Tex Normal", VertexFormatPresets.POSITION_COLOR_TEX_NORMAL);
        layouts.put("Position Color", java.util.List.of("position", "color"));
        for (var layout : layouts.entrySet()) {
            Bare b = bare().settings(s -> new com.lowdragmc.kilagraph.rendertype.RenderTypeGraph.Settings(
                    layout.getValue(), s.vertexFormatMode(), s.blend(), s.depthTest(), s.depthWrite(), s.cull(),
                    s.outputTarget(), false, false));
            NodeModel color = addNode(b.graph(), VertexColorNode.class);
            setOption(color, "mode", VertexColorNode.MODE_COLOR);
            wire(b.graph(), b.color(), color.getOutputsById().get("out"));
            RenderTypeGraphMaterial material = material(ctx, b);
            try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 1, 1, 0.5f, argb))) {
                expect(ctx, layout.getKey() + " layout carries the vertex colour", rgba(img, C, C), 0.2f, 0.6f, 0.9f);
            } finally {
                material.close();
            }
        }
    }

    private static final float[][] QUADRANTS = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}};

    /** Canvas pixel of an NDC point (row 0 is the top). */
    private static float[] pixel(NativeImage img, float x, float y) {
        return rgba(img, Math.round((x * 0.5f + 0.5f) * Canvas.SIZE), Math.round((0.5f - y * 0.5f) * Canvas.SIZE));
    }

    /** Four instances of one small quad, moved by a per-instance vec2 and coloured by a per-instance vec3. */
    private static void instancedData(TestContext ctx) {
        Bare b = bare();
        NodeModel position = addNode(b.graph(), PositionNode.class);
        setOption(position, GeometrySpaces.OPTION, GeometrySpaces.OBJECT);
        NodeModel offset = instanceData(b, "Offset", "VEC2");
        NodeModel shift = addNode(b.graph(), AddNode.class);
        wire(b.graph(), shift.getInputsById().get("a"), position.getOutputsById().get("out"));
        wire(b.graph(), shift.getInputsById().get("b"), offset.getOutputsById().get("out"));
        wire(b.graph(), b.positionIn(), shift.getOutputsById().get("out"));
        wire(b.graph(), b.color(), instanceData(b, "Tint", "VEC3").getOutputsById().get("out"));
        float[][] tints = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 1}};
        try (RenderTypeGraphMaterial material = material(ctx, b);
             KGMesh mesh = KGMesh.build(material, vc -> quad(vc, -0.25f, -0.25f, 0.25f, 0.25f, 0.5f, -1));
             KGInstanceBuffer instances = material.createInstanceBuffer(4)) {
            ctx.check("the layout has both attributes", material.instanceLayout() != null
                    && material.instanceLayout().attributes().size() == 2);
            for (int i = 0; i < 4; i++) {
                instances.set(i, "Offset", QUADRANTS[i][0], QUADRANTS[i][1]);
                instances.set(i, "Tint", tints[i]);
            }
            Canvas canvas = canvas(ctx);
            canvas.clear(CLEAR);
            canvas.withCamera(new Matrix4f(), () -> material.drawInstanced(mesh, instances, 4, new Matrix4f()));
            try (NativeImage img = canvas.read()) {
                for (int i = 0; i < 4; i++) {
                    expect(ctx, "instance " + i, pixel(img, QUADRANTS[i][0], QUADRANTS[i][1]),
                            tints[i][0], tints[i][1], tints[i][2]);
                }
                expect(ctx, "nothing between the instances", pixel(img, 0, 0), 0.2f, 0.2f, 0.2f);
            }
            // Drawn the ordinary way, the graph reads its defaults: no offset, black.
            try (NativeImage img = drawImage(ctx, material, new Matrix4f(),
                    vc -> quad(vc, -0.25f, -0.25f, 0.25f, 0.25f, 0.5f, -1))) {
                expect(ctx, "a non-instanced draw reads the default instance", pixel(img, 0, 0), 0, 0, 0);
            }
        }
    }

    /** A per-instance mat4 places each instance; the instance id (0..3) / 3 colours it. */
    private static void instanceTransformAndId(TestContext ctx) {
        Bare b = bare();
        NodeModel position = addNode(b.graph(), PositionNode.class);
        setOption(position, GeometrySpaces.OPTION, GeometrySpaces.OBJECT);
        NodeModel transform = addNode(b.graph(), Mat4TransformNode.class);
        wire(b.graph(), transform.getInputsById().get("m"), instanceData(b, "Transform", "MAT4").getOutputsById().get("out"));
        wire(b.graph(), transform.getInputsById().get("v"), position.getOutputsById().get("out"));
        wire(b.graph(), b.positionIn(), transform.getOutputsById().get("out"));
        NodeModel id = addNode(b.graph(), InstanceIdNode.class);
        wire(b.graph(), b.color(), divided(b, id.getOutputsById().get("out"), 3));
        try (RenderTypeGraphMaterial material = material(ctx, b);
             KGMesh mesh = KGMesh.build(material, vc -> quad(vc, -0.25f, -0.25f, 0.25f, 0.25f, 0.5f, -1));
             KGInstanceBuffer instances = material.createInstanceBuffer(4)) {
            for (int i = 0; i < 4; i++) {
                instances.set(i, "Transform", new Matrix4f().translation(QUADRANTS[i][0], QUADRANTS[i][1], 0));
            }
            Canvas canvas = canvas(ctx);
            canvas.clear(CLEAR);
            canvas.withCamera(new Matrix4f(), () -> material.drawInstanced(mesh, instances, 4, new Matrix4f()));
            try (NativeImage img = canvas.read()) {
                for (int i = 0; i < 4; i++) {
                    float v = i / 3f;
                    expect(ctx, "instance " + i + " (id / 3)", pixel(img, QUADRANTS[i][0], QUADRANTS[i][1]), v, v, v);
                }
            }
            try (NativeImage img = drawImage(ctx, material, new Matrix4f(),
                    vc -> quad(vc, -0.25f, -0.25f, 0.25f, 0.25f, 0.5f, -1))) {
                expect(ctx, "a non-instanced draw: identity transform, id 0", pixel(img, 0, 0), 0, 0, 0);
            }
        }
    }

    private static NodeModel instanceData(Bare b, String name, String type) {
        NodeModel node = addNode(b.graph(), InstanceDataNode.class);
        setOption(node, InstanceDataNode.OPTION_NAME, name);
        setOption(node, InstanceDataNode.OPTION_TYPE, type);
        return node;
    }

    /** Scene Color returns exactly the texel of the captured frame it samples. */
    private static void sceneColor(TestContext ctx) {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        // Upper part of the frame, so an upside-down read would land elsewhere.
        int tx = main.width / 2, ty = main.height / 6;
        Bare b = bare();
        NodeModel scene = addNode(b.graph(), SceneColorNode.class);
        NodeModel uv = addNode(b.graph(), com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec2Node.class);
        // Render-target v runs bottom-to-top; the readback is top-down.
        setInputConstant(uv, "x", (tx + 0.5f) / main.width);
        setInputConstant(uv, "y", 1f - (ty + 0.5f) / main.height);
        wire(b.graph(), scene.getInputsById().get("uv"), uv.getOutputsById().get("out"));
        wire(b.graph(), b.color(), scene.getOutputsById().get("out"));
        RenderTypeGraphMaterial material = material(ctx, b); // acquires the capture
        try {
            SceneCaptureManager.INSTANCE.capture();
            ctx.check("the capture produced a texture", SceneCaptureManager.INSTANCE.colorView() != null);
            float[] expected;
            try (NativeImage frame = FrameCapture.grab(main)) {
                expected = rgba(frame, tx, ty);
            }
            expect(ctx, "Scene Color returns the captured texel", draw(ctx, material, C, C),
                    expected[0], expected[1], expected[2]);
        } finally {
            material.close();
        }
    }

    // ---- building blocks ---------------------------------------------------------------------------

    private static Bare solid(float r, float g, float b, DepthTest test, boolean write) {
        Bare graph = bare().settings(s -> settings(s, s.blend(), test, write, false));
        wire(graph.graph(), graph.color(), vec3(graph, r, g, b).getOutputsById().get("out"));
        return graph;
    }

    private static com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel divided(
            Bare b, com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel value, float by) {
        NodeModel divide = addNode(b.graph(), DivideNode.class);
        wire(b.graph(), divide.getInputsById().get("a"), value);
        setInputConstant(divide, "b", by);
        return divide.getOutputsById().get("out");
    }

    /** One node, its inputs set to constants ({@code id, value, id, value, ...}), output broadcast to rgb. */
    private static void scalar(TestContext ctx, Class<? extends Node> nodeClass, float expected, Object... inputs) {
        vector(ctx, nodeClass, new float[]{expected, expected, expected}, inputs);
    }

    private static void vector(TestContext ctx, Class<? extends Node> nodeClass, float[] expected, Object... inputs) {
        Bare b = bare();
        NodeModel node = addNode(b.graph(), nodeClass);
        StringBuilder label = new StringBuilder(nodeClass.getSimpleName()).append('(');
        for (int i = 0; i < inputs.length; i += 2) {
            setInputConstant(node, (String) inputs[i], inputs[i + 1]);
            label.append(i == 0 ? "" : ", ").append(inputs[i]).append('=').append(inputs[i + 1]);
        }
        wire(b.graph(), b.color(), node.getOutputsById().get("out"));
        expect(ctx, label.append(')').toString(), drawCenter(ctx, b), expected[0], expected[1], expected[2]);
    }

    private static RenderTypeGraphMaterial material(TestContext ctx, Bare b) {
        RenderTypeGraphMaterial material = RenderTypeFactory.createMaterial(b.graph());
        ctx.require("the graph compiles into a pipeline the backend accepts", material != null);
        return material;
    }

    private static Canvas canvas(TestContext ctx) {
        return ctx.get(CANVAS);
    }

    /** Compile {@code b}, draw a full-canvas quad, return the centre pixel. */
    private static float[] drawCenter(TestContext ctx, Bare b) {
        RenderTypeGraphMaterial material = material(ctx, b);
        try {
            return draw(ctx, material, C, C);
        } finally {
            material.close();
        }
    }

    private static float[] draw(TestContext ctx, RenderTypeGraphMaterial material, int x, int y) {
        try (NativeImage img = drawImage(ctx, material, new Matrix4f(), vc -> quad(vc, -1, -1, 1, 1, 0.5f, -1))) {
            return rgba(img, x, y);
        }
    }

    /** Run {@code submits} through a real {@link FeatureRenderDispatcher} into the cleared canvas and read it. */
    private static NativeImage dispatch(TestContext ctx, Consumer<SubmitNodeStorage> submits) {
        var mc = Minecraft.getInstance();
        Canvas canvas = canvas(ctx);
        canvas.clear(CLEAR);
        try (var buffers = new RenderBuffers(1);
             var dispatcher = new FeatureRenderDispatcher(buffers, mc.getModelManager(), mc.getAtlasManager(),
                     mc.font, mc.gameRenderer.gameRenderState())) {
            canvas.withCamera(new Matrix4f(), () -> {
                var storage = new SubmitNodeStorage();
                submits.accept(storage);
                try (var frame = dispatcher.prepareFrame(storage)) {
                    frame.executeSolid();
                    frame.executeTranslucent();
                    frame.executeTranslucentAfterTerrain();
                    frame.executeAlwaysOnTop();
                }
                // prepareFrame drained the storage; the private staged buffer is reset for the next use.
                buffers.endFrame();
            });
        }
        return canvas.read();
    }

    private static NativeImage drawImage(TestContext ctx, RenderTypeGraphMaterial material, Matrix4f projection,
                                         Consumer<VertexConsumer> emit) {
        Canvas canvas = canvas(ctx);
        canvas.clear(CLEAR);
        canvas.draw(material, projection, emit);
        return canvas.read();
    }

    private static void expect(TestContext ctx, String what, float[] actual, float r, float g, float b) {
        boolean ok = Math.abs(actual[0] - r) <= TOL && Math.abs(actual[1] - g) <= TOL && Math.abs(actual[2] - b) <= TOL;
        ctx.check(what, ok, fmt(new float[]{r, g, b, actual[3]}), fmt(actual));
    }
}
