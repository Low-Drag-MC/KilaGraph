package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The GPU-side {@code KG_Material} uniform buffer for one material instance. Holds the per-field
 * values on the Java side, packs them at {@link MaterialUniformLayout#std140Offsets()}, and uploads to
 * a {@link KGUploadBuffer} that {@code PreparedRenderTypeMixin} binds during the draw.
 *
 * <p>Values are set with {@link #set}; {@link #prepareUpload()} re-uploads the GPU buffer after a change.
 * Empty layouts allocate nothing and return a {@code null} slice (the mixin then binds no custom UBO).</p>
 */
public final class MaterialUniformBuffer implements AutoCloseable {

    private final MaterialUniformLayout layout;
    private final Map<String, float[]> values = new HashMap<>();
    private final int byteSize;

    private final KGUploadBuffer buffer = new KGUploadBuffer(() -> "KG_Material UBO", GpuBuffer.USAGE_UNIFORM);
    private boolean dirty = true;
    private boolean closed = false;

    public MaterialUniformBuffer(MaterialUniformLayout layout) {
        this.layout = layout;
        this.byteSize = layout.std140Size();
    }

    /** Set a field's value (1-4 components depending on the field's GLSL type). Marks dirty. */
    public void set(String name, float... components) {
        values.put(name, components.clone());
        dirty = true;
    }

    /** Whether this material has any uniform fields at all. */
    public boolean isEmpty() {
        return layout.isEmpty();
    }

    /**
     * Upload the values when they changed since the last upload. Best called before the render pass opens
     * (where the buffer is written in place); see {@link KGUploadBuffer} for inside one. Render thread.
     */
    public void prepareUpload() {
        if (layout.isEmpty() || closed) return;
        RenderSystem.assertOnRenderThread();
        if (dirty) {
            upload();
            dirty = false;
        }
    }

    /**
     * The buffer slice to bind as {@code KG_Material}. Pure (no GPU write), so it is safe to call
     * inside an open render pass. Returns {@code null} until {@link #prepareUpload()} has created the
     * buffer (or for an empty layout).
     */
    @Nullable
    public GpuBufferSlice slice() {
        if (layout.isEmpty() || closed) return null;
        return buffer.slice();
    }

    /** {@link #slice()} for a draw prepared now: these values stay in it until the frame ends
     *  ({@link KGUploadBuffer#capture}). */
    @Nullable
    public GpuBufferSlice capture() {
        if (layout.isEmpty() || closed) return null;
        return buffer.capture();
    }

    private void upload() {
        // zeroed, so padding and unset members read 0
        ByteBuffer bb = MemoryUtil.memCalloc(byteSize);
        try {
            var fields = layout.fields();
            int[] offsets = layout.std140Offsets();
            for (int i = 0; i < fields.size(); i++) {
                var f = fields.get(i);
                int at = offsets[i];
                if (at < 0) continue; // a sampler: not a member of the block
                float[] v = values.getOrDefault(f.name(), new float[0]);
                switch (f.type()) {
                    // int / bool members take integer bits
                    case INT, BOOL -> bb.putInt(at, (int) at(v, 0));
                    case FLOAT -> bb.putFloat(at, at(v, 0));
                    case VEC2 -> putFloats(bb, at, v, 2);
                    case VEC3 -> putFloats(bb, at, v, 3);
                    case VEC4 -> putFloats(bb, at, v, 4);
                    // column-major
                    case MAT4 -> putFloats(bb, at, v, 16);
                    // KG_Gradient: GradientGlsl.pack() = [header(4), colors(32), alphas(32)], 17 contiguous vec4
                    case GRADIENT -> putFloats(bb, at, v, (1 + GradientGlsl.MAX_KEYS * 2) * 4);
                    // KG_Curve: CurveGlsl.pack() = [header(4), segments(64)], 17 contiguous vec4
                    case CURVE -> putFloats(bb, at, v, (1 + CurveGlsl.MAX_SEGMENTS * 2) * 4);
                    default -> { }
                }
            }
            // absolute puts: bb still holds all byteSize bytes
            buffer.upload(bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }

    private static void putFloats(ByteBuffer bb, int offset, float[] v, int count) {
        for (int i = 0; i < count; i++) bb.putFloat(offset + i * 4, at(v, i));
    }

    private static float at(float[] v, int i) {
        return i < v.length ? v[i] : 0f;
    }

    @Override
    public void close() {
        closed = true;
        buffer.close();
    }
}
