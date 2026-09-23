package com.lowdragmc.kilagraph.test.uitest;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentAlphaBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.input.basic.Vec3Node;
import com.lowdragmc.kilagraph.rendertype.nodes.vertex.VertexModelPositionBlock;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.lowdraglib2.client.scene.SceneCameraContext;
import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.variable.VariableKind;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addBlock;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.addNode;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.setInputConstant;
import static com.lowdragmc.kilagraph.test.gametest.KGGameTestHelpers.wire;

/** Shared plumbing for the RenderType GPU scenarios: bare graphs, an off-screen canvas drawn through the real
 *  material path, and pixel reads. */
final class ShaderTestKit {

    private ShaderTestKit() {}

    /** A RenderType graph with the two fixed stages and nothing else — no default entity network. */
    static final class BareGraph extends RenderTypeGraph {
        @Override
        protected void initializeDefaultEntityShader() {
        }
    }

    /** A bare graph plus its Position (identity), Base Color and Alpha blocks: the output is exactly
     *  {@code vec4(baseColor, alpha)}. */
    record Bare(RenderTypeGraph graph, NodeModel position, NodeModel baseColor, NodeModel alpha) {
        PortModel color() {
            return baseColor.getInputsById().get("color");
        }

        PortModel alphaIn() {
            return alpha.getInputsById().get("alpha");
        }

        PortModel positionIn() {
            return position.getInputsById().get("position");
        }

        Bare settings(UnaryOperator<RenderTypeGraph.Settings> edit) {
            graph.setSettings(edit.apply(graph.getSettings()));
            return this;
        }
    }

    /** A fresh {@link Bare} graph: opaque, LEQUAL with depth writes, no culling. */
    static Bare bare() {
        var graph = new BareGraph();
        var position = addBlock(graph, graph.getVertexStageModel(), VertexModelPositionBlock.class);
        var baseColor = addBlock(graph, graph.getFragmentStageModel(), FragmentBaseColorBlock.class);
        var alpha = addBlock(graph, graph.getFragmentStageModel(), FragmentAlphaBlock.class);
        var bare = new Bare(graph, position, baseColor, alpha);
        return bare.settings(s -> settings(s, s.blend(), s.depthTest(), s.depthWrite(), false));
    }

    /** A bare graph whose base colour is an EXPOSED vec3 variable "Tint", default red. */
    static Bare exposedTint() {
        Bare b = bare();
        var tint = (VariableDeclarationModelBase) b.graph().graphModel.createVariable(
                "Tint", RenderTypeGraphTypes.VEC3, new Vector3f(1, 0, 0), VariableKind.INPUT);
        tint.setScope(VariableScope.EXPOSED);
        var node = b.graph().graphModel.createVariableNode(tint, new Vector2f(), null, null);
        wire(b.graph(), b.color(), node.getOutputPort());
        return b;
    }

    /** A constant {@code vec3} node in {@code b}'s graph. */
    static NodeModel vec3(Bare b, float x, float y, float z) {
        NodeModel node = addNode(b.graph(), Vec3Node.class);
        setInputConstant(node, "x", x);
        setInputConstant(node, "y", y);
        setInputConstant(node, "z", z);
        return node;
    }

    /** A 2×2 texture; image row 0 is the top. */
    static DynamicTexture texture(int topLeft, int topRight, int bottomLeft, int bottomRight) {
        var image = new NativeImage(2, 2, false);
        image.setPixel(0, 0, topLeft);
        image.setPixel(1, 0, topRight);
        image.setPixel(0, 1, bottomLeft);
        image.setPixel(1, 1, bottomRight);
        return new DynamicTexture(() -> "KilaGraph uitest texture", image);
    }

    /** {@code s} with another colour format and depth offset. */
    static RenderTypeGraph.Settings withTarget(RenderTypeGraph.Settings s, RenderTypeGraph.Settings.ColorFormat format,
                                               float offsetFactor, float offsetUnits) {
        return new RenderTypeGraph.Settings(s.vertexFormatElements(), s.vertexFormatMode(), s.blend(), s.depthTest(),
                s.depthWrite(), s.cull(), s.outputTarget(), s.affectsOutline(), s.sortOnUpload(), format,
                offsetFactor, offsetUnits);
    }

    static RenderTypeGraph.Settings settings(RenderTypeGraph.Settings s, RenderTypeGraph.Settings.BlendMode blend,
                                             RenderTypeGraph.Settings.DepthTest depthTest, boolean depthWrite,
                                             boolean cull) {
        return new RenderTypeGraph.Settings(s.vertexFormatElements(), s.vertexFormatMode(), blend, depthTest,
                depthWrite, cull, s.outputTarget(), false, false);
    }

    /** One quad at depth {@code z} covering {@code [x0,x1]×[y0,y1]}; uv runs 0..1 left→right, bottom→top. */
    static void quad(VertexConsumer vc, float x0, float y0, float x1, float y1, float z, int argb) {
        vertex(vc, x0, y0, z, 0, 0, argb);
        vertex(vc, x1, y0, z, 1, 0, argb);
        vertex(vc, x1, y1, z, 1, 1, argb);
        vertex(vc, x0, y1, z, 0, 1, argb);
    }

    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0, 0, 1);
    }

    /** A small off-screen target; draws use an identity model-view and publish their projection through
     *  {@link SceneCameraContext}. */
    static final class Canvas implements AutoCloseable {
        static final int SIZE = 32;

        final TextureTarget target;
        private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("KilaGraph test canvas");

        Canvas() {
            this(GpuFormat.RGBA8_UNORM);
        }

        Canvas(GpuFormat format) {
            target = new TextureTarget("KilaGraph test canvas", SIZE, SIZE, true, format);
        }

        /** Clear colour to {@code argb} and depth to the far plane (0, reversed-Z). */
        void clear(int argb) {
            var clear = new Vector4f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                    (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), clear, target.getDepthTexture(), RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE);
        }

        /** Draw {@code emit}'s geometry through {@code material} with an identity projection (clip == view). */
        void draw(RenderTypeGraphMaterial material, Consumer<VertexConsumer> emit) {
            draw(material, new Matrix4f(), emit);
        }

        void draw(RenderTypeGraphMaterial material, Matrix4f projection, Consumer<VertexConsumer> emit) {
            withCamera(projection, () -> RenderUtils.drawImmediate(material.renderType(), emit));
        }

        /** Run {@code body} with its draws landing in this canvas, seen through {@code projection}. */
        void withCamera(Matrix4f projection, Runnable body) {
            GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
            GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
            ScissorState scissor = new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
            var modelView = RenderSystem.getModelViewStack();
            RenderSystem.backupProjectionMatrix();
            modelView.pushMatrix();
            try {
                RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
                modelView.identity();
                RenderSystem.disableScissorForRenderTypeDraws();
                RenderSystem.outputColorTextureOverride = target.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
                SceneCameraContext.set(new Matrix4f(), new Matrix4f(projection));
                body.run();
            } finally {
                SceneCameraContext.clear();
                RenderSystem.outputColorTextureOverride = prevColor;
                RenderSystem.outputDepthTextureOverride = prevDepth;
                if (scissor.enabled()) {
                    RenderSystem.enableScissorForRenderTypeDraws(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                }
                modelView.popMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        }

        /** Read the target back (synchronous). Row 0 is the top. The caller owns the image. */
        NativeImage read() {
            return FrameCapture.grab(target);
        }

        @Override
        public void close() {
            target.destroyBuffers();
            projectionBuffer.close();
        }
    }

    /** A pixel as {@code {r, g, b, a}} in 0..1. */
    static float[] rgba(NativeImage image, int x, int y) {
        int argb = image.getPixel(x, y);
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f};
    }

    static String fmt(float[] c) {
        return String.format(java.util.Locale.ROOT, "(%.3f, %.3f, %.3f, %.3f)", c[0], c[1], c[2], c[3]);
    }
}
