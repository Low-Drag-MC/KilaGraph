package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.lowdraglib2.LDLib2Registries;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Read access to LDLib2's {@code ldlib2:ui_element} registry — the same table that maps an XML tag
 * name to an element class.
 *
 * <p>Everything here is a thin wrapper, but a wrapper worth having: the registry's value type is
 * {@code AutoRegistry.Holder<LDLRegister, UIElement, Supplier<UIElement>>}, which is unpleasant to
 * spell at every call site, and the "registry name" that a node option stores is the same string
 * that an XML tag uses. Keeping both facts in one place is what lets
 * {@code ldlib2_ui_element_new} and {@code ldlib2_ui_element_from_xml} agree on what a type name
 * means.</p>
 */
public final class UIElements {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The registry name of the plain container element — the sane default for a picker. */
    public static final String DEFAULT = "element";

    /** One registered element type, flattened to what a picker and a node need. */
    public record Entry(String name, String group, Class<? extends UIElement> clazz,
                        Supplier<UIElement> factory) {
        /** {@code "basic / button"}, or just {@code "button"} for an ungrouped type. */
        public String label() {
            return group.isEmpty() ? name : group + " / " + name;
        }
    }

    private UIElements() {}

    /** Every registered element type, grouped then alphabetical — the order a picker should list. */
    public static List<Entry> all() {
        var entries = new ArrayList<Entry>();
        for (var e : LDLib2Registries.UI_ELEMENTS.entries()) {
            AutoRegistry.Holder<LDLRegister, UIElement, Supplier<UIElement>> holder = e.getValue();
            if (holder == null) continue;
            entries.add(new Entry(e.getKey(), holder.annotation().group(), holder.clazz(), holder.value()));
        }
        entries.sort(Comparator.comparing(Entry::group).thenComparing(Entry::name));
        return entries;
    }

    @Nullable
    public static Entry find(String registryName) {
        if (registryName == null || registryName.isEmpty()) return null;
        var holder = LDLib2Registries.UI_ELEMENTS.get(registryName);
        if (holder == null) return null;
        return new Entry(registryName, holder.annotation().group(), holder.clazz(), holder.value());
    }

    /** The class registered under {@code registryName}, or {@code null}. */
    @Nullable
    public static Class<? extends UIElement> classOf(String registryName) {
        var entry = find(registryName);
        return entry == null ? null : entry.clazz();
    }

    /**
     * Builds one element of the named type.
     *
     * <p>Falls back to a plain {@link UIElement} rather than {@code null} when the name is unknown:
     * a graph that names a type from a mod which is no longer installed should lose that element's
     * appearance, not blow up the whole UI it was part of. The name is logged so the cause is
     * visible.</p>
     */
    public static UIElement create(String registryName) {
        var entry = find(registryName);
        if (entry == null) {
            if (registryName != null && !registryName.isEmpty() && !DEFAULT.equals(registryName)) {
                LOGGER.warn("Unknown ldlib2 ui element type '{}', falling back to a plain element", registryName);
            }
            return new UIElement();
        }
        UIElement element = entry.factory().get();
        return element == null ? new UIElement() : element;
    }
}
