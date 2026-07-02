package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.lowdraglib2.client.shader.uniform.UniformCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Binds the builtin / KG-managed uniforms a compiled graph declares (1.21.1: individual uniforms, no UBO —
 * see {@code ShaderGraphCompiler.useBuiltinUniform}). Called each draw with the material's linked
 * {@link com.lowdragmc.lowdraglib2.client.shader.management.ShaderProgram}'s {@link UniformCache} while the
 * program is bound.
 *
 * <p>Vanilla builtins ({@code ModelViewMat}/{@code ProjMat}/{@code FogColor}/…) come from {@link RenderSystem}
 * (the same values vanilla's core-shader {@code ShaderInstance} would receive); KG-managed values
 * ({@code kg_Time}, {@code kg_<transform matrix>}) are computed here. Unknown names (e.g. EXPOSED material
 * variables, or modern fog fields with no 1.21.1 source) are skipped — those are set elsewhere or absent.</p>
 */
public final class KGBuiltinUniforms {

    private KGBuiltinUniforms() {}

    public static void bind(UniformCache uc, Map<String, GlslType> builtins) {
        for (String name : builtins.keySet()) {
            switch (name) {
                case "ModelViewMat" -> uc.glUniformMatrix4F(name, RenderSystem.getModelViewMatrix());
                case "ProjMat" -> uc.glUniformMatrix4F(name, RenderSystem.getProjectionMatrix());
                case "TextureMat" -> uc.glUniformMatrix4F(name, RenderSystem.getTextureMatrix());
                case "ModelOffset" -> uc.glUniform3F(name, 0f, 0f, 0f); // no chunk offset outside terrain rendering
                case "ColorModulator" -> { float[] c = RenderSystem.getShaderColor(); uc.glUniform4F(name, c[0], c[1], c[2], c[3]); }
                case "FogColor" -> { float[] c = RenderSystem.getShaderFogColor(); uc.glUniform4F(name, c[0], c[1], c[2], c[3]); }
                case "FogStart" -> uc.glUniform1F(name, RenderSystem.getShaderFogStart());
                case "FogEnd" -> uc.glUniform1F(name, RenderSystem.getShaderFogEnd());
                case "FogShape" -> uc.glUniform1I(name, RenderSystem.getShaderFogShape().getIndex());
                case "GameTime" -> uc.glUniform1F(name, RenderSystem.getShaderGameTime());
                case "ScreenSize" -> {
                    var w = Minecraft.getInstance().getWindow();
                    uc.glUniform2F(name, w.getWidth(), w.getHeight());
                }
                // TODO(1.21-backport milestone 2, lighting group): Light0/1_Direction — RenderSystem's light
                // directions are private in 1.21.1; wire the diffuse-light directions when the lighting group
                // is done (only litVertexColor / lighting nodes reference them). Left unset (0) for now.
                // KG-managed:
                case "kg_Time" -> uc.glUniform1F(name, timeSeconds());
                case "kg_IModelViewMat" -> uc.glUniformMatrix4F(name, new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
                case "kg_IProjMat" -> uc.glUniformMatrix4F(name, new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
                // TODO(1.21-backport milestone 2, transform group): world<->view rotation matrices need the live
                // camera; identity for now (only the Transform / world-normal nodes reference these).
                case "kg_ViewMat", "kg_IViewMat" -> uc.glUniformMatrix4F(name, new Matrix4f());
                default -> { /* EXPOSED material uniforms (set by the material) or unsupported fields — skip */ }
            }
        }
    }

    /** World time in seconds, wrapping every MC day (24000 ticks = 1200 s) to keep float precision — matches
     *  the KG_Globals {@code Time} the 26.1 engine block provided. Tick granularity (partial-tick smoothing is
     *  a milestone-2 refinement). */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        return (gameTime % 24000L) / 20.0f;
    }
}
