package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.InstanceAttribute;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Per-instance values for {@link RenderTypeGraphMaterial#drawInstanced}, addressed by Instance Data name. Values
 * start at zero ({@code mat4}: identity) and are uploaded on the next draw after a change.
 */
public final class KGInstanceBuffer implements AutoCloseable {

    private final KGInstanceLayout layout;
    private final int capacity;
    private final ByteBuffer data;
    @Nullable private GpuBuffer buffer;
    private boolean dirty = true;
    private boolean closed;

    KGInstanceBuffer(KGInstanceLayout layout, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        this.layout = layout;
        this.capacity = capacity;
        this.data = MemoryUtil.memCalloc(layout.stride() * capacity);
        for (InstanceAttribute attribute : layout.attributes()) {
            if (attribute.type() != InstanceAttribute.Type.MAT4) continue;
            for (int i = 0; i < capacity; i++) {
                for (int c = 0; c < 4; c++) data.putFloat(position(i, attribute.name()) + c * 20, 1f);
            }
        }
    }

    public KGInstanceLayout layout() {
        return layout;
    }

    public int capacity() {
        return capacity;
    }

    /** Set a float-typed value ({@code float}/{@code vec2..4}/{@code mat4}, column-major) of one instance. */
    public KGInstanceBuffer set(int instance, String name, float... values) {
        InstanceAttribute attribute = require(name, values.length);
        if (attribute.type() == InstanceAttribute.Type.INT) throw new IllegalArgumentException(name + " is an int");
        int at = position(instance, name);
        for (int i = 0; i < values.length; i++) data.putFloat(at + i * 4, values[i]);
        dirty = true;
        return this;
    }

    public KGInstanceBuffer set(int instance, String name, Vector2fc value) {
        return set(instance, name, value.x(), value.y());
    }

    public KGInstanceBuffer set(int instance, String name, Vector3fc value) {
        return set(instance, name, value.x(), value.y(), value.z());
    }

    public KGInstanceBuffer set(int instance, String name, Vector4fc value) {
        return set(instance, name, value.x(), value.y(), value.z(), value.w());
    }

    public KGInstanceBuffer set(int instance, String name, Matrix4fc value) {
        require(name, 16);
        value.get(position(instance, name), data);
        dirty = true;
        return this;
    }

    public KGInstanceBuffer setInt(int instance, String name, int value) {
        InstanceAttribute attribute = require(name, 1);
        if (attribute.type() != InstanceAttribute.Type.INT) throw new IllegalArgumentException(name + " is not an int");
        data.putInt(position(instance, name), value);
        dirty = true;
        return this;
    }

    /** Upload pending changes; call outside a render pass. */
    void upload() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("instance buffer is closed");
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "KilaGraph instances",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.capacity());
        }
        if (!dirty) return;
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data.clear());
        dirty = false;
    }

    GpuBufferSlice slice() {
        if (buffer == null) throw new IllegalStateException("instance buffer was never uploaded");
        return buffer.slice();
    }

    private InstanceAttribute require(String name, int components) {
        InstanceAttribute attribute = layout.attribute(name);
        if (attribute == null) throw new IllegalArgumentException("no instance data named " + name);
        if (attribute.type().components() != components) {
            throw new IllegalArgumentException(name + " is a " + attribute.type() + ", got " + components + " components");
        }
        return attribute;
    }

    private int position(int instance, String name) {
        if (instance < 0 || instance >= capacity) {
            throw new IndexOutOfBoundsException("instance " + instance + " of " + capacity);
        }
        return instance * layout.stride() + layout.offset(name);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (buffer != null) buffer.close();
        MemoryUtil.memFree(data);
    }
}
