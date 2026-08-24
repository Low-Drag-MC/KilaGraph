package com.lowdragmc.kilagraph.blueprint.nodes.convert;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.TypeMismatchException;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Strict downcast / "promise me this is of type X". Input is {@code UNKNOWN}; output's TypeHandle
 * is taken from the {@code targetType} option (picked via a search component over
 * {@code graph.getSupportTypes()}). Throws {@link TypeMismatchException} at evaluate time if the
 * upstream value doesn't fit the target type.
 *
 * <p>Fully imperative declaration — no field annotations. CastNode sits at the heart of KilaGraph's
 * UNKNOWN-bridging story and shapes its own ports/options to taste.</p>
 */
@NodeAttribute(name = "convert_cast", group = "convert", graphTypes = BlueprintGraph.class)
public class CastNode extends AnnotatedNode {
    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption("targetType", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        context.addInputPort("in", TypeHandles.UNKNOWN);
        context.addOutputPort("out", currentTargetType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        if (v == null) { ctx.setOutput("out", null); return; }
        TypeHandle t = currentTargetType();
        if (!t.equals(TypeHandles.UNKNOWN)
                && t.resolve() instanceof Class<?> expected && !expected.isInstance(v)) {
            // EvalContext.coerce style: try to coerce before declaring failure.
            Object coerced = EvalContext.coerce(v, expected);
            if (coerced == null) {
                throw new TypeMismatchException("Cast: value " + v.getClass().getName()
                        + " is not assignable to " + expected.getName());
            }
            ctx.setOutput("out", coerced);
            return;
        }
        ctx.setOutput("out", v);
    }

    private TypeHandle currentTargetType() {
        var opt = getNodeOptionById("targetType");
        if (opt == null) return TypeHandles.UNKNOWN;
        String id = opt.tryGetValue(String.class).result().map(String.class::cast).orElse(null);
        return id == null || id.isEmpty() ? TypeHandles.UNKNOWN : TypeHandle.create(id);
    }

    private List<TypeHandle> supportedTypes() {
        var model = getNodeModel() == null ? null : getNodeModel().getGraphModel();
        if (model instanceof GraphModel gm) return gm.getSupportTypes();
        return List.of();
    }
}
