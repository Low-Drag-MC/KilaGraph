package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerAddress;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerFilter;
import com.lowdragmc.kilagraph.rendertype.compiler.ColorTarget;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.format.KGVertexFormat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceRegistry;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a {@link CompiledShaderGraph} into a usable {@link RenderType}. The expensive products —
 * generated GLSL + {@link RenderPipeline} — are cached by the graph's content hash and shared across
 * material instances; each distinct set of textures/uniform values becomes a lightweight
 * {@link RenderTypeGraphMaterial} wrapping its own {@link RenderType} over the shared pipeline.
 *
 * <p><b>Resource management.</b> Generated pipelines/sources are reference-counted by content hash:
 * {@link #createMaterial} acquires a reference, {@link RenderTypeGraphMaterial#close()} releases it,
 * and when the last reference drops we evict the pipeline + GLSL source from our maps (and the
 * material frees its UBO {@link GpuBuffer}). This bounds our heap to live
 * materials — important for the live preview, which churns a new hash on every edit.</p>
 *
 * <p><b>Known limitation:</b> the device's pipeline cache has no per-pipeline release API (only a
 * global {@code clearPipelineCache}), so a generated shader program already compiled on the GPU
 * lingers in the device until a resource reload. We avoid creating <em>duplicate</em> device entries
 * by reusing one {@link RenderPipeline} object per live hash, but cannot free dead ones individually.</p>
 *
 * <p>All methods must run on the render thread (they touch the GPU device / pipeline cache).</p>
 */
public final class RenderTypeFactory {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cache: graph content hash -> shared RenderPipeline. */
    private static final Map<String, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();
    /** Live material references per content hash; entry evicted when it reaches 0. */
    private static final Map<String, Integer> REFCOUNTS = new ConcurrentHashMap<>();
    /** Hashes that failed to compile on the GPU. Content hash is deterministic, so a failed hash
     *  stays failed — skip re-precompiling it every frame (and dump its GLSL only once). */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    /** Graph hashes already warned about Scene Color/Depth on an opaque material (self-sampling feedback). */
    private static final Set<String> SCENE_ON_OPAQUE_WARNED = ConcurrentHashMap.newKeySet();
    /** Graph hashes already warned about a vertex format the Iris integration can't route (warn once each). */
    private static final Set<String> IRIS_FORMAT_WARNED = ConcurrentHashMap.newKeySet();
    private static final String IRIS_ALBEDO_SAMPLER = "Sampler0";
    private static final Identifier NEUTRAL_WHITE = Identifier.fromNamespaceAndPath(Kilagraph.MODID, "textures/misc/white.png");

    private RenderTypeFactory() {}

    /**
     * Compile {@code graph} and build a material from its compiled defaults. Returns {@code null} if the
     * generated pipeline fails to compile on the GPU. Dynamic per-instance textures/uniforms are set by
     * the caller afterward via {@link RenderTypeGraphMaterial#setTexture}/{@code setUniform}.
     */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(RenderTypeGraph graph) {
        return createMaterial(graph.createCompiler().compile());
    }

    /**
     * Build a material from an already-compiled graph (avoids recompiling when the caller has it). The
     * material is initialized purely from the graph's compiled defaults — EXPOSED variable defaults into
     * the UBO and {@code samplerDefaults} (else a missing-texture placeholder) into every declared
     * sampler; callers set dynamic values/textures afterward via {@code setUniform}/{@code setTexture}.
     * Validates the pipeline on the GPU; returns {@code null} (and cleans up the unreferenced entry) if
     * it is invalid, so callers never draw with a broken pipeline.
     */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled) {
        String hash = compiled.contentHash();
        if (FAILED.contains(hash)) return null; // known-bad: don't re-precompile / re-spam every frame
        if (compiled.hasStageErrors()) {
            for (var e : compiled.stageErrors()) LOGGER.warn("[KilaGraph] stage error: {}", e.message());
            return null; // generated GLSL references stage-unavailable data; don't build it
        }
        RenderPipeline pipeline = getOrBuildPipeline(compiled);

        if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
            if (FAILED.add(hash)) {
                LOGGER.warn("[KilaGraph] " +
                                "generated pipeline invalid for hash {}.\n--- VERTEX ---\n{}\n--- FRAGMENT ---\n{}",
                        hash, compiled.vertexSource(), compiled.fragmentSource());
            }
            if (!REFCOUNTS.containsKey(hash)) evictGenerated(hash); // nobody references it; drop our entry
            return null;
        }

        // A pack's entity program reads Sampler0/1/2 like a vanilla entity type does; unbound, it reads stale textures.
        boolean irisRouted = IrisCompat.ENABLED
                && compiled.settings().colorFormat() == RenderTypeGraph.Settings.ColorFormat.RGBA8
                && compiled.instanceAttributes().isEmpty()
                && compiled.colorTargets().isEmpty()
                && IrisCompat.supportsVertexFormat(vertexFormat(compiled.settings().vertexFormatElements()));
        boolean useLightmap = compiled.usesLightmap() || irisRouted;
        boolean useOverlay = compiled.usesOverlay() || irisRouted;
        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline);
        if (irisRouted) setup.withTexture(IRIS_ALBEDO_SAMPLER, NEUTRAL_WHITE);
        // Baked Sampler2D default textures + sampler params (per-instance dynamic textures are applied
        // later via RenderTypeGraphMaterial.setTexture, re-bound each draw by the mixin).
        Map<String, SamplerDefault> samplerDefaults = compiled.samplerDefaults();
        for (Map.Entry<String, SamplerDefault> e : samplerDefaults.entrySet()) {
            SamplerDefault def = e.getValue();
            setup.withTexture(e.getKey(), def.texture(), () -> gpuSampler(def));
        }
        // Every sampler the pipeline declares MUST be bound at draw or the GPU encoder throws
        // "Missing sampler". Bind a placeholder for any declared sampler not provided and not covered
        // by overlay (Sampler1) / lightmap (Sampler2).
        for (String sampler : compiled.layout().samplers()) {
            if (samplerDefaults.containsKey(sampler)) continue;
            if (sampler.equals("Sampler1") && useOverlay) continue;
            if (sampler.equals("Sampler2") && useLightmap) continue;
            // Scene colour/depth are bound dynamically from SceneCaptureManager each draw, not via RenderSetup.
            if (isSceneSampler(sampler)) continue;
            setup.withTexture(sampler, MissingTextureAtlasSprite.getLocation());
        }
        if (useLightmap) setup.useLightmap();
        if (useOverlay) setup.useOverlay();
        if (compiled.settings().sortOnUpload()) setup.sortOnUpload();
        setup.setOutputTarget(outputTarget(compiled.settings().outputTarget()));

        REFCOUNTS.merge(hash, 1, Integer::sum);
        // The graph samples the captured opaque scene — start (and refcount) the capture while this material lives.
        if (compiled.usesSceneColor() || compiled.usesSceneDepth()) {
            SceneCaptureManager.INSTANCE.acquire();
            // Scene Color/Depth sample the capture taken AFTER opaque geometry. An opaque material draws
            // BEFORE that point, so at its own screen position the capture contains... itself (last frame):
            // a self-sampling feedback loop that renders black with view-dependent fringes (identical in
            // vanilla and under shaderpacks; same constraint as Unity's Scene Color). Warn once per graph.
            if (compiled.settings().blend() == RenderTypeGraph.Settings.BlendMode.OPAQUE
                    && SCENE_ON_OPAQUE_WARNED.add(hash)) {
                Kilagraph.LOGGER.warn("[KilaGraph] graph {} uses Scene Color/Depth on an OPAQUE material: "
                        + "it draws before the scene capture and will sample ITSELF (black feedback). "
                        + "Use a translucent BlendMode and draw it in the translucent phase.", hash);
            }
        }
        String name = Kilagraph.MODID + ":graph/" + hash;
        RenderType renderType = RenderType.create(name, setup.createRenderSetup());
        // UBOs only the injection snippet needs (fragment reconstructions — e.g. Fresnel's viewDir pulls
        // KG_Globals.ScreenSize only under injection): uploaded + Iris-bound by the material, but kept out
        // of the vanilla render-pass binding (the vanilla pipeline doesn't declare them).
        List<ShaderUniformBlock> injectionOnly = new ArrayList<>();
        Map<String, SamplerDefault> injectionOnlyTextures = new LinkedHashMap<>();
        if (compiled.injectionSnippet() != null) {
            for (ShaderUniformBlock block : compiled.injectionSnippet().uniformBlocks()) {
                boolean inMain = compiled.uniformBlocks().stream()
                        .anyMatch(b -> b.uboName().equals(block.uboName()));
                if (!inMain) injectionOnly.add(block);
            }
            // Samplers only the snippet bakes (the kg_NeutralWhite degrade) — same gap as the blocks:
            // without these the injected program's sampler uniform stays on unit 0 (the pack's atlas).
            Map<String, SamplerDefault> mainTextures = buildMaterialTextures(compiled);
            for (Map.Entry<String, SamplerDefault> e : compiled.injectionSnippet().samplerDefaults().entrySet()) {
                if (!mainTextures.containsKey(e.getKey()) && !isSceneSampler(e.getKey())) {
                    injectionOnlyTextures.put(e.getKey(), e.getValue());
                }
            }
        }
        RenderTypeGraphMaterial material = new RenderTypeGraphMaterial(
                renderType, compiled.layout(), compiled.uniformBlocks(), injectionOnly, hash,
                compiled.uniformFields(), compiled.variableSamplers(), buildMaterialTextures(compiled),
                injectionOnlyTextures, compiled.usesSceneColor(), compiled.usesSceneDepth());
        material.setInstanceLayout(KGInstanceLayout.of(compiled.instanceAttributes()));
        material.setColorTargets(compiled.colorTargets());
        // Bake EXPOSED-variable defaults into the material UBO; callers may override later via setUniform.
        for (Map.Entry<String, float[]> e : compiled.uniformDefaults().entrySet()) {
            material.setUniformField(e.getKey(), e.getValue());
        }
        // Iris compatibility: register the graph's compiled surface so a shaderpack routes our geometry
        // through its entities gbuffers program and runs our real shading there. register() returns a
        // non-zero kg_surface_id when the graph is injection-compatible (the injected kg_surface(id,...)
        // dispatch then shades our geometry), or 0 when it isn't — id 0 still assigns to entities so the
        // geometry renders, falling back to the shaderpack's own albedo (passthrough). No-op without Iris.
        // Gated on the vertex format: the entity ShaderKeys we assign to carry the ENTITY layout, and Iris
        // re-checks nothing (see IrisCompat.supportsVertexFormat) — routing another layout there draws
        // silently-wrong geometry, so such a graph is left out of the integration entirely.
        if (IrisCompat.ENABLED) {
            if (irisRouted) {
                material.setIrisSurfaceId(IrisSurfaceRegistry.register(compiled));
                boolean translucent = compiled.settings().blend() != RenderTypeGraph.Settings.BlendMode.OPAQUE;
                IrisCompat.assignToEntities(pipeline, translucent, compiled.alphaDiscards());
                // A reload to inject this new surface (if the programs are now stale) is driven from the
                // client tick (IrisCompat.reloadShadersIfStale via IrisDebugCommand), NOT here — reloading
                // mid-frame crashes the world pipeline.
            } else if (IRIS_FORMAT_WARNED.add(hash)) {
                // Skipping is not just correctness: registering would GL-validate the surface, bump the
                // registry generation (forcing a full shaderpack recompile on the next tick) and inject this
                // surface into every pack program for the rest of the session — all for geometry the pack
                // program could never shade correctly. Unassigned, Iris finds no override for the pipeline
                // (it logs one "doesn't have a shader override" error of its own) and leaves our own program
                // in place, so the material still draws with KilaGraph's shading, just without pack lighting.
                LOGGER.warn("[KilaGraph][Iris] graph {} uses vertex format {} — shaderpack integration needs "
                                + "the ENTITY format (Position, Color, UV0, UV1, UV2, Normal), so this graph is "
                                + "not routed through the pack: it keeps rendering with its own shader, "
                                + "unlit by the shaderpack.",
                        hash, compiled.settings().vertexFormatElements());
            }
        }
        return material;
    }

    /** Release a material's reference to its generated pipeline; evict when the last one drops. */
    static void release(String contentHash) {
        // Mirror the release into the Iris surface registry (no-op if this graph wasn't injection-registered).
        if (IrisCompat.LOADED) IrisSurfaceRegistry.release(contentHash);
        REFCOUNTS.compute(contentHash, (h, count) -> {
            if (count == null) return null;
            if (count <= 1) {
                evictGenerated(h);
                return null;
            }
            return count - 1;
        });
    }

    /** Drop our heap references to a generated pipeline + its GLSL source (GPU program can't be freed individually). */
    private static void evictGenerated(String contentHash) {
        PIPELINES.remove(contentHash);
        DynamicShaderSourceRegistry.unregister(DynamicShaderSourceRegistry.shaderId(contentHash));
    }

    /** Get the cached pipeline for this compiled graph, building + registering its GLSL on first use. */
    public static RenderPipeline getOrBuildPipeline(CompiledShaderGraph compiled) {
        return PIPELINES.computeIfAbsent(compiled.contentHash(), hash -> buildPipeline(compiled));
    }

    private static RenderPipeline buildPipeline(CompiledShaderGraph compiled) {
        Identifier shaderId = DynamicShaderSourceRegistry.shaderId(compiled.contentHash());
        // Resolve #moj_import the same way ShaderManager does for asset shaders — the GL device
        // compiles whatever the registry returns, and the driver can't understand #moj_import.
        String vsh = GlslImportProcessor.process(compiled.vertexSource());
        String fsh = GlslImportProcessor.process(compiled.fragmentSource());
        DynamicShaderSourceRegistry.register(shaderId, vsh, fsh);

        RenderTypeGraph.Settings settings = compiled.settings();
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "pipeline/" + compiled.contentHash()))
                .withVertexShader(shaderId)
                .withFragmentShader(shaderId)
                .withVertexBinding(0, vertexFormat(settings.vertexFormatElements()))
                .withPrimitiveTopology(primitiveTopology(settings.vertexFormatMode()))
                .withColorTargetState(colorTarget(blendFunction(settings.blend()), settings.colorFormat()))
                .withDepthStencilState(depthState(settings))
                .withCull(settings.cull());
        for (ColorTarget target : compiled.colorTargets()) {
            b.withColorTargetState(target.location(), colorTarget(blendFunction(settings.blend()), target.format()));
        }

        // One bind group for the whole program (Vulkan rejects anything the shader declares but the layout lacks).
        BindGroupLayout.Builder layout = BindGroupLayout.builder();

        // Builtin UBOs actually referenced by the generated GLSL, plus DynamicTransforms: the vanilla draw
        // path binds it on EVERY draw, so a pipeline of ours must declare it whether or not a node read it.
        // That requirement belongs here and not in the compiler — builtinUniforms() means "what the GLSL
        // references", and a consumer driving its own draw (Photon's fullscreen post-effect passes) binds
        // nothing of Minecraft's, so a declaration it can't honour is a hard "Missing uniform" at draw.
        Set<String> builtins = new LinkedHashSet<>(compiled.builtinUniforms());
        builtins.add("DynamicTransforms");
        for (String ubo : builtins) {
            layout.withUniform(ubo, UniformType.UNIFORM_BUFFER);
        }
        // Per-material UBO + samplers exposed by the graph.
        if (!compiled.layout().isEmpty()) {
            layout.withUniform(MaterialUniformLayout.UBO_NAME, UniformType.UNIFORM_BUFFER);
        }
        // KilaGraph-managed UBOs the graph uses (engine globals / transforms / a mod's own), uploaded by us
        // each frame. Generic — adding a new engine UBO needs no change here.
        for (ShaderUniformBlock block : compiled.uniformBlocks()) {
            layout.withUniform(block.uboName(), UniformType.UNIFORM_BUFFER);
        }
        for (String sampler : compiled.layout().samplers()) {
            layout.withSampler(sampler);
        }
        KGInstanceLayout instances = KGInstanceLayout.of(compiled.instanceAttributes());
        if (instances != null) b.withVertexBinding(1, instances.format());
        return b.withBindGroupLayout(layout.build()).build();
    }

    /**
     * The material's dynamic sampler map (sampler name -> texture + params): every graph sampler the
     * material manages, excluding overlay/lightmap (vanilla-owned), defaulting to the baked default or
     * the missing-texture. Used at build and on {@link RenderTypeGraphMaterial#refreshDefaults} (a
     * value-only graph edit changes these but not the GLSL/content hash, so the material re-bakes them
     * without a pipeline rebuild).
     */
    public static Map<String, SamplerDefault> buildMaterialTextures(CompiledShaderGraph compiled) {
        Map<String, SamplerDefault> textures = new HashMap<>();
        for (String sampler : compiled.layout().samplers()) {
            if (sampler.equals("Sampler1") && compiled.usesOverlay()) continue;
            if (sampler.equals("Sampler2") && compiled.usesLightmap()) continue;
            if (isSceneSampler(sampler)) continue; // bound from SceneCaptureManager, not TextureManager
            textures.put(sampler, compiled.samplerDefaults().getOrDefault(sampler, SamplerDefault.missing()));
        }
        return textures;
    }

    /** Whether {@code sampler} is one of the captured scene samplers (bound dynamically, not via RenderSetup). */
    private static boolean isSceneSampler(String sampler) {
        return sampler.equals(ShaderGraphCompiler.SCENE_COLOR_SAMPLER)
                || sampler.equals(ShaderGraphCompiler.SCENE_DEPTH_SAMPLER);
    }

    /** Build the GPU sampler for a {@link SamplerDefault}, mapping KilaGraph's enums to blaze3d's. */
    public static GpuSampler gpuSampler(SamplerDefault def) {
        FilterMode filter = def.filter() == SamplerFilter.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST;
        AddressMode address = def.address() == SamplerAddress.REPEAT ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        return RenderSystem.getSamplerCache().getSampler(address, address, filter, filter, def.mipmap());
    }

    // ---- Settings -> pipeline state mapping --------------------------------------------------

    public static VertexFormat vertexFormat(List<String> elementKeys) {
        return KGVertexFormat.of(elementKeys);
    }

    public static PrimitiveTopology primitiveTopology(RenderTypeGraph.Settings.VertexFormatMode mode) {
        return switch (mode) {
            case QUADS -> PrimitiveTopology.QUADS;
            case TRIANGLES -> PrimitiveTopology.TRIANGLES;
            case TRIANGLE_STRIP -> PrimitiveTopology.TRIANGLE_STRIP;
            case LINES -> PrimitiveTopology.LINES;
            case LINE_STRIP -> PrimitiveTopology.DEBUG_LINE_STRIP;
        };
    }

    private static ColorTargetState colorTarget(Optional<BlendFunction> function,
                                                RenderTypeGraph.Settings.ColorFormat format) {
        return new ColorTargetState(function, GpuFormat.valueOf(format.gpuFormat), ColorTargetState.WRITE_ALL);
    }

    private static Optional<BlendFunction> blendFunction(RenderTypeGraph.Settings.BlendMode blend) {
        return switch (blend) {
            case OPAQUE -> Optional.empty();
            case TRANSLUCENT -> Optional.of(BlendFunction.TRANSLUCENT);
            case ADDITIVE -> Optional.of(BlendFunction.ADDITIVE);
            case LIGHTNING -> Optional.of(BlendFunction.LIGHTNING);
            case GLINT -> Optional.of(BlendFunction.GLINT);
            case OVERLAY -> Optional.of(BlendFunction.OVERLAY);
            case TRANSLUCENT_PREMULTIPLIED_ALPHA -> Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
            case ENTITY_OUTLINE_BLIT -> Optional.of(BlendFunction.ENTITY_OUTLINE_BLIT);
            case INVERT -> Optional.of(BlendFunction.INVERT);
            // The colour operations below keep the destination alpha.
            case MULTIPLY -> Optional.of(colorOp(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendOp.ADD));
            case SUBTRACT -> Optional.of(colorOp(BlendFactor.ONE, BlendFactor.ONE, BlendOp.REVERSE_SUBTRACT));
            case MIN -> Optional.of(colorOp(BlendFactor.ONE, BlendFactor.ONE, BlendOp.MIN));
            case MAX -> Optional.of(colorOp(BlendFactor.ONE, BlendFactor.ONE, BlendOp.MAX));
        };
    }

    private static BlendFunction colorOp(BlendFactor src, BlendFactor dst, BlendOp op) {
        return new BlendFunction(src, dst, op, BlendFactor.ZERO, BlendFactor.ONE, BlendOp.ADD);
    }

    /** Minecraft renders reversed-Z, so "nearer" ({@code LEQUAL}/{@code LESS}) is a greater-than compare, and a
     *  positive bias pulls toward the camera. */
    private static DepthStencilState depthState(RenderTypeGraph.Settings settings) {
        CompareOp op = switch (settings.depthTest()) {
            case LEQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
            case LESS -> CompareOp.GREATER_THAN;
            case EQUAL -> CompareOp.EQUAL;
            case ALWAYS, NONE -> CompareOp.ALWAYS_PASS;
        };
        float factor = settings.depthOffsetFactor(), units = settings.depthOffsetUnits();
        // Minecraft's Vulkan backend only enables depth bias when both are non-zero (GL: either).
        if ((factor == 0) != (units == 0)) {
            if (factor == 0) factor = Float.MIN_VALUE;
            else units = Float.MIN_VALUE;
        }
        return new DepthStencilState(op, settings.depthWrite(), factor, units);
    }

    private static OutputTarget outputTarget(RenderTypeGraph.Settings.OutputTarget target) {
        return switch (target) {
            case WEATHER -> OutputTarget.WEATHER_TARGET;
            case ITEM_ENTITY -> OutputTarget.ITEM_ENTITY_TARGET;
            case MAIN, TRANSLUCENT, PARTICLES -> OutputTarget.MAIN_TARGET;
        };
    }
}
