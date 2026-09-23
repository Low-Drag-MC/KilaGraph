package com.lowdragmc.kilagraph.rendertype.format;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The registry of {@link KGVertexElement}s users can compose into a vertex format. Seeded with
 * Minecraft's built-in elements (Position, Color, UV0/UV1/UV2, Normal, LineWidth); other mods may
 * {@link #register} their own.
 *
 * <p><b>Client-safe.</b> Holds no {@code com.mojang.blaze3d} types; the {@code GpuFormat} names it stores
 * are resolved only when {@link KGVertexFormat} builds a format on the client. Iteration order is
 * registration order (built-ins first), which is what the Settings UI offers.</p>
 */
public final class KGVertexElements {

    private static final Map<String, KGVertexElement> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, KGVertexElement> BY_ATTRIB = new LinkedHashMap<>();
    /** First-registration index per key: the canonical layout order. */
    private static final Map<String, Integer> ORDER = new LinkedHashMap<>();

    // Minecraft built-ins, with DefaultVertexFormat's GpuFormats.
    public static final KGVertexElement POSITION = register(new KGVertexElement("position", "Position", "vec3", "RGB32_FLOAT"));
    public static final KGVertexElement COLOR = register(new KGVertexElement("color", "Color", "vec4", "RGBA8_UNORM"));
    public static final KGVertexElement UV0 = register(new KGVertexElement("uv0", "UV0", "vec2", "RG32_FLOAT"));
    public static final KGVertexElement UV1 = register(new KGVertexElement("uv1", "UV1", "ivec2", "RG16_SINT"));
    public static final KGVertexElement UV2 = register(new KGVertexElement("uv2", "UV2", "ivec2", "RG16_SINT"));
    public static final KGVertexElement NORMAL = register(new KGVertexElement("normal", "Normal", "vec3", "RGBA8_SNORM"));
    public static final KGVertexElement LINE_WIDTH = register(new KGVertexElement("line_width", "LineWidth", "float", "R32_FLOAT"));

    /**
     * The reserved registry key for a per-vertex tangent. <b>Nothing registers it</b> — Minecraft has no
     * tangent vertex attribute, so the shader compiler derives a tangent basis instead (see
     * {@code ShaderGraphCompiler#tangentBasis(String)}). The key is reserved so a mod that <em>does</em> feed
     * its own geometry can opt in: register an element under this key and every tangent-reading node
     * (Tangent/Bitangent, the Tangent space of Position/View Direction/Transform, and any normal-map graph
     * built on them) switches from the derived basis to the real attribute, with no graph edits.
     *
     * <p>The contract: bind name {@code Tangent}; GLSL type {@code vec4} — {@code xyz} the unit tangent in
     * the same space as {@code Normal}, {@code w} the bitangent handedness ({@code ±1}, the glTF/Unity
     * convention) — or {@code vec3} for a right-handed-only tangent; a float {@code GpuFormat}. Note that
     * {@code BufferBuilder} only writes the built-in attributes, so the mod must upload such vertices itself.</p>
     */
    public static final String TANGENT_KEY = "tangent";

    /** The GLSL/{@code VertexFormat} binding name a {@link #TANGENT_KEY} element must use. */
    public static final String TANGENT_ATTRIB_NAME = "Tangent";

    private KGVertexElements() {}

    /** The registered per-vertex tangent element, or {@code null} when nothing supplies one (the default —
     *  the compiler then derives a basis). See {@link #TANGENT_KEY}. */
    @Nullable
    public static KGVertexElement tangent() {
        return get(TANGENT_KEY);
    }

    /** Register an element. Replaces any element with the same key; the last registration for a given
     * attribute name wins the reverse lookup. Returns the registered element for static-field convenience. */
    public static KGVertexElement register(KGVertexElement element) {
        BY_KEY.put(element.key(), element);
        BY_ATTRIB.put(element.attribName(), element);
        ORDER.computeIfAbsent(element.key(), k -> ORDER.size());
        return element;
    }

    /** Canonical layout position of a key (its first registration index); unknown keys sort last. */
    public static int orderOf(String key) {
        // ORDER survives unregister, so gate on the live registry.
        Integer order = BY_KEY.containsKey(key) ? ORDER.get(key) : null;
        return order == null ? Integer.MAX_VALUE : order;
    }

    /** Drop a previously {@link #register}ed element (both lookups). Returns the removed element, or
     *  {@code null} if the key was not registered. The built-ins can be removed too — don't. */
    @Nullable
    public static KGVertexElement unregister(String key) {
        KGVertexElement removed = BY_KEY.remove(key);
        if (removed != null) BY_ATTRIB.remove(removed.attribName(), removed);
        return removed;
    }

    @Nullable
    public static KGVertexElement get(String key) {
        return BY_KEY.get(key);
    }

    /** The element bound to a {@code VertexFormat} attribute name, or {@code null} — used when mapping a
     * built {@code VertexFormat}'s attributes back to descriptors (e.g. for preview writing). */
    @Nullable
    public static KGVertexElement byAttribName(String attribName) {
        return BY_ATTRIB.get(attribName);
    }

    public static Collection<KGVertexElement> all() {
        return Collections.unmodifiableCollection(BY_KEY.values());
    }

    /** Ensures the static initializer has run so the built-ins are present. */
    public static void bootstrap() {}
}
