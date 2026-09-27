package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ColorTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.MaterialUniformLayout;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.*;
import java.util.function.Supplier;

/**
 * A compiled, ready-to-render material: a Minecraft {@link RenderType} (sharing cached pipelines)
 * plus its per-instance {@code KG_Material} uniform buffer and dynamic sampler bindings. Renderers get
 * {@link #renderType()} for {@code submitCustomGeometry}. Values are uploaded when the render type is prepared
 * and again once the frame's geometry is built ({@link #flushPrepared}), all before the render passes open;
 * {@code PreparedRenderTypeMixin} calls {@link #bindCustomUniforms} inside the pass to bind both the custom UBO
 * and the per-instance textures that vanilla's {@code RenderSetup} path doesn't update.
 *
 * <p><b>Setting values.</b> EXPOSED graph variables are addressed by their <em>display name</em> (not
 * the mangled {@code kg_*} GLSL identifier): {@link #setUniform(String, float)} and its typed overloads
 * resolve the variable name to its UBO field and pack std140 for you; {@link #setColorUniform} takes an
 * ARGB int. Sampler2D variables are updated with {@link #setTexture} — dynamically, without rebuilding
 * the RenderType (the binding is re-applied each draw in {@link #bindCustomUniforms}). The low-level
 * {@link #setUniformField} (by GLSL field name) remains for baking compiled defaults.</p>
 *
 * <p>Overlay ({@code Sampler1}) / lightmap ({@code Sampler2}) stay owned by vanilla and are not part of
 * this material's dynamic texture map.</p>
 *
 * <p>Instances register themselves in a static identity side-table keyed by {@link RenderType} so the
 * prepare mixin can find the owning material without adding fields to the vanilla class.</p>
 */
public final class RenderTypeGraphMaterial implements AutoCloseable {

    private static final Map<RenderType, RenderTypeGraphMaterial> BY_RENDER_TYPE =
            Collections.synchronizedMap(new IdentityHashMap<>());
    /** Materials prepared since the last {@link #flushPrepared}. Render thread. */
    private static final Set<RenderTypeGraphMaterial> PREPARED = Collections.newSetFromMap(new IdentityHashMap<>());

    private final RenderType renderType;
    private final MaterialUniformBuffer uniforms;
    /** KilaGraph-managed UBOs this material's graph uses — uploaded each draw (HEAD) + bound in the pass. */
    private final java.util.List<ShaderUniformBlock> uniformBlocks;
    /** UBOs only the Iris-injection snippet references (fragment reconstructions pull blocks the vanilla
     *  path never needs, e.g. Fresnel's viewDir needs KG_Globals.ScreenSize only under injection). Uploaded
     *  alongside {@link #uniformBlocks} and bound onto the injected program — but NOT bound on the vanilla
     *  render pass (the vanilla pipeline doesn't declare them). */
    private final java.util.List<ShaderUniformBlock> injectionOnlyBlocks;
    private final String contentHash;
    /** Variable display name -> KG_Material field (name + type), for set-by-name uniform updates. */
    private final Map<String, MaterialUniformLayout.Field> uniformFields;
    /** Variable display name -> sampler uniform name, so setTexture accepts the friendly name too. */
    private final Map<String, String> variableSamplers;
    /** Sampler uniform name -> bound texture + params. (Re)bound each draw so setTexture is dynamic. */
    private final Map<String, SamplerDefault> textures;
    /** Samplers only the Iris-injection snippet bakes (e.g. the {@code kg_NeutralWhite} degrade) — resolved
     *  alongside {@link #textures} and offered to the Iris binder, but never bound on the vanilla pass. */
    private final Map<String, SamplerDefault> injectionOnlyTextures;
    /** Sampler uniform name -> resolved view+sampler, refreshed in {@link #prepareUniforms} (pre-pass). */
    private final Map<String, ResolvedSampler> resolvedTextures = new HashMap<>();
    /** Resolved injection-only samplers (see {@link #injectionOnlyTextures}). */
    private final Map<String, ResolvedSampler> resolvedInjectionTextures = new HashMap<>();
    /** Sampler uniform name -> externally-supplied raw view+sampler (e.g. another mod's live {@code GpuTexture}),
     *  bound in preference to the Identifier/TextureManager binding. Lets a host feed a texture that has no
     *  registered {@link Identifier} (like SlideShow's decoded slide) straight into a graph sampler. */
    private final Map<String, ResolvedSampler> externalViews = new HashMap<>();
    /** Whether the graph samples the captured scene colour/depth — bound from {@link SceneCaptureManager}. */
    private final boolean usesSceneColor;
    private final boolean usesSceneDepth;
    /** Non-zero discriminator written to the shaderpack program's injected {@code kg_surface_id} uniform while
     *  this material draws (see {@code IrisShaderInjector}). 0 = no Iris injection. Set by the factory when a
     *  shaderpack-compatible variant is active. */
    private int irisSurfaceId = 0;
    /** Blended, but in a way order-independent transparency can't express (see {@link RenderTypeFactory}). */
    private final boolean solidUnderImprovedTransparency;
    private boolean closed;

    private record ResolvedSampler(GpuTextureView view, GpuSampler sampler) {}

    public RenderTypeGraphMaterial(RenderType renderType, MaterialUniformLayout layout,
                                   java.util.List<ShaderUniformBlock> uniformBlocks,
                                   java.util.List<ShaderUniformBlock> injectionOnlyBlocks, String contentHash,
                                   Map<String, MaterialUniformLayout.Field> uniformFields,
                                   Map<String, String> variableSamplers,
                                   Map<String, SamplerDefault> textures,
                                   Map<String, SamplerDefault> injectionOnlyTextures,
                                   boolean usesSceneColor, boolean usesSceneDepth,
                                   boolean solidUnderImprovedTransparency) {
        this.renderType = renderType;
        this.uniforms = new MaterialUniformBuffer(layout);
        this.uniformBlocks = List.copyOf(uniformBlocks);
        this.injectionOnlyBlocks = List.copyOf(injectionOnlyBlocks);
        this.contentHash = contentHash;
        this.uniformFields = new HashMap<>(uniformFields);
        this.variableSamplers = new HashMap<>(variableSamplers);
        this.textures = new HashMap<>(textures);
        this.injectionOnlyTextures = new HashMap<>(injectionOnlyTextures);
        this.usesSceneColor = usesSceneColor;
        this.usesSceneDepth = usesSceneDepth;
        this.solidUnderImprovedTransparency = solidUnderImprovedTransparency;
        BY_RENDER_TYPE.put(renderType, this);
    }

    /** Lookup the material owning a render type, or {@code null} if it isn't a KilaGraph material. */
    @Nullable
    public static RenderTypeGraphMaterial of(RenderType renderType) {
        return BY_RENDER_TYPE.get(renderType);
    }

    public RenderType renderType() {
        return renderType;
    }

    /**
     * Whether, with improved transparency on, the material's custom geometry draws in the solid phase: it blends,
     * but in a way order-independent transparency can't express (multiply, min, invert… depend on what is behind),
     * so it blends over the opaque scene instead, unsorted against other translucent geometry.
     */
    public boolean drawsSolidUnderImprovedTransparency() {
        return solidUnderImprovedTransparency;
    }

    /** Called when one of this material's render types is prepared: uploads now, and again at {@link #flushPrepared}. */
    public void onPrepared() {
        prepareUniforms();
        PREPARED.add(this);
    }

    /**
     * Upload every material prepared since the last call. Minecraft prepares a render type before it runs the
     * geometry callback that fills it, so values set there are uploaded here — when the frame's geometry is built
     * and before its passes open. Render thread.
     */
    public static void flushPrepared() {
        for (RenderTypeGraphMaterial material : PREPARED) material.prepareUniforms();
        PREPARED.clear();
    }

    // ---- instancing --------------------------------------------------------------------------

    @Nullable private KGInstanceLayout instanceLayout;
    /** One default instance, bound when an instanced graph is drawn the ordinary way (preview, submits). */
    @Nullable private KGInstanceBuffer defaultInstance;

    void setInstanceLayout(@Nullable KGInstanceLayout layout) {
        this.instanceLayout = layout;
    }

    /** The graph's per-instance layout, or {@code null} when it reads no Instance Data. */
    @Nullable
    public KGInstanceLayout instanceLayout() {
        return instanceLayout;
    }

    /** A buffer of per-instance values for {@link #prepareInstanced}. The caller owns (closes) it. */
    public KGInstanceBuffer createInstanceBuffer(int capacity) {
        if (instanceLayout == null) throw new IllegalStateException("the graph reads no instance data");
        return new KGInstanceBuffer(instanceLayout, capacity);
    }

    /**
     * Prepare a draw of {@code count} instances of {@code mesh}, transformed by {@code pose} on top of the current
     * model-view, to {@link InstancedDraw#execute} in a render pass later in the frame. Call on the render thread
     * before that pass opens: the values, instances and transforms are uploaded here, since a pass allows no
     * uploads. {@code instances} is required when the graph reads Instance Data, and must hold {@code count}
     * values. Returns {@code null} when there is nothing to draw, or the pipeline doesn't compile (logged).
     */
    @Nullable
    public InstancedDraw prepareInstanced(KGMesh mesh, @Nullable KGInstanceBuffer instances, int count, Matrix4fc pose) {
        RenderSystem.assertOnRenderThread();
        if (KGUploadBuffer.inRenderPass()) {
            throw new IllegalStateException("prepare an instanced draw before its render pass opens");
        }
        if (count <= 0) return null;
        GpuBufferSlice instanceSlice = null;
        if (instanceLayout != null) {
            if (instances == null || !instances.layout().attributes().equals(instanceLayout.attributes())) {
                throw new IllegalArgumentException("an instance buffer created for this graph is required");
            }
            if (instances.capacity() < count) {
                throw new IllegalArgumentException(count + " instances drawn from a buffer of " + instances.capacity());
            }
            instances.upload();
            instanceSlice = instances.slice();
        }
        mesh.prepareIndices();
        PreparedRenderType main;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.mul(pose);
            main = renderType.prepare(); // uploads this material (RenderTypeMixin)
        } finally {
            modelView.popMatrix();
        }
        ((KGPreparedRenderType) (Object) main).kilagraph$setInstances(instanceSlice, count);
        PreparedRenderType withTargets = null;
        if (colorTargetsPipeline != null) {
            if (KGPipelines.ensureCompiled(colorTargetsPipeline)) {
                withTargets = new PreparedRenderType(main.name(), colorTargetsPipeline, null,
                        main.dynamicTransforms(), main.scissorState(), main.textures());
                var tagged = (KGPreparedRenderType) (Object) withTargets;
                tagged.kilagraph$setMaterial(this);
                tagged.kilagraph$setInstances(instanceSlice, count);
            } else if (!colorTargetsFailedWarned) {
                colorTargetsFailedWarned = true;
                Kilagraph.LOGGER.warn("[KilaGraph] the colour-target pipeline of {} doesn't compile; "
                        + "its draws write only the main target", contentHash);
            }
        }
        return new InstancedDraw(mesh, main, withTargets);
    }

    /**
     * Draw {@code count} instances of {@code mesh} into the main render target now (see {@link #prepareInstanced}),
     * writing the bound colour targets too when the graph has some. Render thread, outside a render pass.
     */
    public void drawInstanced(KGMesh mesh, @Nullable KGInstanceBuffer instances, int count, Matrix4fc pose) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        drawInstanced(mesh, instances, count, pose, target.getColorTextureView(), target.getDepthTextureView());
    }

    /**
     * Draw {@code count} instances of {@code mesh} into {@code color} (+ {@code depth}) now, in a pass of its own
     * that also holds the bound colour targets when the graph has some. {@code color} must have the graph's colour
     * format. Render thread, outside a render pass.
     */
    public void drawInstanced(KGMesh mesh, @Nullable KGInstanceBuffer instances, int count, Matrix4fc pose,
                              GpuTextureView color, @Nullable GpuTextureView depth) {
        InstancedDraw draw = prepareInstanced(mesh, instances, count, pose);
        if (draw == null) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (draw.hasColorTargets()) {
            try (RenderPass pass = encoder.createRenderPass(colorTargetPass(() -> "KilaGraph instanced " + contentHash,
                    color, Optional.empty(), depth, OptionalDouble.empty()))) {
                draw.executeWithColorTargets(pass);
            }
        } else {
            try (RenderPass pass = encoder.createRenderPass(() -> "KilaGraph instanced " + contentHash,
                    color, Optional.empty(), depth, OptionalDouble.empty())) {
                draw.execute(pass);
            }
        }
    }

    /** An instanced draw uploaded by {@link #prepareInstanced}, for a render pass later in the same frame. */
    public static final class InstancedDraw {
        private final KGMesh mesh;
        private final PreparedRenderType main;
        @Nullable private final PreparedRenderType withColorTargets;

        private InstancedDraw(KGMesh mesh, PreparedRenderType main, @Nullable PreparedRenderType withColorTargets) {
            this.mesh = mesh;
            this.main = main;
            this.withColorTargets = withColorTargets;
        }

        /** Whether {@link #executeWithColorTargets} can draw: the graph writes colour targets and their pipeline compiled. */
        public boolean hasColorTargets() {
            return withColorTargets != null;
        }

        /** Draw into {@code pass}, whose single colour attachment is the main target. */
        public void execute(RenderPass pass) {
            main.drawFromBuffer(mesh.executeInfo(), pass);
        }

        /** Draw into a pass from {@link RenderTypeGraphMaterial#colorTargetPass}, writing the colour targets too. */
        public void executeWithColorTargets(RenderPass pass) {
            if (withColorTargets == null) throw new IllegalStateException("the draw has no colour-target pipeline");
            withColorTargets.drawFromBuffer(mesh.executeInfo(), pass);
        }
    }

    /** The default instance's buffer (uploaded by {@link #prepareUniforms}); {@code null} without instance data. */
    @Nullable
    public GpuBufferSlice defaultInstanceSlice() {
        return defaultInstance == null ? null : defaultInstance.slice();
    }

    // ---- extra colour targets (MRT) -----------------------------------------------------------

    private List<ColorTarget> colorTargets = List.of();
    /** The pipeline writing the colour targets too; {@code null} when the graph writes none. */
    @Nullable private RenderPipeline colorTargetsPipeline;
    private boolean colorTargetsFailedWarned;
    private final @Nullable GpuTextureView[] colorTargetViews = new GpuTextureView[ColorTarget.MAX_LOCATION + 1];
    private boolean colorTargetSizeWarned;

    void setColorTargets(List<ColorTarget> targets, @Nullable RenderPipeline pipeline) {
        this.colorTargets = List.copyOf(targets);
        this.colorTargetsPipeline = pipeline;
    }

    /** The graph's extra colour targets (Color Target blocks), sorted by location. They are written only by a draw
     *  into a pass that has them ({@link #colorTargetPass}): a Minecraft pass has the main target alone. */
    public List<ColorTarget> colorTargets() {
        return colorTargets;
    }

    /** The pipeline that writes the colour targets as well as the main one, for a pass from {@link #colorTargetPass};
     *  {@code null} when the graph writes no colour targets. */
    @Nullable
    public RenderPipeline colorTargetsPipeline() {
        return colorTargetsPipeline;
    }

    /**
     * Bind {@code view} as the colour target at {@code location} (1..7) for every draw of this material into a
     * {@link #colorTargetPass}; null discards that output. Its format must match the Color Target block's; its size
     * must match the main target at draw time, else it is skipped for that draw.
     */
    public void setColorTarget(int location, @Nullable GpuTextureView view) {
        ColorTarget target = colorTargets.stream().filter(t -> t.location() == location).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("the graph writes no colour target " + location));
        if (view != null && view.texture().getFormat() != GpuFormat.valueOf(target.format().gpuFormat)) {
            throw new IllegalArgumentException("colour target " + location + " is " + target.format()
                    + ", the texture is " + view.texture().getFormat());
        }
        colorTargetViews[location] = view;
    }

    /** The render pass for a draw into {@code color}: the bound colour targets, unused attachments elsewhere. */
    public RenderPassDescriptor colorTargetPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clearColor,
                                                @Nullable GpuTextureView depth, OptionalDouble clearDepth) {
        int width = color.getWidth(0), height = color.getHeight(0);
        RenderPassDescriptor.Builder pass = RenderPassDescriptor.builder(label).withColorAttachment(color, clearColor);
        int count = colorTargets.isEmpty() ? 1 : colorTargets.getLast().location() + 1;
        for (int location = 1; location < count; location++) {
            GpuTextureView view = colorTargetViews[location];
            if (view != null && (view.isClosed() || view.getWidth(0) != width || view.getHeight(0) != height)) {
                if (!colorTargetSizeWarned) {
                    colorTargetSizeWarned = true;
                    Kilagraph.LOGGER.warn("[KilaGraph] colour target {} of {} is closed or not {}x{}; skipped",
                            location, contentHash, width, height);
                }
                view = null;
            }
            if (view != null) pass.withColorAttachment(view);
            else pass.withUnusedColorAttachment();
        }
        if (depth != null) pass.withDepthAttachment(depth, clearDepth);
        return pass.withRenderArea(new RenderPass.RenderArea(0, 0, width, height)).build();
    }

    public String contentHash() {
        return contentHash;
    }

    /** The {@code kg_surface_id} written to the active shaderpack program while this material draws; 0 = none. */
    public int irisSurfaceId() {
        return irisSurfaceId;
    }

    public void setIrisSurfaceId(int id) {
        this.irisSurfaceId = id;
    }

    /** Whether the graph samples the captured scene colour ({@code KG_SceneColor}) — under a shaderpack the
     *  injected program's sampler is bound from {@code SceneCaptureManager} by {@code IrisSurfaceUniform}. */
    public boolean usesSceneColor() {
        return usesSceneColor;
    }

    /** The uploaded {@code KG_Material} buffer slice, for binding onto an Iris shaderpack program at draw
     *  (see {@code IrisSurfaceUniform}); null when this material has no uniform fields or before upload. */
    @Nullable
    public GpuBufferSlice irisMaterialSlice() {
        return uniforms.slice();
    }

    /** The KilaGraph-managed UBOs ({@code KG_Globals}/{@code KG_Transforms}/...) to bind onto an Iris
     *  shaderpack program at draw — the vanilla list plus the injection-only reconstruction blocks. */
    public List<ShaderUniformBlock> irisUniformBlocks() {
        if (injectionOnlyBlocks.isEmpty()) return uniformBlocks;
        var all = new ArrayList<ShaderUniformBlock>(uniformBlocks.size() + injectionOnlyBlocks.size());
        all.addAll(uniformBlocks);
        all.addAll(injectionOnlyBlocks);
        return all;
    }

    /** Receives each of this material's dynamic samplers (uniform name + resolved view/params) for binding
     *  onto an Iris shaderpack program at draw. */
    public interface IrisSamplerBinder {
        void bind(String samplerName, GpuTextureView view, GpuSampler sampler);
    }

    /** Feed each resolved dynamic sampler (with any external-view override applied) to {@code binder}, so
     *  {@code IrisSurfaceUniform} can bind them onto the shaderpack program. Call after {@link #prepareUniforms}
     *  (which fills {@link #resolvedTextures}). Excludes overlay/lightmap/scene (not in the dynamic map);
     *  includes the injection-only samplers (e.g. {@code kg_NeutralWhite}). */
    public void bindIrisSamplers(IrisSamplerBinder binder) {
        for (Map.Entry<String, ResolvedSampler> e : resolvedTextures.entrySet()) {
            ResolvedSampler r = externalViews.getOrDefault(e.getKey(), e.getValue());
            binder.bind(e.getKey(), r.view(), r.sampler());
        }
        for (Map.Entry<String, ResolvedSampler> e : resolvedInjectionTextures.entrySet()) {
            binder.bind(e.getKey(), e.getValue().view(), e.getValue().sampler());
        }
    }

    /**
     * Re-bake the per-instance baked defaults (EXPOSED uniform values + sampler textures/params) from a
     * freshly compiled graph with the <b>same content hash</b>. A value-only edit (a Color/uniform
     * default, a Sampler2D texture or its filter/address/mipmap) changes these but not the GLSL, so the
     * pipeline is reused (no rebuild) — but the material must still refresh, or the edit wouldn't reach
     * the GPU until an unrelated structural change forced a rebuild. Cheap (no RenderType/pipeline work).
     */
    public void refreshDefaults(CompiledShaderGraph compiled) {
        for (Map.Entry<String, float[]> e : compiled.uniformDefaults().entrySet()) {
            setUniformField(e.getKey(), e.getValue());
        }
        textures.clear();
        textures.putAll(RenderTypeFactory.buildMaterialTextures(compiled));
        // Keep the injection-only samplers in step too (same lifecycle as textures).
        injectionOnlyTextures.clear();
        if (compiled.injectionSnippet() != null) {
            for (Map.Entry<String, SamplerDefault> e : compiled.injectionSnippet().samplerDefaults().entrySet()) {
                if (!textures.containsKey(e.getKey())) injectionOnlyTextures.put(e.getKey(), e.getValue());
            }
        }
    }

    // ---- uniform value setters ---------------------------------------------------------------

    /** Set a material uniform field value directly by its GLSL field name (1-4 std140 components). */
    public void setUniformField(String fieldName, float... components) {
        uniforms.set(fieldName, components);
    }

    /** Set an EXPOSED variable's value by its display name. Returns false if no such uniform variable. */
    public boolean setUniform(String variableName, float value) {
        return setByVariable(variableName, value);
    }

    public boolean setUniform(String variableName, Vector2fc v) {
        return setByVariable(variableName, v.x(), v.y());
    }

    public boolean setUniform(String variableName, Vector3fc v) {
        return setByVariable(variableName, v.x(), v.y(), v.z());
    }

    public boolean setUniform(String variableName, Vector4fc v) {
        return setByVariable(variableName, v.x(), v.y(), v.z(), v.w());
    }

    /** Set a vec4 (e.g. a Color variable) from an ARGB int — unpacked to rgba in 0..1. */
    public boolean setColorUniform(String variableName, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return setByVariable(variableName, r, g, b, a);
    }

    /**
     * Set a vec4 (an HDR Color variable) from an {@link HDRColor} — the intensity is premultiplied into
     * rgb, so components may exceed 1. See {@link HDRColor#toVector4f()} for the convention.
     */
    public boolean setHDRColorUniform(String variableName, HDRColor color) {
        var v = color.toVector4f();
        return setByVariable(variableName, v.x, v.y, v.z, v.w);
    }

    public boolean setUniform(String variableName, Matrix4fc m) {
        float[] arr = new float[16];
        m.get(arr); // column-major, matching std140 mat4 packing
        return setByVariable(variableName, arr);
    }

    /** Set an EXPOSED Gradient variable from a {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue}
     *  — std140-packed (header + 8 colour + 8 alpha vec4) into its {@code KG_Gradient} UBO field. */
    public boolean setGradient(String variableName, com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue value) {
        return setByVariable(variableName,
                com.lowdragmc.kilagraph.rendertype.compiler.GradientGlsl.pack(value));
    }

    /** Set an EXPOSED Curve variable from a {@link com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue}
     *  — std140-packed (header + 16 segment vec4) into its {@code KG_Curve} UBO field. */
    public boolean setCurve(String variableName, com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue value) {
        return setByVariable(variableName,
                com.lowdragmc.kilagraph.rendertype.compiler.CurveGlsl.pack(value));
    }

    private boolean setByVariable(String variableName, float... components) {
        MaterialUniformLayout.Field field = uniformFields.get(variableName);
        if (field == null) return false;
        uniforms.set(field.name(), components);
        return true;
    }

    // ---- texture setters ---------------------------------------------------------------------

    /**
     * Bind a Sampler2D variable's <b>whole value</b> — the texture <i>and</i> the filter/address/mipmap
     * the value carries — by display name (or raw sampler uniform name).
     *
     * <p>This is what an inspector that edits a {@link RenderTypeGraphTypes.Sampler2DValue} must call.
     * {@link #setTexture} deliberately keeps the params the graph baked, so routing an edited value
     * through it silently drops the wrap/filter the user picked.</p>
     *
     * <p>Returns false (changing nothing) when the value has no parseable texture location, or when
     * this material does not manage that sampler — the same contract as {@link #setTexture}. The
     * membership check is not decoration here: {@link #apply} binds every entry of the dynamic texture
     * map, so inventing a key would have it try to bind a sampler the program does not declare.</p>
     */
    public boolean setSampler(String name, RenderTypeGraphTypes.Sampler2DValue value) {
        String sampler = variableSamplers.getOrDefault(name, name);
        if (!textures.containsKey(sampler)) return false;
        SamplerDefault def = SamplerDefault.of(value);
        if (def == null) return false;
        textures.put(sampler, def);
        return true;
    }

    /**
     * Bind a texture to a sampler dynamically (no RenderType rebuild), keeping the sampler params the
     * graph baked. {@code name} may be a Sampler2D variable's display name or the raw sampler uniform
     * name. Returns false if this material doesn't manage that sampler (e.g. overlay/lightmap, owned by
     * vanilla). Takes effect on the next draw. To apply a value's params too, use {@link #setSampler}.
     */
    public boolean setTexture(String name, Identifier texture) {
        String sampler = variableSamplers.getOrDefault(name, name);
        SamplerDefault current = textures.get(sampler);
        if (current == null || texture == null) return false;
        // Swap the texture, keep the sampler params (filter/address/mipmap) the graph configured.
        textures.put(sampler, new SamplerDefault(texture, current.filter(), current.address(), current.mipmap()));
        return true;
    }

    /**
     * Bind a raw {@link GpuTextureView} (not a registered {@link Identifier}) to a sampler dynamically,
     * taking precedence over {@link #setTexture}'s TextureManager binding. {@code name} may be a Sampler2D
     * variable's display name or the raw sampler uniform name. Pass a {@code null} view/sampler to clear the
     * override (falling back to the Identifier binding). Returns false if this material doesn't manage that
     * sampler (e.g. overlay/lightmap/scene, not in the dynamic texture map). Takes effect on the next draw.
     */
    public boolean setTextureView(String name, @Nullable GpuTextureView view, @Nullable GpuSampler sampler) {
        String s = variableSamplers.getOrDefault(name, name);
        if (!textures.containsKey(s)) return false;
        if (view == null || sampler == null) {
            externalViews.remove(s);
        } else {
            externalViews.put(s, new ResolvedSampler(view, sampler));
        }
        return true;
    }

    /** The custom sampler uniform names this material manages (excludes overlay/lightmap/scene, which are
     *  bound elsewhere). Lets a host rebind every custom sampler — e.g. feed one external texture into all
     *  of a graph's samplers — without knowing their generated names. */
    public Set<String> managedSamplerNames() {
        return Collections.unmodifiableSet(textures.keySet());
    }

    // ---- draw-time binding -------------------------------------------------------------------

    /**
     * (Re)upload the UBOs if values changed and resolve the dynamic textures, before the render pass opens. Both
     * belong outside the pass: a buffer write and a lazy texture load ({@code getTexture} → {@code registerAndLoad}
     * → {@code writeToTexture}) are illegal inside one (see {@link KGUploadBuffer} for the buffers' fallback).
     * Called when the render type is prepared and at {@link #flushPrepared}; a renderer drawing through
     * {@link #bindCustomUniforms} in its own pass calls it before opening that pass.
     */
    public void prepareUniforms() {
        if (closed) return;
        uniforms.prepareUpload();
        if (instanceLayout != null) {
            if (defaultInstance == null) defaultInstance = new KGInstanceBuffer(instanceLayout, 1);
            defaultInstance.upload();
        }
        for (ShaderUniformBlock block : uniformBlocks) block.prepareUpload();
        // Injection-only blocks must upload too, or the injected program reads a stale/empty buffer.
        for (ShaderUniformBlock block : injectionOnlyBlocks) block.prepareUpload();
        // Resolve (loading if needed) each bound texture now, and build its GpuSampler from the params —
        // the actual bindTexture in the pass then only reads the already-uploaded view/sampler.
        if (!textures.isEmpty()) {
            var textureManager = Minecraft.getInstance().getTextureManager();
            resolvedTextures.clear();
            for (Map.Entry<String, SamplerDefault> e : textures.entrySet()) {
                SamplerDefault def = e.getValue();
                AbstractTexture tex = textureManager.getTexture(def.texture());
                resolvedTextures.put(e.getKey(), new ResolvedSampler(tex.getTextureView(), RenderTypeFactory.gpuSampler(def)));
            }
        }
        if (!injectionOnlyTextures.isEmpty()) {
            var textureManager = Minecraft.getInstance().getTextureManager();
            resolvedInjectionTextures.clear();
            for (Map.Entry<String, SamplerDefault> e : injectionOnlyTextures.entrySet()) {
                SamplerDefault def = e.getValue();
                AbstractTexture tex = textureManager.getTexture(def.texture());
                resolvedInjectionTextures.put(e.getKey(), new ResolvedSampler(tex.getTextureView(), RenderTypeFactory.gpuSampler(def)));
            }
        }
    }

    /** Called from the draw mixin inside the active pass: bind the custom UBO + engine UBO + textures. */
    public void bindCustomUniforms(RenderPass renderPass) {
        if (!uniforms.isEmpty()) {
            GpuBufferSlice slice = uniforms.slice();
            if (slice != null) renderPass.setUniform(MaterialUniformLayout.UBO_NAME, slice);
        }
        for (ShaderUniformBlock block : uniformBlocks) {
            GpuBufferSlice s = block.slice();
            if (s != null) renderPass.setUniform(block.uboName(), s);
        }
        // Dynamic samplers: (re)bind the textures resolved in prepareUniforms, so setTexture takes effect
        // without rebuilding the RenderType. Binding is a pure pass command (no GPU upload).
        for (Map.Entry<String, ResolvedSampler> e : resolvedTextures.entrySet()) {
            // An external raw view (e.g. SlideShow's live slide texture) wins over the Identifier binding.
            ResolvedSampler r = externalViews.getOrDefault(e.getKey(), e.getValue());
            renderPass.setUniform(e.getKey(), r.view(), r.sampler());
        }
        // Captured scene colour/depth: bound from the live SceneCaptureManager (not TextureManager). Before
        // the first capture the view is null — bind the missing-texture as a placeholder so the encoder's
        // "every declared sampler must be bound" check passes.
        if (usesSceneColor) {
            GpuTextureView view = SceneCaptureManager.INSTANCE.colorView();
            renderPass.setUniform(ShaderGraphCompiler.SCENE_COLOR_SAMPLER,
                    view != null ? view : missingView(), SceneCaptureManager.INSTANCE.sampler());
        }
        if (usesSceneDepth) {
            GpuTextureView view = SceneCaptureManager.INSTANCE.depthView();
            renderPass.setUniform(ShaderGraphCompiler.SCENE_DEPTH_SAMPLER,
                    view != null ? view : missingView(), SceneCaptureManager.INSTANCE.sampler());
        }
    }

    /** The MC missing-texture view, a safe placeholder for a scene sampler before the first capture.
     *  Public so {@code IrisSurfaceUniform} binds the same placeholder on injected shaderpack programs. */
    public static GpuTextureView missingView() {
        return Minecraft.getInstance().getTextureManager()
                .getTexture(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())
                .getTextureView();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        BY_RENDER_TYPE.remove(renderType);
        PREPARED.remove(this);
        uniforms.close();
        if (defaultInstance != null) defaultInstance.close();
        if (usesSceneColor || usesSceneDepth) SceneCaptureManager.INSTANCE.release();
        RenderTypeFactory.release(contentHash);
    }
}
