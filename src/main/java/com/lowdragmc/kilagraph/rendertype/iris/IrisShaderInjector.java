package com.lowdragmc.kilagraph.rendertype.iris;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a shaderpack's final GLSL (the {@code source} string Iris hands to GL compilation via
 * {@code ShaderCreator.createShader}) so that KilaGraph materials can run their own surface logic
 * <em>inside the shaderpack's own gbuffers program</em> — the only way to keep custom shading while a
 * shaderpack owns the gbuffer/lighting passes (see the project plan).
 *
 * <p>Iris compiles <b>one shared program per {@code ShaderKey}</b> (all terrain share one program, all
 * entities another, ...), so the injected code must be <b>conditional</b>: it is gated on a
 * {@code uniform int kg_surface_id} that the runtime sets (per draw, via GL) to a non-zero id only while
 * drawing our geometry, and resets to 0 afterwards. When {@code kg_surface_id == 0} the original
 * shaderpack behaviour is preserved bit-for-bit, so vanilla blocks/entities are never affected.</p>
 *
 * <p>It hijacks the gbuffers base-colour sampler. OptiFine/Iris packs name it inconsistently —
 * {@code gtexture}, {@code tex}, or {@code gcolor} (Complementary's patched source uses {@code tex}). We
 * rewrite every {@code texture(<sampler>, uv)} read of whichever of these a program actually samples into
 * {@code kg_sample_<sampler>(uv)}, which — for our geometry only ({@code kg_surface_id != 0}) — returns the
 * dispatched {@code kg_surface(kg_surface_id, uv)}, and the real texture otherwise.</p>
 *
 * <p><b>Per-material dispatch.</b> Iris shares one gbuffers program across all entity-keyed draws, so the
 * program carries <em>every</em> live KilaGraph surface ({@link IrisSurfaceRegistry}) as a
 * {@code kg_surface_<id>(uv)} function plus a dispatch {@code kg_surface(int id, vec2 uv)} switching on the
 * per-draw {@code kg_surface_id}. Each surface's compiled {@code KG_Material}/sampler/managed-UBO
 * declarations are emitted (deduped across surfaces); the runtime binds the active material's data onto the
 * program at draw (see {@link IrisSurfaceUniform}).</p>
 *
 * <p>Pure string transform (no Iris/Minecraft classes) so it is unit-testable — the surface set is passed in
 * by {@link #injectFragment(String, java.util.List)}; the mixin entry pulls the live
 * {@link IrisSurfaceRegistry#snapshot()}. Idempotent: a {@link #MARKER} guard skips already-injected sources
 * (Iris may compile the same source repeatedly).</p>
 */
public final class IrisShaderInjector {

    /** Sentinel placed in injected sources to make the transform idempotent. */
    public static final String MARKER = "KG_INJECTED";

    /**
     * Seam-test mode: when on, the injected helper ignores {@code kg_surface_id} and tints <em>all</em>
     * gtexture reads red. This proves the injection seam (mixin applies, rewritten GLSL compiles, our code
     * runs inside the shaderpack program and is lit) independently of the per-draw discriminator timing.
     * Off by default (real, gated behaviour). Initial value from {@code -Dkilagraph.iris.debugTint}; can be
     * toggled at runtime via the {@code /kgiris tint} command (takes effect on the next shader reload — F3+T).
     */
    public static volatile boolean DEBUG_FORCE_TINT = Boolean.getBoolean("kilagraph.iris.debugTint");

    /** Candidate gbuffers base-colour sampler names, in priority order. A program is rewritten for each
     *  one it actually reads ({@code texture(<name>, ...)}); a helper is only emitted for samplers found,
     *  so we never reference an undeclared uniform. */
    private static final String[] ALBEDO_SAMPLERS = {"gtexture", "tex", "gcolor"};
    /** LabPBR normal-map sampler name(s). */
    private static final String[] NORMAL_SAMPLERS = {"normals"};
    /** LabPBR specular-map sampler name(s). */
    private static final String[] SPECULAR_SAMPLERS = {"specular"};

    /** The pack's own <b>view-space smooth normal</b> fragment varying, if it declares one named
     *  {@code normal} ({@code in vec3 normal;} / {@code varying vec3 normal;} — Complementary/BSL/Solas all do;
     *  photon doesn't). When present we feed it into {@code kg_normalView} for a smooth surface normal; when
     *  absent we fall back to a constant, so a graph's normal/Fresnel degrades gracefully but never breaks the
     *  pack. Word-exact on {@code normal} so it doesn't catch {@code normalM}/{@code newNormal}/etc. */
    private static final Pattern PACK_NORMAL_VARYING =
            Pattern.compile("(?m)^\\s*(?:flat\\s+)?(?:in|varying)\\s+vec3\\s+normal\\s*;");

    /** An existing {@code depthtex1} sampler declaration in the pack's (flattened) source. Some packs declare
     *  every sampler globally (Complementary's {@code lib/uniforms.glsl}), so a scene-depth surface must only
     *  add the declaration when it's genuinely absent — a redeclaration is a compile error (which the
     *  whole-source self-check would turn into a full passthrough fallback). Tolerates layout/precision
     *  qualifiers. */
    private static final Pattern DEPTHTEX1_DECL = Pattern.compile(
            "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(?:highp\\s+|mediump\\s+|lowp\\s+)?sampler2D\\s+depthtex1\\s*;");

    /** Shared GLSL: the surface struct, emitted once when any surface is injected. Field order is the
     *  canonical one used by {@code buildInjectionSnippet} / {@code IrisSurfaceRegistry} / the encoders.
     *  Package-visible so {@code IrisSurfaceRegistry.validationHarness} wraps surfaces with the exact same
     *  struct the injector will emit. */
    static final String KG_SURFACE_STRUCT =
            "struct kg_Surface {\n" +
            "    vec3 albedo; float alpha;\n" +
            "    vec3 normalTS; float smoothness; float metallic; float emission;\n" +
            "    float ao; float height; float porosity; float sss;\n" +
            "};\n";
    /** Shared GLSL: encode a tangent normal + AO + height into a LabPBR {@code _n} texel. */
    private static final String KG_ENCODE_NORMAL =
            "vec4 kg_encodeNormal(vec3 n, float ao, float height) {\n" +
            "    return vec4(normalize(n).xy * 0.5 + 0.5, ao, height);\n" +
            "}\n";
    /** Shared GLSL: encode smoothness/metallic/porosity/SSS/emission into a LabPBR {@code _s} texel. */
    private static final String KG_ENCODE_SPECULAR =
            "vec4 kg_encodeSpecular(float smoothness, float metallic, float porosity, float sss, float emission) {\n" +
            "    float g = metallic >= 0.5 ? 1.0 : 0.04;\n" +
            "    float b = sss > 0.0 ? (65.0 + clamp(sss, 0.0, 1.0) * 190.0) / 255.0 : clamp(porosity, 0.0, 1.0) * (64.0 / 255.0);\n" +
            "    float a = min(clamp(emission, 0.0, 1.0), 254.0 / 255.0);\n" +
            "    return vec4(clamp(smoothness, 0.0, 1.0), g, b, a);\n" +
            "}\n";

    /** First non-(blank|line-comment|preprocessor) line — a legal spot for our global declarations
     *  (after the leading {@code #version}/{@code #extension}/{@code #define} block). */
    private static final Pattern FIRST_CODE_LINE =
            Pattern.compile("(?m)^(?!\\s*$)(?!\\s*//)(?!\\s*#).*$");

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Program names we've already logged an injection for (avoid per-recompile log spam). */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    /** Program names we've already logged an injection FAILURE for (see {@link #injectGuarded}). */
    private static final Set<String> GUARD_LOGGED = ConcurrentHashMap.newKeySet();

    /** The {@link IrisSurfaceRegistry#generation()} baked into the most recently compiled programs. When the
     *  live registry advances past this (a new world graph appeared), the programs are stale and a shaderpack
     *  reload is needed to re-inject — see {@code IrisCompat.reloadShadersIfStale}. */
    private static volatile int lastInjectedGeneration = -1;

    private IrisShaderInjector() {}

    /** The registry generation captured into the current shaderpack programs (see {@link #lastInjectedGeneration}). */
    public static int lastInjectedGeneration() {
        return lastInjectedGeneration;
    }

    /**
     * Mixin entry point: inject into {@code source} and log (once per program {@code name}) whether the
     * injection landed — ground truth that our {@code ShaderCreator} mixin is actually rewriting the
     * shaderpack's programs. {@code name} is the Iris program name (e.g. {@code gbuffers_entities});
     * {@code typeName} is the Iris {@code ShaderType} enum name ({@code VERTEX}/{@code FRAGMENT}/...) — a
     * String so this class stays free of Iris types (unit-testable, soft-dep). Fragment sources get the
     * surface-shading injection, vertex sources the displacement read-hijack (see {@link #injectVertex});
     * every other stage (GEOMETRY/COMPUTE/TESSELATION_*) passes through untouched.
     */
    public static String inject(String name, String typeName, String source) {
        if (!IrisCompat.ENABLED) return source; // kill-switch (defense in depth if the mixin still applied)
        IrisCompat.seamAlive(); // the ShaderCreator seam demonstrably works — clear any dead-seam latch
        try {
            // Iris is rebuilding its programs; GL may reuse the deleted programs' integer ids, so every
            // program-id-keyed cache is now unsafe (see IrisCompat.pollPipelineChange for the full story).
            IrisSurfaceUniform.invalidateCaches();
        } catch (Throwable ignored) {
            // cache invalidation must never break the pack compile
        }
        return switch (typeName) {
            case "FRAGMENT" -> injectGuarded(name, source, IrisSurfaceRegistry.snapshot());
            case "VERTEX" -> injectVertexGuarded(name, source, IrisSurfaceRegistry.snapshot());
            default -> source;
        };
    }

    /**
     * The guarded transform behind {@link #inject}: <b>any</b> failure returns the untouched {@code source},
     * so a KilaGraph bug can cost us our own shading but can never break the shaderpack's program
     * compilation. Package-private so the guarantee is unit-testable with a poisoned surface list.
     */
    static String injectGuarded(String name, String source, List<IrisSurfaceRegistry.Surface> surfaces) {
        try {
            // Every program in one pack compile is injected from the same registry state; record it so the
            // runtime can detect when a later-registered surface needs a reload to be picked up.
            lastInjectedGeneration = IrisSurfaceRegistry.generation();
            String out = injectFragment(source, surfaces);
            if (out != source) {
                // Whole-source GL self-check: if the injected result doesn't compile, hand Iris the original —
                // the pack can never be broken by us.
                String err = null;
                boolean validated = false;
                try {
                    err = IrisGlslValidator.compileFragmentError(out);
                    validated = true;
                } catch (Throwable ignored) {
                    // no LWJGL/GL in this environment (unit tests) — validation unavailable, accept
                }
                if (validated && err != null) {
                    if (GUARD_LOGGED.add(name)) {
                        LOGGER.error("[KilaGraph][Iris] injected source for '{}' fails GL compilation — leaving "
                                + "the shaderpack source untouched. Driver log:\n{}", name, err);
                    }
                    return source;
                }
            }
            // Key by name + source length + surface count: a DIFFERENT pack's same-named program re-logs,
            // and so does a reload that bakes a different surface set (the original source length alone is
            // identical across reloads and would silently swallow the "injected N surface(s)" evidence).
            if (out != source && LOGGED.add(name + "#" + (source == null ? 0 : source.length()) + "#" + surfaces.size())) {
                // Report which sampler families the injected fragment reads — the ground truth for "does this pack
                // program do albedo/normal/specular PBR for our geometry". Key by name + source length so a
                // DIFFERENT pack's same-named program (different source) re-logs.
                LOGGER.info("[KilaGraph][Iris] injected {} surface(s) into '{}' — fragment reads albedo={} normals={} specular={}",
                        surfaces.size(), name, detectReads(source, ALBEDO_SAMPLERS),
                        detectReads(source, NORMAL_SAMPLERS), detectReads(source, SPECULAR_SAMPLERS));
            }
            return out;
        } catch (Throwable t) {
            if (GUARD_LOGGED.add(name)) {
                LOGGER.error("[KilaGraph][Iris] injection into '{}' threw — leaving the shaderpack source untouched "
                        + "(KilaGraph materials render passthrough in this program)", name, t);
            }
            return source;
        }
    }

    /** {@link #injectGuarded}'s vertex twin: any throw or a failed whole-source <b>vertex</b>-stage GL
     *  self-check returns the untouched source — the pack can never be broken; KilaGraph geometry then
     *  renders undisplaced in this program (fragment shading is a separate source and survives). */
    static String injectVertexGuarded(String name, String source, List<IrisSurfaceRegistry.Surface> surfaces) {
        try {
            String out = injectVertex(source, surfaces);
            if (out != source) {
                String err = null;
                boolean validated = false;
                try {
                    err = IrisGlslValidator.compileVertexError(out);
                    validated = true;
                } catch (Throwable ignored) {
                    // no LWJGL/GL in this environment (unit tests) — validation unavailable, accept
                }
                if (validated && err != null) {
                    if (GUARD_LOGGED.add(name + "#vsh")) {
                        LOGGER.error("[KilaGraph][Iris] injected vertex source for '{}' fails GL compilation — "
                                + "leaving the shaderpack source untouched. Driver log:\n{}", name, err);
                    }
                    return source;
                }
                if (LOGGED.add(name + "#vsh#" + (source == null ? 0 : source.length()) + "#" + surfaces.size())) {
                    LOGGER.info("[KilaGraph][Iris] injected vertex displacement into '{}' ({} displacing surface(s))",
                            name, surfaces.stream().filter(IrisSurfaceRegistry.Surface::hasVertex).count());
                }
            }
            return out;
        } catch (Throwable t) {
            if (GUARD_LOGGED.add(name + "#vsh")) {
                LOGGER.error("[KilaGraph][Iris] vertex injection into '{}' threw — leaving the shaderpack source "
                        + "untouched (KilaGraph geometry renders undisplaced in this program)", name, t);
            }
            return source;
        }
    }

    /**
     * Transform a <b>vertex</b>-stage shaderpack source: hijack every READ of the {@code iris_Position}
     * (and, when needed, {@code iris_Normal}) attribute into {@code kg_vpos(iris_Position)} /
     * {@code kg_vnormal(iris_Normal)}, dispatching on the per-draw {@code kg_surface_id} exactly like the
     * fragment side — id 0 / an unclaimed id returns the input unchanged, so non-KilaGraph draws are
     * bit-identical. Iris's transformer renders {@code gl_Vertex} reads as {@code vec4(iris_Position, 1.0)}
     * (or {@code vec4(iris_Position + iris_vertex_offset, 1.0)}), so hijacking the bare token composes with
     * every downstream use (ftransform/world-curvature/TAA jitter/shadow distortion all see the displaced
     * position). Attribute <em>declaration</em> lines are skipped. Returns {@code source} unchanged (not
     * even a marker) when no surface displaces, the source is already injected, or it never reads the
     * attributes — packs stay untouched unless a displacement genuinely exists.
     */
    public static String injectVertex(String source, List<IrisSurfaceRegistry.Surface> surfaces) {
        if (source == null || source.contains(MARKER)) return source;
        List<IrisSurfaceRegistry.Surface> displacing =
                surfaces.stream().filter(IrisSurfaceRegistry.Surface::hasVertex).toList();
        if (displacing.isEmpty()) return source;
        boolean anyPos = displacing.stream().anyMatch(s -> s.vertexPositionFunction() != null);
        boolean anyNorm = displacing.stream().anyMatch(s -> s.vertexNormalFunction() != null);
        boolean hasPositionAttr = attrDecl("iris_Position").matcher(source).find();
        boolean hasNormalAttr = attrDecl("iris_Normal").matcher(source).find();

        int[] count = {0};
        String body = source;
        if (anyPos) body = rewriteAttributeReads(body, "iris_Position", "kg_vpos", count);
        int posReads = count[0];
        if (anyNorm && hasNormalAttr) body = rewriteAttributeReads(body, "iris_Normal", "kg_vnormal", count);
        if (count[0] == 0) return source; // reads nothing we hijack (composite/final passes) — untouched
        boolean emitNormalFn = anyNorm && hasNormalAttr && count[0] > posReads;

        // Declarations after the leading preprocessor block: the discriminator + every displacing surface's
        // declaration units (BYTE-IDENTICAL strings to the fragment injection — the same namespaced units —
        // so the program's cross-stage UBO/block definitions match at link) + dispatcher forward decls.
        StringBuilder decls = new StringBuilder("\n// " + MARKER + "\nuniform int kg_surface_id;\n");
        LinkedHashSet<String> declUnits = new LinkedHashSet<>();
        for (var s : displacing) declUnits.addAll(s.declarationUnits());
        for (String unit : declUnits) decls.append(unit);
        if (posReads > 0) decls.append("vec3 kg_vpos(vec3 kg_p);\n");
        if (emitNormalFn) decls.append("vec3 kg_vnormal(vec3 kg_n);\n");
        body = insertAtFirstCodeLine(body, decls.toString());

        // Definitions at the end (appended AFTER the rewrite, so their own attribute references are never
        // re-hijacked): deduped helpers, the per-surface functions, then the id dispatchers. The RAW
        // attributes are passed through (matching vanilla, where both block expressions read the original
        // mesh inputs); a missing iris_Normal declaration degrades to object +Y — never reference a name
        // the pack didn't declare.
        String rawNormal = hasNormalAttr ? "iris_Normal" : "vec3(0.0, 1.0, 0.0)";
        String rawPosition = hasPositionAttr ? "iris_Position" : "vec3(0.0)";
        StringBuilder defs = new StringBuilder();
        LinkedHashSet<String> fns = new LinkedHashSet<>();
        for (var s : displacing) fns.addAll(s.vertexFunctions());
        for (String fn : fns) defs.append('\n').append(fn);
        for (var s : displacing) {
            if (s.vertexPositionFunction() != null) defs.append('\n').append(s.vertexPositionFunction());
            if (s.vertexNormalFunction() != null) defs.append('\n').append(s.vertexNormalFunction());
        }
        if (posReads > 0) {
            defs.append("\nvec3 kg_vpos(vec3 kg_p) {\n");
            for (var s : displacing) {
                if (s.vertexPositionFunction() == null) continue;
                defs.append("    if (kg_surface_id == ").append(s.id()).append(") return kg_vpos_")
                        .append(s.id()).append("(kg_p, ").append(rawNormal).append(");\n");
            }
            defs.append("    return kg_p;\n}\n");
        }
        if (emitNormalFn) {
            defs.append("\nvec3 kg_vnormal(vec3 kg_n) {\n");
            for (var s : displacing) {
                if (s.vertexNormalFunction() == null) continue;
                defs.append("    if (kg_surface_id == ").append(s.id()).append(") return kg_vnormal_")
                        .append(s.id()).append("(").append(rawPosition).append(", kg_n);\n");
            }
            defs.append("    return kg_n;\n}\n");
        }
        return body + defs;
    }

    /** An {@code in}/{@code attribute} declaration of {@code attr} (any type, optional layout qualifier). */
    private static Pattern attrDecl(String attr) {
        return Pattern.compile("(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:in|attribute)\\s+\\w+\\s+" + attr + "\\s*;");
    }

    /** Rewrite every read of {@code attr} into {@code fn(attr)}, skipping its declaration line(s); the
     *  replacement count accumulates into {@code count[0]}. */
    private static String rewriteAttributeReads(String src, String attr, String fn, int[] count) {
        Pattern declLine = Pattern.compile("^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:in|attribute)\\s+\\w+\\s+"
                + attr + "\\s*;.*$");
        Pattern read = Pattern.compile("\\b" + attr + "\\b");
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (declLine.matcher(lines[i]).matches()) continue;
            Matcher m = read.matcher(lines[i]);
            if (!m.find()) continue;
            StringBuilder sb = new StringBuilder();
            do {
                m.appendReplacement(sb, fn + "(" + attr + ")");
                count[0]++;
            } while (m.find());
            m.appendTail(sb);
            lines[i] = sb.toString();
        }
        return String.join("\n", lines);
    }

    /**
     * Transform a fragment-stage shaderpack source, dispatching to {@code surfaces}. Returns {@code source}
     * unchanged if it is already injected or samples none of the {@link #ALBEDO_SAMPLERS} (nothing to
     * hijack). The surface set is explicit so this is unit-testable; the mixin entry passes the live
     * {@link IrisSurfaceRegistry#snapshot()}.
     */
    public static String injectFragment(String source, List<IrisSurfaceRegistry.Surface> surfaces) {
        if (source == null || source.contains(MARKER)) return source;

        // 1. Redirect each albedo/normals/specular sampler the program reads to our gated helper. Done before
        //    appending the helper bodies so their own real texture(...) calls aren't themselves rewritten.
        String body = source;
        List<String> albedo = new ArrayList<>(), normals = new ArrayList<>(), specular = new ArrayList<>();
        for (String s : ALBEDO_SAMPLERS) body = hijack(body, s, albedo);
        for (String s : NORMAL_SAMPLERS) body = hijack(body, s, normals);
        for (String s : SPECULAR_SAMPLERS) body = hijack(body, s, specular);
        if (albedo.isEmpty() && normals.isEmpty() && specular.isEmpty()) return source;

        // 2. After the leading preprocessor block: the discriminator uniform, the shared surface struct,
        //    every surface's (deduped) KG_Material/sampler/managed-UBO declarations, and a forward
        //    declaration per hijacked sampler helper.
        StringBuilder decls = new StringBuilder("\n// " + MARKER + "\nuniform int kg_surface_id;\n");
        decls.append(KG_SURFACE_STRUCT);
        // A surface that reads the mesh normal (Fresnel etc.) needs a smooth view-space normal. We never touch
        // the vsh, so we take the pack's OWN view-space normal varying when it declares one named `normal`
        // (Complementary/BSL/Solas do; photon doesn't → constant fallback), filled per hijacked sampler below
        // into this global — the compiler's kg_recon_normal() rotates it to world. viewDir/position/screen need
        // nothing from the pack (reconstructed from gl_FragCoord + our UBOs).
        boolean anyGeometry = surfaces.stream().anyMatch(IrisSurfaceRegistry.Surface::usesGeometry);
        String normalFill = !anyGeometry ? null
                : (PACK_NORMAL_VARYING.matcher(source).find() ? "normal" : "vec3(0.0, 0.0, 1.0)");
        if (anyGeometry) decls.append("vec3 kg_normalView;\n");
        // Scene depth reads Iris's depthtex1 (auto-bound by name on every gbuffers program, so declaring +
        // sampling it is all that's needed) — a GLOBAL shared declaration, emitted once and only when the
        // pack's flattened source doesn't already declare it.
        boolean anySceneDepth = surfaces.stream().anyMatch(IrisSurfaceRegistry.Surface::usesSceneDepth);
        if (anySceneDepth && !DEPTHTEX1_DECL.matcher(source).find()) {
            decls.append("uniform sampler2D depthtex1;\n");
        }
        LinkedHashSet<String> declUnits = new LinkedHashSet<>();
        for (var s : surfaces) declUnits.addAll(s.declarationUnits());
        for (String unit : declUnits) decls.append(unit);
        for (String s : albedo) decls.append(samplerForwardDecls(s));
        for (String s : normals) decls.append(samplerForwardDecls(s));
        for (String s : specular) decls.append(samplerForwardDecls(s));
        body = insertAtFirstCodeLine(body, decls.toString());

        // 3. At the end (where samplers/uniforms are certainly declared): the encoders, deduped helper
        //    functions, each surface function, the struct dispatch, then the sampler helpers.
        StringBuilder defs = new StringBuilder();
        defs.append('\n').append(KG_ENCODE_NORMAL).append('\n').append(KG_ENCODE_SPECULAR);
        LinkedHashSet<String> functions = new LinkedHashSet<>();
        for (var s : surfaces) functions.addAll(s.functions());
        for (String fn : functions) defs.append('\n').append(fn);
        for (var s : surfaces) defs.append('\n').append(s.surfaceFunction());
        defs.append('\n').append(dispatchFunction(surfaces));

        for (String s : albedo) defs.append(samplerHelper(s, "albedo", normalFill));
        for (String s : normals) defs.append(samplerHelper(s, "normals", normalFill));
        for (String s : specular) defs.append(samplerHelper(s, "specular", normalFill));
        return body + defs;
    }

    /** Convenience for the common (registry-driven) call. */
    public static String injectFragment(String source) {
        return injectFragment(source, IrisSurfaceRegistry.snapshot());
    }

    /** Diagnostic: which of {@code names} the source actually reads via {@code texture(name,…)}. */
    private static List<String> detectReads(String source, String[] names) {
        List<String> found = new ArrayList<>();
        for (String n : names) if (samplePattern(n).matcher(source).find()) found.add(n);
        return found;
    }

    /** Redirect {@code texture(<sampler>,…)} reads to {@code kg_sample_<sampler>(}, recording the sampler if
     *  the program actually reads it (so we only emit a helper for samplers that exist). */
    private static String hijack(String body, String sampler, List<String> found) {
        Pattern read = samplePattern(sampler);
        if (!read.matcher(body).find()) return body;
        found.add(sampler);
        return read.matcher(body).replaceAll("kg_sample_" + sampler + "(");
    }

    /** Forward declarations for a hijacked sampler's helper — both the {@code (vec2)} form and the
     *  {@code (vec2,float)} LOD-absorbing overload, since the pack body may call either before the defs. */
    private static String samplerForwardDecls(String sampler) {
        return "vec4 kg_sample_" + sampler + "(vec2 kg_uv);\n"
                + "vec4 kg_sample_" + sampler + "(vec2 kg_uv, float kg_lod);\n"
                + "vec4 kg_sample_" + sampler + "(vec2 kg_uv, vec2 kg_dx, vec2 kg_dy);\n";
    }

    /** A gated sampler helper: for our geometry ({@code kg_surface_id != 0}) it returns the dispatched
     *  surface's channel encoded for the sampler's role (albedo / LabPBR normals / LabPBR specular); else the
     *  real texture read (passthrough). Emits a {@code (vec2,float)} overload too so explicit-LOD reads
     *  ({@code texture2DLod}) resolve (the LOD is dropped — fine for our flat per-fragment surface).
     *  {@code DEBUG_FORCE_TINT} tints the albedo red (seam test). */
    private static String samplerHelper(String sampler, String kind, String normalFill) {
        // LOD- and gradient-form reads (textureLod / textureGrad / texture2DGradARB) keep their trailing
        // args after the rewrite; these overloads absorb them and delegate to the (vec2) form (the explicit
        // LOD/gradient is irrelevant for our flat per-fragment surface).
        String overloads = "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv, float kg_lod) { return kg_sample_"
                + sampler + "(kg_uv); }\n"
                + "vec4 kg_sample_" + sampler + "(vec2 kg_uv, vec2 kg_dx, vec2 kg_dy) { return kg_sample_"
                + sampler + "(kg_uv); }\n";
        if (DEBUG_FORCE_TINT && kind.equals("albedo")) {
            return "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv) {\n"
                    + "    return vec4(1.0, 0.0, 0.0, 1.0);\n}\n" + overloads;
        }
        String encoded = switch (kind) {
            case "normals" -> "kg_encodeNormal(s.normalTS, s.ao, s.height)";
            case "specular" -> "kg_encodeSpecular(s.smoothness, s.metallic, s.porosity, s.sss, s.emission)";
            default -> "vec4(s.albedo, s.alpha)";
        };
        // Fill the per-program normal global (from the pack's own varying or a constant) right before the
        // surface runs, so kg_recon_normal() has a value. Only when some surface reads geometry.
        String geom = normalFill != null ? "kg_normalView = " + normalFill + "; " : "";
        return "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv) {\n"
                + "    if (kg_surface_id != 0) { " + geom + "kg_Surface s = kg_surface(kg_surface_id, kg_uv); return " + encoded + "; }\n"
                + "    return texture(" + sampler + ", kg_uv);\n}\n" + overloads;
    }

    /** The {@code kg_Surface kg_surface(int id, vec2 uv)} dispatch over the live surfaces (a neutral default
     *  surface for an id no surface claims — e.g. one registered after this program compiled, awaiting reload). */
    private static String dispatchFunction(List<IrisSurfaceRegistry.Surface> surfaces) {
        StringBuilder sb = new StringBuilder("kg_Surface kg_surface(int kg_id, vec2 kg_uv) {\n");
        for (var s : surfaces) {
            sb.append("    if (kg_id == ").append(s.id()).append(") return kg_surface_")
                    .append(s.id()).append("(kg_uv);\n");
        }
        sb.append("    return kg_Surface(vec3(0.0), 1.0, vec3(0.0, 0.0, 1.0), 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);\n}\n");
        return sb.toString();
    }

    /** Reads of a specific sampler in any of the forms a pack uses: {@code texture(s,…)},
     *  {@code texture2D(s,…)}, and the explicit-LOD {@code textureLod(s,…,lod)} / {@code texture2DLod(s,…,lod)}
     *  (Complementary's emission mipmap-fix uses {@code texture2DLod(specular,…,0)} — missing it leaves an
     *  un-hijacked read that breaks the channel). The trailing {@code lod} arg, if present, stays and is
     *  absorbed by the {@code kg_sample_<s>(vec2, float)} overload. */
    private static Pattern samplePattern(String sampler) {
        // texture / texture2D, optionally with an explicit-LOD (Lod) or gradient (Grad / GradARB) suffix —
        // BSL reads albedo/normals/specular via texture2DGradARB(...) under parallax, Complementary via
        // texture2DLod(...). The trailing lod/gradient args stay and are absorbed by the kg_sample overloads.
        return Pattern.compile("texture2?D?(?:Lod|Grad(?:ARB)?)?\\s*\\(\\s*" + sampler + "\\b\\s*,\\s*");
    }

    /** Insert {@code text} immediately before the first line that is real code (not blank/comment/#). */
    private static String insertAtFirstCodeLine(String source, String text) {
        Matcher m = FIRST_CODE_LINE.matcher(source);
        if (!m.find()) return text + source; // no code line found — prepend
        int at = m.start();
        return source.substring(0, at) + text + source.substring(at);
    }
}
