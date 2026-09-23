package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Static geometry uploaded once, for {@link RenderTypeGraphMaterial#drawInstanced}. */
public final class KGMesh implements AutoCloseable {

    private final GpuBuffer vertexBuffer;
    @Nullable private final GpuBuffer indexBuffer;
    private final IndexType indexType;
    private final PrimitiveTopology topology;
    private final int indexCount;

    private KGMesh(GpuBuffer vertexBuffer, @Nullable GpuBuffer indexBuffer, IndexType indexType,
                   PrimitiveTopology topology, int indexCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexType = indexType;
        this.topology = topology;
        this.indexCount = indexCount;
    }

    /** Geometry in {@code material}'s vertex format and topology; {@code null} when {@code geometry} emits nothing. */
    @Nullable
    public static KGMesh build(RenderTypeGraphMaterial material, Consumer<VertexConsumer> geometry) {
        return build(material.renderType().format(), material.renderType().primitiveTopology(), geometry);
    }

    @Nullable
    public static KGMesh build(VertexFormat format, PrimitiveTopology topology, Consumer<VertexConsumer> geometry) {
        RenderSystem.assertOnRenderThread();
        try (var bytes = new ByteBufferBuilder(format.getVertexSize() * 64)) {
            var builder = new BufferBuilder(bytes, topology, format);
            geometry.accept(builder);
            MeshData mesh = builder.build();
            if (mesh == null) return null;
            try (mesh) {
                var device = RenderSystem.getDevice();
                var drawState = mesh.drawState();
                GpuBuffer vertices = device.createBuffer(() -> "KilaGraph mesh vertices", GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
                GpuBuffer indices = mesh.indexBuffer() == null ? null : device.createBuffer(
                        () -> "KilaGraph mesh indices", GpuBuffer.USAGE_INDEX, mesh.indexBuffer());
                return new KGMesh(vertices, indices, drawState.indexType(), drawState.primitiveTopology(),
                        drawState.indexCount());
            }
        }
    }

    void draw(PreparedRenderType prepared) {
        GpuBuffer indices = indexBuffer;
        IndexType type = indexType;
        if (indices == null) {
            // No own indices: the shared sequential buffer, fetched per draw since it can be regrown.
            var sequential = RenderSystem.getSequentialBuffer(topology);
            indices = sequential.getBuffer(indexCount);
            type = sequential.type();
        }
        prepared.drawFromBuffer(vertexBuffer, indices, type, 0, 0, indexCount);
    }

    @Override
    public void close() {
        vertexBuffer.close();
        if (indexBuffer != null) indexBuffer.close();
    }
}
