package com.lowdragmc.kilagraph.mixin;

import com.lowdragmc.kilagraph.rendertype.runtime.KGSamplerBinder;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds/unbinds KilaGraph's per-sampler GL sampler objects (filter/wrap) around a shader draw, riding vanilla's
 * guaranteed {@code apply() -> draw -> clear()} lifecycle so the sampler-state override is always balanced and
 * never leaks onto a later (vanilla) draw. The work lives in {@link KGSamplerBinder}; a non-KG shader has
 * nothing staged, so both hooks are cheap no-ops for it.
 */
@Mixin(ShaderInstance.class)
public class ShaderInstanceMixin {

    @Inject(method = "apply", at = @At("TAIL"))
    private void kilagraph$bindSamplers(CallbackInfo ci) {
        KGSamplerBinder.onApply((ShaderInstance) (Object) this);
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void kilagraph$unbindSamplers(CallbackInfo ci) {
        KGSamplerBinder.onClear();
    }
}
