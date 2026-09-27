package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.jetbrains.annotations.Nullable;

/**
 * A KilaGraph-managed uniform block (UBO) that the engine declares in the generated GLSL, binds on the
 * pipeline, and uploads itself each frame — as opposed to per-material {@code KG_Material} values supplied
 * by the renderer, or Minecraft's own builtin UBOs (declared via {@code #include} includes).
 *
 * <p>A shader node opts a graph into a block by registering it with the compiler
 * ({@code ShaderCompileContext.useUniformBlock(block)}); the compiler then emits {@link #declareGlsl()}
 * into the source, the pipeline declares {@link #uboName()}, and the material drives {@link #prepareUpload()}
 * + {@link #capture()} (when it is prepared, before the frame's passes open) and binds that — or {@link #slice()}
 * — inside the pass. This is the single extension point for new engine UBOs — implement it, hand a node your
 * instance, and nothing in the compiler / factory / material needs to change. {@link KGEngineUniforms}/
 * {@link KGTransformUniforms} are the built-in implementations.</p>
 *
 * <p>Implementations are typically singletons (one shared GPU buffer per block); the same instance may be
 * registered by many nodes/graphs in a frame, so {@link #prepareUpload()} must be idempotent per frame.</p>
 */
public interface ShaderUniformBlock {

    /** The pipeline binding name / GLSL block name (e.g. {@code KG_Globals}, {@code KG_Transforms}). */
    String uboName();

    /** The std140 block declaration, emitted before {@code main()} (must match the buffer layout exactly). */
    String declareGlsl();

    /** (Re)create + upload the buffer for the current frame. Render thread; normally before the render pass opens,
     *  but a render type can be prepared inside one — upload through a {@link KGUploadBuffer}, which handles both. */
    void prepareUpload();

    /** The buffer slice to bind for {@link #uboName()} — pure (safe inside a render pass), or null if absent. */
    @Nullable
    GpuBufferSlice slice();

    /**
     * The slice a draw prepared now binds, right after {@link #prepareUpload()}: for values that differ between the
     * draws of a frame (the transforms), these values, kept until the frame ends — see
     * {@link KGUploadBuffer#capture}. By default {@code null}: the draw binds {@link #slice()} when it runs, as a view
     * of one of Minecraft's own blocks should — those are set for the pass.
     */
    @Nullable
    default GpuBufferSlice capture() {
        return null;
    }
}
