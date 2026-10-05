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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.BlendOp;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Turns a {@link CompiledShaderGraph} into a usable {@link RenderType}. The expensive products —
 * generated GLSL + {@link RenderPipeline}s — are cached by the graph's content hash and shared across
 * material instances; each distinct set of textures/uniform values becomes a lightweight
 * {@link RenderTypeGraphMaterial} wrapping its own {@link RenderType} over the shared pipelines.
 *
 * <p><b>Pipelines.</b> One GLSL source serves every pipeline of a graph, its shader defines picking the variant
 * (see {@code ShaderGraphCompiler.assembleFragment}):</p>
 * <ul>
 *   <li>the <b>main</b> pipeline, drawn in Minecraft's passes, which have a single colour attachment;</li>
 *   <li>a <b>colour-targets</b> pipeline when the graph writes extra colour targets, for a pass that has them;</li>
 *   <li>an {@link OitPipelineSet} for a blended material Minecraft's order-independent transparency can
 *       express, drawn in the OIT phases when improved transparency is on. A blend those can't express
 *       (multiply, min, invert…) draws in the solid phase instead, with either transparency setting — as
 *       vanilla's own such render types do (see {@link RenderTypeGraphMaterial#drawsInSolidPhase}).</li>
 * </ul>
 *
 * <p><b>Resource management.</b> Generated pipelines/sources are reference-counted by content hash:
 * {@link #createMaterial} acquires a reference, {@link RenderTypeGraphMaterial#close()} releases it,
 * and when the last reference drops the pipelines are closed and evicted along with their GLSL source (and the
 * material frees its UBO {@link GpuBuffer}). This bounds both heap and GPU to live materials — important for the
 * live preview, which churns a new hash on every edit. A preview's background build ({@link #buildInBackground})
 * holds no reference until its material is made; one nobody makes a material of is dropped with
 * {@link #abandonBuild}.</p>
 *
 * <p>All methods must run on the render thread (they touch the GPU device / pipeline cache).</p>
 */
public final class RenderTypeFactory {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The pipelines generated for one graph (see the class javadoc). */
    public record GeneratedPipelines(RenderPipeline main, @Nullable RenderPipeline colorTargets,
                                     @Nullable OitPipelineSet oit) {
        public List<RenderPipeline> all() {
            List<RenderPipeline> all = new ArrayList<>(5);
            all.add(main);
            if (colorTargets != null) all.add(colorTargets);
            if (oit != null) {
                all.add(oit.depthBoundsPipeline());
                all.add(oit.transmittancePipeline());
                all.add(oit.accumulatePipeline());
            }
            return all;
        }
    }

    /** Cache: graph content hash -> the pipelines shared by every material of that graph. */
    private static final Map<String, GeneratedPipelines> PIPELINES = new ConcurrentHashMap<>();
    /** Live material references per content hash; entry evicted when it reaches 0. */
    private static final Map<String, Integer> REFCOUNTS = new ConcurrentHashMap<>();
    /** Hashes that failed to compile on the GPU. Content hash is deterministic, so a failed hash
     *  stays failed — skip re-compiling it every frame (and dump its GLSL only once). */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    /** Graph hashes already warned about Scene Color/Depth drawn before the capture (self-sampling feedback). */
    private static final Set<String> SCENE_BEFORE_CAPTURE_WARNED = ConcurrentHashMap.newKeySet();
    /** Blend modes already told about drawing in the solid phase. */
    private static final Set<RenderTypeGraph.Settings.BlendMode> SOLID_PHASE_LOGGED = ConcurrentHashMap.newKeySet();
    /** Sources of evicted graphs a background compile still reads, dropped once it is done. Render thread. */
    private static final Map<String, CompletableFuture<?>> SOURCES_IN_USE = new HashMap<>();
    /** Graph hashes already warned about a vertex format the Iris integration can't route (warn once each). */
    private static final Set<String> IRIS_FORMAT_WARNED = ConcurrentHashMap.newKeySet();
    /** Numbers each material's render type, so two materials of one graph never prepare to equal
     *  {@code PreparedRenderType} records — equal ones share a draw (and so one material's values). */
    private static final AtomicInteger MATERIAL_SERIAL = new AtomicInteger();
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
     * Compiles the main pipeline on the GPU; returns {@code null} (and cleans up the unreferenced entry) if
     * it is invalid, so callers never draw with a broken pipeline — or when no shader reload has completed yet,
     * so there are no sources to compile against.
     */
    @Nullable
    public static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled) {
        return createMaterial(compiled, true);
    }

    /**
     * {@link #createMaterial(CompiledShaderGraph)} for a preview drawn in a scene of its own, which Minecraft's
     * order-independent transparency never draws: the material is the same, but its OIT pipelines aren't compiled
     * up front — only should one ever draw.
     */
    @Nullable
    public static RenderTypeGraphMaterial createPreviewMaterial(CompiledShaderGraph compiled) {
        return createMaterial(compiled, false);
    }

    /**
     * Compile {@code compiled}'s main pipeline off the render thread for a {@link #createPreviewMaterial} later,
     * which then doesn't wait for it; call again (on later frames) until it isn't {@code COMPILING}. A {@code FAILED}
     * build is reported by that createPreviewMaterial, which returns {@code null}. Drop a build nobody will make a
     * material of with {@link #abandonBuild}.
     */
    public static KGPipelines.State buildInBackground(CompiledShaderGraph compiled) {
        if (FAILED.contains(compiled.contentHash()) || compiled.hasStageErrors()) return KGPipelines.State.FAILED;
        return KGPipelines.compileInBackground(getOrBuildPipelines(compiled).main());
    }

    /** Drop {@link #buildInBackground}'s pipelines and sources unless a material uses them. */
    public static void abandonBuild(CompiledShaderGraph compiled) {
        String hash = compiled.contentHash();
        if (!REFCOUNTS.containsKey(hash) && PIPELINES.containsKey(hash)) evictGenerated(hash);
    }

    @Nullable
    private static RenderTypeGraphMaterial createMaterial(CompiledShaderGraph compiled, boolean compileOit) {
        String hash = compiled.contentHash();
        if (FAILED.contains(hash)) return null; // known-bad: don't re-compile / re-spam every frame
        if (compiled.hasStageErrors()) {
            for (var e : compiled.stageErrors()) LOGGER.warn("[KilaGraph] stage error: {}", e.message());
            return null; // generated GLSL references stage-unavailable data; don't build it
        }
        GeneratedPipelines pipelines = getOrBuildPipelines(compiled);
        if (!KGPipelines.ensureCompiled(pipelines.main())) {
            if (KGPipelines.hasFailed(pipelines.main()) && FAILED.add(hash)) {
                LOGGER.warn("[KilaGraph] " +
                                "generated pipeline invalid for hash {}.\n--- VERTEX ---\n{}\n--- FRAGMENT ---\n{}",
                        hash, compiled.vertexSource(), compiled.fragmentSource());
            }
            if (!REFCOUNTS.containsKey(hash)) evictGenerated(hash); // nobody references it; drop our entry
            return null;
        }
        // The OIT pipelines are compiled now only when they're about to be drawn — otherwise at their first draw,
        // which skips a pipeline that doesn't compile (see PreparedRenderTypeMixin) rather than failing the frame.
        OitPipelineSet oit = pipelines.oit();
        if (oit != null && compileOit && Minecraft.getInstance().gameRenderer.useImprovedTransparency()
                && !compileAll(oit)) {
            LOGGER.warn("[KilaGraph] graph {}: its order-independent transparency pipelines don't compile, "
                    + "so it draws in the solid phase", hash);
            oit = null;
        }
        RenderTypeGraph.Settings.BlendMode blend = compiled.settings().blend();
        boolean translucent = blend != RenderTypeGraph.Settings.BlendMode.OPAQUE;
        // A blend order-independent transparency can't express draws in the solid phase whatever the transparency
        // setting — as vanilla's own do (its glint, banner patterns) — blending over the opaque scene there.
        boolean solidPhase = translucent && oit == null;

        // A pack's entity program reads Sampler0/1/2 like a vanilla entity type does; unbound, it reads stale textures.
        boolean irisRouted = IrisCompat.ENABLED
                && compiled.settings().colorFormat() == RenderTypeGraph.Settings.ColorFormat.RGBA8
                && compiled.instanceAttributes().isEmpty()
                && compiled.colorTargets().isEmpty()
                && IrisCompat.supportsVertexFormat(vertexFormat(compiled.settings().vertexFormatElements()));
        boolean useLightmap = compiled.usesLightmap() || irisRouted;
        boolean useOverlay = compiled.usesOverlay() || irisRouted;
        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipelines.main());
        if (oit != null) setup.setOitPipelines(oit);
        if (irisRouted) setup.withTexture(IRIS_ALBEDO_SAMPLER, NEUTRAL_WHITE);
        // Baked Sampler2D default textures + sampler params (per-instance dynamic textures are applied
        // later via RenderTypeGraphMaterial.setTexture, re-bound each draw by the mixin).
        Map<String, SamplerDefault> samplerDefaults = compiled.samplerDefaults();
        for (Map.Entry<String, SamplerDefault> e : samplerDefaults.entrySet()) {
            SamplerDefault def = e.getValue();
            setup.withTexture(e.getKey(), def.texture(), () -> gpuSampler(def));
        }
        // Every sampler the pipeline declares MUST be bound at draw (the render pass validates it). Bind a
        // placeholder for any declared sampler not provided and not covered by overlay (Sampler1) / lightmap
        // (Sampler2).
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
        // Vanilla routes model submits by this; SubmitNodeCollectionMixin applies it to the other submits.
        if (solidPhase) setup.withForcedSolidModelPhase();

        REFCOUNTS.merge(hash, 1, Integer::sum);
        // The graph samples the captured opaque scene — start (and refcount) the capture while this material lives.
        if (compiled.usesSceneColor() || compiled.usesSceneDepth()) {
            SceneCaptureManager.INSTANCE.acquire();
            // Scene Color/Depth sample the capture taken AFTER the solid phase. A material drawn IN it (opaque, or
            // a blend with no order-independent form) draws BEFORE that point, so at its own screen position the
            // capture contains... itself (last frame): a self-sampling feedback loop that renders black with
            // view-dependent fringes (identical in vanilla and under shaderpacks; same constraint as Unity's Scene
            // Color). Warn once per graph.
            if ((!translucent || solidPhase) && SCENE_BEFORE_CAPTURE_WARNED.add(hash)) {
                Kilagraph.LOGGER.warn("[KilaGraph] graph {} uses Scene Color/Depth but draws in the solid phase "
                        + "(blend mode {}): before the scene capture, so it will sample ITSELF (black feedback). "
                        + "Use a translucent BlendMode.", hash, blend);
            }
        }
        if (solidPhase && SOLID_PHASE_LOGGED.add(blend)) {
            LOGGER.info("[KilaGraph] blend mode {} has no order-independent form: its materials draw in the solid "
                    + "phase, blending over the opaque scene", blend);
        }
        String name = Kilagraph.MODID + ":graph/" + hash + "/" + MATERIAL_SERIAL.incrementAndGet();
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
                injectionOnlyTextures, compiled.usesSceneColor(), compiled.usesSceneDepth(), solidPhase);
        material.setInstanceLayout(KGInstanceLayout.of(compiled.instanceAttributes()));
        material.setColorTargets(compiled.colorTargets(), pipelines.colorTargets());
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
                IrisCompat.assignToEntities(pipelines.main(), translucent, compiled.alphaDiscards());
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

    private static boolean compileAll(OitPipelineSet oit) {
        return KGPipelines.ensureCompiled(oit.depthBoundsPipeline())
                && KGPipelines.ensureCompiled(oit.transmittancePipeline())
                && KGPipelines.ensureCompiled(oit.accumulatePipeline());
    }

    /** Release a material's reference to its generated pipelines; evict when the last one drops. */
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

    /** Close a graph's pipelines and drop them + their GLSL source. */
    private static void evictGenerated(String contentHash) {
        GeneratedPipelines pipelines = PIPELINES.remove(contentHash);
        List<CompletableFuture<?>> compiling = new ArrayList<>();
        if (pipelines != null) {
            for (RenderPipeline pipeline : pipelines.all()) {
                CompletableFuture<?> compile = KGPipelines.evict(pipeline);
                if (compile != null) compiling.add(compile);
            }
        }
        if (compiling.isEmpty()) {
            DynamicShaderSourceRegistry.unregister(DynamicShaderSourceRegistry.shaderId(contentHash));
        } else {
            // A background compile still reads them: drop them once it is done (dropUnusedSources).
            SOURCES_IN_USE.put(contentHash, CompletableFuture.allOf(compiling.toArray(CompletableFuture[]::new)));
        }
    }

    /** Drop the sources of evicted graphs whose background compiles are done — unless the graph was built again
     *  meanwhile. Render thread, once a frame. */
    public static void dropUnusedSources() {
        SOURCES_IN_USE.entrySet().removeIf(entry -> {
            if (!entry.getValue().isDone()) return false;
            if (!PIPELINES.containsKey(entry.getKey())) {
                DynamicShaderSourceRegistry.unregister(DynamicShaderSourceRegistry.shaderId(entry.getKey()));
            }
            return true;
        });
    }

    /** The cached pipelines for this compiled graph, building + registering its GLSL on first use. */
    public static GeneratedPipelines getOrBuildPipelines(CompiledShaderGraph compiled) {
        return PIPELINES.computeIfAbsent(compiled.contentHash(), hash -> buildPipelines(compiled));
    }

    private static GeneratedPipelines buildPipelines(CompiledShaderGraph compiled) {
        String hash = compiled.contentHash();
        Identifier shaderId = DynamicShaderSourceRegistry.shaderId(hash);
        DynamicShaderSourceRegistry.register(shaderId, compiled.vertexSource(), compiled.fragmentSource());

        RenderTypeGraph.Settings settings = compiled.settings();
        // Everything but the colour targets and the depth state, which the OIT phases set their own way.
        RenderPipeline.Builder base = RenderPipeline.builder()
                .withVertexShader(shaderId)
                .withFragmentShader(shaderId)
                .withVertexBinding(0, vertexFormat(settings.vertexFormatElements()))
                .withPrimitiveTopology(primitiveTopology(settings.vertexFormatMode()))
                .withCull(settings.cull());
        KGInstanceLayout instances = KGInstanceLayout.of(compiled.instanceAttributes());
        if (instances != null) base.withVertexBinding(1, instances.format());
        for (BindGroupLayout layout : bindGroupLayouts(compiled)) base.withBindGroupLayout(layout);
        RenderPipeline.Snippet snippet = base.buildSnippet();

        Optional<BlendFunction> blend = blendFunction(settings.blend());
        ColorTargetState mainTarget = colorTarget(blend, settings.colorFormat());
        RenderPipeline main = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "pipeline/" + hash))
                .withColorTargetState(mainTarget)
                .withDepthStencilState(depthState(settings))
                .build();
        RenderPipeline colorTargets = null;
        if (!compiled.colorTargets().isEmpty()) {
            RenderPipeline.Builder b = RenderPipeline.builder(snippet)
                    .withLocation(Identifier.fromNamespaceAndPath(Kilagraph.MODID, "pipeline/" + hash + "_color_targets"))
                    .withShaderDefine(ShaderGraphCompiler.COLOR_TARGETS_DEFINE)
                    .withColorTargetState(mainTarget)
                    .withDepthStencilState(depthState(settings));
            for (ColorTarget target : compiled.colorTargets()) {
                b.withColorTargetState(target.location(), colorTarget(blend, target.format()));
            }
            colorTargets = b.build();
        }
        return new GeneratedPipelines(main, colorTargets, oitPipelines(settings, snippet, hash));
    }

    /** How a blend mode is drawn in the OIT phases; {@code null}: it can't be (it depends on what's behind). */
    private record OitBlend(boolean additive, @Nullable String define) {}

    @Nullable
    private static OitBlend oitBlend(RenderTypeGraph.Settings.BlendMode blend) {
        return switch (blend) {
            case TRANSLUCENT, ENTITY_OUTLINE_BLIT -> new OitBlend(false, null);
            case TRANSLUCENT_PREMULTIPLIED_ALPHA -> new OitBlend(false, ShaderGraphCompiler.PREMULTIPLIED_ALPHA_DEFINE);
            // (SRC_ALPHA, ONE) colour — OVERLAY only differs in the alpha channel, which the main target doesn't show.
            case LIGHTNING, OVERLAY -> new OitBlend(true, null);
            case ADDITIVE -> new OitBlend(true, ShaderGraphCompiler.ADDITIVE_ONE_DEFINE);
            case OPAQUE, GLINT, INVERT, MULTIPLY, SUBTRACT, MIN, MAX -> null;
        };
    }

    @Nullable
    private static OitPipelineSet oitPipelines(RenderTypeGraph.Settings settings, RenderPipeline.Snippet snippet, String hash) {
        OitBlend oit = oitBlend(settings.blend());
        if (oit == null) return null;
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet);
        if (oit.additive()) builder.withShaderDefine("OIT_ADDITIVE");
        if (oit.define() != null) builder.withShaderDefine(oit.define());
        OitPipelineSet.Builder set = OitPipelineSet.builder(Identifier.fromNamespaceAndPath(Kilagraph.MODID, hash), builder);
        // The phases test depth like the material does, and none of them writes it.
        if (settings.depthTest() == RenderTypeGraph.Settings.DepthTest.ALWAYS
                || settings.depthTest() == RenderTypeGraph.Settings.DepthTest.NONE) {
            set.withoutDepthTest();
        } else {
            DepthStencilState depth = depthState(settings, false);
            Consumer<RenderPipeline.Builder> modifier = b -> b.withDepthStencilState(depth);
            set.withDepthBoundsModifier(modifier).withTransmittanceModifier(modifier).withAccumulateModifier(modifier);
        }
        return set.build();
    }

    /**
     * The bind group layouts of a pipeline running {@code compiled}'s GLSL: one per uniform it declares (the
     * Minecraft blocks its includes declare, {@code KG_Material}, the KilaGraph-managed blocks, the samplers).
     * One layout each, so one Minecraft also adds (the OIT phases' {@code Globals}/{@code Projection}) is the
     * same layout rather than a duplicate name. A pipeline must declare nothing more: the render pass requires
     * every declared uniform to be bound at draw.
     */
    public static List<BindGroupLayout> bindGroupLayouts(CompiledShaderGraph compiled) {
        Map<String, UniformType> uniforms = new LinkedHashMap<>();
        for (String ubo : compiled.builtinUniforms()) uniforms.put(ubo, UniformType.UNIFORM_BUFFER);
        if (!compiled.layout().isEmpty()) uniforms.put(MaterialUniformLayout.UBO_NAME, UniformType.UNIFORM_BUFFER);
        for (ShaderUniformBlock block : compiled.uniformBlocks()) uniforms.put(block.uboName(), UniformType.UNIFORM_BUFFER);
        for (String sampler : compiled.layout().samplers()) uniforms.put(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
        List<BindGroupLayout> layouts = new ArrayList<>(uniforms.size());
        uniforms.forEach((name, type) -> layouts.add(BindGroupLayout.builder().withUniform(name, type).build()));
        return layouts;
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

    /** Build the GPU sampler for a {@link SamplerDefault}, mapping KilaGraph's enums to renderpearl's. */
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

    private static DepthStencilState depthState(RenderTypeGraph.Settings settings) {
        return depthState(settings, settings.depthWrite());
    }

    /** Minecraft renders reversed-Z, so "nearer" ({@code LEQUAL}/{@code LESS}) is a greater-than compare, and a
     *  positive bias pulls toward the camera. */
    private static DepthStencilState depthState(RenderTypeGraph.Settings settings, boolean depthWrite) {
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
        return new DepthStencilState(op, depthWrite, factor, units);
    }
}
