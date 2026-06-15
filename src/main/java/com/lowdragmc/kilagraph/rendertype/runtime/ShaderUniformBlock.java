package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.jetbrains.annotations.Nullable;

/**
 * A KilaGraph-managed uniform block (UBO) that the engine declares in the generated GLSL, binds on the
 * pipeline, and uploads itself each frame — as opposed to per-material {@code KG_Material} values supplied
 * by the renderer, or Minecraft's own builtin UBOs (declared via {@code #moj_import} includes).
 *
 * <p>A shader node opts a graph into a block by registering it with the compiler
 * ({@code ShaderCompileContext.useUniformBlock(block)}); the compiler then emits {@link #declareGlsl()}
 * into the source, the pipeline declares {@link #uboName()}, and the material drives {@link #prepareUpload()}
 * (at {@code RenderType.draw} HEAD, before the pass) + {@link #slice()} (bound inside the pass). This is the
 * single extension point for new engine UBOs — implement it, hand a node your instance, and nothing in the
 * compiler / factory / material needs to change. {@link KGEngineUniforms}/{@link KGTransformUniforms} are the
 * built-in implementations.</p>
 *
 * <p>Implementations are typically singletons (one shared GPU buffer per block); the same instance may be
 * registered by many nodes/graphs in a frame, so {@link #prepareUpload()} must be idempotent per frame.</p>
 */
public interface ShaderUniformBlock {

    /** The pipeline binding name / GLSL block name (e.g. {@code KG_Globals}, {@code KG_Transforms}). */
    String uboName();

    /** The std140 block declaration, emitted before {@code main()} (must match the buffer layout exactly). */
    String declareGlsl();

    /** (Re)create + upload the buffer for the current frame. Render thread, BEFORE any render pass opens. */
    void prepareUpload();

    /** The buffer slice to bind for {@link #uboName()} — pure (safe inside a render pass), or null if absent. */
    @Nullable
    GpuBufferSlice slice();
}
