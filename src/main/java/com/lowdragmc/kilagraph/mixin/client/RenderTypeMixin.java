package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.KGPreparedRenderType;
import com.lowdragmc.kilagraph.rendertype.runtime.KGRenderType;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries a KilaGraph render type's material, stamps it onto the {@link PreparedRenderType} its {@code prepare()}
 *  returns, and uploads the material's values for that draw: preparing is where 26.3 puts everything a draw needs
 *  before its pass opens. */
@Mixin(RenderType.class)
public class RenderTypeMixin implements KGRenderType {

    @Unique
    private @Nullable RenderTypeGraphMaterial kilagraph$material;

    @Override
    public @Nullable RenderTypeGraphMaterial kilagraph$material() {
        return this.kilagraph$material;
    }

    @Override
    public void kilagraph$setMaterial(RenderTypeGraphMaterial material) {
        this.kilagraph$material = material;
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void kilagraph$tagMaterial(CallbackInfoReturnable<PreparedRenderType> cir) {
        var material = kilagraph$material;
        if (material == null) return;
        var prepared = (KGPreparedRenderType) (Object) cir.getReturnValue();
        prepared.kilagraph$setMaterial(material);
        material.onPrepared(prepared);
    }
}
