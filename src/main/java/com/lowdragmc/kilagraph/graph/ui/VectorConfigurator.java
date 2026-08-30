package com.lowdragmc.kilagraph.graph.ui;

import com.lowdragmc.kilagraph.graph.type.Vectors;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorSelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;

import java.util.List;

/**
 * The inline editor for a {@code VECTOR} port's constant: a width picker (2 / 3 / 4) followed by that
 * many component fields. Registered via {@code TypeHandleHelpers.setCustomConfigurable(VECTOR, …)};
 * client-only and loaded lazily, so the dedicated server never touches it.
 *
 * <p><b>What this replaces.</b> A VECTOR port resolves to {@code Vector3f}, so the default accessor
 * path would draw exactly three fields and there would be no way to author a Vector2 or Vector4
 * literal at all — you had to wire in a {@code vector_make2}/{@code vector_make4} or drag a typed
 * Constant node just to type four numbers. The width the user picks here is the width the constant
 * actually carries, and {@link Vectors#CODEC} is what makes it survive a save.</p>
 */
public final class VectorConfigurator {

    /** Component labels, in order. Lang keys so the fields read X/Y/Z/W rather than 0/1/2/3. */
    private static final String[] AXIS_KEYS = {"kg.vector.x", "kg.vector.y", "kg.vector.z", "kg.vector.w"};

    private static final List<Integer> WIDTHS = List.of(2, 3, 4);

    private VectorConfigurator() {
    }

    public static IConfigurable build(IFieldValueConfigurable vc) {
        return IConfigurable.create(group -> group.addConfigurator(
                new ConfiguratorSelectorConfigurator<>(
                        "kg.vector.width",
                        () -> Vectors.clampWidth(Vectors.components(vc.getValue()).length),
                        width -> vc.setValue(Vectors.carrier(
                                Vectors.resize(Vectors.components(vc.getValue()), width))),
                        Vectors.DEFAULT_WIDTH, true,
                        WIDTHS, String::valueOf,
                        (width, sub) -> {
                            // width comes from WIDTHS via the supplier above, which already clamped
                            for (int axis = 0; axis < width; axis++) {
                                sub.addConfigurator(componentField(vc, axis));
                            }
                        })));
    }

    /**
     * One component field.
     *
     * <p>Reads and writes go through the stored value every time rather than through a cached array:
     * the width picker rebuilds this group, and a field holding a snapshot from before the rebuild
     * would write a stale component back the next time it was edited.</p>
     */
    private static NumberConfigurator componentField(IFieldValueConfigurable vc, int axis) {
        return new NumberConfigurator(
                AXIS_KEYS[axis],
                () -> Vectors.at(Vectors.components(vc.getValue()), axis),
                value -> vc.setValue(Vectors.carrier(Vectors.withComponent(
                        Vectors.components(vc.getValue()), axis, value.floatValue()))),
                0f, vc.forceUpdate());
    }
}
