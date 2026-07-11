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

    /** std140 size: float (Time) + vec2 (ScreenSize) + float (GameTime). Grows as engine fields are added. */
    private static final int UBO_SIZE = new Std140SizeCalculator().putFloat().putVec2().putFloat().get();

    @Nullable private static GpuBuffer buffer;
    private static float currentTimeSeconds;
    private static float screenWidth, screenHeight;
    private static long lastUpdateKey = Long.MIN_VALUE;

    private KGEngineUniforms() {}

    /** GLSL declaration of the engine globals block (must match the buffer layout exactly). */
    public static String declareGlsl() {
        return "layout(std140) uniform " + UBO_NAME + " {\n"
                + "    float Time;\n"
                + "    vec2 ScreenSize;\n"
                + "    float GameTime;\n"
                + "} " + UBO_INSTANCE + ";\n";
    }

    /** GLSL accessor for the normalised day fraction — same value as Minecraft's {@code Globals.GameTime}
     *  ({@code (gameTime % 24000 + partialTick) / 24000}), carried in OUR block so the Game Time node stays
     *  injectable under an Iris shaderpack (a {@code #moj_import} include would reject the whole graph). */
    public static String gameTimeAccessor() {
        return UBO_INSTANCE + ".GameTime";
    }

    /** GLSL accessor for the world time in seconds. */
    public static String timeAccessor() {
        return UBO_INSTANCE + ".Time";
    }

    /** GLSL accessor for the framebuffer size in pixels ({@code gl_FragCoord} basis) — used to turn
     *  {@code gl_FragCoord} into a screen UV or reconstruct view/world position inside a fragment. */
    public static String screenSizeAccessor() {
        return UBO_INSTANCE + ".ScreenSize";
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
        screenWidth = mc.getWindow().getWidth();
        screenHeight = mc.getWindow().getHeight();
        // Quantise time to ~1ms so repeated calls in the same frame share a key; fold in the framebuffer
        // size so a resize re-uploads even within the same millisecond.
        return (long) (currentTimeSeconds * 1000.0f) * 31L + (long) screenWidth * 7L + (long) screenHeight;
    }

    private static void upload() {
        ByteBuffer bb = MemoryUtil.memAlloc(UBO_SIZE);
        try {
            // GameTime = day fraction; currentTimeSeconds already wraps at one day (1200 s), so /1200
            // reproduces Minecraft's ((gameTime % 24000) + partial) / 24000 exactly.
            Std140Builder.intoBuffer(bb).putFloat(currentTimeSeconds).putVec2(screenWidth, screenHeight)
                    .putFloat(currentTimeSeconds / 1200.0f);
            bb.rewind();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }
}
