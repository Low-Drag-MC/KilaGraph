package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.kilagraph.rendertype.nodes.logic.ExpressionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.ExpressionNode.ExpressionSpec;
import com.lowdragmc.kilagraph.rendertype.nodes.logic.ExpressionNode.PortSpec;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.TextAreaConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import dev.vfyjxf.taffy.style.FlexDirection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The editor configurator for an {@link ExpressionNode}'s spec (stored as a JSON STRING option). Layout: an
 * <b>Inputs</b> list and an <b>Outputs</b> list (each a row editor with a <b>name field</b> + a <b>type
 * dropdown</b>, add/remove/reorder) and a multi-line <b>GLSL Body</b> text area. Each edit rewrites the full
 * spec JSON, which re-defines the node's ports automatically (the option framework calls {@code defineNode}).
 *
 * <p>Client-only and referenced lazily from the node's {@code withConfigurable} lambda so the headless
 * compiler never loads it (mirrors {@link Sampler2DConfigurator}/{@link ChoiceConfigurator}). The type
 * dropdown offers {@link ExpressionNode#INPUT_TYPES} / {@link ExpressionNode#OUTPUT_TYPES}; the name is the
 * verbatim GLSL identifier used in the body, so avoid GLSL reserved words ({@code in}/{@code out}/{@code
 * float}/…) — a bad name surfaces as a compile error in the preview panel.</p>
 */
public final class ExpressionConfigurator {
    private ExpressionConfigurator() {}

    public static IConfigurable build(IFieldValueConfigurable vc) {
        Supplier<ExpressionSpec> get = () -> {
            Object v = vc.getValue();
            return v instanceof String s && !s.isEmpty() ? ExpressionNode.fromJson(s) : ExpressionNode.DEFAULT_SPEC;
        };

        var inputs = new ArrayConfiguratorGroup<PortSpec>("kg.logic.expression.inputs", false,
                () -> new ArrayList<>(get.get().inputs()),
                (g, s) -> portRow(g, s, ExpressionNode.INPUT_TYPES),
                false);
        inputs.setAddDefault(() -> new PortSpec(uniqueName("in", get.get().inputs()), "FLOAT"));
        inputs.setOnUpdate(list -> {
            var spec = get.get();
            vc.setValue(ExpressionNode.toJson(new ExpressionSpec(new ArrayList<>(list), spec.outputs(), spec.body())));
        });

        var outputs = new ArrayConfiguratorGroup<PortSpec>("kg.logic.expression.outputs", false,
                () -> new ArrayList<>(get.get().outputs()),
                (g, s) -> portRow(g, s, ExpressionNode.OUTPUT_TYPES),
                false);
        outputs.setAddDefault(() -> new PortSpec(uniqueName("out", get.get().outputs()), "FLOAT"));
        outputs.setOnUpdate(list -> {
            var spec = get.get();
            vc.setValue(ExpressionNode.toJson(new ExpressionSpec(spec.inputs(), new ArrayList<>(list), spec.body())));
        });

        // Inputs and Outputs sit side by side, each taking half the width.
        inputs.layout(l -> l.flex(1).widthAuto());
        outputs.layout(l -> l.flex(1).widthAuto());
        var ioRow = new ConfiguratorGroup("", false).hideTitle();
        ioRow.configuratorContainer(c -> c.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(2)));
        ioRow.addConfigurators(inputs, outputs);

        // The GLSL body is a collapsible group titled "GLSL Body" above a (label-less) text area.
        var bodyArea = new TextAreaConfigurator("",
                () -> get.get().body().split("\n", -1),
                lines -> {
                    var spec = get.get();
                    vc.setValue(ExpressionNode.toJson(new ExpressionSpec(spec.inputs(), spec.outputs(), String.join("\n", lines))));
                },
                ExpressionNode.DEFAULT_SPEC.body().split("\n", -1), false);
        var bodyGroup = new ConfiguratorGroup("kg.logic.expression.body", true);
        bodyGroup.addConfigurator(bodyArea);

        return IConfigurable.create(group -> group.addConfigurators(ioRow, bodyGroup));
    }

    /** One port row: a name text field + a type dropdown, both committing back via {@code set}. */
    private static Configurator portRow(Supplier<PortSpec> get, Consumer<PortSpec> set, List<String> types) {
        var row = new ConfiguratorGroup("", false);
        var name = new StringConfigurator("kg.logic.expression.name",
                () -> get.get().name(),
                n -> set.accept(new PortSpec(n, get.get().type())),
                "x", false);
        var type = new SelectorConfigurator<>("kg.logic.expression.type",
                () -> get.get().type(),
                t -> set.accept(new PortSpec(get.get().name(), t)),
                types.get(0), false, types, s -> s);
        row.addConfigurators(name, type);
        return row;
    }

    /** A {@code base}+N name not already used by {@code existing} (so a fresh row never duplicates/collides). */
    private static String uniqueName(String base, List<PortSpec> existing) {
        Set<String> used = new HashSet<>();
        for (PortSpec p : existing) used.add(p.name());
        for (int i = 1; ; i++) {
            String candidate = base + i;
            if (!used.contains(candidate)) return candidate;
        }
    }
}
