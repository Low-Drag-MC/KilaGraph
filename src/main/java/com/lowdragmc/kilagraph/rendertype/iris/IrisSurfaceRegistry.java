package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the live set of KilaGraph fragment surfaces that must be injected into an Iris shaderpack's shared
 * gbuffers program, each addressed by a non-zero {@code kg_surface_id}. Because Iris compiles <b>one program
 * per {@code ShaderKey}</b>, every KilaGraph material drawn with that key shares the same GL program, so the
 * program must contain <em>every</em> material's {@code kg_surface_N(uv)} function and dispatch on the id the
 * runtime sets per draw (see {@link IrisShaderInjector} / {@link IrisSurfaceUniform}).
 *
 * <p>Entries are keyed by graph <b>content hash</b> (so the many materials sharing one graph share one id +
 * snippet) and reference-counted in lockstep with {@code RenderTypeFactory}'s pipeline cache. Registering a
 * graph the program doesn't yet contain bumps {@link #generation()} — the cue for {@code IrisCompat} to
 * schedule a shaderpack recompile so {@code createShader} re-runs and picks up the new function.</p>
 *
 * <p>Per-id <b>namespacing</b> is applied here, not in the compiler: each snippet is emitted with the raw
 * names {@code KG_Material}/{@code kg_material} and {@code kg_grad_*}, which would collide between snippets
 * (different fields under the same block name; per-compile gradient counters). We suffix exactly those with
 * the id. Everything else is either globally unique ({@code kg_tex_<uid>}, EXPOSED {@code kg_*} fields, which
 * live inside the namespaced block) or an identical shared declaration the injector dedups by exact string
 * ({@code kg_MissingSampler}, {@code KG_Globals}/{@code KG_Transforms} blocks, procedural helpers).</p>
 *
 * <p>Render-thread only (called from material build + shader compile); guarded for safety.</p>
 */
public final class IrisSurfaceRegistry {

    /** A live surface, with all collision-prone identifiers already id-namespaced. */
    public record Surface(int id, List<String> declarationUnits, List<String> functions, String surfaceFunction) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    /** Content hashes we've already logged a not-injection-compatible warning for (avoid spam). */
    private static final Set<String> LOGGED_INCOMPATIBLE = new LinkedHashSet<>();
    /** content hash -> injected surface (insertion order = id order). Cached for the whole session — see
     *  {@link #release} — so a material recreated with the same graph reuses its id without a reload. */
    private static final Map<String, Surface> BY_HASH = new LinkedHashMap<>();
    private static int nextId = 1;
    /** Bumped whenever a NEW surface is added — lets the reload trigger detect a program that predates it. */
    private static int generation = 0;
    /** {@code System.nanoTime()} of the last new-surface registration, for debouncing the shaderpack reload
     *  (coalesce a burst of new graphs — e.g. cycling displays or live editing — into one reload). */
    private static long lastChangeNanos = 0;

    private IrisSurfaceRegistry() {}

    /**
     * Register {@code compiled}'s injection snippet (idempotent by content hash) and return its non-zero
     * surface id, or {@code 0} when the graph is not injection-compatible ({@code injectionSnippet()} is
     * {@code null}) — that material then falls back to the M0 passthrough under Iris.
     *
     * <p>Surfaces are cached for the <b>session</b>, deliberately decoupled from material lifetime (see
     * {@link #release}): re-registering the same graph hash returns the existing id with no generation bump,
     * so churn from materials being closed+recreated (switching holograms, exiting the editor) does <b>not</b>
     * force a shaderpack recompile — only a genuinely new graph does.</p>
     */
    public static int register(CompiledShaderGraph compiled) {
        String hash = compiled.contentHash();
        InjectionSnippet snippet = compiled.injectionSnippet();
        if (snippet == null) {
            synchronized (LOCK) {
                if (LOGGED_INCOMPATIBLE.add(hash)) {
                    LOGGER.info("[KilaGraph][Iris] graph {} has no injection snippet -> passthrough (id 0). "
                            + "Either not injection-compatible (Fog/Lighting/Scene) or compile threw.", hash);
                }
            }
            return 0;
        }
        synchronized (LOCK) {
            Surface surface = BY_HASH.get(hash);
            if (surface == null) {
                surface = build(nextId++, snippet);
                BY_HASH.put(hash, surface);
                generation++;
                lastChangeNanos = System.nanoTime();
                LOGGER.info("[KilaGraph][Iris] registered surface id={} for graph {} (decls={}, fns={}, total={})",
                        surface.id(), hash, surface.declarationUnits().size(), surface.functions().size(), BY_HASH.size());
            }
            return surface.id();
        }
    }

    /** A human-readable dump of the live surfaces + generation state for the {@code /kgiris surfaces} command. */
    public static String debugSummary() {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder("surfaces=").append(BY_HASH.size())
                    .append(", generation=").append(generation);
            for (Map.Entry<String, Surface> e : BY_HASH.entrySet()) {
                sb.append("\n  id=").append(e.getValue().id()).append(" hash=").append(e.getKey());
            }
            return sb.toString();
        }
    }

    /**
     * No-op: a surface is <b>kept</b> after its material closes (cached for the session), so a material
     * recreated with the same graph hash reuses the already-injected surface instead of re-registering and
     * forcing another shaderpack reload. Materials still close their own GPU resources via
     * {@code RenderTypeFactory.release}; only the injected GLSL surface persists here. (Kept as a method so the
     * factory's release path stays symmetric and a future LRU cap can hook in.)
     */
    public static void release(String contentHash) {
        // intentionally empty — see the contract above
    }

    /** Nanotime of the last new-surface registration (for the reload debounce). */
    public static long lastChangeNanos() {
        synchronized (LOCK) {
            return lastChangeNanos;
        }
    }

    /** An ordered snapshot of the live surfaces for the injector (defensive copy). */
    public static List<Surface> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(BY_HASH.values());
        }
    }

    /** A monotonically increasing counter bumped on every change to the live surface set. */
    public static int generation() {
        synchronized (LOCK) {
            return generation;
        }
    }

    /** Apply per-id namespacing to a snippet's pieces and assemble its {@code kg_surface_<id>} function.
     *  Package-private for unit testing the namespacing/assembly without a full {@link CompiledShaderGraph}. */
    static Surface build(int id, InjectionSnippet snippet) {
        List<String> decls = new ArrayList<>(snippet.declarationUnits().size());
        for (String unit : snippet.declarationUnits()) decls.add(namespace(unit, id));
        List<String> fns = new ArrayList<>(snippet.functions().size());
        for (String fn : snippet.functions()) fns.add(namespace(fn, id));
        String fn = "kg_Surface kg_surface_" + id + "(vec2 kg_uv) {\n"
                + namespace(snippet.body(), id)
                + "    return kg_Surface(" + namespace(snippet.surfaceArgs(), id) + ");\n}\n";
        return new Surface(id, decls, fns, fn);
    }

    /**
     * Suffix the collision-prone identifiers in {@code glsl} with {@code id}: the {@code KG_Material} block
     * name + its {@code kg_material} instance (different fields per material) and the per-compile gradient
     * builder prefix {@code kg_grad_} (gradient counters reset per compile). Word-boundary matched so a
     * larger identifier that merely contains the token is left alone.
     */
    private static String namespace(String glsl, int id) {
        return glsl
                .replaceAll("\\bKG_Material\\b", "KG_Material_" + id)
                .replaceAll("\\bkg_material\\b", "kg_material_" + id)
                .replace("kg_grad_", "kg_grad_" + id + "_");
    }

    /** The GLSL block name carrying the per-material UBO for surface {@code id} (bound at draw). */
    public static String materialBlockName(int id) {
        return "KG_Material_" + id;
    }
}
