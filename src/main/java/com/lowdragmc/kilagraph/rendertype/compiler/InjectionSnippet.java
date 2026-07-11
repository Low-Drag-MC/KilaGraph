package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock;

import java.util.List;
import java.util.Map;

/**
 * The GLSL pieces needed to run a graph's fragment surface shading <em>inside an Iris shaderpack's shared
 * gbuffers program</em> (see {@code IrisShaderInjector}). Produced by
 * {@link ShaderGraphCompiler#buildInjectionSnippet()} in <em>injection mode</em>: the fragment graph is
 * compiled into the body of a {@code kg_Surface kg_surface_<id>(vec2 kg_uv)} function — mesh uv becomes the
 * function parameter and the vertex varyings degrade to preview defaults (vertexColor/color → white, normal → +Y,
 * viewDir → +Z, position → 0), so the snippet is self-contained except for the {@code KG_Material} UBO, the
 * graph's own samplers, and KilaGraph-managed UBOs ({@code KG_Globals}/{@code KG_Transforms}), which the
 * runtime binds onto the shaderpack program at draw.
 *
 * <p>Because the shaderpack compiles <b>one shared program per {@code ShaderKey}</b>, many materials' snippets
 * coexist in that program, gated by {@code kg_surface_id}. The pieces are kept separate so
 * {@code IrisSurfaceRegistry} can namespace per-id collision-prone identifiers and the injector can dedup
 * shared declarations/helpers across snippets. All strings are raw (not yet id-namespaced).</p>
 *
 * <p>A graph whose fragment output needs a Minecraft engine include ({@code #moj_import}, i.e.
 * Fog/vanilla-Lighting) is <b>not</b> injection-compatible; {@link ShaderGraphCompiler#buildInjectionSnippet()}
 * returns {@code null} for those and the material falls back to the M0 passthrough under Iris. Scene
 * Color/Depth <em>are</em> injectable: depth reads Iris's own {@code depthtex1} and colour reads the
 * {@code KG_SceneColor} capture the runtime binds at draw (see {@code SceneCaptureManager}).</p>
 *
 * @param declarationUnits global declarations (the {@code KG_Material} block, each sampler line, each managed
 *                         UBO block, the {@code KG_Gradient} struct) — one complete declaration per element so
 *                         identical ones dedup across snippets
 * @param functions        helper function definitions (procedural/gradient), deduped by the injector
 * @param body             the function body statements (hoisted temps)
 * @param surfaceArgs      the comma-joined arguments to the {@code kg_Surface(...)} constructor, in the
 *                         canonical field order: albedo, alpha, normalTS, smoothness, metallic, emission,
 *                         ao, height, porosity, sss
 * @param usesGeometry     whether the surface reads the mesh normal — the cue for {@code IrisShaderInjector}
 *                         to declare the {@code vec3 kg_normalView} global and fill it from the pack's own
 *                         {@code normal} varying (constant fallback when the pack has none). View direction,
 *                         position, and screen inputs are reconstructed in the fragment from
 *                         {@code gl_FragCoord} + KilaGraph UBOs; the vertex stage is never touched.
 * @param usesSceneDepth   whether the surface samples the scene depth — under injection that reads Iris's
 *                         own {@code depthtex1} (the opaque-depth snapshot Iris auto-binds by name on every
 *                         gbuffers program), so the injector must declare the sampler iff the pack's
 *                         flattened source doesn't already
 * @param uniformBlocks    the KilaGraph-managed UBOs the <b>snippet</b> references. Can be a superset of
 *                         the main compile's list: injection-only reconstructions pull blocks the vanilla
 *                         path never needs (e.g. Fresnel's view-direction needs {@code KG_Globals.ScreenSize}
 *                         only under injection) — the material must upload + bind these too, or the injected
 *                         program reads zeros (the "pure white Fresnel" failure)
 * @param samplerDefaults  baked defaults for the samplers the <b>snippet</b> references. Like
 *                         {@code uniformBlocks} this can exceed the main compile's set (the injection-only
 *                         {@code kg_NeutralWhite} degrade sampler) — the material must resolve + bind the
 *                         extras onto the injected program, or their uniforms stay on unit 0 (the pack's
 *                         atlas)
 */
public record InjectionSnippet(
        List<String> declarationUnits,
        List<String> functions,
        String body,
        String surfaceArgs,
        boolean usesGeometry,
        boolean usesSceneDepth,
        List<ShaderUniformBlock> uniformBlocks,
        Map<String, SamplerDefault> samplerDefaults
) {}
