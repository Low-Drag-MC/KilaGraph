package com.lowdragmc.kilagraph.mixin.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.kilagraph.rendertype.iris.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Skips the render pass's strict draw validation while a shaderpack is in use, which Iris's own draws fail (samplers
 * bound through raw GL, entity overrides with one vertex format where the check walks 16 slots). It only runs in a
 * development environment ({@code FrontendGpuDevice.STRICT_VALIDATION}), the only one {@link IrisMixinPlugin} applies
 * this in.
 */
@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendRenderPass")
public class MixinFrontendRenderPassValidation {

    @ModifyExpressionValue(method = "validateDraw", at = @At(value = "FIELD",
            target = "Lcom/mojang/renderpearl/frontend/FrontendGpuDevice;STRICT_VALIDATION:Z"))
    private boolean kilagraph$notUnderShaderpack(boolean strict) {
        return strict && !IrisCompat.isShaderPackInUse();
    }
}
