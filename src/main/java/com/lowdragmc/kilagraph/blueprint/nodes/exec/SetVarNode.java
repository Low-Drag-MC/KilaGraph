package com.lowdragmc.kilagraph.blueprint.nodes.exec;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.GraphModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Writes {@code value} to the graph variable named {@code varName} (in
 * {@code EvaluationEnvironment.variables()}). The companion to Phase 1's
 * {@code GraphExecutor.runOutputs()} — written values participate in the OUTPUT-variable result
 * map. Reads are still served by Phase 1's {@code IVariableNode} path.
 *
 * <h2>It refers to a declaration, not to a spelling</h2>
 *
 * <p>Reading a variable is a {@code VariableNodeModel}: it holds the declaration's <b>uid</b>, takes
 * its pin type from the declaration, and follows a rename. Writing one used to be this node holding
 * a bare string, with an {@code UNKNOWN} value pin — so the same variable was two different kinds of
 * thing depending on which way the data went, and renaming it silently unhooked every write while
 * every read followed along. Both symptoms are the one cause.
 *
 * <p>So the uid is kept here too ({@link #UID_OPTION}), the name beside it as the readable
 * spelling, and the pin takes the declaration's type. {@link #syncDeclarations} writes all three
 * back before a save, which is where a rename is picked up.
 *
 * <p>⚠️ The <b>type is mirrored</b> ({@link #TYPE_OPTION}) rather than resolved on every define, and
 * that is load-order, not caching: deserialization builds the nodes before the variables exist, so a
 * node that asked the graph at that moment would type its pin {@code UNKNOWN} — and LDLib2 drops a
 * saved constant whose type no longer fits its pin, so the wire and the value on it would be gone by
 * the time the declarations arrived. The mirror is what a caller of a custom event keeps for the
 * same reason.
 */
@NodeAttribute(name = "exec_set_var", group = "exec", graphTypes = BlueprintGraph.class)
public class SetVarNode extends AnnotatedNode {

    /** The variable's name — what the runtime writes by, and what an author reads on the node. */
    public static final String NAME_OPTION = "varName";
    /** The declaration's uid: the identity a rename does not change. Hidden. */
    public static final String UID_OPTION = "varUid";
    /** The declared type's id, mirrored so a load can type the pin before the variables exist. Hidden. */
    public static final String TYPE_OPTION = "varType";

    @ExecInputPort public ExecutionFlow trigger;
    @ExecOutputPort public ExecutionFlow next;

    @Override
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {
        context.addOption(NAME_OPTION, TypeHandles.STRING)
                .withDisplayName(Component.empty())
                .withDefaultValue("")
                .withConfigurable((value, type) -> IConfigurable.create(group -> {
                    List<String> names = new ArrayList<>();
                    for (VariableDeclarationModelBase declaration : declarations()) {
                        names.add(declaration.getName());
                    }
                    String current = value.getValue() instanceof String name ? name : "";
                    if (names.isEmpty()) {
                        // no table to choose from — a subgraph being defined, or a graph with no
                        // variables yet. A text box still lets the name be typed, which is what
                        // this node always was.
                        group.addConfigurator(new StringConfigurator("graph.variable",
                                () -> current, v -> value.setValue(v == null ? "" : v), "", false));
                        return;
                    }
                    // ⚠️ Keep a name that is not in the list rather than snapping to the first: a
                    // graph loaded before its variables, or one whose variable was deleted, would
                    // otherwise be silently rewritten to write somewhere else.
                    if (!current.isEmpty() && !names.contains(current)) {
                        names.add(current);
                    }
                    group.addConfigurator(new SelectorConfigurator<>("graph.variable",
                            () -> current, name -> {
                                value.setValue(name == null ? "" : name);
                                rememberDeclaration(name);
                            }, "", false, names, Function.identity()));
                }))
                .build();
        context.addOption(UID_OPTION, TypeHandles.STRING).withDefaultValue("").withoutConfigurator().build();
        context.addOption(TYPE_OPTION, TypeHandles.STRING).withDefaultValue("").withoutConfigurator().build();
    }

    @Override
    protected void onDefineDynamicPorts(IPortDefinitionContext ctx) {
        ctx.addInputPort("value", valueType());
    }

    @Override
    public void execute(ExecContext ctx) {
        // setVariable rather than variables().put(name, getInputRaw("value")): the latter reads the
        // value as an Object, which boxes every number on the way out of the port table. This copies
        // it lane to lane, so writing a float allocates nothing.
        ctx.setVariable(ctx.getOption(NAME_OPTION, String.class, ""), "value");
        ctx.flow("next");
    }

    // ---- the declaration behind the name ----

    /** The graph this node is in, or null while it is being built outside one. */
    @Nullable
    private GraphModel graph() {
        var model = getNodeModel();
        return model == null ? null : model.getGraphModel();
    }

    /** This node's model, when it has the option table on it — null while it is being defined bare. */
    @Nullable
    private NodeModel model() {
        return getNodeModel() instanceof NodeModel node ? node : null;
    }

    /**
     * The variables this node can write: its own graph's. A subgraph runs on a store of its own
     * ({@code GraphExecutor.checkoutChild} hands it a cleared one), so its parent's names are not
     * writable from inside it and must not be offered.
     */
    private List<VariableDeclarationModelBase> declarations() {
        GraphModel graph = graph();
        return graph == null ? List.of() : graph.getGraphVariableModels();
    }

    /** The declaration this node writes: by uid, else by name — which is how an older save resolves. */
    @Nullable
    public VariableDeclarationModelBase declaration() {
        String uid = optionValue(UID_OPTION, String.class, "");
        String name = optionValue(NAME_OPTION, String.class, "");
        for (VariableDeclarationModelBase declaration : declarations()) {
            if (!uid.isEmpty() && uid.equals(declaration.getUid().toString())) {
                return declaration;
            }
        }
        if (name.isEmpty()) {
            return null;
        }
        for (VariableDeclarationModelBase declaration : declarations()) {
            if (name.equals(declaration.getName())) {
                return declaration;
            }
        }
        return null;
    }

    /** The value pin's type: the declaration's while it is reachable, else the mirror of it. */
    private TypeHandle valueType() {
        VariableDeclarationModelBase declaration = declaration();
        if (declaration != null && declaration.getDataTypeHandle() != null) {
            return declaration.getDataTypeHandle();
        }
        String mirrored = optionValue(TYPE_OPTION, String.class, "");
        return mirrored.isEmpty() ? TypeHandles.UNKNOWN : TypeHandle.create(mirrored);
    }

    /** Picking a name in the dropdown records which declaration it was, so a later rename follows. */
    private void rememberDeclaration(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        for (VariableDeclarationModelBase declaration : declarations()) {
            if (name.equals(declaration.getName())) {
                write(model(), UID_OPTION, declaration.getUid().toString());
                TypeHandle type = declaration.getDataTypeHandle();
                write(model(), TYPE_OPTION, type == null ? "" : type.getIdentification());
                return;
            }
        }
    }

    /**
     * Brings every {@code Set Var} in the graph back in step with the declarations before a save:
     * the name a rename changed, the uid an older save never had, the type mirror a load will need.
     * Called from {@code KGGraphModel.serializeNBT} — the same moment, and for the same reason, that
     * a custom event's callers write down the signature they mirror.
     */
    public static void syncDeclarations(GraphModel graphModel) {
        for (var model : graphModel.getNodeModels()) {
            if (!(model instanceof NodeModel node)
                    || !(node instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel custom)
                    || !(custom.getNode() instanceof SetVarNode setVar)) {
                continue;
            }
            VariableDeclarationModelBase declaration = setVar.declaration();
            if (declaration == null) {
                continue;
            }
            write(node, NAME_OPTION, declaration.getName());
            write(node, UID_OPTION, declaration.getUid().toString());
            TypeHandle type = declaration.getDataTypeHandle();
            write(node, TYPE_OPTION, type == null ? "" : type.getIdentification());
        }
    }

    /** Writes an option's constant without redefining the node — the caller is mid-serialize. */
    private static void write(@Nullable NodeModel node, String optionId, String value) {
        if (node == null) {
            return;
        }
        for (var option : node.getNodeOptions()) {
            if (!option.id.equals(optionId)) {
                continue;
            }
            var constant = node.getInputConstantsById().get(option.portModel.getUniqueName());
            if (constant != null && !value.equals(constant.getValue())) {
                constant.setValue(value);
            }
            return;
        }
    }

    /**
     * Points a freshly spawned node at a declaration — what the Get/Set drop menu does with the
     * variable that was dragged. Redefines the node, so its value pin comes up already typed.
     */
    public static void prefill(NodeModel node, VariableDeclarationModelBase declaration) {
        write(node, NAME_OPTION, declaration.getName());
        write(node, UID_OPTION, declaration.getUid().toString());
        TypeHandle type = declaration.getDataTypeHandle();
        write(node, TYPE_OPTION, type == null ? "" : type.getIdentification());
        node.defineNode();
    }
}
