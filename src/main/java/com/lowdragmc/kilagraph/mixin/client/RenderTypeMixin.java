package com.lowdragmc.kilagraph.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds a KilaGraph material's custom {@code KG_Material} uniform block during {@code RenderType.draw}.
 * Vanilla only binds default uniforms + {@code DynamicTransforms} + {@code RenderSetup} textures, so
 * the per-material UBO must be bound here. Injected right after
 * {@code RenderSystem.bindDefaultUniforms}, capturing the active {@link RenderPass} local; a no-op for
 * non-KilaGraph render types.
 */
@Mixin(RenderType.class)
public class RenderTypeMixin {

    /**
     * Upload the material's custom UBO before the render pass opens — {@code writeToBuffer} is illegal
     * inside an active pass (vanilla likewise writes {@code DynamicTransforms} before {@code createRenderPass}).
     */
    @Inject(method = "draw", at = @At("HEAD"))
    private void kilagraph$prepareMaterialUniforms(MeshData mesh, CallbackInfo ci) {
        var material = RenderTypeGraphMaterial.of((RenderType) (Object) this);
        if (material != null) {
            material.prepareUniforms();
        }
    }

    /**
     * Bind our custom UBO + dynamic textures right before {@code drawIndexed} — i.e. <em>after</em> the
     * {@code RenderSetup} texture loop, which runs late in {@code draw} (after {@code bindDefaultUniforms}).
     * Binding earlier would let RenderSetup's baked defaults overwrite our per-instance {@code setTexture}.
     */
    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void kilagraph$bindMaterialUniforms(MeshData mesh, CallbackInfo ci, @Local(name = "renderPass") RenderPass renderPass) {
        var material = RenderTypeGraphMaterial.of((RenderType) (Object) this);
        if (material != null) {
            material.bindCustomUniforms(renderPass);
            // Iris: record the active material so the trySetup hook can flag this draw (kg_surface_id) AND
            // bind the material's KG_Material/managed UBOs onto the shaderpack's shared gbuffers program once
            // it's actually bound (the program isn't bound at this hook). Reset after. id 0 = passthrough
            // (graph not injection-compatible) — left unflagged so it shows the pack's own albedo.
            if (material.irisSurfaceId() != 0 && IrisCompat.isShaderPackInUse()) {
                IrisSurfaceUniform.setCurrent(material);
            }
        }
    }

    /** Clear the discriminator after our draw so the next, unrelated draw sharing this program is not
     *  flagged as ours (the program is still bound here, so the reset reaches it). */
    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V",
                    shift = At.Shift.AFTER
            )
    )
    private void kilagraph$resetSurfaceId(MeshData mesh, CallbackInfo ci) {
        var material = RenderTypeGraphMaterial.of((RenderType) (Object) this);
        if (material != null && material.irisSurfaceId() != 0 && IrisCompat.isShaderPackInUse()) {
            IrisSurfaceUniform.clearBoundProgram();
        }
    }
}
