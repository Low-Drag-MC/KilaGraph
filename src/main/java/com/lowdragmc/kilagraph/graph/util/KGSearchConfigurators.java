package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.ITypeConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Reusable {@link SearchComponentConfigurator.ISearchConfigurator} factories for KilaGraph
 * options that need richer pickers than the default field configurator.
 */
public final class KGSearchConfigurators {

    private KGSearchConfigurators() {}

    /**
     * Search configurator for picking a {@link TypeHandle} from a dynamic candidate set
     * (typically {@code graph.getSupportTypes()}). Filters by identification or friendly name,
     * case-insensitive.
     */
    /**
     * Convenience: builds the full {@link ITypeConfigurable} that powers a TypeHandle picker for
     * a String-typed option whose value is a {@code TypeHandle.identification()}. Wraps
     * {@link #typeHandleSearch} in a {@link SearchComponentConfigurator}.
     *
     * <p>Reads the option's current value as a String (the type-handle id) and writes back the
     * picked handle's id. Defensive against the generic {@code IFieldValueConfigurable.getValue()}
     * overload-resolution gotcha by reading through {@code Object} + {@code toString()}.</p>
     */
    public static ITypeConfigurable typeHandlePickerOption(
            Supplier<Collection<TypeHandle>> candidatesSupplier) {
        SearchComponentConfigurator.ISearchConfigurator<TypeHandle> search = typeHandleSearch(candidatesSupplier);
        return (vc, th) -> com.lowdragmc.lowdraglib2.configurator.IConfigurable.create(group ->
                group.addConfigurator(new SearchComponentConfigurator<>(
                        "",
                        () -> readIdAsHandle(vc),
                        h -> vc.setValue(h == null ? TypeHandles.UNKNOWN.getIdentification() : h.getIdentification()),
                        search,
                        vc.forceUpdate()
                )));
    }

    private static TypeHandle readIdAsHandle(IFieldValueConfigurable vc) {
        Object v = vc.getValue();
        String id = v == null ? "" : v.toString();
        return id.isEmpty() ? TypeHandles.UNKNOWN : TypeHandle.create(id);
    }

    @MethodsReturnNonnullByDefault
    public static SearchComponentConfigurator.ISearchConfigurator<TypeHandle> typeHandleSearch(
            Supplier<Collection<TypeHandle>> candidatesSupplier) {
        return new SearchComponentConfigurator.ISearchConfigurator<>() {
            @Override
            public TypeHandle defaultValue() {
                return TypeHandles.UNKNOWN;
            }

            @Override
            public String resultText(@NotNull TypeHandle value) {
                return value.getFriendlyName();
            }

            @Override
            public void search(String word, com.lowdragmc.lowdraglib2.utils.search.IResultHandler<TypeHandle> handler) {
                Collection<TypeHandle> candidates = candidatesSupplier.get();
                if (candidates == null) return;
                String lower = word == null ? "" : word.toLowerCase();
                for (TypeHandle th : candidates) {
                    if (Thread.interrupted()) return;
                    if (th == null) continue;
                    String id = th.getIdentification() == null ? "" : th.getIdentification().toLowerCase();
                    String friendly = LocalizationUtils.format(th.getFriendlyName()).toLowerCase();
                    if (lower.isEmpty() || id.contains(lower) || friendly.contains(lower)) {
                        handler.accept(th);
                    }
                }
            }

            @Override
            public Component mapping(@NonNull TypeHandle value) {
                return Component.literal(value.getFriendlyName());
            }
        };
    }
}
