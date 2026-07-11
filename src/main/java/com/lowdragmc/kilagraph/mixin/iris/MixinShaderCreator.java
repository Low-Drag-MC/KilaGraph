package com.lowdragmc.kilagraph.mixin.iris;

import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.kilagraph.rendertype.iris.IrisShaderInjector;
import net.irisshaders.iris.gl.shader.ShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites every shaderpack program's final GLSL before Iris compiles it. {@code createShader(name, type,
 * source)} is the single chokepoint all of Iris's programs (shaderpack / fallback / shadow) pass through,
 * so modifying its {@code source} argument here lets KilaGraph inject its surface logic (fragment) and
 * vertex displacement (vertex) into the shaderpack's own gbuffers programs (see {@link IrisShaderInjector}).
 *
 * <p>Applied only when Iris is present (gated by {@code IrisMixinPlugin}), so referencing Iris's
 * {@code ShaderType} enum here is safe — this class is only ever loaded to be applied to an Iris class.
 * The injector itself receives the stage as a plain String and stays Iris-type-free (unit-testable).
 * {@code remap = false} because the target is not a Mojang-mapped class.</p>
 */
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public class MixinShaderCreator {

    @ModifyVariable(method = "createShader", at = @At("HEAD"), argsOnly = true, remap = false, name = "source")
    private static String kilagraph$injectSource(String source,
                                                 @Local(argsOnly = true, ordinal = 0) String name,
                                                 @Local(argsOnly = true) ShaderType type) {
        // The stage drives the dispatch: FRAGMENT -> surface-shading injection, VERTEX -> displacement
        // read-hijack, anything else untouched. The name (e.g. gbuffers_entities) is only used for the
        // one-shot diagnostic logs confirming the injection landed.
        return IrisShaderInjector.inject(name, type == null ? "" : type.name(), source);
    }
}
