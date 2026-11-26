package com.lowdragmc.kilagraph.blueprint.nodes.compare;

import com.lowdragmc.kilagraph.graph.type.EnumTypes;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code type} option Equals and Not Equals share. Unset — the default, and what a graph saved before the option
 * existed reads as — the inputs are typed exactly as they always were and take any two values. Set, every input is
 * that type, so an unwired one gets the type's own editor (an enum's dropdown) to compare against.
 *
 * <p>Only the ports follow it. The comparison is the same either way, so evaluation never reads it.</p>
 */
public final class ComparedType {

    public static final String OPTION = "type";

    private ComparedType() {}

    public static void define(IOptionDefinitionContext context, INode node) {
        context.addOption(OPTION, String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOptionOrAny(() -> EnumTypes.typesAndEnums(node)))
                .build();
    }

    /** The type picked, or null for any. */
    @Nullable
    public static TypeHandle of(@Nullable String id) {
        if (id == null || id.isEmpty() || id.equals(TypeHandles.UNKNOWN.getIdentification())) return null;
        return TypeHandle.create(id);
    }

    /**
     * An input of the picked type. An enum with no default of its own starts at its first value, set on this port
     * alone: giving the type a default would change every other unwired port of it, some of which read null as "not
     * given".
     */
    public static void addInput(IPortDefinitionContext context, INode node, String portId, TypeHandle type) {
        var port = context.addInputPort(portId, type);
        Object first = EnumTypes.firstValue(type);
        if (first == null) return;
        port.withDefaultValue(first);
        // A port's default reaches only a constant made for it. An existing one is carried over to the new type with
        // its value, and Equals' untyped input holds an empty one, which would come over as null: the dropdown showing
        // the first value while the input hands on null. Let an empty one of another type go, so this one is made.
        if (node.getNodeModel() instanceof NodeModel model
                && model.getInputConstantsById().get(portId) instanceof Constant held
                && held.getValue() == null
                && !type.equals(held.getTypeHandle())) {
            model.getInputConstantsById().remove(portId);
        }
    }
}
