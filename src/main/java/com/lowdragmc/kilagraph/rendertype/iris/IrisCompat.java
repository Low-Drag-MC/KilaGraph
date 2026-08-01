package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.mixin.iris.IrisMixinPlugin;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

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

    /** Master kill-switch: Iris present <em>and</em> the integration not disabled via
     *  {@code -Dkilagraph.iris.disabled=true}. Read once at class init (toggling requires a restart);
     *  {@code IrisMixinPlugin} reads the same property independently at mixin-bootstrap, so with the switch
     *  off neither the mixins nor any runtime path runs — KilaGraph behaves as if Iris were absent. */
    public static final boolean ENABLED = LOADED && !Boolean.getBoolean("kilagraph.iris.disabled");

    /** Pipelines already handed to {@code IrisApi.assignPipeline}. Iris throws if a pipeline is assigned
     *  twice, and one cached pipeline (per content hash) is shared by many materials (preview + in-world),
     *  so we must assign each pipeline at most once. Weakly held: Iris's own pipeline map keeps a strong
     *  reference for as long as the assignment exists, so the weak set only removes OUR contribution to the
     *  leak of evicted pipelines (and self-heals if Iris ever releases them). {@code RenderPipeline} doesn't
     *  override equals/hashCode, so identity semantics are preserved; a re-add after a GC'd entry hits the
     *  "already assigned" swallow in {@link #assignToEntities}, which is already correct. */
    private static final Set<RenderPipeline> ASSIGNED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Quiet gap after the last new surface before we reload, so a burst coalesces into one recompile. */
    private static final long RELOAD_DEBOUNCE_NANOS = 300_000_000L; // 300 ms
    /** Cooldown between <em>failed</em> reload attempts, and the cap after which we give up. */
    private static final long RETRY_COOLDOWN_NANOS = 5_000_000_000L; // 5 s
    private static final int MAX_RELOAD_ATTEMPTS = 5;

    /** Behavioural dead-seam latch: a reload demonstrably never reached {@code IrisShaderInjector} (the
     *  {@code MixinShaderCreator} injection point silently missed under {@code require=0}), or reloads keep
     *  failing — stop requesting reloads; KilaGraph geometry keeps rendering as shaderpack passthrough.
     *  Self-healing: {@link #seamAlive()} clears it the moment the injector provably runs again. */
    private static volatile boolean seamDead;
    /** Hard latch (never cleared): a critical mixin didn't apply at all — see {@link #warnOnceIfSeamsMissing}. */
    private static volatile boolean mixinsMissing;
    /** Hard latch: the reflective shadow-pass mapping registration failed (future Iris changed
     *  {@code IrisPipelines.assignToShadow}) — our geometry is then skipped during the shadow pass. */
    private static volatile boolean shadowAssignBroken;
    private static boolean seamCheckDone;
    private static long retryBackoffUntilNanos;
    private static int failedReloadAttempts;
    /** The Iris pipeline instance we last saw (weak — never pin a destroyed pipeline), to detect swaps. */
    private static WeakReference<Object> lastPipeline = new WeakReference<>(null);

    private IrisCompat() {}

    private static boolean detect() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, IrisCompat.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True only when the integration is enabled <em>and</em> a shaderpack is currently active. */
    public static boolean isShaderPackInUse() {
        return ENABLED && Api.shaderPackInUse();
    }

    /** Number of KilaGraph pipelines registered with Iris so far (diagnostics). */
    public static int assignedCount() {
        return ASSIGNED.size();
    }

    /**
     * Whether a graph whose composed vertex format is {@code format} may be routed through a shaderpack at
     * all — the precondition for calling {@link #assignToEntities} (and for registering an injected surface).
     *
     * <p><b>Why this check is ours to make.</b> Iris's own {@code ShaderKey.findBestMatch} only ever returns a
     * key whose {@code vertexFormat} is <em>identity-equal</em> to the pipeline's, so the public
     * {@code IrisApi.assignPipeline} cannot produce a layout mismatch. {@link #assignToEntities} deliberately
     * skips that lookup (it resolved to the alpha-tested {@code ENTITIES_ALPHA} key, which discarded all our
     * fragments) and names the {@code ShaderKey} directly — so the format check findBestMatch was performing
     * has to be performed here instead.</p>
     *
     * <p><b>What an unchecked mismatch does.</b> Iris binds the pack program's attribute locations by walking
     * the <em>ShaderKey's</em> format ({@code VertexFormat.bindAttributesIris} → {@code glBindAttribLocation(
     * i++, name)}), while the VAO is laid out from <em>our</em> pipeline's format. Any other composition
     * shifts every attribute after the first: with a BLOCK-format graph the pack's {@code iris_UV1} reads our
     * lightmap, {@code iris_UV2} reads our normal bytes, and {@code iris_Normal} reads nothing at all (a
     * disabled attribute yields the generic default) — the geometry draws lightmap-black with garbage uv, with
     * no GL error and no driver warning anywhere.</p>
     *
     * <p><b>Why identity and not {@code equals}.</b> Iris upgrades exactly the {@code DefaultVertexFormat}
     * constants to its extended formats ({@code ENTITY → IrisVertexFormats.ENTITY}) — and extends the vertex
     * writes to match — by that same identity test, so a structurally-equal-but-distinct format silently
     * misses both halves. {@code KGVertexFormat} returns the stock constant whenever a graph's composition
     * matches one, which is precisely what makes entity-format graphs work today.</p>
     *
     * <p>Only {@link DefaultVertexFormat#ENTITY} qualifies for now: {@code assignToEntities} is the only
     * routing we implement. Supporting BLOCK-format graphs means mapping them to {@code TERRAIN_*} /
     * {@code SHADOW_TERRAIN_CUTOUT} instead, which is a separate (unverified) piece of work.</p>
     */
    public static boolean supportsVertexFormat(VertexFormat format) {
        return format == DefaultVertexFormat.ENTITY;
    }

    /**
     * Register {@code pipeline} so that, while a shaderpack is active, geometry drawn with it is routed
     * through the shaderpack's entities gbuffers program — {@code ENTITIES_TRANSLUCENT} when
     * {@code translucent}, {@code ENTITIES_CUTOUT} when the graph alpha-discards, else
     * {@code ENTITIES_SOLID}. Idempotent (assigns each pipeline at most once, swallows Iris's "already
     * assigned"). No-op without Iris.
     *
     * <p>Note the CUTOUT-keyed pack program applies <em>its own</em> alpha test (typically 0.1), not the
     * graph's configured cutoff — an exact cutoff is unreachable inside a pack-owned program; the injection
     * snippet deliberately drops the graph's own {@code discard} for the same reason (the pack tests the
     * alpha our surface returns).</p>
     *
     * <p><b>Why not the public {@code IrisApi.assignPipeline(.., IrisProgram.ENTITIES)}?</b> That routes
     * through {@code ShaderKey.findBestMatch}, which for the ENTITIES program returns the <em>alpha-tested</em>
     * key {@code ENTITIES_ALPHA} ("okay program match" in Iris's log) whose alpha test discarded <em>all</em>
     * our fragments — geometry was lit-but-invisible (confirmed in the debugger). We instead assign the
     * internal {@code ShaderKey} directly. This uses Iris-internal classes, consistent with our
     * {@code ShaderCreator} mixin already depending on internals.</p>
     *
     * <p><b>Precondition:</b> {@link #supportsVertexFormat} on the graph's composed vertex format. These keys
     * carry the entity format and nothing downstream re-checks it — bypassing {@code findBestMatch} (above)
     * also bypassed its format check, so the caller owns it.</p>
     */
    public static void assignToEntities(RenderPipeline pipeline, boolean translucent, boolean cutout) {
        if (!ENABLED || !ASSIGNED.add(pipeline)) return;
        try {
            Api.assignEntities(pipeline, translucent, cutout);
        } catch (IllegalStateException alreadyAssigned) {
            // Iris already has a mapping for this pipeline's location — the mapping exists, so this is fine.
        }
        // Iris ≥1.11 keeps SEPARATE pipeline→ShaderKey maps for the main and SHADOW passes, and the public
        // assignPipeline only fills the main one. Without a shadow entry, Iris logs "Missing program ... in
        // override list" during its shadow pass and falls back to OUR OWN program — which then rasterises
        // into the shadow framebuffer with main-pass uniforms, CORRUPTING the shadow map (view-dependent
        // black/red artifacts on our geometry via the pack's shadow sampling; confirmed in the debugger).
        // assignToShadow is private → reflection; on failure we latch shadowAssignBroken and the draw path
        // SKIPS our geometry during the shadow pass instead (no shadow cast, but nothing corrupted).
        if (!shadowAssignBroken) {
            try {
                Api.assignShadow(pipeline);
            } catch (Throwable t) {
                shadowAssignBroken = true;
                LOGGER.warn("[KilaGraph][Iris] could not register a shadow-pass mapping (Iris internals "
                        + "changed?) — KilaGraph geometry will not cast shadows under shaderpacks", t);
            }
        }
    }

    /**
     * Whether a KilaGraph draw must be skipped right now because Iris is rendering its <b>shadow pass</b>
     * and we could not register a shadow-pass program mapping ({@link #assignToEntities}): drawing would
     * run our own program inside the shadow framebuffer and corrupt the shadow map for everything nearby.
     * Cheap: only consults Iris when the (rare) broken-latch is set.
     */
    public static boolean shouldSkipShadowDraw() {
        if (!ENABLED || !shadowAssignBroken) return false;
        try {
            return Api.renderingShadowPass();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Per-client-tick housekeeping (driven by {@code IrisDebugCommand.onClientTick}): invalidate the
     * GL-program-keyed caches when Iris swapped its pipeline, then drive the staleness reload.
     */
    public static void clientTick() {
        if (!ENABLED) return;
        pollPipelineChange();
        reloadShadersIfStale();
    }

    /** Proof the {@code ShaderCreator} injection seam works (called from {@code IrisShaderInjector.inject}) —
     *  clears the behavioural dead-seam latch so a transient failure self-heals. */
    static void seamAlive() {
        seamDead = false;
    }

    /**
     * Detect an Iris pipeline swap (pack reload/toggle, dimension change, resize-triggered recreation) by
     * identity and clear {@code IrisSurfaceUniform}'s GL-program-keyed caches: Iris deletes its old GL
     * programs and GL may <b>reuse the integer program ids</b>, so a stale cached uniform location would
     * write {@code kg_surface_id} (or bind UBOs/samplers) into an unrelated program — a "breaks other
     * content" bug, not just waste. Backed up by the per-inject clear in {@code IrisShaderInjector.inject}
     * (this poll also catches reloads that never reach our injector, e.g. the pack being toggled OFF).
     */
    private static void pollPipelineChange() {
        try {
            Object pipeline = Api.pipelineNullable();
            if (pipeline != lastPipeline.get()) {
                lastPipeline = new WeakReference<>(pipeline);
                IrisSurfaceUniform.invalidateCaches();
            }
        } catch (Throwable t) {
            // A future Iris changed PipelineManager — degrade to the inject-time invalidation only.
        }
    }

    /**
     * Reload the shaderpack if the live {@link IrisSurfaceRegistry} has advanced past what the current programs
     * were injected with (a new world graph appeared since they compiled, e.g. a material created lazily after
     * the pack loaded) — so {@code ShaderCreator.createShader} re-runs and bakes in the new
     * {@code kg_surface_<id>}. Until then that geometry hits the dispatch fallback (renders black/passthrough).
     *
     * <p><b>Must be called from the client tick</b> (see {@link #clientTick}), NOT mid-frame:
     * {@code Iris.reload()} destroys + recreates the world rendering pipeline, and doing that during
     * {@code renderLevel} crashes ({@code Tried to use a destroyed world rendering pipeline}). The tick is the
     * same safe point Iris uses for its own reload keybind.</p>
     *
     * <p><b>Success is verified, not assumed:</b> only when the post-reload
     * {@code IrisShaderInjector.lastInjectedGeneration()} caught up is the staleness considered cleared. A
     * reload that ran with a pack active yet never reached our injector proves the mixin seam is dead
     * ({@code require=0} silently missed its injection point) → {@link #seamDead}, stop reloading. A throwing
     * reload retries after a cooldown, up to {@link #MAX_RELOAD_ATTEMPTS} consecutive failures.</p>
     */
    public static void reloadShadersIfStale() {
        if (!isShaderPackInUse()) return;
        warnOnceIfSeamsMissing();
        if (seamDead || mixinsMissing) return;
        int generation = IrisSurfaceRegistry.generation();
        if (IrisShaderInjector.lastInjectedGeneration() >= generation) return; // programs already current
        // Debounce: wait for a quiet gap after the last new surface so a burst of new graphs (cycling
        // holograms, live editing) coalesces into ONE recompile instead of one per graph.
        if (System.nanoTime() - IrisSurfaceRegistry.lastChangeNanos() < RELOAD_DEBOUNCE_NANOS) return;
        if (System.nanoTime() < retryBackoffUntilNanos) return; // cooling down after a failed attempt
        try {
            Api.reload();
            if (IrisShaderInjector.lastInjectedGeneration() >= generation) {
                failedReloadAttempts = 0;
                LOGGER.info("[KilaGraph][Iris] reloaded shaderpack to inject new surface(s) ({} live)",
                        IrisSurfaceRegistry.snapshot().size());
            } else if (Api.shaderPackInUse()) {
                seamDead = true;
                LOGGER.error("[KilaGraph][Iris] shaderpack reloaded but the injection seam never ran "
                        + "(MixinShaderCreator's injection point missed?) — degrading to passthrough: "
                        + "KilaGraph materials keep rendering without graph shading; the pack is unaffected.");
            }
            // else: the pack was toggled off during the reload — nothing to verify; retry when it's back.
        } catch (Exception e) {
            if (++failedReloadAttempts >= MAX_RELOAD_ATTEMPTS) {
                seamDead = true;
                LOGGER.error("[KilaGraph][Iris] {} consecutive shaderpack reload failures — giving up "
                        + "(new surfaces stay passthrough until a manual shader reload)", failedReloadAttempts, e);
            } else {
                retryBackoffUntilNanos = System.nanoTime() + RETRY_COOLDOWN_NANOS;
                LOGGER.warn("[KilaGraph][Iris] shaderpack reload failed — retrying in 5s", e);
            }
        }
    }

    /**
     * The GL texture id + size of Iris's <b>colortex0 main texture</b> — the buffer the world is actually
     * rendered into while a shaderpack is active (MC's main colour target is stale until Iris's final blit).
     * {@code null} when unavailable (no pack, not an {@code IrisRenderingPipeline}, pipeline destroyed, or a
     * future Iris changed internals) — the caller then falls back to the vanilla capture source.
     *
     * <p><b>Never cache the returned id:</b> it goes stale on pack reload/toggle, window resize, and
     * dimension change. Re-fetch every frame — the accessor chain is cheap (only the private
     * {@code renderTargets} {@link java.lang.reflect.Field} object is cached, never its value).</p>
     *
     * @return {@code {glTextureId, width, height}} or {@code null}
     */
    public static int @Nullable [] irisColorTargetInfo() {
        if (!ENABLED) return null;
        try {
            return Api.colorTargetInfo();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * One-time handshake once a pack compile has demonstrably reached the injector (which proves the mixin
     * target classes are loaded): if the <em>binding</em> seam ({@code MixinGlCommandEncoderSurface}) never
     * applied, per-draw {@code kg_surface_id}/UBO binding can't happen — everything ours would render
     * passthrough and reloads would be pointless stutter, so latch {@link #mixinsMissing} and say so loudly.
     * (A dead {@code MixinShaderCreator} is caught behaviourally in {@link #reloadShadersIfStale} instead —
     * with it dead, {@code lastInjectedGeneration} stays -1 and this check never arms.)
     */
    private static void warnOnceIfSeamsMissing() {
        if (seamCheckDone) return;
        if (IrisShaderInjector.lastInjectedGeneration() < 0) return; // no pack compile seen yet
        seamCheckDone = true;
        try {
            if (!IrisMixinPlugin.wasApplied("MixinGlCommandEncoderSurface")) {
                mixinsMissing = true;
                LOGGER.error("[KilaGraph][Iris] MixinGlCommandEncoderSurface did not apply (MC/Iris internals "
                        + "changed?) — integration degraded: KilaGraph materials render as shaderpack "
                        + "passthrough; everything else is unaffected.");
            }
        } catch (Throwable ignored) {
            // never let the handshake break the client tick
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

        static void assignEntities(RenderPipeline pipeline, boolean translucent, boolean cutout) {
            net.irisshaders.iris.pipeline.programs.ShaderKey key = translucent
                    ? net.irisshaders.iris.pipeline.programs.ShaderKey.ENTITIES_TRANSLUCENT
                    : cutout
                    ? net.irisshaders.iris.pipeline.programs.ShaderKey.ENTITIES_CUTOUT
                    : net.irisshaders.iris.pipeline.programs.ShaderKey.ENTITIES_SOLID;
            net.irisshaders.iris.pipeline.IrisPipelines.assignPipeline(pipeline, key);
        }

        static Object pipelineNullable() {
            return net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
        }

        static boolean renderingShadowPass() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isRenderingShadowPass();
        }

        /** Cached private {@code IrisPipelines.assignToShadow(RenderPipeline, Function)}. */
        private static java.lang.reflect.Method assignToShadowMethod;

        /**
         * Register {@code pipeline} in Iris's <b>shadow-pass</b> override map → {@code SHADOW_ENTITIES_CUTOUT}
         * (the entity-vertex-format shadow program; alpha-tested, so cutout surfaces shadow correctly — the
         * same key vanilla entities use). The shadow program is also injected by {@code IrisShaderInjector}
         * (it samples the albedo), so our per-draw {@code kg_surface_id}/bindings apply there too.
         * Idempotent on Iris's side (re-registration only warns).
         */
        static void assignShadow(RenderPipeline pipeline) throws Exception {
            if (assignToShadowMethod == null) {
                var m = net.irisshaders.iris.pipeline.IrisPipelines.class.getDeclaredMethod(
                        "assignToShadow", RenderPipeline.class, it.unimi.dsi.fastutil.Function.class);
                m.setAccessible(true);
                assignToShadowMethod = m;
            }
            it.unimi.dsi.fastutil.Function<net.irisshaders.iris.pipeline.IrisRenderingPipeline,
                    net.irisshaders.iris.pipeline.programs.ShaderKey> fn =
                    p -> net.irisshaders.iris.pipeline.programs.ShaderKey.SHADOW_ENTITIES_CUTOUT;
            assignToShadowMethod.invoke(null, pipeline, fn);
        }

        /** Cached {@link java.lang.reflect.Field} for {@code IrisRenderingPipeline.renderTargets} — the ONE
         *  private member this integration reads (everything else in the chain is public API/classes). */
        private static java.lang.reflect.Field renderTargetsField;

        static int[] colorTargetInfo() throws Exception {
            var pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
            if (!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline irp)) return null;
            if (renderTargetsField == null) {
                var f = net.irisshaders.iris.pipeline.IrisRenderingPipeline.class.getDeclaredField("renderTargets");
                f.setAccessible(true);
                renderTargetsField = f;
            }
            var targets = (net.irisshaders.iris.targets.RenderTargets) renderTargetsField.get(irp);
            if (targets == null) return null;
            return new int[]{
                    targets.getOrCreate(0).getMainTexture(),
                    targets.getCurrentWidth(),
                    targets.getCurrentHeight()
            };
        }
    }
}
