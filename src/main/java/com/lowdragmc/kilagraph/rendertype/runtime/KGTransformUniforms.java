package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.lowdragmc.lowdraglib2.client.scene.SceneCameraContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The shared {@code KG_Transforms} uniform block — coordinate-space matrices KilaGraph precomputes each
 * draw so shader nodes (notably the {@code Transform} node) can convert between Object / View / World /
 * Clip space without per-pixel {@code inverse()} calls.
 *
 * <p>Minecraft exposes {@code ModelViewMat} (Object→View, {@code DynamicTransforms}) and {@code ProjMat}
 * (View→Clip, {@code Projection}). To reach World space and the reverse directions we precompute,
 * CPU-side, only what MC doesn't give us: the inverse of {@code ModelViewMat}, the camera's world→view
 * rotation matrix + its inverse, and the inverse projection (clip→view). MC dropped the direct CPU
 * projection getter, but {@link Camera#getViewRotationProjectionMatrix} returns {@code ProjMat ·
 * ViewRotMatrix}, so we recover {@code ProjMat = that · inverse(ViewRotMatrix)} and invert it — keeping
 * clip conversions off the per-pixel {@code inverse()} path too.</p>
 *
 * <pre>{@code
 * layout(std140) uniform KG_Transforms {
 *     mat4 IModelViewMat;  // view  -> object  = inverse(ModelViewMat)
 *     mat4 ViewMat;        // world -> view    (camera rotation)
 *     mat4 IViewMat;       // view  -> world   = inverse(ViewMat)
 *     mat4 IProjMat;       // clip  -> view    = inverse(ProjMat)
 * } kg_transforms;
 * }</pre>
 *
 * <p>The world camera <em>position</em> is intentionally NOT here — it duplicates Minecraft's
 * {@code globals.glsl} ({@code CameraBlockPos} + {@code CameraOffset}, a precision-split form that is more
 * accurate far from the origin), so the Transform node's world translation reads that instead. World here
 * means <em>absolute world</em>: {@code world = IViewMat * view + cameraWorldPos}, with {@code ViewMat}
 * carrying only the camera rotation. Updated once per frame at {@code RenderType.draw} HEAD (before any
 * pass — {@code writeToBuffer} is illegal inside a pass) via {@link #prepareUpload()}; bound inside the
 * pass via {@link #slice()}.</p>
 *
 * <p><b>Camera source:</b> the object&harr;view matrix always comes from {@code RenderSystem} (set
 * correctly by whatever is rendering). The camera rotation + projection come from the live game camera in
 * the normal pipeline, but an off-screen renderer (e.g. an LDLib2 {@code WorldSceneRenderer} preview) uses
 * its own camera while leaving the game's main {@code Camera} untouched — so we prefer the camera it
 * publishes via {@code SceneCameraContext} when active, ensuring previews reflect the scene camera, not the
 * world camera.</p>
 */
public final class KGTransformUniforms {

    public static final String UBO_NAME = "KG_Transforms";
    public static final String UBO_INSTANCE = "kg_transforms";

    /** The {@link ShaderUniformBlock} view a node registers via {@code ctx.useUniformBlock(...)}. */
    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGTransformUniforms.declareGlsl(); }
        @Override public void prepareUpload() { KGTransformUniforms.prepareUpload(); }
        @Override public GpuBufferSlice slice() { return KGTransformUniforms.slice(); }
    };

    /** std140 size: four mat4. */
    private static final int UBO_SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putMat4f().putMat4f().get();

    @Nullable private static GpuBuffer buffer;

    private static final Matrix4f iModelView = new Matrix4f();
    private static final Matrix4f viewMat = new Matrix4f();
    private static final Matrix4f iViewMat = new Matrix4f();
    private static final Matrix4f iProjMat = new Matrix4f();
    private static final Matrix4f projTmp = new Matrix4f();

    private KGTransformUniforms() {}

    /** GLSL declaration of the transforms block (must match the buffer layout exactly). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    mat4 IModelViewMat;\n"
                + "    mat4 ViewMat;\n"
                + "    mat4 IViewMat;\n"
                + "    mat4 IProjMat;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor for a field, e.g. {@code kg_transforms.ViewMat}. */
    public static String accessor(String field) {
        return UBO_INSTANCE + "." + field;
    }

    /**
     * Create (if needed) and refresh the buffer for the current frame from the live MC matrices + camera.
     * Performs a {@code writeToBuffer} — call before {@code RenderType.draw} opens its pass.
     */
    public static void prepareUpload() {
        RenderSystem.assertOnRenderThread();
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "KG_Transforms UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
        }
        compute();
        upload();
    }

    /** The slice to bind as {@code KG_Transforms} (pure — safe inside a render pass). */
    @Nullable
    public static GpuBufferSlice slice() {
        return buffer == null ? null : buffer.slice();
    }

    private static void compute() {
        // Object<->view always comes from RenderSystem, which is set correctly by whatever is rendering
        // (the main pipeline OR an off-screen scene), so this is context-independent.
        iModelView.set(RenderSystem.getModelViewMatrix()).invert();

        // The camera rotation + projection are NOT on RenderSystem in a usable CPU form. The main pipeline
        // reads them off the game camera; but an off-screen renderer (e.g. an LDLib2 WorldSceneRenderer
        // preview) uses its OWN camera while leaving the game's main Camera untouched. So prefer the camera
        // a custom renderer publishes via SceneCameraContext, falling back to the main world camera.
        if (SceneCameraContext.isActive()) {
            viewMat.set(SceneCameraContext.viewRotation());
            iProjMat.set(SceneCameraContext.projection()).invert();
        } else {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            camera.getViewRotationMatrix(viewMat);
            // Recover ProjMat (no direct CPU getter): getViewRotationProjectionMatrix = ProjMat * ViewRotMatrix,
            // so ProjMat = that * inverse(ViewRotMatrix); then invert for clip->view.
            camera.getViewRotationProjectionMatrix(projTmp);
            projTmp.mul(new Matrix4f(viewMat).invert());
            projTmp.invert(iProjMat);
        }
        viewMat.invert(iViewMat);
    }

    private static void upload() {
        ByteBuffer bb = MemoryUtil.memAlloc(UBO_SIZE);
        try {
            Std140Builder.intoBuffer(bb)
                    .putMat4f(iModelView)
                    .putMat4f(viewMat)
                    .putMat4f(iViewMat)
                    .putMat4f(iProjMat);
            bb.rewind();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }
}
