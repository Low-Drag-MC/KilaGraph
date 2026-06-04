package com.lowdragmc.kilagraph.blueprint.nodes.map;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link LinkedHashMap} from N key/value input pairs. {@code keyType}/{@code valueType}
 * options drive both port TypeHandles.
 */
@NodeAttribute(name = "map_create", group = "map", graphTypes = BlueprintGraph.class)
public class MapCreateNode extends AnnotatedNode {
    @Option public int inputs = 1;
    @OutputPort public Map<?, ?> out;

    @Override public Component getDisplayName() { return Component.literal("Map Create"); }

    @Override protected void onDefineExtraOptions(IOptionDefinitionContext ctx) {
        ctx.addOption("keyType", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
        ctx.addOption("valueType", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        int n = Math.max(1, optionValue("inputs", Integer.class, inputs));
        TypeHandle kt = current("keyType");
        TypeHandle vt = current("valueType");
        for (int i = 1; i <= n; i++) {
            ctx.addInputPort("key" + i, kt);
            ctx.addInputPort("value" + i, vt);
        }
    }

    @Override public void evaluate(EvalContext ctx) {
        int n = Math.max(1, ctx.getOption("inputs", Integer.class, inputs));
        Map<Object, Object> result = new LinkedHashMap<>();
        for (int i = 1; i <= n; i++) {
            Object k = ctx.getInput("key" + i).orElse(null);
            Object v = ctx.getInput("value" + i).orElse(null);
            if (k != null) result.put(k, v);
        }
        ctx.setOutput("out", result);
    }

    private TypeHandle current(String optionId) {
        var opt = getNodeOptionById(optionId);
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
