package com.lowdragmc.kilagraph.mixin.client;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pipeline cache of the last shader reload, which generated pipelines are compiled into and evicted from. */
@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {
    @Accessor("currentPipelineCache")
    @Nullable
    static PipelineCache kilagraph$getCurrentPipelineCache() {
        throw new AssertionError();
    }
}
