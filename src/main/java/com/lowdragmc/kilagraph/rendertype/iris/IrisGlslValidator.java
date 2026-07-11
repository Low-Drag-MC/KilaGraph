package com.lowdragmc.kilagraph.rendertype.iris;

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

    private IrisGlslValidator() {}

    /**
     * @return {@code null} when {@code source} compiles as a fragment shader — or when no GL context is
     * current (validation unavailable, accept); else the driver's compile info log.
     */
    @Nullable
    static String compileFragmentError(String source) {
        try {
            GL.getCapabilities();
        } catch (Throwable noContext) {
            return null; // headless / no context on this thread — cannot validate, accept
        }
        int shader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        if (shader == 0) return null; // driver refused a shader object — can't validate, accept
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) return null;
            String log = GL20.glGetShaderInfoLog(shader);
            return log == null || log.isBlank() ? "(no driver info log)" : log;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }
}
