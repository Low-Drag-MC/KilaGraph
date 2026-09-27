package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes the values of the KilaGraph render types a frame prepared again once its geometry is built: a render type
 * is prepared before the geometry callback that fills it, so this is the point where the values set there are
 * final — and it is still before the frame's render passes open.
 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {

    @Inject(method = "prepareFrameWithContext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;upload()V"))
    private void kilagraph$uploadMaterials(FeatureFrameContext context, SubmitNodeStorage submitNodeStorage,
                                           CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
        RenderTypeGraphMaterial.flushPrepared();
    }
}
