package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A GPU buffer whose whole contents are replaced from the CPU, from anywhere on the render thread.
 *
 * <p>Outside a render pass the data is written into the buffer. Inside one no write is allowed — yet a
 * {@code RenderType} may be prepared there (an immediate draw into a pass that is already open) — so the buffer is
 * re-created holding the data instead, and the one it replaces is closed at the next write outside a pass (draws
 * already recorded in the open pass still read it).</p>
 */
public final class KGUploadBuffer implements AutoCloseable {

    private final Supplier<String> label;
    private final @GpuBuffer.Usage int usage;
    @Nullable private GpuBuffer buffer;
    private final List<GpuBuffer> retired = new ArrayList<>(0);

    public KGUploadBuffer(Supplier<String> label, @GpuBuffer.Usage int usage) {
        this.label = label;
        this.usage = usage | GpuBuffer.USAGE_COPY_DST;
    }

    /** Whether a render pass is open, so buffers can't be written. */
    public static boolean inRenderPass() {
        return RenderSystem.getDevice().createCommandEncoder() instanceof FrontendCommandEncoder encoder
                && encoder.isInRenderPass();
    }

    /** Replace the contents with {@code data} (its remaining bytes). */
    public void upload(ByteBuffer data) {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        if (!inRenderPass()) {
            retired.forEach(GpuBuffer::close);
            retired.clear();
            if (buffer != null && buffer.size() == data.remaining()) {
                device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
                return;
            }
            if (buffer != null) buffer.close();
        } else if (buffer != null) {
            retired.add(buffer);
        }
        buffer = device.createBuffer(label, usage, data);
    }

    /** The current buffer, or {@code null} before the first upload. */
    @Nullable
    public GpuBufferSlice slice() {
        return buffer == null ? null : buffer.slice();
    }

    @Override
    public void close() {
        retired.forEach(GpuBuffer::close);
        retired.clear();
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
