package com.lowdragmc.kilagraph.rendertype.iris;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.InjectionSnippet;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
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

    /** A live surface, with all collision-prone identifiers already id-namespaced. {@code usesGeometry} marks
     *  that the surface reads the mesh normal (Fresnel etc.) — the injector then declares the
     *  {@code vec3 kg_normalView} global and fills it from the pack's own {@code normal} varying (or a
     *  constant); viewDir/position/screen are reconstructed in the fragment from {@code gl_FragCoord} + our
     *  UBOs. {@code usesSceneDepth} marks that the surface samples Iris's {@code depthtex1} — the injector
     *  declares that sampler iff the pack doesn't already (Iris auto-binds it by name).
     *
     *  <p>{@code vertexPositionFunction}/{@code vertexNormalFunction} are the ready-to-emit (namespaced)
     *  {@code vec3 kg_vpos_<id>(vec3 kg_pos, vec3 kg_n)} / {@code kg_vnormal_<id>(...)} definitions for the
     *  pack's <b>vertex</b> source, dispatched by the same per-draw {@code kg_surface_id} — null when the
     *  graph doesn't displace that output (the injector then leaves the corresponding attribute reads
     *  untouched). {@code vertexFunctions} are their helper definitions (noise etc.).</p> */
    public record Surface(int id, List<String> declarationUnits, List<String> functions, String surfaceFunction,
                          boolean usesGeometry, boolean usesSceneDepth,
                          @Nullable String vertexPositionFunction, @Nullable String vertexNormalFunction,
                          List<String> vertexFunctions) {

        /** Whether this surface modifies the vertex stage at all (the injector's cue to touch a pack vsh). */
        public boolean hasVertex() {
            return vertexPositionFunction != null || vertexNormalFunction != null;
        }

        /** This surface with the vertex parts stripped — the fragment-only degrade when the vertex half
         *  fails standalone GL validation (shading survives, displacement dropped). */
        Surface withoutVertex() {
            return new Surface(id, declarationUnits, functions, surfaceFunction,
                    usesGeometry, usesSceneDepth, null, null, List.of());
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    /** Content hashes we've already logged a not-injection-compatible warning for (avoid spam). */
    private static final Set<String> LOGGED_INCOMPATIBLE = new LinkedHashSet<>();
    /** Content hashes we've already logged the glPosition-block vanilla-only note for (avoid spam). */
    private static final Set<String> LOGGED_LEGACY_VERTEX = new LinkedHashSet<>();
    /** Content hashes whose surface failed standalone GL validation — permanently passthrough (id 0) this
     *  session, never re-validated (the graph source is content-hashed, so the failure is deterministic). */
    private static final Set<String> FAILED_HASHES = new LinkedHashSet<>();
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
                if (FAILED_HASHES.contains(hash)) return 0; // known-bad — permanently passthrough
                if (snippet.legacyVertexBlock() && LOGGED_LEGACY_VERTEX.add(hash)) {
                    LOGGER.info("[KilaGraph][Iris] graph {} uses the advanced glPosition block — vanilla-only: "
                            + "under a shaderpack the vertex stage falls back to the pack's standard "
                            + "transform; fragment shading still injects.", hash);
                }
                Surface candidate = build(nextId, snippet);
                // Standalone GL validation BEFORE the surface enters the registry: a surface that can't
                // compile would otherwise poison every program (the whole-source fallback in injectGuarded
                // would then degrade ALL surfaces to passthrough). Rejecting here degrades only this graph.
                String err = validateStandalone(candidate);
                if (err != null) {
                    FAILED_HASHES.add(hash);
                    LOGGER.error("[KilaGraph][Iris] surface for graph {} fails standalone GL validation -> "
                            + "passthrough (id 0). Driver log:\n{}", hash, err);
                    return 0;
                }
                // The vertex half validates separately, and a failure degrades to fragment-only (never
                // FAILED_HASHES): shading keeps working, geometry renders undisplaced.
                if (candidate.hasVertex()) {
                    String verr = validateVertexStandalone(candidate);
                    if (verr != null) {
                        LOGGER.error("[KilaGraph][Iris] vertex displacement for graph {} fails standalone GL "
                                + "validation -> fragment-only (undisplaced). Driver log:\n{}", hash, verr);
                        candidate = candidate.withoutVertex();
                    }
                }
                nextId++;
                surface = candidate;
                BY_HASH.put(hash, surface);
                generation++;
                lastChangeNanos = System.nanoTime();
                LOGGER.info("[KilaGraph][Iris] registered surface id={} for graph {} (decls={}, fns={}, vertex={}, total={})",
                        surface.id(), hash, surface.declarationUnits().size(), surface.functions().size(),
                        surface.hasVertex(), BY_HASH.size());
            }
            return surface.id();
        }
    }

    /** GL-compile {@code surface} wrapped in {@link #validationHarness}; {@code null} = valid (or validation
     *  unavailable — no GL context/LWJGL — in which case we accept, matching pre-hardening behaviour). */
    @Nullable
    private static String validateStandalone(Surface surface) {
        try {
            return IrisGlslValidator.compileFragmentError(validationHarness(surface));
        } catch (Throwable ignored) {
            return null; // no LWJGL/GL in this environment — validation unavailable, accept
        }
    }

    /** GL-compile the surface's vertex half wrapped in {@link #vertexValidationHarness}; {@code null} = valid
     *  (or validation unavailable — accept). */
    @Nullable
    private static String validateVertexStandalone(Surface surface) {
        try {
            return IrisGlslValidator.compileVertexError(vertexValidationHarness(surface));
        } catch (Throwable ignored) {
            return null; // no LWJGL/GL in this environment — validation unavailable, accept
        }
    }

    /**
     * Wrap a surface's vertex half as a complete standalone {@code #version 330 core} vertex shader whose
     * {@code main} <em>calls</em> the {@code kg_vpos_<id>}/{@code kg_vnormal_<id>} functions (so nothing is
     * stripped and a compile error means the displacement itself is bad). Uses the same declaration units
     * the injector will emit. Package-private for unit tests.
     */
    static String vertexValidationHarness(Surface surface) {
        StringBuilder sb = new StringBuilder("#version 330 core\n");
        for (String unit : surface.declarationUnits()) sb.append(unit);
        for (String fn : surface.vertexFunctions()) sb.append(fn);
        if (surface.vertexPositionFunction() != null) sb.append(surface.vertexPositionFunction());
        if (surface.vertexNormalFunction() != null) sb.append(surface.vertexNormalFunction());
        sb.append("void main() {\n")
          .append("    vec3 kg_p = vec3(0.5);\n")
          .append("    vec3 kg_nrm = vec3(0.0, 1.0, 0.0);\n");
        if (surface.vertexPositionFunction() != null) {
            sb.append("    kg_p = kg_vpos_").append(surface.id()).append("(kg_p, kg_nrm);\n");
        }
        if (surface.vertexNormalFunction() != null) {
            sb.append("    kg_nrm = kg_vnormal_").append(surface.id()).append("(kg_p, kg_nrm);\n");
        }
        sb.append("    gl_Position = vec4(kg_p + kg_nrm, 1.0);\n}\n");
        return sb.toString();
    }

    /**
     * Wrap a surface as a complete standalone {@code #version 330 core} fragment shader (the version Iris
     * emits) whose {@code main} <em>calls</em> the surface function — so the compiler can't strip anything
     * and a compile error means the surface itself is bad. Mirrors exactly what {@code IrisShaderInjector}
     * will emit for this surface (same struct constant, same globals). Package-private for unit tests.
     */
    static String validationHarness(Surface surface) {
        StringBuilder sb = new StringBuilder("#version 330 core\n");
        sb.append(IrisShaderInjector.KG_SURFACE_STRUCT);
        if (surface.usesGeometry()) sb.append("vec3 kg_normalView;\n");
        if (surface.usesSceneDepth()) sb.append("uniform sampler2D depthtex1;\n");
        for (String unit : surface.declarationUnits()) sb.append(unit);
        for (String fn : surface.functions()) sb.append(fn);
        sb.append(surface.surfaceFunction());
        sb.append("out vec4 kg_out;\n")
          .append("void main() {\n")
          .append("    kg_Surface s = kg_surface_").append(surface.id()).append("(vec2(0.5));\n")
          .append("    kg_out = vec4(s.albedo, s.alpha);\n")
          .append("}\n");
        return sb.toString();
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

    /** Apply per-id namespacing to a snippet's pieces and assemble its {@code kg_surface_<id>} function
     *  (+ the {@code kg_vpos_<id>}/{@code kg_vnormal_<id>} vertex functions when the graph displaces).
     *  Package-private for unit testing the namespacing/assembly without a full {@link CompiledShaderGraph}. */
    static Surface build(int id, InjectionSnippet snippet) {
        List<String> decls = new ArrayList<>(snippet.declarationUnits().size());
        for (String unit : snippet.declarationUnits()) decls.add(namespace(unit, id));
        List<String> fns = new ArrayList<>(snippet.functions().size());
        for (String fn : snippet.functions()) fns.add(namespace(fn, id));
        String fn = "kg_Surface kg_surface_" + id + "(vec2 kg_uv) {\n"
                + namespace(snippet.body(), id)
                + "    return kg_Surface(" + namespace(snippet.surfaceArgs(), id) + ");\n}\n";
        // Both vertex functions take (position, normal) so displace-along-normal and normal-from-position
        // both work; the dispatchers pass the pack's RAW attributes (matching the vanilla compile, where
        // both block expressions read the ORIGINAL mesh inputs).
        String vpos = snippet.vertexPositionExpr() == null ? null
                : "vec3 kg_vpos_" + id + "(vec3 kg_pos, vec3 kg_n) {\n"
                + namespace(snippet.vertexPositionBody() == null ? "" : snippet.vertexPositionBody(), id)
                + "    return " + namespace(snippet.vertexPositionExpr(), id) + ";\n}\n";
        String vnormal = snippet.vertexNormalExpr() == null ? null
                : "vec3 kg_vnormal_" + id + "(vec3 kg_pos, vec3 kg_n) {\n"
                + namespace(snippet.vertexNormalBody() == null ? "" : snippet.vertexNormalBody(), id)
                + "    return " + namespace(snippet.vertexNormalExpr(), id) + ";\n}\n";
        List<String> vfns = new ArrayList<>(snippet.vertexFunctions().size());
        for (String vf : snippet.vertexFunctions()) vfns.add(namespace(vf, id));
        return new Surface(id, decls, fns, fn, snippet.usesGeometry(), snippet.usesSceneDepth(),
                vpos, vnormal, vfns);
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
