package com.lowdragmc.kilagraph.blueprint.nodes.optional;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.List;

/**
 * Returns {@code in} if non-null, otherwise {@code defaultValue}. Types of both inputs and the
 * output are driven by the {@code type} option (TypeHandle picker).
 */
@NodeAttribute(name = "optional_default", group = "optional", graphTypes = BlueprintGraph.class)
public class DefaultNode extends AnnotatedNode {
    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext ctx) {
        ctx.addOption("type", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        TypeHandle t = currentType();
        ctx.addInputPort("in", t);
        ctx.addInputPort("defaultValue", t);
        ctx.addOutputPort("out", t);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        ctx.setOutput("out", v != null ? v : ctx.getInputRaw("defaultValue"));
    }

    private TypeHandle currentType() {
        var opt = getNodeOptionById("type");
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
