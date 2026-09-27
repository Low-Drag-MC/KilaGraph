package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.mixin.client.PipelineCacheAccessor;
import com.lowdragmc.kilagraph.mixin.client.RenderSystemAccessor;
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Compiles generated pipelines into the pipeline cache of the last shader reload, and closes them one by one.
 *
 * <p>Minecraft compiles a pipeline lazily at its first draw, and a failed lazy compile throws mid-frame (after a
 * second, resource-only attempt that can't see generated sources and only logs a misleading error). A generated
 * pipeline is compiled here instead — before anything draws with it — so a broken one is refused up front, once.
 * A shader reload replaces the cache (closing everything in it); the new cache then recompiles a live pipeline
 * lazily, reading its sources through {@code ShaderManagerMixin}, or here when a draw asks first.</p>
 *
 * <p>Render thread only.</p>
 */
public final class KGPipelines {

    /** Pipelines that failed to compile against the current cache's sources; not retried until a reload. */
    private static final Set<RenderPipeline> FAILED = Collections.newSetFromMap(new WeakHashMap<>());
    @Nullable private static PipelineCache failedIn;

    private KGPipelines() {}

    /** Compile {@code pipeline} into the current cache unless it is already there; false if it doesn't compile. */
    public static boolean ensureCompiled(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        if (cache == null) return false; // no shader reload has finished yet: nothing to compile against
        var accessor = (PipelineCacheAccessor) cache;
        if (accessor.kilagraph$getCache().containsKey(pipeline)) return true;
        if (failedIn != cache) {
            FAILED.clear(); // a reload may have fixed what broke it (an include from a resource pack)
            failedIn = cache;
        }
        if (FAILED.contains(pipeline)) return false;
        CompiledRenderPipeline compiled = RenderSystem.getDevice()
                .compilePipeline(pipeline, accessor.kilagraph$getShaderSource(), Util.backgroundExecutor())
                .join()
                .finishCompile();
        if (compiled == null) {
            FAILED.add(pipeline);
            return false;
        }
        cache.insert(pipeline, compiled);
        return true;
    }

    /** Whether {@code pipeline} failed to compile against the current sources. */
    public static boolean hasFailed(RenderPipeline pipeline) {
        return failedIn == RenderSystemAccessor.kilagraph$getCurrentPipelineCache() && FAILED.contains(pipeline);
    }

    /** Close {@code pipeline}'s compiled program and drop it from the current cache; no-op when absent. */
    public static void evict(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        FAILED.remove(pipeline);
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        if (cache == null) return;
        CompiledRenderPipeline compiled = ((PipelineCacheAccessor) cache).kilagraph$getCache().remove(pipeline);
        if (compiled != null) compiled.close();
    }
}
