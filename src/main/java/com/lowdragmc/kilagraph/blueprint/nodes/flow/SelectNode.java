package com.lowdragmc.kilagraph.blueprint.nodes.flow;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
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
 * Ternary value selector: {@code out = cond ? ifTrue : ifFalse}. Pure data — both branches are
 * pulled regardless of {@code cond}; this is not control flow.
 *
 * <p>The {@code valueType} option (picked via a search component over
 * {@code graph.getSupportTypes()}) drives the {@code ifTrue} / {@code ifFalse} / {@code out}
 * TypeHandles. Wire-time compatibility is enforced by the graph model; runtime values are
 * coerced by {@code EvalContext} per the node's needs.</p>
 */
@NodeAttribute(name = "flow_select", group = "flow", graphTypes = BlueprintGraph.class)
public class SelectNode extends AnnotatedNode {

    @InputPort public boolean cond = false;
@Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption("type", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        TypeHandle t = currentValueType();
        context.addInputPort("ifTrue", t);
        context.addInputPort("ifFalse", t);
        context.addOutputPort("out", t);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        boolean c = ctx.getInput("cond", Boolean.class, false);
        Object value = c ? ctx.getInput("ifTrue").orElse(null)
                         : ctx.getInput("ifFalse").orElse(null);
        ctx.setOutput("out", value);
    }

    private TypeHandle currentValueType() {
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
