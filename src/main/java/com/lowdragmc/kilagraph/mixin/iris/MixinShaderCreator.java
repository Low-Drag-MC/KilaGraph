package com.lowdragmc.kilagraph.mixin.iris;

import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.kilagraph.rendertype.iris.IrisShaderInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites every shaderpack program's final GLSL before Iris compiles it. {@code createShader(name, type,
 * source)} is the single chokepoint all of Iris's programs (shaderpack / fallback / shadow) pass through,
 * so modifying its {@code source} argument here lets KilaGraph inject its surface logic into the
 * shaderpack's own gbuffers program (see {@link IrisShaderInjector}).
 *
 * <p>Applied only when Iris is present (gated by {@code IrisMixinPlugin}). Targets Iris by string and
 * touches no Iris type itself, keeping the mod a soft dependency. {@code remap = false} because the
 * target is not a Mojang-mapped class.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public class MixinShaderCreator {

    @ModifyVariable(method = "createShader", at = @At("HEAD"), argsOnly = true, remap = false, name = "source")
    private static String kilagraph$injectSource(String source, @Local(argsOnly = true, ordinal = 0) String name) {
        // inject() no-ops on sources that don't sample gtexture (i.e. vertex/other stages), so a single
        // unconditional call is safe for every program type. The name (e.g. gbuffers_entities) is only used
        // for the one-shot diagnostic log confirming the injection landed.
        return IrisShaderInjector.inject(name, source);
    }
}
