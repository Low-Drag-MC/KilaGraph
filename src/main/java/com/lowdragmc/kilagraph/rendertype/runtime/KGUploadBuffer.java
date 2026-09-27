package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A GPU buffer whose whole contents are replaced from the CPU, from anywhere on the render thread, for draws
 * prepared now and run later in the frame.
 *
 * <p>A prepared draw binds the buffer as it was then ({@link #capture}), so a captured buffer isn't written again
 * this frame: the next upload replaces it with a new one, and the captured one is closed when the frame ends
 * ({@link #endFrame}). The buffer is replaced too inside a render pass, where nothing may be written — yet a
 * {@code RenderType} may be prepared there, for an immediate draw into an open pass. Otherwise the data is written
 * into it.</p>
 */
public final class KGUploadBuffer implements AutoCloseable {

    /** Buffers replaced while a draw may still read them, closed at the end of the frame. Render thread. */
    private static final List<GpuBuffer> RETIRED = new ArrayList<>();
    /** Buffers a draw prepared this frame binds: replaced rather than written until the frame ends. Render thread. */
    private static final Set<KGUploadBuffer> CAPTURED = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Supplier<String> label;
    private final @GpuBuffer.Usage int usage;
    @Nullable private GpuBuffer buffer;

    public KGUploadBuffer(Supplier<String> label, @GpuBuffer.Usage int usage) {
        this.label = label;
        this.usage = usage | GpuBuffer.USAGE_COPY_DST;
    }

    /** Whether a render pass is open, so buffers can't be written. */
    public static boolean inRenderPass() {
        return RenderSystem.getDevice().createCommandEncoder() instanceof FrontendCommandEncoder encoder
                && encoder.isInRenderPass();
    }

    /** The frame's draws are recorded: close the replaced buffers, and write the captured ones in place again.
     *  No-op while a render pass is open. */
    public static void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (inRenderPass()) return;
        RETIRED.forEach(GpuBuffer::close);
        RETIRED.clear();
        CAPTURED.clear();
    }

    /** How many replaced buffers wait for the frame to end. */
    public static int retiredCount() {
        return RETIRED.size();
    }

    /** Replace the contents with {@code data} (its remaining bytes). */
    public void upload(ByteBuffer data) {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        if (buffer != null) {
            boolean captured = CAPTURED.remove(this);
            if (captured || inRenderPass()) {
                RETIRED.add(buffer);
            } else if (buffer.size() == data.remaining()) {
                device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
                return;
            } else {
                buffer.close();
            }
        }
        buffer = device.createBuffer(label, usage, data);
    }

    /** The current buffer, or {@code null} before the first upload. */
    @Nullable
    public GpuBufferSlice slice() {
        return buffer == null ? null : buffer.slice();
    }

    /** The current buffer, for a draw prepared now that runs later in the frame: it keeps these contents until the
     *  frame ends. {@code null} before the first upload. */
    @Nullable
    public GpuBufferSlice capture() {
        if (buffer == null) return null;
        CAPTURED.add(this);
        return buffer.slice();
    }

    @Override
    public void close() {
        if (buffer == null) return;
        if (CAPTURED.remove(this) || inRenderPass()) RETIRED.add(buffer);
        else buffer.close();
        buffer = null;
    }
}
