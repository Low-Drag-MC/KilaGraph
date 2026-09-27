package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;

/** Duck interface on {@link PreparedRenderType}: the KilaGraph material it was prepared from, and what it binds. */
public interface KGPreparedRenderType {

    /** {@code null} when the render type isn't a KilaGraph one. */
    @Nullable
    RenderTypeGraphMaterial kilagraph$material();

    void kilagraph$setMaterial(RenderTypeGraphMaterial material);

    /** The material's values the draw binds, taken when it was prepared; {@code null} before. */
    @Nullable
    RenderTypeGraphMaterial.Bindings kilagraph$bindings();

    void kilagraph$setBindings(RenderTypeGraphMaterial.Bindings bindings);

    /** Draw {@code count} instances, reading per-instance data from {@code instances} (null: the default). */
    void kilagraph$setInstances(@Nullable GpuBufferSlice instances, int count);
}
