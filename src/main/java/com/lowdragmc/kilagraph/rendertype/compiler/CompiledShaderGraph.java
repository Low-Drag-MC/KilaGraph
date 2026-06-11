package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * The product of compiling a {@link RenderTypeGraph}: the generated vertex/fragment GLSL, the
 * material uniform layout, the set of Minecraft builtin UBOs referenced (e.g. {@code DynamicTransforms},
 * {@code Fog}, {@code Projection}), whether the graph uses KilaGraph's engine-globals block
 * ({@code KG_Globals}, e.g. Time), and the render-state settings. {@link #contentHash()} is a stable
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
        boolean usesEngineGlobals,
        List<StageError> stageErrors,
        RenderTypeGraph.Settings settings,
        Map<String, float[]> uniformDefaults,
        Map<String, SamplerDefault> samplerDefaults,
        Map<String, MaterialUniformLayout.Field> uniformFields,
        Map<String, String> variableSamplers,
        boolean usesOverlay,
        boolean usesLightmap,
        // Attribute names a node/block default referenced that aren't in the vertex format (a safe constant
        // was substituted in the GLSL). Diagnostics only — surfaced as editor warnings; the GLSL already
        // reflects the substitution, so this is NOT part of contentHash().
        List<String> missingAttributes
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
