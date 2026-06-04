package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * Base class for KilaGraph nodes that declare their structure through annotated Java fields
 * ({@link InputPort}, {@link OutputPort}, {@link Option}, {@link ExecInputPort},
 * {@link ExecOutputPort}).
 *
 * <p>The framework reflects each field, registers the matching LDLib2 port/option, forwards
 * field + owner via {@code withFieldContext(...)} so LDLib2's {@code @Configurable} ecosystem
 * picks up UI metadata, and forwards any {@link CustomConfigurable} via {@code withConfigurable}.</p>
 *
 * <p>Node instances are pure definitions: they carry no per-evaluation state. All runtime state
 * lives in {@link EvalContext}, which makes the same node safely usable by multiple executors
 * (current or future async/parallel).</p>
 */
public abstract class AnnotatedNode extends Node {

    private NodeMetadata metadata;

    NodeMetadata metadata() {
        if (metadata == null) metadata = NodeMetadata.CACHE.computeIfAbsent(getClass(), NodeMetadata::scan);
        return metadata;
    }

    @Override
    public final void onDefineOptions(IOptionDefinitionContext context) {
        metadata().applyOptions(context, this);
        onDefineExtraOptions(context);
    }

    @Override
    public final void onDefinePorts(IPortDefinitionContext context) {
        metadata().applyPorts(context, this);
        onDefineDynamicPorts(context);
    }

    /** Hook for nodes that need options the annotation scan can't express. Default: no-op. */
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {}

    /**
     * Hook for nodes whose port shape depends on option values (e.g. variable input counts).
     * Default: no-op. Add ports via {@code context.addInputPort(...)} / {@code addOutputPort(...)}.
     */
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {}

    @Override
    public Component getDisplayName() {
        return Component.literal(getClass().getSimpleName());
    }

    /**
     * Pull-based evaluation entry point. Read inputs / options via {@code ctx}; write outputs via
     * {@code ctx.setOutput(...)}. The node instance itself must not be mutated for per-evaluation
     * state — keep it stateless aside from definition fields.
     *
     * <p>Default: no-op. Exec-only nodes (EntryNode, Noop) can ignore this; data and hybrid nodes
     * override it.</p>
     */
    public void evaluate(EvalContext ctx) {}

    /**
     * Execution-flow entry point. Called by {@link com.lowdragmc.kilagraph.graph.exec.GraphExecutor#executeFrom}
     * when the executor pops this node off its pending queue. The node reads inputs / options via
     * {@code ctx}, writes any data outputs via {@code ctx.setOutput(...)}, and calls
     * {@code ctx.flow(execOutputId)} zero or more times to advance.
     *
     * <p>Default: no-op. Data-only nodes ignore this; exec and hybrid nodes override it.</p>
     */
    public void execute(ExecContext ctx) {}

    /**
     * Convenience for {@link #onDefineDynamicPorts} / {@link #onDefineExtraOptions}: read the
     * current value of a node option (typed), falling back to {@code defaultIfMissing} when the
     * option doesn't exist or its value can't be coerced. Use this rather than the bare Java
     * field — the field tracks the *declared default*, while the option constant tracks the
     * editor's current value.
     */
    protected final <T> T optionValue(String optionId, Class<T> type, T defaultIfMissing) {
        var opt = getNodeOptionById(optionId);
        if (opt == null) return defaultIfMissing;
        Object raw = opt.tryGetValue(Object.class).result().orElse(null);
        T coerced = EvalContext.coerce(raw, type);
        return coerced != null ? coerced : defaultIfMissing;
    }
}
