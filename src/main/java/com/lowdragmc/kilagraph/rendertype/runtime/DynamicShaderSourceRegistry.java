package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the GLSL sources for KilaGraph-generated shaders, keyed by a synthetic shader
 * {@link ResourceLocation} (always in the {@link Kilagraph#MODID} namespace, path {@code generated/<hash>})
 * and shader {@link Stage}.
 *
 * <p>This registry is consulted by {@code ShaderManagerMixin} on newer Minecraft, which patches
 * {@code ShaderManager.getShader(id, type)}. TODO(1.21-backport milestone 2): 1.21.1 has no
 * {@code ShaderManager}/{@code com.mojang.blaze3d.shaders.ShaderType} — the shader stage type was replaced
 * here by the local {@link Stage} enum, and the injection seam must be re-wired to the 1.21.1
 * shader/program pipeline (e.g. {@code com.mojang.blaze3d.shaders.Program.Type}).</p>
 */
public final class DynamicShaderSourceRegistry {

    /** Shader stage. Replaces {@code com.mojang.blaze3d.shaders.ShaderType} (absent in 1.21.1). */
    public enum Stage { VERTEX, FRAGMENT }

    private record Key(ResourceLocation id, Stage type) {}

    private static final Map<Key, String> SOURCES = new ConcurrentHashMap<>();

    private DynamicShaderSourceRegistry() {}

    /** The synthetic shader identifier for a compiled-graph content hash. */
    public static ResourceLocation shaderId(String contentHash) {
        return ResourceLocation.fromNamespaceAndPath(Kilagraph.MODID, "generated/" + contentHash);
    }

    /** Register the vertex + fragment GLSL for a generated shader id (idempotent). */
    public static void register(ResourceLocation id, String vertexSource, String fragmentSource) {
        SOURCES.put(new Key(id, Stage.VERTEX), vertexSource);
        SOURCES.put(new Key(id, Stage.FRAGMENT), fragmentSource);
    }

    /** Whether the id belongs to KilaGraph's generated namespace+path. */
    public static boolean isGenerated(ResourceLocation id) {
        return id != null && Kilagraph.MODID.equals(id.getNamespace()) && id.getPath().startsWith("generated/");
    }

    /** Drop both stage sources for a generated shader id (called when no material references it). */
    public static void unregister(ResourceLocation id) {
        SOURCES.remove(new Key(id, Stage.VERTEX));
        SOURCES.remove(new Key(id, Stage.FRAGMENT));
    }

    /** The GLSL source for a generated shader id+stage, or {@code null} if unknown. */
    @Nullable
    public static String get(ResourceLocation id, Stage type) {
        return SOURCES.get(new Key(id, type));
    }
}
