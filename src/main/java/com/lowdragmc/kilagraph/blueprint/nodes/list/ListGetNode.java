package com.lowdragmc.kilagraph.blueprint.nodes.list;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
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
 * Reads element {@code index} from {@code list}. The {@code elementType} option (search picker
 * over {@code graph.getSupportTypes()}) drives the {@code value} output's TypeHandle and a
 * runtime {@code instanceof} check.
 */
@NodeAttribute(name = "list_get", group = "list", graphTypes = BlueprintGraph.class)
public class ListGetNode extends AnnotatedNode {

    @InputPort public List<?> list = List.of();
    @InputPort public int     index = 0;
@Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption("type", String.class)
                .withDefaultValue(TypeHandles.UNKNOWN.getIdentification())
                .withConfigurable(KGSearchConfigurators.typeHandlePickerOption(this::supportedTypes))
                .build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {
        context.addOutputPort("value", currentElementType());
    }

    @Override
    public void evaluate(EvalContext ctx) {
        List<?> l = ctx.getInput("list", List.class, List.of());
        int i = ctx.getInt("index", 0);
        if (i < 0 || i >= l.size()) {
            throw new IndexOutOfBoundsException("ListGet index " + i + " out of bounds (size " + l.size() + ")");
        }
        Object v = l.get(i);

        TypeHandle t = currentElementType();
        if (v != null && !t.equals(TypeHandles.UNKNOWN)
                && t.resolve() instanceof Class<?> expected && !expected.isInstance(v)) {
            throw new TypeMismatchException("ListGet element " + i + " is " + v.getClass().getName()
                    + ", expected " + expected.getName());
        }
        ctx.setOutput("value", v);
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
