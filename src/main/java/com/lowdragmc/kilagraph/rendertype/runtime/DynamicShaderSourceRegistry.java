package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.Kilagraph;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the GLSL sources for KilaGraph-generated shaders, keyed by a synthetic shader
 * {@link Identifier} (always in the {@link Kilagraph#MODID} namespace, path {@code generated/<hash>})
 * and {@link ShaderType}.
 *
 * <p>This registry is consulted by {@code ShaderManagerMixin}, which patches
 * {@code ShaderManager.getShader(id, type)} — the source used by the GL device for both lazy
 * {@code setPipeline} compilation and explicit {@code precompilePipeline}. Because the device always
 * resolves shader source through that method, generated pipelines survive resource reloads with no
 * manual re-precompile: the reload rebuilds the device's pipeline cache, and each recompile re-reads
 * source from here.</p>
 */
public final class DynamicShaderSourceRegistry {

    private record Key(Identifier id, ShaderType type) {}

    private static final Map<Key, String> SOURCES = new ConcurrentHashMap<>();

    private DynamicShaderSourceRegistry() {}

    /** The synthetic shader identifier for a compiled-graph content hash. */
    public static Identifier shaderId(String contentHash) {
        return Identifier.fromNamespaceAndPath(Kilagraph.MODID, "generated/" + contentHash);
    }

    /** Register the vertex + fragment GLSL for a generated shader id (idempotent). */
    public static void register(Identifier id, String vertexSource, String fragmentSource) {
        SOURCES.put(new Key(id, ShaderType.VERTEX), vertexSource);
        SOURCES.put(new Key(id, ShaderType.FRAGMENT), fragmentSource);
    }

    /** Whether the id belongs to KilaGraph's generated namespace+path. */
    public static boolean isGenerated(Identifier id) {
        return id != null && Kilagraph.MODID.equals(id.getNamespace()) && id.getPath().startsWith("generated/");
    }

    /** Drop both stage sources for a generated shader id (called when no material references it). */
    public static void unregister(Identifier id) {
        SOURCES.remove(new Key(id, ShaderType.VERTEX));
        SOURCES.remove(new Key(id, ShaderType.FRAGMENT));
    }

    /** The GLSL source for a generated shader id+type, or {@code null} if unknown. */
    @Nullable
    public static String get(Identifier id, ShaderType type) {
        return SOURCES.get(new Key(id, type));
    }
}
