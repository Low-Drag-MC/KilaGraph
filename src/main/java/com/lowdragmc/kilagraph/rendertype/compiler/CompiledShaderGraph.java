package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.runtime.ShaderUniformBlock;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * The product of compiling a {@link RenderTypeGraph}: the generated vertex/fragment GLSL, the
 * material uniform layout, the set of Minecraft builtin UBOs referenced (e.g. {@code DynamicTransforms},
 * {@code Fog}, {@code Projection}), the KilaGraph-managed {@link ShaderUniformBlock}s the graph uses
 * ({@code KG_Globals}, {@code KG_Transforms}, or a mod's own — each declared in the GLSL, bound on the
 * pipeline, and uploaded each frame), and the render-state settings. {@link #contentHash()} is a stable
 * hash over the GLSL + settings used as the cache key for pipeline reuse.
 *
 * <p>{@code uniformDefaults} / {@code samplerDefaults} carry the baked-in values for EXPOSED graph
 * variables (uniform field -> std140 components; sampler name -> default texture). They are
 * per-instance values applied at material build, deliberately <b>not</b> part of {@link #contentHash()}
 * (the GLSL is identical regardless of default value, so pipeline sharing stays correct).</p>
 *
 * <p>{@code uniformFields} / {@code variableSamplers} map a graph variable's <em>display name</em> to
 * its generated uniform field (name + type) / sampler name, so the runtime can offer set-by-name
 * uniform/texture updates without callers knowing the mangled {@code kg_*} identifiers. Also metadata,
 * not part of {@link #contentHash()}.</p>
 *
 * <p>{@code usesOverlay} / {@code usesLightmap} record whether an Overlay/LightMap node referenced
 * {@code Sampler1}/{@code Sampler2}, so the runtime enables vanilla overlay/lightmap binding (replacing
 * the removed {@code Settings.useOverlay/useLightmap}).</p>
 */
public record CompiledShaderGraph(
        String vertexSource,
        String fragmentSource,
        MaterialUniformLayout layout,
        List<String> builtinUniforms,
        // KilaGraph-managed UBOs the graph uses (engine globals / transforms / a mod's own). Each is declared
        // in the generated GLSL (so it's implicitly covered by contentHash via the sources), bound on the
        // pipeline, and uploaded each frame by the material. Replaced the per-block usesEngineGlobals/
        // usesTransforms flags — the generic extension point for new engine UBOs.
        List<ShaderUniformBlock> uniformBlocks,
        List<StageError> stageErrors,
        RenderTypeGraph.Settings settings,
        Map<String, float[]> uniformDefaults,
        Map<String, SamplerDefault> samplerDefaults,
        Map<String, MaterialUniformLayout.Field> uniformFields,
        Map<String, String> variableSamplers,
        boolean usesOverlay,
        boolean usesLightmap,
        // Whether a SceneColor/SceneDepth node referenced KG_SceneColor/KG_SceneDepth — the runtime then
        // captures the opaque scene color/depth (SceneCaptureManager) and binds it at draw instead of a
        // TextureManager texture. Like usesOverlay/usesLightmap, drives runtime binding, not the GLSL.
        boolean usesSceneColor,
        boolean usesSceneDepth,
        // Whether the fragment stage has an Alpha Discard block (a `discard` below its cutoff). Drives the
        // Iris ShaderKey choice (ENTITIES_CUTOUT vs ENTITIES_SOLID) so the pack's alpha-tested program
        // renders our cutout geometry. Derived from the fragment source (which IS hashed), so it's
        // consistent across materials sharing a pipeline; not separately part of contentHash().
        boolean alphaDiscards,
        // Attribute names a node/block default referenced that aren't in the vertex format (a safe constant
        // was substituted in the GLSL). Diagnostics only — surfaced as editor warnings; the GLSL already
        // reflects the substitution, so this is NOT part of contentHash().
        List<String> missingAttributes,
        // The fragment surface compiled as a kg_surface() function for injection into an Iris shaderpack's
        // gbuffers program (see InjectionSnippet / IrisShaderInjector). Null when the graph isn't
        // injection-compatible or for editor previews. Metadata — NOT part of contentHash().
        @Nullable InjectionSnippet injectionSnippet
) {

    /** Whether stage-affinity violations were found (the generated GLSL is then not safe to compile). */
    public boolean hasStageErrors() {
        return !stageErrors.isEmpty();
    }

    /** Stable content hash over the generated sources + settings, for pipeline caching. */
    public String contentHash() {
        CRC32 crc = new CRC32();
        crc.update(vertexSource.getBytes(StandardCharsets.UTF_8));
        crc.update((byte) 0);
        crc.update(fragmentSource.getBytes(StandardCharsets.UTF_8));
        crc.update((byte) 0);
        crc.update(String.valueOf(settings).getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
