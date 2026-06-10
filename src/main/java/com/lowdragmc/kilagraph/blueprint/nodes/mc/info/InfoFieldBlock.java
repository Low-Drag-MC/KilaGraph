package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.graph.core.IGraphEvaluable;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.MemberInfoRegistry;
import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.util.KGSearchConfigurators;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

/**
 * A reader block inside an {@link InfoContextNode}. It selects one member (field or no-arg getter)
 * of the parent context's {@code target} via the {@code field} option and exposes it on a single
 * {@code value} output, typed by the selected member's return type.
 *
 * <p>The block reads the parent context's {@code target} input (not its own — it has no data input),
 * so several blocks in one context all read the same target. Available members come from
 * {@link MemberInfoRegistry} scoped to the parent's {@link InfoContextNode#targetType()}. Null-safe:
 * a null target / unknown key / no parent yields a null output.</p>
 */
@NodeAttribute(name = "info_field", group = "mc_info", graphTypes = com.lowdragmc.kilagraph.blueprint.BlueprintGraph.class)
public class InfoFieldBlock extends BlockNode implements IGraphEvaluable {

    @Override
    public Component getDisplayName() {
        return Component.literal("Info Field");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);
        context.addOption("field", String.class)
                .withDefaultValue("")
                .withConfigurable(KGSearchConfigurators.memberKeyPickerOption(this::memberKeys))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addOutputPort("value", handleForKey(currentKey()));
    }

    @Override
    public void evaluate(EvalContext ctx) {
        Class<?> targetClass = parentTargetClass();
        Object target = parentTargetValue(ctx);
        String key = ctx.getOption("field", String.class, "");
        ctx.setOutput("value", read(targetClass, target, key));
    }

    // ---- parent access ---------------------------------------------------------------------

    private InfoContextNode<?> parentContext() {
        return getContextNode() instanceof InfoContextNode<?> ic ? ic : null;
    }

    private Class<?> parentTargetClass() {
        var pc = parentContext();
        return pc == null ? null : pc.targetType();
    }

    /** Pull the parent context's {@code target} input value through the executor. */
    private Object parentTargetValue(EvalContext ctx) {
        var blockModel = getBlockNodeModel();
        if (blockModel == null) return null;
        var contextModel = blockModel.getContextNodeModel();
        if (contextModel == null) return null;
        PortModel targetPort = contextModel.getInputsById().get("target");
        return targetPort == null ? null : ctx.getExecutor().pullInputValue(targetPort);
    }

    // ---- member resolution -----------------------------------------------------------------

    private Collection<String> memberKeys() {
        Class<?> tc = parentTargetClass();
        return tc == null ? List.of() : MemberInfoRegistry.membersFor(tc).keySet();
    }

    private String currentKey() {
        var opt = getNodeOptionById("field");
        if (opt == null) return "";
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        return raw == null ? "" : raw.toString();
    }

    private TypeHandle handleForKey(String key) {
        Class<?> tc = parentTargetClass();
        if (tc == null || key == null || key.isEmpty()) return TypeHandles.UNKNOWN;
        var member = MemberInfoRegistry.member(tc, key);
        return member == null ? TypeHandles.UNKNOWN : KGTypeHandles.handleFor(member.returnType());
    }

    private Object read(Class<?> targetClass, Object target, String key) {
        if (targetClass == null || target == null || key == null || key.isEmpty()) return null;
        var member = MemberInfoRegistry.member(targetClass, key);
        return member == null ? null : member.getter().apply(target);
    }
}
