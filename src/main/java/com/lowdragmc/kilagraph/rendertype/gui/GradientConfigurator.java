package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.BlendMode;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.GradientValue;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.accessors.EnumAccessor;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The editor configurator for a {@code GRADIENT} value ({@link GradientValue}). Registered via
 * {@code TypeHandleHelpers.setCustomConfigurable(GRADIENT, …)}; client-only, loaded lazily so the
 * compiler/headless path never touches it.
 *
 * <p>Two rows: an interactive Unity-style gradient bar ({@link GradientColorConfigurator} — a preview
 * swatch that opens the draggable {@link GradientColorSelector}) editing the colour + alpha keys, and a
 * Blend/Fixed interpolation-mode dropdown. Each edit rebuilds the immutable {@link GradientValue} (the
 * gradient is deep-copied so the stored value never aliases the live-editing instance).</p>
 */
public final class GradientConfigurator {
    private GradientConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        Supplier<GradientValue> get = () -> vc.getValue() instanceof GradientValue g ? g : GradientValue.defaultValue();

        var gradient = new GradientColorConfigurator("",
                () -> get.get().gradient().copy(),
                g -> vc.setValue(new GradientValue(g.copy(), get.get().mode())),
                new GradientColor(0xFF000000, 0xFFFFFFFF), false);

        var mode = EnumAccessor.create(
                "kg.gradient.mode", List.of(BlendMode.values()),
                () -> get.get().mode(),
                m -> vc.setValue(get.get().withMode(m)),
                BlendMode.BLEND, true);

        return IConfigurable.create(group -> group.addConfigurators(gradient, mode));
    }
}
