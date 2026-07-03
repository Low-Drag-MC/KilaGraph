package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import net.minecraft.resources.ResourceLocation;

/**
 * The synthetic {@link ResourceLocation} naming scheme for KilaGraph-generated shaders — always in the
 * {@link Kilagraph#MODID} namespace, path {@code generated/<contentHash>}. Used by
 * {@link KGShaderResourceProvider} (the served {@code shaders/core/generated/<hash>.{json,vsh,fsh}} id) and as
 * the {@code RenderType} name in {@link RenderTypeGraphMaterial}.
 *
 * <p>(Previously this also held a source registry consulted by a {@code ShaderManagerMixin} on 1.21.5+; that
 * injection seam does not exist in 1.21.1 — the generated GLSL is served through {@link KGShaderResourceProvider}
 * instead — so only the id helper remains.)</p>
 */
public final class DynamicShaderSourceRegistry {

    private DynamicShaderSourceRegistry() {}

    /** The synthetic shader identifier for a compiled-graph content hash. */
    public static ResourceLocation shaderId(String contentHash) {
        return ResourceLocation.fromNamespaceAndPath(Kilagraph.MODID, "generated/" + contentHash);
    }
}
