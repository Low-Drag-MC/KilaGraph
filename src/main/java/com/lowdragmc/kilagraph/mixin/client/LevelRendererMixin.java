package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.SceneCaptureManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Takes the scene capture ({@link SceneCaptureManager}) at the opaque&rarr;translucent boundary. Copying the main
 * target needs no render pass open on it:
 * <ul>
 *   <li>with improved transparency the solid pass has closed when {@code executeOit} starts;</li>
 *   <li>with classic transparency the solid and translucent draws share one pass, so it is split: closed before
 *       the translucent draws (the caller's own close is then a no-op), captured, and continued in a pass on the
 *       same targets, closed when they are done.</li>
 * </ul>
 * Nothing changes while no material samples the scene.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Unique
    private @Nullable RenderPass kilagraph$translucentPass;

    @ModifyVariable(method = "executeClassicTransparency", at = @At("HEAD"), argsOnly = true)
    private RenderPass kilagraph$captureBeforeTranslucent(RenderPass renderPass) {
        if (!SceneCaptureManager.INSTANCE.isNeeded()) return renderPass;
        renderPass.close();
        SceneCaptureManager.INSTANCE.capture();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Main (after scene capture)", main.getColorTextureView(), Optional.empty(),
                main.getDepthTextureView(), OptionalDouble.empty());
        RenderSystem.bindDefaultUniforms(pass);
        kilagraph$translucentPass = pass;
        return pass;
    }

    @Inject(method = "executeClassicTransparency", at = @At("RETURN"))
    private void kilagraph$closeTranslucentPass(ChunkSectionsToRender chunkSectionsToRender,
                                                FeatureRenderDispatcher.PreparedFrame featureFrame, RenderPass renderPass,
                                                CallbackInfo ci) {
        if (kilagraph$translucentPass != null) {
            kilagraph$translucentPass.close();
            kilagraph$translucentPass = null;
        }
    }

    @Inject(method = "executeOit", at = @At("HEAD"))
    private void kilagraph$captureBeforeOit(ChunkSectionsToRender chunkSectionsToRender,
                                            FeatureRenderDispatcher.PreparedFrame featureFrame, CallbackInfo ci) {
        SceneCaptureManager.INSTANCE.capture();
    }
}
