package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.DynamicShaderSourceRegistry;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes KilaGraph-generated shader ids ({@code kilagraph:generated/...}) to
 * {@link DynamicShaderSourceRegistry}. {@code ShaderManager.getShader} is the source the GL device
 * uses for both lazy {@code setPipeline} compilation and explicit {@code precompilePipeline}, so this
 * single seam makes generated pipelines compile everywhere and survive resource reloads.
 */
@Mixin(ShaderManager.class)
public class ShaderManagerMixin {

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void kilagraph$provideGeneratedShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (DynamicShaderSourceRegistry.isGenerated(id)) {
            String source = DynamicShaderSourceRegistry.get(id, type);
            if (source != null) {
                cir.setReturnValue(source);
            }
        }
    }
}
