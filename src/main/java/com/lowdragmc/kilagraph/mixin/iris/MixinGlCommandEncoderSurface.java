package com.lowdragmc.kilagraph.mixin.iris;

import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Writes the {@code kg_surface_id} discriminator onto the shaderpack program at the moment it is bound.
 * {@code GlCommandEncoder.trySetup} binds the (Iris-substituted) program for the upcoming draw, so its
 * {@code @RETURN} is the first point where {@code GL_CURRENT_PROGRAM} is the program our geometry will draw
 * with — our own {@code RenderType.draw} "before drawIndexed" hook runs too early (program not bound yet).
 *
 * <p>Applied only when Iris is present (gated by {@link IrisMixinPlugin}). Touches no Iris type; works on
 * whatever program is current, so it is harmless for non-Iris draws (it no-ops unless our geometry is the
 * one being drawn — see {@link IrisSurfaceUniform}).</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", remap = false)
public class MixinGlCommandEncoderSurface {

    @Inject(method = "trySetup", at = @At("RETURN"), remap = false)
    private void kilagraph$applySurfaceId(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            IrisSurfaceUniform.applyToBoundProgram();
        }
    }
}
