package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.Kilagraph;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import com.lowdragmc.kilagraph.rendertype.runtime.KGPipelines;
import com.lowdragmc.kilagraph.rendertype.runtime.KGPreparedRenderType;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Binds a KilaGraph material's UBOs, textures and instances in the draw every render type funnels through —
 * {@code drawFromBuffer} and each OIT phase's {@code drawFromBufferOit} — inside the caller's render pass. The
 * material was uploaded when the render type was prepared (see {@code RenderTypeMixin}).
 */
@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin implements KGPreparedRenderType {

    /** Pipelines already reported as not compiling, so each is logged once. */
    @Unique
    private static final Set<RenderPipeline> kilagraph$REPORTED = Collections.newSetFromMap(new WeakHashMap<>());

    @Unique
    private @Nullable RenderTypeGraphMaterial kilagraph$material;
    @Unique
    private @Nullable GpuBufferSlice kilagraph$instances;
    @Unique
    private int kilagraph$instanceCount = 1;

    @Override
    public @Nullable RenderTypeGraphMaterial kilagraph$material() {
        return this.kilagraph$material;
    }

    @Override
    public void kilagraph$setMaterial(RenderTypeGraphMaterial material) {
        this.kilagraph$material = material;
    }

    @Override
    public void kilagraph$setInstances(@Nullable GpuBufferSlice instances, int count) {
        this.kilagraph$instances = instances;
        this.kilagraph$instanceCount = count;
    }

    /** Skip a draw whose pipeline doesn't compile — Minecraft would fail the frame. The main pipeline compiled when
     *  the material was built; an OIT phase's compiles at its first draw, a pipeline after a shader reload at its
     *  next. Also skips an Iris shadow pass we have no mapping for (it would corrupt the shadow map). */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void kilagraph$skipBroken(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline pipeline,
                                      CallbackInfo ci) {
        var material = kilagraph$material;
        if (material == null) return;
        if (IrisCompat.shouldSkipShadowDraw()) {
            ci.cancel();
            return;
        }
        if (!KGPipelines.ensureCompiled(pipeline)) {
            if (kilagraph$REPORTED.add(pipeline)) {
                Kilagraph.LOGGER.warn("[KilaGraph] pipeline {} of {} doesn't compile; its draws are skipped",
                        pipeline.getLocation(), material.contentHash());
            }
            ci.cancel();
        }
    }

    /** Bind after vanilla's own texture loop, so per-instance textures win. */
    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
    private void kilagraph$bindMaterialUniforms(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass,
                                                RenderPipeline pipeline, CallbackInfo ci) {
        var material = kilagraph$material;
        if (material == null) return;
        material.bindCustomUniforms(renderPass);
        if (material.instanceLayout() != null) {
            renderPass.setVertexBuffer(1, kilagraph$instances != null ? kilagraph$instances : material.defaultInstanceSlice());
        }
        // Iris: record the active material so the trySetup hook can flag this draw (kg_surface_id) AND bind
        // the material's KG_Material/managed UBOs onto the shaderpack's shared gbuffers program once it is
        // actually bound (the program isn't bound at this hook). Reset after. id 0 = passthrough (graph not
        // injection-compatible) — left unflagged so it shows the pack's own albedo.
        if (material.irisSurfaceId() != 0 && IrisCompat.isShaderPackInUse()) {
            IrisSurfaceUniform.setCurrent(material);
        }
    }

    /** firstInstance stays 0, so gl_InstanceIndex is the instance's index on both backends. */
    @ModifyArg(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"),
            index = 1)
    private int kilagraph$instanceCount(int instanceCount) {
        return kilagraph$material != null ? kilagraph$instanceCount : instanceCount;
    }

    /** Clear the discriminator after our draw so the next, unrelated draw sharing this program is not
     *  flagged as ours (the program is still bound here, so the reset reaches it). */
    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V", shift = At.Shift.AFTER))
    private void kilagraph$resetSurfaceId(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass,
                                          RenderPipeline pipeline, CallbackInfo ci) {
        var material = kilagraph$material;
        if (material != null && material.irisSurfaceId() != 0 && IrisCompat.isShaderPackInUse()) {
            IrisSurfaceUniform.clearBoundProgram();
        }
    }
}
