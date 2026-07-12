package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Binds the <b>KG-managed</b> builtin uniforms a compiled graph declares that vanilla's
 * {@code ShaderInstance.setDefaultUniforms} does <em>not</em> already set (1.21.1: individual uniforms, no UBO —
 * see {@code ShaderGraphCompiler.useBuiltinUniform}). Called each draw with the material's {@link ShaderInstance}.
 *
 * <p>The vanilla builtins ({@code ModelViewMat}/{@code ProjMat}/{@code FogColor}/{@code GameTime}/{@code ScreenSize}/
 * {@code ColorModulator}/{@code Fog*}/{@code Light0/1_Direction}/…) are set for us by
 * {@code ShaderInstance.setDefaultUniforms} inside {@code VertexBuffer.drawWithShader} from {@link RenderSystem} —
 * so they are deliberately NOT touched here (that's why this shrank from the raw-{@code ShaderProgram} version,
 * which had to push them all manually). This only computes the KG-managed values: {@code kg_Time},
 * {@code kg_<transform matrix>}, and {@code ModelOffset} (0 outside terrain rendering, which
 * {@code setDefaultUniforms} leaves alone). Uniforms the shader doesn't declare are skipped.</p>
 */
public final class KGBuiltinUniforms {

    private KGBuiltinUniforms() {}

    public static void bind(ShaderInstance shader, Map<String, GlslType> builtins) {
        for (String name : builtins.keySet()) {
            Uniform u = shader.getUniform(name);
            if (u == null) continue; // vanilla builtins (auto-set by setDefaultUniforms) or unused — skip
            switch (name) {
                // ModelOffset (aka ChunkOffset): no chunk offset outside terrain rendering, and setDefaultUniforms
                // doesn't set it, so zero it here.
                case "ModelOffset" -> u.set(0f, 0f, 0f);
                // KG-managed:
                case "kg_Time" -> u.set(timeSeconds());
                case "kg_IProjMat" -> u.set(new Matrix4f(RenderSystem.getProjectionMatrix()).invert());
                // 1.21.1 renders camera-relative: geometry carries no model matrix at the shader level, so the view
                // matrix IS the modelview (camera rotation, camera at origin). Hence kg_ViewMat == ModelViewMat and
                // kg_IViewMat == kg_IModelViewMat. These feed the Transform / world-normal / clip<->world nodes
                // (was identity before, which wrongly left those nodes world==view).
                case "kg_ViewMat" -> u.set(new Matrix4f(RenderSystem.getModelViewMatrix()));
                case "kg_IModelViewMat", "kg_IViewMat" -> u.set(new Matrix4f(RenderSystem.getModelViewMatrix()).invert());
                // KG-managed absolute world camera position, in Minecraft's precision-split form (1.21.1 renders
                // camera-relative; no vanilla camera uniform). The absolute position as a single float jitters
                // past ~16M blocks (float32's exact-integer limit), so we split it CPU-side from the DOUBLE
                // camera position into an integer block + a small fractional offset (the offset stays float-exact),
                // and shaders reconstruct camPos = block - offset. Only the absolute block value still rounds far
                // out; the per-frame jitter is gone. See CameraNode/GlobalsUboNode/Transform/Position.
                case "kg_CameraBlockPos" -> {
                    var p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    u.set((float) Math.floor(p.x), (float) Math.floor(p.y), (float) Math.floor(p.z));
                }
                case "kg_CameraOffset" -> {
                    var p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    u.set((float) (Math.floor(p.x) - p.x), (float) (Math.floor(p.y) - p.y), (float) (Math.floor(p.z) - p.z));
                }
                default -> { /* vanilla builtin (setDefaultUniforms) or EXPOSED material uniform (material) — skip */ }
            }
        }
    }

    /** World time in seconds, wrapping every MC day (24000 ticks = 1200 s) to keep float precision — matches
     *  the KG_Globals {@code Time} the 26.1 engine block provided. Adds the render partial-tick so the value
     *  advances smoothly per frame instead of stepping at 20 Hz (1.21.1: {@code Minecraft.getTimer()} is the
     *  {@link net.minecraft.client.DeltaTracker}; 26.1 spells the accessor {@code getDeltaTracker()}). */
    private static float timeSeconds() {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return ((gameTime % 24000L) + partial) / 20.0f;
    }
}
