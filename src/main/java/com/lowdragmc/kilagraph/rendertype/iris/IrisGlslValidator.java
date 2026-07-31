package com.lowdragmc.kilagraph.rendertype.iris;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Compiles a GLSL string against the live GL context purely to <em>validate</em> it — the safety net that
 * keeps a bad KilaGraph injection from ever reaching (and breaking) the shaderpack's real program compile.
 * Used at two layers: {@code IrisSurfaceRegistry.register} validates each <b>new surface</b> standalone
 * (reject → passthrough id 0, other surfaces unaffected), and {@code IrisShaderInjector.injectGuarded}
 * validates the <b>whole injected program source</b> (fail → hand Iris the untouched original).
 *
 * <p>Both call sites run on the render thread with a context current (material build; Iris's pack compile),
 * so the no-context path is a defensive fallback only (headless unit tests, exotic threading): validation is
 * then skipped and the source accepted — the pre-hardening behaviour, never worse.</p>
 */
final class IrisGlslValidator {

    /** Info-log cap handed to {@code glGetShaderInfoLog} — same value vanilla uses for its program logs. */
    private static final int INFO_LOG_MAX = 32768;

    private IrisGlslValidator() {}

    /**
     * @return {@code null} when {@code source} compiles as a fragment shader — or when no GL context is
     * current (validation unavailable, accept); else the driver's compile info log.
     */
    @Nullable
    static String compileFragmentError(String source) {
        return compileError(source, GL20.GL_FRAGMENT_SHADER);
    }

    /** {@link #compileFragmentError} for the vertex stage (the injected pack vsh / a surface's
     *  {@code kg_vpos}/{@code kg_vnormal} validation harness). */
    @Nullable
    static String compileVertexError(String source) {
        return compileError(source, GL20.GL_VERTEX_SHADER);
    }

    @Nullable
    private static String compileError(String source, int glShaderType) {
        // The GlStateManager wrappers below assert the render thread (and a context is only current there), so
        // check first and skip validation off-thread rather than letting the assert escape as an exception.
        if (!RenderSystem.isOnRenderThread()) return null;
        try {
            GL.getCapabilities();
        } catch (Throwable noContext) {
            return null; // headless / no context on this thread — cannot validate, accept
        }
        int shader = GlStateManager.glCreateShader(glShaderType);
        if (shader == 0) return null; // driver refused a shader object — can't validate, accept
        try {
            GlStateManager.glShaderSource(shader, source);
            GlStateManager.glCompileShader(shader);
            if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) return null;
            String log = GlStateManager.glGetShaderInfoLog(shader, INFO_LOG_MAX);
            return log == null || log.isBlank() ? "(no driver info log)" : log;
        } finally {
            GlStateManager.glDeleteShader(shader);
        }
    }
}
