package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

import java.util.ArrayList;
import java.util.List;

@NodeAttribute(name = "list_repeat", group = "list", graphTypes = BlueprintGraph.class)
public class ListRepeatNode extends AnnotatedNode {
    @InputPort public int count = 0;
    @OutputPort public List<?> out;

    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext ctx) {
        ctx.addOption("type", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", currentElementType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        int n = Math.max(0, ctx.getInt("count", 0));
        Object v = ctx.getInputRaw("value");
        List<Object> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) result.add(v);
        ctx.setOutput("out", result);
    }

    private TypeHandle currentElementType() {
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
