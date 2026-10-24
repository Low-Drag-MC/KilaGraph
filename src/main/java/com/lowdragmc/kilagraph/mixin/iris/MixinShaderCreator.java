package com.lowdragmc.kilagraph.mixin.iris;

import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.kilagraph.rendertype.iris.IrisShaderInjector;
import net.irisshaders.iris.gl.shader.ShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites a shaderpack program's final GLSL before Iris compiles it. {@code createShader(name, type,
 * source)} is the single chokepoint all of Iris's programs (shaderpack / fallback / shadow) pass through,
 * so modifying its {@code source} argument here lets KilaGraph inject its surface logic (fragment) and
 * vertex displacement (vertex) into the programs its geometry is drawn with (see {@link IrisShaderInjector}).
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
        // The name (e.g. entities_solid) picks the programs to inject into; the stage what to inject.
        return IrisShaderInjector.inject(name, type == null ? "" : type.name(), source);
    }
}
