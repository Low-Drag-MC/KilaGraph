package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.KGPreparedRenderType;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 /** Stamps a KilaGraph render type's material onto the {@link PreparedRenderType} its {@code prepare()} returns. */
@Mixin(RenderType.class)
public class RenderTypeMixin {

    @Inject(method = "prepare", at = @At("RETURN"))
    private void kilagraph$tagMaterial(CallbackInfoReturnable<PreparedRenderType> cir) {
        var material = RenderTypeGraphMaterial.of((RenderType) (Object) this);
        if (material == null) return;
        ((KGPreparedRenderType) (Object) cir.getReturnValue()).kilagraph$setMaterial(material);
    }
}
