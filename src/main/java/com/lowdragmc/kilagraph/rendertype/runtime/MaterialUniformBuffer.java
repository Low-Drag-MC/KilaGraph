package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The GPU-side {@code KG_Material} uniform buffer for one material instance. Holds the per-field
 * values on the Java side, packs them std140 in {@link MaterialUniformLayout} order, and uploads to
 * a persistent GPU buffer that {@code RenderTypeMixin} binds during {@code RenderType.draw}.
 *
 * <p>TODO(1.21-backport milestone 2): the GPU upload path (backed by
 * {@code com.mojang.blaze3d.buffers.GpuBuffer}/{@code GpuBufferSlice}/{@code Std140Builder}) does not exist
 * in 1.21.1. The Java-side value store ({@link #set}) is kept, but {@link #prepareUpload()} is stubbed and
 * {@link #slice()} retyped to {@code Object}. Reimplement the std140 packing + upload against the 1.21.1
 * rendering model.</p>
 */
public final class MaterialUniformBuffer implements AutoCloseable {

    private final MaterialUniformLayout layout;
    private final Map<String, float[]> values = new HashMap<>();
    private boolean dirty = true;
    private boolean closed = false;

    public MaterialUniformBuffer(MaterialUniformLayout layout) {
        this.layout = layout;
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

    // TODO(1.21-backport milestone 2): create + upload the GpuBuffer when values changed.
    public void prepareUpload() {
        if (layout.isEmpty() || closed) return;
        throw new UnsupportedOperationException("KG_Material UBO upload: 1.21.1 backport pending (milestone 2)");
    }

    // TODO(1.21-backport milestone 2): return type was com.mojang.blaze3d.buffers.GpuBufferSlice.
    @Nullable
    public Object slice() {
        return null;
    }

    @Override
    public void close() {
        closed = true;
    }
}
