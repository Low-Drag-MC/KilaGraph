package com.lowdragmc.kilagraph.mixin.client;

import com.lowdragmc.kilagraph.rendertype.runtime.DynamicShaderSourceRegistry;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes KilaGraph-generated shader ids ({@code kilagraph:generated/...}) to {@link DynamicShaderSourceRegistry}.
 * {@code ShaderManager.Configs} is the {@code ShaderSource} of the pipeline cache every shader reload installs,
 * so this single seam serves generated sources to both KilaGraph's own compiles ({@code KGPipelines}) and the
 * cache's lazy ones — which is how generated pipelines survive a resource reload. Their {@code #include}s resolve
 * through the same source like any asset shader's.
 */
@Mixin(ShaderManager.Configs.class)
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
