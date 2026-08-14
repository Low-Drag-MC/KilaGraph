package com.lowdragmc.kilagraph.blueprint.nodes.map;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NodeAttribute(name = "map_remove", group = "map", graphTypes = BlueprintGraph.class)
public class MapRemoveNode extends AnnotatedNode {
    @InputPort public Map<?, ?> map = Map.of();
    @OutputPort public Map<?, ?> out;
@Override protected void onDefineExtraOptions(IOptionDefinitionContext ctx) {
        ctx.addOption("keyType", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("key", current());
    }

    @Override public void evaluate(EvalContext ctx) {
        Map<?, ?> src = ctx.getInput("map", Map.class, Map.of());
        Object k = ctx.getInputRaw("key");
        Map<Object, Object> result = new LinkedHashMap<>(src);
        result.remove(k);
        ctx.setOutput("out", result);
    }

    private TypeHandle current() {
        var opt = getNodeOptionById("keyType");
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
