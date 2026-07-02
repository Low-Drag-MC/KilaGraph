package com.lowdragmc.kilagraph.rendertype.compiler;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.Sampler2DValue;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerAddress;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.SamplerFilter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A compiled sampler's baked default: the texture to bind plus its GPU sampler parameters (filter /
 * address / mipmap). Carries KilaGraph's own {@link SamplerFilter}/{@link SamplerAddress} enums (not
 * blaze3d's) so the compiler/types layer stays headless-safe; the runtime maps these to the
 * {@code com.mojang.blaze3d.textures} enums when building the {@code GpuSampler}.
 */
public record SamplerDefault(ResourceLocation texture, SamplerFilter filter, SamplerAddress address, boolean mipmap) {

    /** {@code minecraft:missingno} — MC's missing-texture placeholder (the literal value of the client-only
     *  {@code MissingTextureAtlasSprite.getLocation()}, inlined so the compiler/types layer stays headless-safe). */
    private static final ResourceLocation MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");

    /** The MC missing-texture placeholder with sane defaults — used for unconnected sampler fallbacks. */
    public static SamplerDefault missing() {
        return new SamplerDefault(MISSING_TEXTURE, SamplerFilter.NEAREST, SamplerAddress.CLAMP, false);
    }

    /**
     * Build from a {@link Sampler2DValue} (a constant/variable's value): parse its location to an
     * {@link ResourceLocation} (else {@code null} — the runtime then binds the missing-texture placeholder).
     */
    @Nullable
    public static SamplerDefault of(Object value) {
        if (!(value instanceof RenderTypeGraphTypes.Sampler2DValue s)) return null;
        String loc = s.location();
        if (loc == null || loc.isBlank()) return null;
        ResourceLocation texture = ResourceLocation.tryParse(loc);
        if (texture == null) return null;
        return new SamplerDefault(texture, s.filter(), s.address(), s.mipmap());
    }
}
