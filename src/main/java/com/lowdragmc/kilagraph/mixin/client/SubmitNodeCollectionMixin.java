package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ShapeOutlineFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A KilaGraph material whose blend order-independent transparency can't express draws in the solid phase (see
 * {@link RenderTypeGraphMaterial#drawsInSolidPhase}). Its render type forces the solid model phase, which vanilla
 * honours for model submits; these are the other submits a render type is handed to, which route by blending
 * alone — to the translucent phases, and with improved transparency to the OIT ones, which such a material has no
 * pipelines for. Each lands where vanilla's model submit puts it.
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {

    @Shadow @Final public SimpleFeatureRenderPhase solid;

    @Unique
    private static boolean kilagraph$drawsInSolidPhase(RenderType renderType) {
        var material = RenderTypeGraphMaterial.of(renderType);
        return material != null && material.drawsInSolidPhase();
    }

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void kilagraph$solidCustomGeometry(PoseStack poseStack, RenderType renderType,
                                               SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer,
                                               CallbackInfo ci) {
        if (!kilagraph$drawsInSolidPhase(renderType)) return;
        solid.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, customGeometryRenderer));
        ci.cancel();
    }

    /** (A KilaGraph render type has no outline, so the outline half of the submit adds nothing.) */
    @Inject(method = "submitBlockModel", at = @At("HEAD"), cancellable = true)
    private void kilagraph$solidBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts,
                                           int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor,
                                           CallbackInfo ci) {
        if (!kilagraph$drawsInSolidPhase(renderType)) return;
        solid.submit(new BlockModelFeatureRenderer.Submit(poseStack.last().copy(), renderType, modelParts, tintLayers,
                lightCoords, overlayCoords, -1, null));
        ci.cancel();
    }

    @Inject(method = "submitShapeOutline", at = @At("HEAD"), cancellable = true)
    private void kilagraph$solidShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
                                             float width, boolean afterTerrain, CallbackInfo ci) {
        if (!kilagraph$drawsInSolidPhase(renderType)) return;
        solid.submit(new ShapeOutlineFeatureRenderer.Submit(poseStack.last().copy(), shape, renderType, color, width));
        ci.cancel();
    }
}
