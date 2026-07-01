package com.lowdragmc.kilagraph.rendertype.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * The shared {@code KG_Globals} uniform block — engine-provided values that <em>we</em> update each
 * frame (as opposed to per-material {@code KG_Material} values supplied by the renderer). Unlike
 * Minecraft's {@code Globals.GameTime} (a normalised day fraction that wraps every 24000 ticks),
 * these are defined and maintained by KilaGraph so nodes like Time get a meaningful unit.
 *
 * <p>The block has a fixed std140 layout shared by every graph that uses any engine global, so the
 * GLSL declaration ({@link #declareGlsl()}) and the buffer stay in lockstep. Currently:</p>
 * <pre>{@code layout(std140) uniform KG_Globals { float Time; } kg_globals; }</pre>
 *
 * <p>Fields are accessed in GLSL as {@code kg_globals.Time}.</p>
 *
 * <p>TODO(1.21-backport milestone 2): the GPU-upload side (the {@code KG_Globals} UBO backed by
 * {@code com.mojang.blaze3d.buffers.GpuBuffer} / {@code Std140Builder}, refreshed at {@code RenderType.draw}
 * HEAD and bound via {@link #slice()}) was removed for the compile-only milestone — those blaze3d APIs do
 * not exist in 1.21.1. Only the version-independent GLSL declaration / accessors are kept so the shader
 * compiler still emits correct source. Reimplement the buffer upload against the 1.21.1 rendering model.</p>
 */
public final class KGEngineUniforms {

    public static final String UBO_NAME = "KG_Globals";
    public static final String UBO_INSTANCE = "kg_globals";

    /** The {@link ShaderUniformBlock} view a node registers via {@code ctx.useUniformBlock(...)}. */
    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGEngineUniforms.declareGlsl(); }
        @Override public void prepareUpload() { KGEngineUniforms.prepareUpload(); }
        @Override public Object slice() { return KGEngineUniforms.slice(); }
    };

    private KGEngineUniforms() {}

    /** GLSL declaration of the engine globals block (must match the buffer layout exactly). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    float Time;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor for the world time in seconds. */
    public static String timeAccessor() {
        return UBO_INSTANCE + ".Time";
    }

    // TODO(1.21-backport milestone 2): rebuild the GpuBuffer upload for the current frame.
    public static void prepareUpload() {
        throw new UnsupportedOperationException("KG_Globals UBO upload: 1.21.1 backport pending (milestone 2)");
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.buffers.GpuBufferSlice.
    @Nullable
    public static Object slice() {
        throw new UnsupportedOperationException("KG_Globals UBO slice: 1.21.1 backport pending (milestone 2)");
    }
}
