package com.lowdragmc.kilagraph.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import com.lowdragmc.kilagraph.rendertype.runtime.KGPreparedRenderType;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Uploads a KilaGraph material's UBOs/textures at {@code drawFromBuffer} HEAD (still outside the pass; later
 * than {@code prepare()}, so values set in the geometry callback count) and binds them before the draw call.
 */
@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin implements KGPreparedRenderType {

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

    /** Upload before the pass opens; skip the draw in an Iris shadow pass we have no mapping for (it would
     *  corrupt the shadow map). */
    @Inject(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kilagraph$prepareMaterial(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType,
                                           int baseVertex, int firstIndex, int indexCount, CallbackInfo ci) {
        var material = kilagraph$material;
        if (material == null) return;
        if (IrisCompat.shouldSkipShadowDraw()) {
            ci.cancel();
            return;
        }
        material.prepareUniforms();
    }

    /** Bind after vanilla's own texture loop, so per-instance textures win. */
    @Inject(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V", shift = At.Shift.BEFORE))
    private void kilagraph$bindMaterialUniforms(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType,
                                                int baseVertex, int firstIndex, int indexCount, CallbackInfo ci,
                                                @Local RenderPass renderPass) {
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

    /** Open the pass with the material's extra colour targets (unused attachments where none is bound). */
    @WrapOperation(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"))
    private RenderPass kilagraph$colorTargets(CommandEncoder encoder, Supplier<String> label, GpuTextureView color,
                                              Optional<Vector4fc> clearColor, @Nullable GpuTextureView depth,
                                              OptionalDouble clearDepth, Operation<RenderPass> original) {
        var material = kilagraph$material;
        if (material == null || material.colorTargets().isEmpty()) {
            return original.call(encoder, label, color, clearColor, depth, clearDepth);
        }
        return encoder.createRenderPass(material.colorTargetPass(label, color, clearColor, depth, clearDepth));
    }

    /** firstInstance stays 0: GL's gl_InstanceID ignores it, Vulkan's gl_InstanceIndex doesn't. */
    @ModifyArg(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V"),
            index = 1)
    private int kilagraph$instanceCount(int instanceCount) {
        return kilagraph$material != null ? kilagraph$instanceCount : instanceCount;
    }

    /** Clear the discriminator after our draw so the next, unrelated draw sharing this program is not
     *  flagged as ours (the program is still bound here, so the reset reaches it). */
    @Inject(
            method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V", shift = At.Shift.AFTER))
    private void kilagraph$resetSurfaceId(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType,
                                          int baseVertex, int firstIndex, int indexCount, CallbackInfo ci) {
        var material = kilagraph$material;
        if (material != null && material.irisSurfaceId() != 0 && IrisCompat.isShaderPackInUse()) {
            IrisSurfaceUniform.clearBoundProgram();
        }
    }
}
