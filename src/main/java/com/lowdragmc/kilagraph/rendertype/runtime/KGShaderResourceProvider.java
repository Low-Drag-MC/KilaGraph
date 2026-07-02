package com.lowdragmc.kilagraph.rendertype.runtime;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link ResourceProvider} that serves a KilaGraph-generated core shader's
 * {@code shaders/core/<path>.json} / {@code .vsh} / {@code .fsh} (produced by {@link KGShaderManifest} and the GLSL
 * compiler) so the compiled graph can be handed straight to {@code new ShaderInstance(provider, location, format)}
 * without shipping the shader as a real asset.
 *
 * <p>Every other request — notably the {@code #moj_import <...>} include files vanilla's
 * {@link com.mojang.blaze3d.shaders.Program} resolves while compiling the {@code .vsh}/{@code .fsh} — is delegated
 * to {@code delegate} (normally {@code Minecraft.getInstance().getResourceManager()}), which owns the vanilla
 * {@code shaders/include/*.glsl} and any KilaGraph-shipped includes. So — unlike the interim raw-{@code ShaderProgram}
 * path in {@link RenderTypeFactory} — the includes are <b>not</b> inlined here; vanilla resolves them normally.</p>
 */
public final class KGShaderResourceProvider implements ResourceProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ResourceLocation, byte[]> generated = new HashMap<>();
    private final ResourceProvider delegate;
    private final PackResources source;

    /**
     * @param shaderLocation the synthetic shader id (e.g. {@code kilagraph:generated/<hash>}); the served files are
     *                       {@code <ns>:shaders/core/<path>.{json,vsh,fsh}}, matching what {@code ShaderInstance} and
     *                       {@code Program.getOrCreate} look up for this location.
     * @param delegate       fallback provider for everything else (includes) — usually the game resource manager.
     */
    public KGShaderResourceProvider(ResourceLocation shaderLocation, String jsonSource, String vertexSource,
                                    String fragmentSource, ResourceProvider delegate) {
        this.delegate = delegate;
        this.source = new SyntheticPack(shaderLocation.toString());
        put(shaderLocation, ".json", jsonSource);
        put(shaderLocation, ".vsh", vertexSource);
        put(shaderLocation, ".fsh", fragmentSource);
    }

    private void put(ResourceLocation shaderLocation, String extension, String sourceText) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(shaderLocation.getNamespace(),
                "shaders/core/" + shaderLocation.getPath() + extension);
        generated.put(loc, sourceText.getBytes(StandardCharsets.UTF_8));
    }

    /** As {@link #createShaderInstance(CompiledShaderGraph, VertexFormat, Set)} with no {@code #define}s. */
    @Nullable
    public static LDShaderInstance createShaderInstance(CompiledShaderGraph compiled, VertexFormat format) {
        return createShaderInstance(compiled, format, Set.of());
    }

    /**
     * Build an {@link LDShaderInstance} from a compiled graph: generates the {@code .json} manifest with
     * {@link KGShaderManifest}, wraps it plus the compiled {@code .vsh}/{@code .fsh} in a
     * {@link KGShaderResourceProvider} (delegating includes to the game resource manager), and hands it to
     * {@code LDShaderInstance.create(provider, id, format, defines)}. The synthetic id is
     * {@code kilagraph:generated/<contentHash>}.
     *
     * <p>We build an {@link LDShaderInstance} (not a plain {@code ShaderInstance}) so the generated shader gets the
     * LDLib2 features: {@code #define} injection (via {@code LDProgramDefineManager}/{@code ProgramMixin} — the
     * given {@code defines} are prepended after {@code #version} at compile), dynamic uniforms/samplers (through an
     * {@code LDShaderHolder}) and the optional geometry stage.</p>
     *
     * <p>Must run on the render thread (it compiles/links GL programs). Returns {@code null} — logging the sources —
     * if the graph has stage errors or the GLSL fails to compile/link, so callers can keep their last good shader.</p>
     */
    @Nullable
    public static LDShaderInstance createShaderInstance(CompiledShaderGraph compiled, VertexFormat format, Set<String> defines) {
        if (compiled.hasStageErrors()) {
            for (var e : compiled.stageErrors()) LOGGER.warn("[KilaGraph] stage error: {}", e.message());
            return null;
        }
        ResourceLocation id = DynamicShaderSourceRegistry.shaderId(compiled.contentHash());
        String json = KGShaderManifest.json(compiled, id.toString());
        String vsh = compiled.vertexSource();
        String fsh = compiled.fragmentSource();
        var provider = new KGShaderResourceProvider(id, json, vsh, fsh, Minecraft.getInstance().getResourceManager());
        try {
            return LDShaderInstance.create(provider, id, format, defines);
        } catch (Throwable e) {
            LOGGER.warn("[KilaGraph] ShaderInstance build failed for {}: {}\n--- MANIFEST ---\n{}\n--- VERTEX ---\n{}\n--- FRAGMENT ---\n{}",
                    id, e.getMessage(), json, vsh, fsh);
            return null;
        }
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        byte[] bytes = generated.get(location);
        if (bytes != null) {
            return Optional.of(new Resource(source, () -> new ByteArrayInputStream(bytes)));
        }
        return delegate.getResource(location);
    }

    /**
     * Minimal {@link PackResources} so {@link Resource#sourcePackId()} resolves during shader compile
     * ({@code Program.getOrCreate} passes it as the source name for error messages). Every other method is unused
     * by the {@code ShaderInstance} / {@code Program} load path.
     */
    private record SyntheticPack(String id) implements PackResources {
        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
            return null;
        }

        @Override
        public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of();
        }

        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return new PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty());
        }

        @Override
        public void close() {
        }
    }
}
