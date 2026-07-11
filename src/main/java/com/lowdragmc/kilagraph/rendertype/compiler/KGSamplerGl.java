package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerAddress;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerFilter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps a compiled sampler's {@link SamplerDefault} (KilaGraph's own filter/address enums) to the concrete
 * GL sampler-object parameters, and resolves which texture unit each managed sampler binds to.
 *
 * <p>1.21.1 has no per-draw {@code GpuSampler} (that is a 1.21.5+ concept), so the runtime instead binds a GL
 * {@code sampler object} ({@code glGenSamplers}/{@code glBindSampler}) to the sampler's texture unit — which
 * overrides the shared texture object's own filter/wrap for that unit only, giving per-material sampling
 * without mutating the texture. This class is the headless half of that: it only computes the int params and
 * unit indices (the {@code GL11}/{@code GL12} values are compile-time constants, folded to literals, so this
 * class carries no runtime GL dependency and stays usable on the dedicated-server / compiler layer). The
 * actual {@code glGenSamplers}/{@code glBindSampler} calls live in the client runtime
 * ({@code KGMaterialValues}).</p>
 *
 * <p>The texture unit for a sampler is its index in {@code ShaderInstance.getSamplerNames()} — because vanilla
 * {@code ShaderInstance.apply()} binds the sampler at index {@code j} to {@code GL_TEXTURE0 + j} (the name list
 * is compacted at link time, so index == unit).</p>
 */
public final class KGSamplerGl {

    private KGSamplerGl() {}

    /** The GL sampler-object parameters (min/mag filter + S/T wrap) for one sampler. */
    public record GlSampler(int minFilter, int magFilter, int wrapS, int wrapT) {}

    /** A sampler object to bind: which texture {@code unit} it goes on, and its {@link GlSampler} params. */
    public record Binding(int unit, GlSampler sampler) {}

    /**
     * The GL params for a {@link SamplerDefault}. Mipmap is intentionally not turned into a mipmapped min-filter:
     * a graph sampler binds an arbitrary {@code TextureManager} texture that usually has no uploaded mip levels,
     * which would sample as black — so filtering stays plain {@code GL_LINEAR}/{@code GL_NEAREST}.
     */
    public static GlSampler params(SamplerDefault def) {
        int filter = def.filter() == SamplerFilter.LINEAR ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        int wrap = def.address() == SamplerAddress.REPEAT ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
        return new GlSampler(filter, filter, wrap, wrap);
    }

    /**
     * For each managed sampler that the shader actually declares, its {@link Binding} (texture unit + GL params).
     * A sampler not present in {@code samplerNames} (never declared, or dropped at link because the program does
     * not use it) is skipped — it has no texture unit to bind to.
     *
     * @param samplerNames the shader's live sampler-name list ({@code ShaderInstance.getSamplerNames()}), whose
     *                     index is the texture unit each sampler is bound to
     * @param bindings     sampler uniform name -> its baked texture + params
     */
    public static List<Binding> resolveBindings(List<String> samplerNames, Map<String, SamplerDefault> bindings) {
        List<Binding> out = new ArrayList<>();
        for (Map.Entry<String, SamplerDefault> e : bindings.entrySet()) {
            int unit = samplerNames.indexOf(e.getKey());
            if (unit < 0) continue;
            out.add(new Binding(unit, params(e.getValue())));
        }
        return out;
    }
}
