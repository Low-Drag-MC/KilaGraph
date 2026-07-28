package com.lowdragmc.kilagraph.graph.util;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.InputOutputPortsNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assembles a node's description panel from its {@code kg.node.<name>.*} lang keys plus the
 * {@link INodeDescription} hooks. Graph-agnostic: the shader graph and the blueprint both route
 * {@link Node#createDescriptionUI()} here.
 *
 * <p>Every section is optional and only rendered when its keys exist, so a node is documented purely by
 * adding lang entries:</p>
 * <ul>
 *   <li>{@code .tooltip} — the summary line (also the node's hover tooltip)</li>
 *   <li>{@code .desc.1} … {@code .N} — body paragraphs</li>
 *   <li>{@code .in.<portId>} / {@code .out.<portId>} — per-port explanation; only documented ports are listed</li>
 *   <li>{@code .option.<id>.tooltip} and {@code .option.<id>.tooltip.<value>} — options and their choices</li>
 *   <li>{@code .example.1} … {@code .N} — a worked example</li>
 *   <li>{@code .note.1} … {@code .N} — caveats</li>
 * </ul>
 */
public final class NodeDescriptions {
    private NodeDescriptions() {}

    private static final String SECTION_PORTS = "kg.desc.section.ports";
    private static final String SECTION_OPTIONS = "kg.desc.section.options";
    /** Only the shader graph fills the code section today, hence the GLSL heading. */
    private static final String SECTION_CODE = "kg.desc.section.glsl";
    private static final String SECTION_EXAMPLE = "kg.desc.section.example";
    private static final String SECTION_NOTES = "kg.desc.section.notes";

    /**
     * Builds the panel, or returns {@code null} when the node has no documentation at all (which keeps the
     * side panel collapsed instead of showing a bare header).
     */
    @Nullable
    public static UIElement build(Node node) {
        var attribute = node.getClass().getAnnotation(NodeAttribute.class);
        if (attribute == null) return null;
        String base = "kg.node." + attribute.name();

        var ui = NodeDescriptionUI.create().header(node, attribute.group());
        ui.paragraphKey(base + ".tooltip");
        ui.paragraphSeries(base + ".desc");

        // The item library instantiates block nodes reflectively for their description, so they arrive
        // with no NodeModel — the model-driven sections simply don't apply to them.
        var model = node.getNodeModel() instanceof InputOutputPortsNodeModel m ? m : null;
        if (model != null) {
            appendPorts(ui, model, base);
            appendOptions(ui, model, base, node);
        }

        if (node instanceof INodeDescription described) {
            String code = described.codeExample();
            if (code != null && !code.isBlank()) {
                ui.section(SECTION_CODE).code(code);
            }
        }

        appendSeries(ui, base + ".example", SECTION_EXAMPLE);
        appendNotes(ui, base);

        if (node instanceof INodeDescription described) {
            described.appendDescription(ui);
        }
        return ui.hasContent() ? ui.build() : null;
    }

    /**
     * Inputs then outputs, in canvas order; a port without a documentation key is skipped. Inputs and
     * outputs use separate prefixes because a node may well have both an input and an output called
     * {@code value} (the shader graph's Custom varying blocks do).
     */
    private static void appendPorts(NodeDescriptionUI ui, InputOutputPortsNodeModel model, String base) {
        var documented = new ArrayList<Map.Entry<PortModel, String>>();
        collectPorts(documented, model.getInputsByDisplayOrder(), base + ".in.");
        collectPorts(documented, model.getOutputsByDisplayOrder(), base + ".out.");
        if (documented.isEmpty()) return;
        ui.section(SECTION_PORTS);
        for (var entry : documented) {
            ui.entry(entry.getKey().getDisplayName(), chipType(entry.getKey()),
                    Component.translatable(entry.getValue()));
        }
    }

    /** The type chip for a port, or {@code null} for an exec port — its arrow already says everything. */
    @Nullable
    private static TypeHandle chipType(PortModel port) {
        TypeHandle handle = port.getDataTypeHandle();
        return TypeHandles.EXECUTION_FLOW.equals(handle) ? null : handle;
    }

    private static void collectPorts(List<Map.Entry<PortModel, String>> out, List<PortModel> ports, String prefix) {
        for (PortModel port : ports) {
            String key = prefix + port.getName();
            if (LocalizationUtils.exist(key)) out.add(Map.entry(port, key));
        }
    }

    /** Options carrying a {@code .tooltip}, each followed by its choices when the node lists them. */
    private static void appendOptions(NodeDescriptionUI ui, InputOutputPortsNodeModel model, String base, Node node) {
        boolean sectionAdded = false;
        for (var option : model.getNodeOptions()) {
            String key = base + ".option." + option.getId() + ".tooltip";
            if (!LocalizationUtils.exist(key)) continue;
            if (!sectionAdded) {
                ui.section(SECTION_OPTIONS);
                sectionAdded = true;
            }
            ui.entry(option.getDisplayName(), null, Component.translatable(key));
            for (String choice : choicesOf(node, option.getId())) {
                String choiceKey = key + "." + choice;
                if (LocalizationUtils.exist(choiceKey)) {
                    ui.subEntry(Component.literal(choice), Component.translatable(choiceKey));
                }
            }
        }
    }

    private static List<String> choicesOf(Node node, String optionId) {
        return node instanceof INodeDescription described ? described.optionChoices(optionId) : List.of();
    }

    private static void appendNotes(NodeDescriptionUI ui, String base) {
        String prefix = base + ".note";
        if (!LocalizationUtils.exist(prefix + ".1")) return;
        ui.section(SECTION_NOTES);
        for (int i = 1; LocalizationUtils.exist(prefix + "." + i); i++) {
            ui.note(Component.translatable(prefix + "." + i));
        }
    }

    private static void appendSeries(NodeDescriptionUI ui, String prefix, String sectionKey) {
        if (!LocalizationUtils.exist(prefix + ".1")) return;
        ui.section(sectionKey);
        ui.paragraphSeries(prefix);
    }
}
