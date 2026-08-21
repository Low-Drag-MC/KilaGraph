package com.lowdragmc.kilagraph.blueprint.nodes.convert;

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
 * Whether a value could be cast to a chosen type — {@code Cast}'s question, asked without the answer
 * being fatal.
 *
 * <h2>Why this has to exist alongside Cast</h2>
 * {@code Cast} throws {@link com.lowdragmc.kilagraph.graph.exec.TypeMismatchException} when the value
 * does not fit, which is the right behaviour for "promise me this is an X" but leaves a graph no way to
 * <em>ask</em>. Without this node the only way to find out is to attempt the cast and be killed by it,
 * which is not a control-flow primitive a blueprint author can use.
 *
 * <p>So this is the guard you put in front of a Cast, or in a Branch when a value may be one of several
 * things — the entity that may be a player, the wire that may be carrying a list.
 *
 * <h2>It answers the question Cast actually asks</h2>
 * Deliberately not a bare {@code Class.isInstance}: Cast tries {@code EvalContext.coerce} before
 * declaring failure, so a Float reaching an Int pin succeeds there. If this node reported
 * {@code isInstance} alone it would say false for exactly the cases Cast then accepts, and a graph
 * guarded by it would refuse work that would have succeeded. Both go through the same test.
 *
 * <p>A null value is false for every type. Java's {@code instanceof} says the same, and it keeps this
 * usable as a combined null-and-type check.
 */
@NodeAttribute(name = "convert_instanceof", group = "convert", graphTypes = BlueprintGraph.class)
public class InstanceOfNode extends AnnotatedNode {

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
        context.addOutputPort("out", TypeHandles.BOOL);
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Object v = ctx.getInputRaw("in");
        if (v == null) {
            ctx.setOutput("out", false);
            return;
        }
        TypeHandle target = currentTargetType();
        // UNKNOWN accepts anything, matching the wire rule — a graph that has not picked a type yet
        // should not read as "nothing is ever this type".
        if (target.equals(TypeHandles.UNKNOWN)) {
            ctx.setOutput("out", true);
            return;
        }
        if (!(target.resolve() instanceof Class<?> expected)) {
            ctx.setOutput("out", false);
            return;
        }
        ctx.setOutput("out", expected.isInstance(v) || EvalContext.coerce(v, expected) != null);
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
