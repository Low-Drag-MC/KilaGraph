package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The GPU-side {@code KG_Material} uniform buffer for one material instance. Holds the per-field
 * values on the Java side, packs them std140 in {@link MaterialUniformLayout} order, and uploads to
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

    private void upload() {
        ByteBuffer bb = MemoryUtil.memAlloc(byteSize);
        try {
            Std140Builder b = Std140Builder.intoBuffer(bb);
            for (MaterialUniformLayout.Field f : layout.fields()) {
                float[] v = values.getOrDefault(f.name(), new float[0]);
                switch (f.type()) {
                    case FLOAT, INT, BOOL -> b.putFloat(at(v, 0));
                    case VEC2 -> b.putVec2(at(v, 0), at(v, 1));
                    case VEC3 -> b.putVec3(at(v, 0), at(v, 1), at(v, 2));
                    case VEC4 -> b.putVec4(at(v, 0), at(v, 1), at(v, 2), at(v, 3));
                    case MAT4 -> {
                        float[] m = new float[16];
                        for (int i = 0; i < 16; i++) m[i] = at(v, i);
                        b.putMat4f(new org.joml.Matrix4f().set(m));
                    }
                    case SAMPLER2D -> { /* samplers are not UBO members */ }
                    case GRADIENT -> {
                        // KG_Gradient: vec4 header + 8 colour vec4 + 8 alpha vec4 (std140 16-aligned each).
                        // v is GradientGlsl.pack(): [header(4), colors(32), alphas(32)] = 68 contiguous floats.
                        for (int i = 0; i < 1 + GradientGlsl.MAX_KEYS * 2; i++) {
                            int base = i * 4;
                            b.putVec4(at(v, base), at(v, base + 1), at(v, base + 2), at(v, base + 3));
                        }
                    }
                    case CURVE -> {
                        // KG_Curve: vec4 header + 16 segment vec4 (std140 16-aligned each).
                        // v is CurveGlsl.pack(): [header(4), segments(64)] = 68 contiguous floats.
                        for (int i = 0; i < 1 + CurveGlsl.MAX_SEGMENTS * 2; i++) {
                            int base = i * 4;
                            b.putVec4(at(v, base), at(v, base + 1), at(v, base + 2), at(v, base + 3));
                        }
                    }
                }
            }
            bb.rewind();
            buffer.upload(bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
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
