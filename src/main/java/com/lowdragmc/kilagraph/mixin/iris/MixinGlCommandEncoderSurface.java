package com.lowdragmc.kilagraph.mixin.iris;

import com.lowdragmc.kilagraph.rendertype.iris.IrisSurfaceUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Writes the {@code kg_surface_id} discriminator and our bindings onto the shaderpack program the draw runs with:
 * {@code GlCommandEncoder.setupDraw} binds the draw's pipeline (Iris's substitute) and the pass's uniforms, so at its
 * {@code RETURN} the pack's program is current. A no-op unless our geometry is the one being drawn (see
 * {@link IrisSurfaceUniform}). Applied only when Iris is present ({@link IrisMixinPlugin}).
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder", remap = false)
public class MixinGlCommandEncoderSurface {

    @Inject(method = "setupDraw", at = @At("RETURN"), remap = false)
    private void kilagraph$applySurfaceId(CallbackInfo ci) {
        IrisSurfaceUniform.applyToBoundProgram();
    }
}
