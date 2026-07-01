package com.lowdragmc.kilagraph.rendertype.runtime;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves {@code #moj_import <minecraft:fog.glsl>} directives in generated shader source the same way
 * {@code ShaderManager} does for asset shaders, inlining the imported {@code shaders/include/*.glsl}
 * files and merging {@code #version} lines via Mojang's {@link GlslPreprocessor}.
 *
 * <p>Necessary because the GL device receives whatever {@code ShaderManager.getShader} returns and
 * compiles it directly — the driver does not understand {@code #moj_import}. Asset shaders are
 * preprocessed at load; our generated shaders must be preprocessed here before registration.</p>
 */
public final class GlslImportProcessor {

    private GlslImportProcessor() {}

    /** Inline all imports in {@code source}, returning driver-ready GLSL. Must run with resources loaded. */
    public static String process(String source) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            private final Set<ResourceLocation> imported = new HashSet<>();

            @Override
            @Nullable
            public String applyImport(boolean isRelative, String path) {
                ResourceLocation location;
                try {
                    // Generated shaders only use absolute imports, e.g. <minecraft:fog.glsl>.
                    location = ResourceLocation.parse(path).withPrefix("shaders/include/");
                } catch (RuntimeException e) {
                    return "#error " + e.getMessage();
                }
                if (!imported.add(location)) {
                    return null; // already imported — emit nothing (matches vanilla)
                }
                Optional<Resource> resource = resources.getResource(location);
                if (resource.isEmpty()) {
                    return "#error missing GLSL import " + location;
                }
                try (Reader reader = resource.get().openAsReader()) {
                    return readFully(reader);
                } catch (IOException e) {
                    return "#error " + e.getMessage();
                }
            }
        };
        return String.join("", preprocessor.process(source));
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
