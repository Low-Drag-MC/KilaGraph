package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds each KG material's per-sampler GL sampler objects (filter/wrap) onto their texture units, riding the
 * vanilla {@code ShaderInstance} {@code apply()}/{@code clear()} draw lifecycle so every bind is balanced by a
 * matching unbind — no caller has to remember to clean up, and a sampler-state override can never leak onto a
 * later (vanilla) draw. 1.21.1 has no per-draw {@code GpuSampler}; a GL sampler object bound to a texture unit
 * overrides the shared texture object's own filter/wrap for that unit only, giving per-material sampling.
 *
 * <p>{@link KGMaterialValues#apply} stages the bindings for the shader it is about to draw; the
 * {@code ShaderInstanceMixin} then binds them in {@code apply()} (right after the textures are bound to their
 * units) and unbinds them in {@code clear()} (after the draw). A non-KG shader stages nothing, so both hooks are
 * no-ops for it. All calls run on the render thread (the only thread that applies/clears a shader).</p>
 */
public final class KGSamplerBinder {

    private KGSamplerBinder() {}

    /** Bindings staged for the next {@code apply()} of {@link #pendingShader} (set by {@link KGMaterialValues#apply}). */
    @Nullable private static ShaderInstance pendingShader;
    @Nullable private static List<KGSamplerGl.Binding> pendingBindings;
    /** Texture units a sampler object is currently bound to (between apply and clear), released in {@link #onClear}. */
    private static final List<Integer> boundUnits = new ArrayList<>();

    /** Stage the sampler-object bindings to apply on {@code shader}'s next {@code apply()} (called before the draw). */
    public static void stage(ShaderInstance shader, List<KGSamplerGl.Binding> bindings) {
        pendingShader = shader;
        pendingBindings = bindings;
    }

    /** {@code ShaderInstance.apply()} TAIL hook: bind the staged sampler objects onto their texture units. */
    public static void onApply(ShaderInstance shader) {
        if (shader != pendingShader || pendingBindings == null) return;
        for (KGSamplerGl.Binding b : pendingBindings) {
            GL33.glBindSampler(b.unit(), GlSamplerObjects.getOrCreate(b.sampler()));
            boundUnits.add(b.unit());
        }
        pendingShader = null;
        pendingBindings = null;
    }

    /** {@code ShaderInstance.clear()} HEAD hook: unbind everything bound by the paired {@link #onApply}. */
    public static void onClear() {
        if (boundUnits.isEmpty()) return;
        for (int unit : boundUnits) GL33.glBindSampler(unit, 0);
        boundUnits.clear();
    }

    /**
     * Process-wide cache of GL sampler objects keyed by their params — at most a handful (2 filters × 2 wraps).
     * Created lazily on the render thread the first time a param combo is used; never freed (trivially small and
     * lives for the GL context's lifetime).
     */
    private static final class GlSamplerObjects {
        private static final Map<KGSamplerGl.GlSampler, Integer> CACHE = new HashMap<>();

        static int getOrCreate(KGSamplerGl.GlSampler p) {
            return CACHE.computeIfAbsent(p, k -> {
                int id = GL33.glGenSamplers();
                GL33.glSamplerParameteri(id, GL11.GL_TEXTURE_MIN_FILTER, k.minFilter());
                GL33.glSamplerParameteri(id, GL11.GL_TEXTURE_MAG_FILTER, k.magFilter());
                GL33.glSamplerParameteri(id, GL11.GL_TEXTURE_WRAP_S, k.wrapS());
                GL33.glSamplerParameteri(id, GL11.GL_TEXTURE_WRAP_T, k.wrapT());
                return id;
            });
        }
    }
}
