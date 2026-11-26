package com.lowdragmc.kilagraph.blueprint.nodes.logic;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.type.EnumTypes;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.jetbrains.annotations.Nullable;

/**
 * One value of an enum, picked: which enum is searched from the ones the graph offers ({@link EnumTypes}), and the
 * value is a second option of that enum's own type, so its row is the enum's dropdown.
 *
 * <p>An option and not an input: a value to pick, not one to wire. Typing it by the enum is also what makes a new
 * enum's row replace the old one — the editor rebuilds an option row when its type changes, not when its value
 * does.</p>
 */
@NodeAttribute(name = "logic_enum", group = "logic", graphTypes = BlueprintGraph.class)
public class EnumNode extends AnnotatedNode {

    public static final String TYPE = "type";
    public static final String VALUE = "value";

    /** The node is only as wide as "Enum", which would cut an enum's name after a few letters. */
    private static final float PICKER_WIDTH = 110f;

    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption(TYPE, String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(() -> EnumTypes.of(this), PICKER_WIDTH))
                .build();
        TypeHandle type = chosenType();
        if (type != null) {
            var value = context.addOption(VALUE, type);
            // a default the type does not have is given here, on this option alone (see ComparedType#addInput)
            Object first = EnumTypes.firstValue(type);
            if (first != null) value.withDefaultValue(first);
            value.build();
        }
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        TypeHandle type = chosenType();
        context.addOutputPort("out", type == null ? TypeHandles.UNKNOWN : type);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption(VALUE, Object.class, null));
    }

    /** The enum picked, or null while none is. */
    @Nullable
    private TypeHandle chosenType() {
        String id = optionValue(TYPE, String.class, "");
        if (id == null || id.isEmpty()) return null;
        TypeHandle type = TypeHandle.create(id);
        return EnumTypes.isEnum(type) ? type : null;
    }
}
