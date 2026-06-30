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

    /** Shared GLSL: the surface struct, emitted once when any surface is injected. Field order is the
     *  canonical one used by {@code buildInjectionSnippet} / {@code IrisSurfaceRegistry} / the encoders. */
    private static final String KG_SURFACE_STRUCT =
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

    /** The {@link IrisSurfaceRegistry#generation()} baked into the most recently compiled programs. When the
     *  live registry advances past this (a new world graph appeared), the programs are stale and a shaderpack
     *  reload is needed to re-inject — see {@code IrisCompat.requestReloadIfStale}. */
    private static volatile int lastInjectedGeneration = -1;

    private IrisShaderInjector() {}

    /** The registry generation captured into the current shaderpack programs (see {@link #lastInjectedGeneration}). */
    public static int lastInjectedGeneration() {
        return lastInjectedGeneration;
    }

    /**
     * Mixin entry point: inject into {@code source} and log (once per program {@code name}) whether the
     * injection landed — ground truth that our {@code ShaderCreator} mixin is actually rewriting the
     * shaderpack's programs. {@code name} is the Iris program name (e.g. {@code gbuffers_entities}).
     */
    public static String inject(String name, String source) {
        // Every program in one pack compile is injected from the same registry state; record it so the
        // runtime can detect when a later-registered surface needs a reload to be picked up.
        lastInjectedGeneration = IrisSurfaceRegistry.generation();
        var surfaces = IrisSurfaceRegistry.snapshot();
        String out = injectFragment(source, surfaces);
        // Iris calls createShader for BOTH the vertex and fragment of each program (same name); only the
        // fragment reads gbuffers samplers, so log the diagnostic for the source we actually injected (out !=
        // source) — and report which sampler families that fragment reads, the ground truth for "does this
        // pack program do albedo/normal/specular PBR for our geometry". Once per program name.
        if (out != source && LOGGED.add(name)) {
            LOGGER.info("[KilaGraph][Iris] injected {} surface(s) into '{}' — fragment reads albedo={} normals={} specular={}",
                    surfaces.size(), name, detectReads(source, ALBEDO_SAMPLERS),
                    detectReads(source, NORMAL_SAMPLERS), detectReads(source, SPECULAR_SAMPLERS));
        }
        return out;
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

        for (String s : albedo) defs.append(samplerHelper(s, "albedo"));
        for (String s : normals) defs.append(samplerHelper(s, "normals"));
        for (String s : specular) defs.append(samplerHelper(s, "specular"));
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
                + "vec4 kg_sample_" + sampler + "(vec2 kg_uv, float kg_lod);\n";
    }

    /** A gated sampler helper: for our geometry ({@code kg_surface_id != 0}) it returns the dispatched
     *  surface's channel encoded for the sampler's role (albedo / LabPBR normals / LabPBR specular); else the
     *  real texture read (passthrough). Emits a {@code (vec2,float)} overload too so explicit-LOD reads
     *  ({@code texture2DLod}) resolve (the LOD is dropped — fine for our flat per-fragment surface).
     *  {@code DEBUG_FORCE_TINT} tints the albedo red (seam test). */
    private static String samplerHelper(String sampler, String kind) {
        String lodOverload = "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv, float kg_lod) { return kg_sample_"
                + sampler + "(kg_uv); }\n";
        if (DEBUG_FORCE_TINT && kind.equals("albedo")) {
            return "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv) {\n"
                    + "    return vec4(1.0, 0.0, 0.0, 1.0);\n}\n" + lodOverload;
        }
        String encoded = switch (kind) {
            case "normals" -> "kg_encodeNormal(s.normalTS, s.ao, s.height)";
            case "specular" -> "kg_encodeSpecular(s.smoothness, s.metallic, s.porosity, s.sss, s.emission)";
            default -> "vec4(s.albedo, s.alpha)";
        };
        return "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv) {\n"
                + "    if (kg_surface_id != 0) { kg_Surface s = kg_surface(kg_surface_id, kg_uv); return " + encoded + "; }\n"
                + "    return texture(" + sampler + ", kg_uv);\n}\n" + lodOverload;
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
        return Pattern.compile("texture2?D?(?:Lod)?\\s*\\(\\s*" + sampler + "\\b\\s*,\\s*");
    }

    /** Insert {@code text} immediately before the first line that is real code (not blank/comment/#). */
    private static String insertAtFirstCodeLine(String source, String text) {
        Matcher m = FIRST_CODE_LINE.matcher(source);
        if (!m.find()) return text + source; // no code line found — prepend
        int at = m.start();
        return source.substring(0, at) + text + source.substring(at);
    }
}
