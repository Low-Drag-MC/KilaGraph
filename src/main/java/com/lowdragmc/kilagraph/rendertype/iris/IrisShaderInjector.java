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

    /** The {@code iris_Normal} <b>vertex attribute declaration</b> (not the {@code iris_NormalMat} uniform,
     *  which shares the prefix): the precise signal that a vertex shader carries per-vertex normals, so our
     *  {@code kg_normal = iris_NormalMat * iris_Normal} write compiles. A plain {@code contains("iris_Normal")}
     *  false-matches {@code iris_NormalMat} on normal-less passes like {@code basic} (lines/leashes) → an
     *  {@code undefined variable "iris_Normal"} compile error that breaks the whole pack. */
    private static final Pattern IRIS_NORMAL_ATTR = Pattern.compile("\\bin\\s+vec3\\s+iris_Normal\\b");

    /** The per-fragment geometry varyings a surface may read (world-space normal + surface→camera view
     *  direction, object-space position). Computed once by the injected vertex stage ({@link #injectVertex})
     *  and read by the fragment surfaces. Order/names match {@code ShaderGraphCompiler}'s injection defaults. */
    private static final String[] GEOMETRY_VARYINGS = {"kg_normal", "kg_viewDir", "kg_localPos"};

    /** Every uniform/attribute/block our injected vertex writes reference. A shading pass must declare <b>all</b>
     *  of them or our writes won't compile — passes differ (e.g. {@code clouds_sodium} has {@code iris_Normal}
     *  but no {@code gbufferModelViewInverse}; {@code basic} has {@code iris_NormalMat} but no {@code iris_Normal}
     *  attribute). {@code iris_Normal} itself is matched by {@link #IRIS_NORMAL_ATTR} (attribute-decl, not the
     *  {@code iris_NormalMat} substring). Missing any ⇒ skip (an undefined reference disables the whole pack). */
    private static final String[] GEOMETRY_WRITE_TOKENS =
            {"iris_Position", "iris_NormalMat", "iris_transforms", "gbufferModelViewInverse"};

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
        // Iris calls createShader for BOTH the vertex and fragment of each program (same name). The fragment
        // reads gbuffers samplers (hijacked); the vertex shader writes gl_Position. Try the fragment transform
        // first; if it didn't change the source it wasn't a hijackable fragment, so try the vertex transform.
        String out = injectFragment(source, surfaces);
        if (out != source) {
            // Report which sampler families the injected fragment reads — the ground truth for "does this pack
            // program do albedo/normal/specular PBR for our geometry". Key by name + source length so a
            // DIFFERENT pack's same-named program (different source) re-logs.
            if (LOGGED.add(name + "#" + (source == null ? 0 : source.length()))) {
                LOGGER.info("[KilaGraph][Iris] injected {} surface(s) into '{}' — fragment reads albedo={} normals={} specular={}",
                        surfaces.size(), name, detectReads(source, ALBEDO_SAMPLERS),
                        detectReads(source, NORMAL_SAMPLERS), detectReads(source, SPECULAR_SAMPLERS));
            }
            return out;
        }
        String vsh = injectVertex(name, source, surfaces);
        if (vsh != source && LOGGED.add(name + "#vsh#" + (source == null ? 0 : source.length()))) {
            LOGGER.info("[KilaGraph][Iris] injected geometry varyings into vertex shader '{}'", name);
        }
        return vsh;
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
        // If any surface reads the mesh normal/viewDir/position, declare those varyings here (matching vertex
        // stage written by injectVertex); the surface functions reference them. Qualifier per GLSL era.
        if (surfaces.stream().anyMatch(IrisSurfaceRegistry.Surface::usesGeometry)) {
            decls.append(geometryVaryingDecls(glslVersion(source) >= 130 ? "in" : "varying"));
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

        for (String s : albedo) defs.append(samplerHelper(s, "albedo"));
        for (String s : normals) defs.append(samplerHelper(s, "normals"));
        for (String s : specular) defs.append(samplerHelper(s, "specular"));
        return body + defs;
    }

    /** Convenience for the common (registry-driven) call. */
    public static String injectFragment(String source) {
        return injectFragment(source, IrisSurfaceRegistry.snapshot());
    }

    /**
     * Transform a <b>vertex</b>-stage source so it computes the per-fragment geometry varyings
     * ({@link #GEOMETRY_VARYINGS}) a surface may read (Fresnel etc.). Returns {@code source} unchanged unless
     * <em>all</em> of: it's a vertex shader ({@code gl_Position}), it's an Iris-transformed shading pass (has
     * the {@code iris_Normal} attribute + the {@code iris_transforms} DynamicTransforms block), at least one
     * surface reads geometry, and it isn't already injected.
     *
     * <p><b>Iris renames the GL built-ins.</b> Iris compiles packs as {@code #version 330 core} "Generated by
     * glsl-transformer", so by {@code createShader} the vertex source uses {@code iris_Position}/
     * {@code iris_Normal} attributes, an {@code iris_NormalMat} (mat3 object&rarr;view normal matrix), and the
     * model-view/offset inside a {@code layout(std140) uniform iris_DynamicTransforms {...} iris_transforms}
     * block — <em>not</em> {@code gl_Vertex}/{@code gl_Normal}/{@code gl_ModelViewMatrix}. {@code gbufferModelViewInverse}
     * (view&rarr;world) and {@code cameraPosition} survive unchanged. We compute exactly as the pack does (cf.
     * its own {@code normal = normalize(iris_NormalMat * iris_Normal)} and
     * {@code iris_transforms.ModelViewMat * translate(ModelOffset) * vec4(iris_Position,1)}), so world normal +
     * surface&rarr;camera view direction + object-space position come out in the same frame as the editor
     * preview. Gating on {@code iris_Normal}/{@code iris_transforms} also guarantees these names exist, so core-
     * profile composite/deferred passes (which lack them, and whose fragments we never hijack) are skipped.
     *
     * <p>The varyings are geometry-only (identical for every surface), so they're declared once, not per id,
     * and aren't gated on {@code kg_surface_id} (writing them for non-KilaGraph geometry is harmless).</p>
     */
    public static String injectVertex(String name, String source, List<IrisSurfaceRegistry.Surface> surfaces) {
        if (source == null || source.contains(MARKER)) return source;
        if (!source.contains("gl_Position")) return source;      // vertex shader only (fragments have none)
        if (!IRIS_NORMAL_ATTR.matcher(source).find()) return source; // has the iris_Normal attribute (not just iris_NormalMat)
        if (surfaces.stream().noneMatch(IrisSurfaceRegistry.Surface::usesGeometry)) return source;
        // Skip any pass missing a name our writes reference (declaration sets differ per pass) — injecting an
        // undefined reference fails compilation and disables the entire shaderpack.
        for (String tok : GEOMETRY_WRITE_TOKENS) if (!source.contains(tok)) return source;

        String decls = "\n// " + MARKER + "\n" + geometryVaryingDecls(glslVersion(source) >= 130 ? "out" : "varying");
        String body = insertAtFirstCodeLine(source, decls);
        // Model-space position incl. the chunk/entity offset — matches the pack's own transform (ModelOffset is
        // 0 for entities, the section offset for terrain). The frame matches the non-injection meshPosition.
        String modelPos = "(iris_Position + iris_transforms.ModelOffset)";
        String writes = "\n    {\n"
                + "        vec3 kgVP = (iris_transforms.ModelViewMat * vec4(" + modelPos + ", 1.0)).xyz;\n"
                + "        kg_normal = normalize(mat3(gbufferModelViewInverse) * (iris_NormalMat * iris_Normal));\n"
                + "        kg_viewDir = normalize(mat3(gbufferModelViewInverse) * (-kgVP));\n"
                + "        kg_localPos = " + modelPos + ";\n"
                + "    }\n";
        return insertBeforeMainEnd(body, writes);
    }

    /** Declarations of the geometry varyings with the given storage qualifier ({@code in}/{@code out}/
     *  {@code varying}) — used in both the fragment (read) and vertex (write) stages. */
    private static String geometryVaryingDecls(String qualifier) {
        StringBuilder sb = new StringBuilder();
        for (String v : GEOMETRY_VARYINGS) sb.append(qualifier).append(" vec3 ").append(v).append(";\n");
        return sb.toString();
    }

    /** The GLSL version from the leading {@code #version N} directive (110 if absent) — picks {@code in}/
     *  {@code out} (core, &ge;130) vs {@code varying} (legacy) for our varying declarations. */
    private static int glslVersion(String source) {
        Matcher m = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)").matcher(source);
        return m.find() ? Integer.parseInt(m.group(1)) : 110;
    }

    /** Insert {@code code} just before the closing brace of {@code void main(){...}} (brace-matched from the
     *  first {@code {} after {@code main}). Returns {@code source} unchanged if no balanced {@code main} body
     *  is found. */
    private static String insertBeforeMainEnd(String source, String code) {
        Matcher m = Pattern.compile("void\\s+main\\s*\\(").matcher(source);
        if (!m.find()) return source;
        int open = source.indexOf('{', m.end());
        if (open < 0) return source;
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                return source.substring(0, i) + code + source.substring(i);
            }
        }
        return source; // unbalanced braces — leave untouched
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
    private static String samplerHelper(String sampler, String kind) {
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
        return "\nvec4 kg_sample_" + sampler + "(vec2 kg_uv) {\n"
                + "    if (kg_surface_id != 0) { kg_Surface s = kg_surface(kg_surface_id, kg_uv); return " + encoded + "; }\n"
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
