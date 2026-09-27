package com.lowdragmc.kilagraph.rendertype.runtime;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;

/** Duck interface on {@link RenderType}: the KilaGraph material that owns it. */
public interface KGRenderType {

    /** {@code null} when the render type isn't a KilaGraph one; still the material once it has closed. */
    @Nullable
    RenderTypeGraphMaterial kilagraph$material();

    void kilagraph$setMaterial(RenderTypeGraphMaterial material);
}
