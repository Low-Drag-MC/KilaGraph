package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

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
 * <p>Fields are accessed in GLSL as {@code kg_globals.Time}. The buffer is updated once per frame at
 * {@code RenderType.draw} HEAD (before any render pass — {@code writeToBuffer} is illegal inside a
 * pass) via {@link #prepareUpload()}, and bound inside the pass via {@link #slice()}.</p>
 */
public final class KGEngineUniforms {

    public static final String UBO_NAME = "KG_Globals";
    public static final String UBO_INSTANCE = "kg_globals";

    /** The {@link ShaderUniformBlock} view a node registers via {@code ctx.useUniformBlock(...)}. */
    public static final ShaderUniformBlock BLOCK = new ShaderUniformBlock() {
        @Override public String uboName() { return UBO_NAME; }
        @Override public String declareGlsl() { return KGEngineUniforms.declareGlsl(); }
        @Override public void prepareUpload() { KGEngineUniforms.prepareUpload(); }
        @Override public GpuBufferSlice slice() { return KGEngineUniforms.slice(); }
    };

    /** std140 size: one float (Time). Grows as engine fields are added (camera, screen size, ...). */
    private static final int UBO_SIZE = new Std140SizeCalculator().putFloat().get();

    @Nullable private static GpuBuffer buffer;
    private static float currentTimeSeconds;
    private static long lastUpdateKey = Long.MIN_VALUE;

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

    /**
     * Create (if needed) and refresh the buffer for the current frame. Performs a {@code writeToBuffer}
     * — call before {@code RenderType.draw} opens its pass. Idempotent within a frame (skips the write
     * when the time hasn't advanced), so many materials per frame upload at most once.
     */
    public static void prepareUpload() {
        RenderSystem.assertOnRenderThread();
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "KG_Globals UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
        }
        long key = computeTimeKey();
        if (key == lastUpdateKey) return;
        lastUpdateKey = key;
        upload();
    }

    /** The slice to bind as {@code KG_Globals} (pure — safe inside a render pass). */
    @Nullable
    public static GpuBufferSlice slice() {
        return buffer == null ? null : buffer.slice();
    }

    /** World time in seconds, wrapping every 1200s (one MC day) to keep float precision. */
    private static long computeTimeKey() {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // ticks -> seconds (20 ticks/s); wrap at one day (24000 ticks = 1200 s) for float precision.
        currentTimeSeconds = ((gameTime % 24000L) + partial) / 20.0f;
        // Quantise to ~1ms so repeated calls in the same frame share a key.
        return (long) (currentTimeSeconds * 1000.0f);
    }

    private static void upload() {
        ByteBuffer bb = MemoryUtil.memAlloc(UBO_SIZE);
        try {
            Std140Builder.intoBuffer(bb).putFloat(currentTimeSeconds);
            bb.rewind();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }
}
