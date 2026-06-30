package com.lowdragmc.kilagraph.mixin.iris;

import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;

/**
 * Temporary workaround for Iris issue <a href="https://github.com/IrisShaders/Iris/issues/3137">#3137</a>
 * ({@code IllegalStateException: Missing sampler Sampler1} crash when enabling a PBR shaderpack).
 *
 * <p>Background: on MC 26.1+ {@code GlCommandEncoder.trySetup} validates that every {@code Uniform.Sampler}
 * declared by the bound program has a matching entry in {@code renderPass.samplers}, throwing otherwise.
 * Iris's {@code ExtendedShader} declares {@code Sampler1}/{@code Sampler2} (PBR normal/specular) but binds
 * their default textures via raw GL only in {@code iris$setupState}, which Iris injects at <em>@RETURN</em>
 * of {@code trySetup} — after the validation loop has already run and thrown. (Iris's {@code iris$bypassSetup}
 * @HEAD hook only skips vanilla setup for composite/deferred {@code CustomPass} draws, not ordinary gbuffers
 * draws.) Because the real GL binding loop later in {@code trySetup} already {@code continue}s over missing
 * samplers, Iris's raw-GL defaults survive — only the validation throw is the problem.</p>
 *
 * <p>This validation is gated by {@code GlRenderPass.VALIDATION = IS_RUNNING_IN_IDE && ...}, so the crash
 * only manifests in a development/IDE environment.</p>
 *
 * <p>The fix redirects the <b>first</b> {@code GlProgram.getUniforms()} call in {@code trySetup} (the one the
 * sampler-validation loop iterates) to return an empty map when the program is an Iris program, making the
 * validation loop a no-op for those draws. The second {@code getUniforms()} call (the real binding loop) is
 * left untouched via {@code ordinal = 0}, so uniforms/samplers still bind normally. Non-Iris draws keep full
 * validation. Only Iris-targeting; applied solely when Iris is present (gated by {@link IrisMixinPlugin}).</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class MixinGlCommandEncoder {

    /** {@code net.irisshaders.iris.pipeline.programs.IrisProgram}, resolved reflectively to keep Iris a soft dep. */
    private static final Class<?> kilagraph$IRIS_PROGRAM = kilagraph$resolveIrisProgram();

    private static Class<?> kilagraph$resolveIrisProgram() {
        try {
            return Class.forName("net.irisshaders.iris.pipeline.programs.IrisProgram");
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Redirect(
            method = "trySetup",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlProgram;getUniforms()Ljava/util/Map;", ordinal = 0)
    )
    private Map<?, ?> kilagraph$skipIrisSamplerValidation(GlProgram program) {
        if (kilagraph$IRIS_PROGRAM != null && kilagraph$IRIS_PROGRAM.isInstance(program)) {
            // Skip vanilla's sampler-presence validation for Iris programs: Iris binds Sampler1/Sampler2
            // defaults via raw GL at trySetup @RETURN, after this check would otherwise throw.
            return Collections.emptyMap();
        }
        return program.getUniforms();
    }
}
