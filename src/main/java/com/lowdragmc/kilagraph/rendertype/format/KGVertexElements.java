package com.lowdragmc.kilagraph.rendertype.format;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The registry of {@link KGVertexElement}s users can compose into a vertex format. Seeded with
 * Minecraft's built-in elements (Position, Color, UV0/UV1/UV2, Normal); other mods may
 * {@link #register} their own (typically alongside a {@code VertexFormatElement.register(findNextId(),…)}
 * call so {@link KGVertexElement#mcElementId()} resolves).
 *
 * <p>1.21.1 has no {@code LineWidth} vertex element (line width there is a render-state — a {@code LineWidth}
 * uniform + {@code LineStateShard} + the {@code rendertype_lines} shader's screen-space expansion — not a
 * per-vertex attribute), so it is deliberately not registered.</p>
 *
 * <p><b>Client-safe.</b> Holds no {@code com.mojang.blaze3d} types; the ids it stores are turned into
 * real {@code VertexFormatElement}s only when {@link KGVertexFormat} builds a format on the client.
 * Iteration order is registration order (built-ins first), which is what the Settings UI offers.</p>
 */
public final class KGVertexElements {

    private static final Map<String, KGVertexElement> BY_KEY = new LinkedHashMap<>();
    private static final Map<Integer, KGVertexElement> BY_MC_ID = new LinkedHashMap<>();

    // Minecraft built-ins (ids match VertexFormatElement.POSITION..NORMAL).
    public static final KGVertexElement POSITION = register(new KGVertexElement("position", "Position", "vec3", 0));
    public static final KGVertexElement COLOR = register(new KGVertexElement("color", "Color", "vec4", 1));
    public static final KGVertexElement UV0 = register(new KGVertexElement("uv0", "UV0", "vec2", 2));
    public static final KGVertexElement UV1 = register(new KGVertexElement("uv1", "UV1", "ivec2", 3));
    public static final KGVertexElement UV2 = register(new KGVertexElement("uv2", "UV2", "ivec2", 4));
    public static final KGVertexElement NORMAL = register(new KGVertexElement("normal", "Normal", "vec3", 5));

    private KGVertexElements() {}

    /** Register an element. Replaces any element with the same key; the last registration for a given
     * Minecraft id wins the reverse lookup. Returns the registered element for static-field convenience. */
    public static KGVertexElement register(KGVertexElement element) {
        BY_KEY.put(element.key(), element);
        BY_MC_ID.put(element.mcElementId(), element);
        return element;
    }

    @Nullable
    public static KGVertexElement get(String key) {
        return BY_KEY.get(key);
    }

    /** The element bound to a Minecraft {@code VertexFormatElement} id, or {@code null} — used when
     * mapping a built {@code VertexFormat}'s elements back to descriptors (e.g. for preview writing). */
    @Nullable
    public static KGVertexElement byMcId(int mcElementId) {
        return BY_MC_ID.get(mcElementId);
    }

    public static Collection<KGVertexElement> all() {
        return Collections.unmodifiableCollection(BY_KEY.values());
    }

    /** Ensures the static initializer has run so the built-ins are present. */
    public static void bootstrap() {}
}
