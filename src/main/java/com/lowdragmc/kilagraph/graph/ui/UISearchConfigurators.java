package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Search pickers for the {@code ldlib2_ui_*} node options whose value is a name from some registry:
 * an element type, a content property, an LSS property, an event type.
 *
 * <p>The shape is copied deliberately from {@link com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators}
 * — including the detail that every one of these reads the option's current value through
 * {@code Object} and {@code String.valueOf}. Calling the generic {@code IFieldValueConfigurable.getValue()}
 * in a position that expects a {@code String} lets javac pick the {@code char[]} overload of
 * {@code String.valueOf}, and the result is a {@code ClassCastException} at runtime rather than a
 * compile error. See {@code docs/CONVENTIONS.md} §6.</p>
 *
 * <p>All four are "soft" pickers: the stored value is a plain string and a node that cannot resolve
 * it degrades rather than throwing. That matters most for the event picker, where a custom event
 * type that no registry knows about is a legitimate thing for a graph to listen for.</p>
 */
public final class UISearchConfigurators {

    private UISearchConfigurators() {}

    // ---- element type ------------------------------------------------------------------------

    /** Picks a registered {@code ldlib2:ui_element} type; the stored value is its registry name. */
    public static ITypeConfigurable uiElementTypeOption() {
        return stringPicker(UIElements.DEFAULT, (word, handler) -> {
            for (var entry : UIElements.all()) {
                if (Thread.interrupted()) return;
                if (matches(word, entry.name(), entry.group())) handler.accept(entry.name());
            }
        }, name -> {
            var entry = UIElements.find(name);
            return entry == null ? name : entry.label();
        });
    }

    // ---- content property --------------------------------------------------------------------

    /**
     * Picks one {@code @Configurable} property of the element type named by {@code elementType}.
     *
     * <p>The candidate set follows the sibling option, so changing the element type on the node
     * re-populates the property list rather than leaving a stale key behind.</p>
     */
    public static ITypeConfigurable propertyOption(Supplier<String> elementType) {
        return stringPicker("", (word, handler) -> {
            for (var property : UIPropertyRegistry.propertiesOfRegistered(elementType.get()).values()) {
                if (Thread.interrupted()) return;
                if (matches(word, property.key(), property.display())) handler.accept(property.key());
            }
        }, key -> {
            var property = UIPropertyRegistry.propertiesOfRegistered(elementType.get()).get(key);
            return property == null ? key : property.display() + " (" + key + ")";
        });
    }

    // ---- LSS property ------------------------------------------------------------------------

    /** Picks an LSS property name out of {@link PropertyRegistry}; the value is the name as written in LSS. */
    public static ITypeConfigurable lssPropertyOption() {
        return stringPicker("", (word, handler) -> {
            for (var property : sortedLssProperties()) {
                if (Thread.interrupted()) return;
                if (matches(word, property)) handler.accept(property);
            }
        }, name -> name);
    }

    private static List<String> sortedLssProperties() {
        var names = new ArrayList<String>();
        for (var property : PropertyRegistry.all()) {
            if (property != null && property.name != null) names.add(property.name);
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    // ---- event type --------------------------------------------------------------------------

    /**
     * Picks a {@link UIEvents} constant. Free text is accepted too — {@code sendMessage}-style custom
     * events dispatched by another graph are not in any registry, and refusing them would make the
     * dispatch node useless.
     */
    public static ITypeConfigurable eventTypeOption() {
        return stringPicker(UIEvents.CLICK, (word, handler) -> {
            for (var type : EVENT_TYPES) {
                if (Thread.interrupted()) return;
                if (matches(word, type)) handler.accept(type);
            }
            // Anything the user typed that is not a known constant is still a valid event name.
            if (word != null && !word.isBlank() && !EVENT_TYPES.contains(word)) handler.accept(word);
        }, name -> name);
    }

    /**
     * The {@code UIEvents} constants, read once off the interface. A hand-maintained copy of the
     * list would be a second place to forget when LDLib2 adds an event; the interface holds nothing
     * but {@code public static final String} fields, so the scan cannot pick up anything else.
     */
    private static final List<String> EVENT_TYPES = scanEventTypes();

    private static List<String> scanEventTypes() {
        var types = new ArrayList<String>();
        for (Field f : UIEvents.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            try {
                if (f.get(null) instanceof String value) types.add(value);
            } catch (IllegalAccessException ignored) {
            }
        }
        types.sort(Comparator.naturalOrder());
        return List.copyOf(types);
    }

    // ---- plumbing ----------------------------------------------------------------------------

    /** True when {@code word} is empty or is a case-insensitive substring of any candidate text. */
    private static boolean matches(String word, String... texts) {
        if (word == null || word.isEmpty()) return true;
        String lower = word.toLowerCase();
        for (String text : texts) {
            if (text != null && text.toLowerCase().contains(lower)) return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface Candidates {
        void search(String word, IResultHandler<String> handler);
    }

    /**
     * Wraps a candidate source into the full {@link ITypeConfigurable} for a {@code String}-typed
     * option: a {@link SearchComponentConfigurator} that reads and writes the option's value.
     */
    private static ITypeConfigurable stringPicker(String defaultValue, Candidates candidates,
                                                  java.util.function.Function<String, String> label) {
        SearchComponentConfigurator.ISearchConfigurator<String> search =
                new SearchComponentConfigurator.ISearchConfigurator<>() {
                    @Override
                    public String defaultValue() {
                        return defaultValue;
                    }

                    @Override
                    public String resultText(@NotNull String value) {
                        return label.apply(value);
                    }

                    @Override
                    public void search(String word, IResultHandler<String> handler) {
                        candidates.search(word, handler);
                    }

                    @Override
                    public Component mapping(@NotNull String value) {
                        return Component.literal(label.apply(value));
                    }
                };
        return (vc, th) -> IConfigurable.create(group ->
                group.addConfigurator(new SearchComponentConfigurator<>(
                        "",
                        () -> read(vc, defaultValue),
                        v -> vc.setValue(v == null ? defaultValue : v),
                        search,
                        vc.forceUpdate())));
    }

    /** See the class javadoc: read through {@code Object}, never through the generic overload. */
    private static String read(IFieldValueConfigurable vc, String defaultValue) {
        Object value = vc.getValue();
        return value == null ? defaultValue : value.toString();
    }
}
