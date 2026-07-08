package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes.CurveValue;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.function.Supplier;

/**
 * The editor configurator for a {@code CURVE} value ({@link CurveValue}). Registered via
 * {@code TypeHandleHelpers.setCustomConfigurable(CURVE, …)}; client-only, loaded lazily so the
 * compiler/headless path never touches it.
 *
 * <p>One row: an interactive curve strip ({@link CurveValueConfigurator} — a preview polyline that opens
 * the draggable {@link CurveSelector} with key points, tangents and the lower/upper remap bounds). Each
 * edit stores a fresh deep-copied {@link CurveValue} (the working state never aliases the stored value).</p>
 */
public final class CurveConfigurator {
    private CurveConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        Supplier<CurveValue> get = () -> vc.getValue() instanceof CurveValue c ? c : CurveValue.defaultValue();

        var curve = new CurveValueConfigurator("",
                () -> get.get().copy(),
                c -> vc.setValue(c.copy()),
                CurveValue.defaultValue(), false);

        return IConfigurable.create(group -> group.addConfigurators(curve));
    }
}
