package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;

/** Duck interface on {@link PreparedRenderType}: the KilaGraph material it was prepared from. */
public interface KGPreparedRenderType {

    /** {@code null} when the render type isn't a KilaGraph one. */
    @Nullable
    RenderTypeGraphMaterial kilagraph$material();

    void kilagraph$setMaterial(RenderTypeGraphMaterial material);

    /** Draw {@code count} instances, reading per-instance data from {@code instances} (null: the default). */
    void kilagraph$setInstances(@Nullable GpuBufferSlice instances, int count);
}
