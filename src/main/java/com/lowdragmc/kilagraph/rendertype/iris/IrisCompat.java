package com.lowdragmc.kilagraph.rendertype.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Soft bridge to the Iris public API ({@code net.irisshaders.iris.api.v0}). Iris is an optional
 * dependency (dev-only {@code localImplementation}, not bundled), so every Iris reference is isolated in
 * the nested {@link Api} holder, which is only class-loaded when a method is actually called — and those
 * calls are all guarded by {@link #LOADED}. When Iris is absent the holder never loads, so there is no
 * {@code NoClassDefFoundError}.
 */
public final class IrisCompat {

    /** Whether Iris is on the classpath. Checked without initializing the class (no side effects). */
    public static final boolean LOADED = detect();

    /** Pipelines already handed to {@code IrisApi.assignPipeline}. Iris throws if a pipeline is assigned
     *  twice, and one cached pipeline (per content hash) is shared by many materials (preview + in-world),
     *  so we must assign each pipeline at most once. */
    private static final Set<RenderPipeline> ASSIGNED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The registry generation we last attempted a shaderpack reload for — so we reload at most once per new
     *  surface set (no per-tick reload spam if a reload doesn't clear the staleness). */
    private static int lastReloadGeneration = -1;
    /** Quiet gap after the last new surface before we reload, so a burst coalesces into one recompile. */
    private static final long RELOAD_DEBOUNCE_NANOS = 300_000_000L; // 300 ms

    private IrisCompat() {}

    private static boolean detect() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, IrisCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True only when Iris is present <em>and</em> a shaderpack is currently active. */
    public static boolean isShaderPackInUse() {
        return LOADED && Api.shaderPackInUse();
    }

    /** Number of KilaGraph pipelines registered with Iris so far (diagnostics). */
    public static int assignedCount() {
        return ASSIGNED.size();
    }

    /**
     * Register {@code pipeline} so that, while a shaderpack is active, geometry drawn with it is routed
     * through the shaderpack's entities gbuffers program — {@code ENTITIES_TRANSLUCENT} when
     * {@code translucent}, else {@code ENTITIES_SOLID}. Idempotent (assigns each pipeline at most once,
     * swallows Iris's "already assigned"). No-op without Iris.
     *
     * <p><b>Why not the public {@code IrisApi.assignPipeline(.., IrisProgram.ENTITIES)}?</b> That routes
     * through {@code ShaderKey.findBestMatch}, which for the ENTITIES program returns the <em>alpha-tested</em>
     * key {@code ENTITIES_ALPHA} ("okay program match" in Iris's log) whose alpha test discarded <em>all</em>
     * our fragments — geometry was lit-but-invisible (confirmed in the debugger). We instead assign the
     * internal {@code ShaderKey} directly. This uses Iris-internal classes, consistent with our
     * {@code ShaderCreator} mixin already depending on internals. TODO(M3): also pick CUTOUT when the graph
     * actually alpha-discards; expose a manual override.</p>
     */
    public static void assignToEntities(RenderPipeline pipeline, boolean translucent) {
        if (!LOADED || !ASSIGNED.add(pipeline)) return;
        try {
            Api.assignEntities(pipeline, translucent);
        } catch (IllegalStateException alreadyAssigned) {
            // Iris already has a mapping for this pipeline's location — the mapping exists, so this is fine.
        }
    }

    /**
     * Reload the shaderpack if the live {@link IrisSurfaceRegistry} has advanced past what the current programs
     * were injected with (a new world graph appeared since they compiled, e.g. a material created lazily after
     * the pack loaded) — so {@code ShaderCreator.createShader} re-runs and bakes in the new
     * {@code kg_surface_<id>}. Until then that geometry hits the dispatch fallback (renders black/passthrough).
     *
     * <p><b>Must be called from the client tick</b> (see {@code IrisDebugCommand.onClientTick}), NOT mid-frame:
     * {@code Iris.reload()} destroys + recreates the world rendering pipeline, and doing that during
     * {@code renderLevel} crashes ({@code Tried to use a destroyed world rendering pipeline}). The tick is the
     * same safe point Iris uses for its own reload keybind. Reloads at most once per new surface set
     * ({@link #lastReloadGeneration}) so a reload that doesn't clear staleness can't spam. No-op without Iris /
     * when the programs are already current.</p>
     */
    public static void reloadShadersIfStale() {
        if (!isShaderPackInUse()) return;
        int generation = IrisSurfaceRegistry.generation();
        if (generation == IrisShaderInjector.lastInjectedGeneration()) return; // programs already current
        if (generation == lastReloadGeneration) return;                        // already tried for this set
        // Debounce: wait for a quiet gap after the last new surface so a burst of new graphs (cycling
        // holograms, live editing) coalesces into ONE recompile instead of one per graph.
        if (System.nanoTime() - IrisSurfaceRegistry.lastChangeNanos() < RELOAD_DEBOUNCE_NANOS) return;
        lastReloadGeneration = generation;
        try {
            Api.reload();
            LOGGER.info("[KilaGraph][Iris] reloaded shaderpack to inject new surface(s) ({} live)",
                    IrisSurfaceRegistry.snapshot().size());
        } catch (Exception e) {
            LOGGER.warn("[KilaGraph][Iris] shaderpack reload failed", e);
        }
    }

    /** Holder for the actual Iris calls — loaded lazily, only when {@link #LOADED} is true. */
    private static final class Api {
        static boolean shaderPackInUse() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }

        static void reload() throws java.io.IOException {
            net.irisshaders.iris.Iris.reload();
        }

        static void assignEntities(RenderPipeline pipeline, boolean translucent) {
            net.irisshaders.iris.pipeline.programs.ShaderKey key = translucent
                    ? net.irisshaders.iris.pipeline.programs.ShaderKey.ENTITIES_TRANSLUCENT
                    : net.irisshaders.iris.pipeline.programs.ShaderKey.ENTITIES_SOLID;
            net.irisshaders.iris.pipeline.IrisPipelines.assignPipeline(pipeline, key);
        }
    }
}
