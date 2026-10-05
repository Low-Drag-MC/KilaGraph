package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.mixin.client.PipelineCacheAccessor;
import com.lowdragmc.kilagraph.mixin.client.RenderSystemAccessor;
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Compiles generated pipelines into the pipeline cache of the last shader reload, and closes them one by one.
 *
 * <p>Minecraft compiles a pipeline lazily at its first draw, and a failed lazy compile throws mid-frame (after a
 * second, resource-only attempt that can't see generated sources and only logs a misleading error). A generated
 * pipeline is compiled here instead — before anything draws with it — so a broken one is refused up front, once.
 * A shader reload replaces the cache (closing everything in it); the new cache then recompiles a live pipeline
 * lazily, reading its sources through {@code ShaderManagerMixin}, or here when a draw asks first.</p>
 *
 * <p>A compile is all but its last step off the render thread (GLSL to SPIR-V, the backend's pipeline), so it can
 * run in the background ({@link #compileInBackground}) for a caller that keeps drawing meanwhile — the editor's
 * previews; {@link #ensureCompiled} waits for it.</p>
 *
 * <p>Render thread only.</p>
 */
public final class KGPipelines {

    /** What {@link #compileInBackground} has got to. */
    public enum State { COMPILED, COMPILING, FAILED }

    /** Pipelines that failed to compile against the current cache's sources; not retried until a reload. */
    private static final Set<RenderPipeline> FAILED = Collections.newSetFromMap(new WeakHashMap<>());
    @Nullable private static PipelineCache failedIn;
    /** Pipelines compiling in the background, with the cache they compile against. */
    private static final Map<RenderPipeline, Background> BACKGROUND = new IdentityHashMap<>();
    /** Background compiles nobody waits for anymore (evicted, or for a replaced cache): closed once they finish. */
    private static final List<CompletableFuture<CompiledRenderPipeline.Pending>> ABANDONED = new ArrayList<>();

    private record Background(PipelineCache cache, CompletableFuture<CompiledRenderPipeline.Pending> compile) {}

    private KGPipelines() {}

    /** Compile {@code pipeline} into the current cache unless it is already there; false if it doesn't compile. */
    public static boolean ensureCompiled(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        if (cache == null) return false; // no shader reload has finished yet: nothing to compile against
        if (((PipelineCacheAccessor) cache).kilagraph$getCache().containsKey(pipeline)) return true;
        if (failed(pipeline, cache)) return false;
        // Compiling in the background already: wait for that rather than compile it twice.
        Background background = background(pipeline, cache);
        BACKGROUND.remove(pipeline);
        return finish(pipeline, cache, background != null ? background.compile() : start(pipeline, cache));
    }

    /**
     * Compile {@code pipeline} into the current cache off the render thread, unless it is compiled, compiling or
     * failed; call again (on later frames) for the outcome — the compile is finished, on the render thread, by the
     * call that finds it done.
     */
    public static State compileInBackground(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        if (cache == null) return State.FAILED;
        if (((PipelineCacheAccessor) cache).kilagraph$getCache().containsKey(pipeline)) return State.COMPILED;
        if (failed(pipeline, cache)) return State.FAILED;
        Background background = background(pipeline, cache);
        if (background == null) {
            BACKGROUND.put(pipeline, new Background(cache, start(pipeline, cache)));
            return State.COMPILING;
        }
        if (!background.compile().isDone()) return State.COMPILING;
        BACKGROUND.remove(pipeline);
        return finish(pipeline, cache, background.compile()) ? State.COMPILED : State.FAILED;
    }

    /** Whether {@code pipeline} is compiled in the current cache (a shader reload empties it). */
    public static boolean isCompiled(RenderPipeline pipeline) {
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        return cache != null && ((PipelineCacheAccessor) cache).kilagraph$getCache().containsKey(pipeline);
    }

    /** Whether {@code pipeline} failed to compile against the current sources. */
    public static boolean hasFailed(RenderPipeline pipeline) {
        return failedIn == RenderSystemAccessor.kilagraph$getCurrentPipelineCache() && FAILED.contains(pipeline);
    }

    /**
     * Close {@code pipeline}'s compiled program and drop it from the current cache; no-op when absent. A compile of
     * it still running in the background is closed once it finishes, and returned — it still reads the sources.
     */
    @Nullable
    public static CompletableFuture<?> evict(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        FAILED.remove(pipeline);
        Background background = BACKGROUND.remove(pipeline);
        if (background != null) ABANDONED.add(background.compile());
        PipelineCache cache = RenderSystemAccessor.kilagraph$getCurrentPipelineCache();
        if (cache != null) {
            CompiledRenderPipeline compiled = ((PipelineCacheAccessor) cache).kilagraph$getCache().remove(pipeline);
            if (compiled != null) compiled.close();
        }
        return background == null ? null : background.compile();
    }

    /** Close the abandoned background compiles that have finished. Render thread, once a frame. */
    public static void closeAbandoned() {
        ABANDONED.removeIf(compile -> {
            if (!compile.isDone()) return false;
            try {
                CompiledRenderPipeline compiled = compile.join().finishCompile();
                if (compiled != null) compiled.close();
            } catch (RuntimeException ignored) {
                // it failed: nothing to close
            }
            return true;
        });
    }

    /** Whether {@code pipeline} failed against {@code cache}'s sources — forgetting the failures of an older cache:
     *  a reload may have fixed what broke them (an include from a resource pack). */
    private static boolean failed(RenderPipeline pipeline, PipelineCache cache) {
        if (failedIn != cache) {
            FAILED.clear();
            failedIn = cache;
        }
        return FAILED.contains(pipeline);
    }

    /** {@code pipeline}'s background compile against {@code cache}; one against an older cache is abandoned. */
    @Nullable
    private static Background background(RenderPipeline pipeline, PipelineCache cache) {
        Background background = BACKGROUND.get(pipeline);
        if (background == null || background.cache() == cache) return background;
        BACKGROUND.remove(pipeline);
        ABANDONED.add(background.compile());
        return null;
    }

    private static CompletableFuture<CompiledRenderPipeline.Pending> start(RenderPipeline pipeline, PipelineCache cache) {
        try {
            return RenderSystem.getDevice().compilePipeline(pipeline,
                    ((PipelineCacheAccessor) cache).kilagraph$getShaderSource(), Util.backgroundExecutor());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Wait for {@code compile}, finish it and put it in {@code cache}; false (remembered) if it doesn't compile. */
    private static boolean finish(RenderPipeline pipeline, PipelineCache cache,
                                  CompletableFuture<CompiledRenderPipeline.Pending> compile) {
        CompiledRenderPipeline compiled;
        try {
            compiled = compile.join().finishCompile();
        } catch (RuntimeException e) {
            // A backend can throw instead of returning no pipeline; either way this one doesn't compile.
            Kilagraph.LOGGER.error("[KilaGraph] compiling {} threw", pipeline.getLocation(), e);
            compiled = null;
        }
        if (compiled == null) {
            FAILED.add(pipeline);
            return false;
        }
        cache.insert(pipeline, compiled);
        return true;
    }
}
