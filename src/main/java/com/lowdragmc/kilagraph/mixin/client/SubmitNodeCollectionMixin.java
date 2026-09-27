package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With improved transparency on, every blended custom geometry draws in the order-independent transparency
 * phases, which need an OIT pipeline set. A KilaGraph material blending in a way OIT can't express has none; it
 * draws in the solid phase instead, blending over the opaque scene (see
 * {@link RenderTypeGraphMaterial#drawsSolidUnderImprovedTransparency}).
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    @Shadow @Final public SimpleFeatureRenderPhase solid;
    @Shadow @Final public SimpleFeatureRenderPhase oitTranslucent;
    @Shadow @Final public SimpleFeatureRenderPhase translucentCustomGeometry;

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void kilagraph$solidUnderImprovedTransparency(PoseStack poseStack, RenderType renderType,
                                                          SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer,
                                                          CallbackInfo ci) {
        if (translucentCustomGeometry != oitTranslucent) return; // classic transparency: nothing to reroute
        var material = RenderTypeGraphMaterial.of(renderType);
        if (material == null || !material.drawsSolidUnderImprovedTransparency()) return;
        solid.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, customGeometryRenderer));
        ci.cancel();
    }
}
