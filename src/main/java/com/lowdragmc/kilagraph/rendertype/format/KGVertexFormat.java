package com.lowdragmc.kilagraph.rendertype.format;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a real {@link VertexFormat} from an ordered list of {@link KGVertexElement} keys (a graph's
 * {@code Settings.vertexFormatElements()}). The attribute <b>name</b> the builder records is the
 * element's {@link KGVertexElement#attribName()}, which is exactly the name the generated GLSL declares
 * its {@code in} by — so the pipeline layout and the shader always agree.
 *
 * <p>Results are cached by key list, padded (via strides) to each attribute's alignment and a 4-byte vertex
 * size, and — when the composition matches a stock {@link DefaultVertexFormat} — that shared constant is
 * returned instead of a fresh-but-equal instance (so Minecraft's own immediate buffers/optimizations apply).</p>
 *
 * <p><b>Client only</b> — references {@code com.mojang.blaze3d}. Reached only from the render-thread
 * factory and editor preview, never from the dedicated server.</p>
 */
public final class KGVertexFormat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<List<String>, VertexFormat> CACHE = new ConcurrentHashMap<>();

    /** Stock formats we reuse when a composition matches them exactly (only canonically ordered ones can). */
    private static final List<VertexFormat> KNOWN = List.of(
            DefaultVertexFormat.BLOCK,
            DefaultVertexFormat.ENTITY,
            DefaultVertexFormat.POSITION,
            DefaultVertexFormat.POSITION_COLOR,
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            DefaultVertexFormat.POSITION_TEX,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH,
            DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

    private KGVertexFormat() {}

    /** The (cached) {@link VertexFormat} for an ordered list of {@link KGVertexElement} keys. */
    public static VertexFormat of(List<String> elementKeys) {
        return CACHE.computeIfAbsent(List.copyOf(elementKeys), KGVertexFormat::build);
    }

    /** The {@link GpuFormat} an element names, or {@code null} when it names no real constant. */
    @Nullable
    private static GpuFormat gpuFormatOf(KGVertexElement element) {
        try {
            return GpuFormat.valueOf(element.gpuFormat());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static VertexFormat build(List<String> elementKeys) {
        record Attribute(String name, GpuFormat format) {}
        List<Attribute> attributes = new ArrayList<>(elementKeys.size());
        var seen = new HashSet<String>();
        for (String key : elementKeys) {
            KGVertexElement desc = KGVertexElements.get(key);
            if (desc == null) continue;
            // A format may not carry the same attribute name twice (the editor can transiently offer one
            // element twice) — keep the first.
            if (!seen.add(desc.attribName())) continue;
            GpuFormat format = gpuFormatOf(desc);
            if (format == null) {
                // The generated vertex shader declares an `in` for EVERY settings element (it has no access
                // to blaze3d), so dropping one here leaves that `in` without an attribute: OpenGL silently
                // reads garbage and Vulkan refuses the pipeline. Say it loudly either way.
                LOGGER.error("[KilaGraph] vertex element '{}' (attribute {}) names no GpuFormat '{}' — "
                                + "skipping it, so the shader's `in {} {}` will be unbound.",
                        key, desc.attribName(), desc.gpuFormat(), desc.glslType(), desc.attribName());
                continue;
            }
            attributes.add(new Attribute(desc.attribName(), format));
        }
        if (attributes.isEmpty()) {
            LOGGER.warn("[KilaGraph] empty/unknown vertex format {} — falling back to ENTITY", elementKeys);
            return DefaultVertexFormat.ENTITY;
        }

        // Widen each stride so the next attribute is aligned (and the vertex size a multiple of 4).
        int[] strides = new int[attributes.size()];
        int offset = 0;
        for (int i = 0; i < attributes.size(); i++) {
            int size = attributes.get(i).format().blockSize();
            int next = offset + size;
            int aligned = i + 1 < attributes.size()
                    ? Mth.roundToward(next, attributes.get(i + 1).format().byteAlignment())
                    : Mth.roundToward(next, 4);
            strides[i] = aligned - offset;
            offset = aligned;
        }

        VertexFormat built;
        try {
            VertexFormat.Builder builder = VertexFormat.builder(0);
            for (int i = 0; i < attributes.size(); i++) {
                Attribute attribute = attributes.get(i);
                builder.addAttribute(attribute.name(), strides[i], attribute.format());
            }
            built = builder.build();
        } catch (RuntimeException e) {
            LOGGER.warn("[KilaGraph] invalid vertex format {} ({}) — falling back to ENTITY", elementKeys, e.getMessage());
            return DefaultVertexFormat.ENTITY;
        }
        for (VertexFormat known : KNOWN) {
            if (known.equals(built)) return known;
        }
        return built;
    }
}
