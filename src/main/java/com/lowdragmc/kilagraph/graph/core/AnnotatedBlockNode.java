package com.lowdragmc.kilagraph.graph.core;

import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.util.INodeDescription;
import com.lowdragmc.kilagraph.graph.util.NodeDescriptions;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AnnotatedNode}, but for a block that lives inside a context node.
 *
 * <p>Same deal: declare ports and options as annotated fields and the framework registers them. The
 * only reason this is a separate class rather than a flag on {@code AnnotatedNode} is that LDLib2 splits
 * the hierarchy — a block has to extend {@link BlockNode} to get a parent context and an index, and
 * Java has no multiple inheritance. The two classes therefore share {@link NodeMetadata} and differ
 * only in their superclass.
 *
 * <p>A subclass still needs {@code @NodeAttribute} to be registered, and
 * {@code @UseWithContext(SomeContext.class)} to say which contexts will accept it. Without the second
 * one it is compatible with no context and can never be placed.
 */
public abstract class AnnotatedBlockNode extends BlockNode implements IGraphEvaluable, INodeDescription {

    private NodeMetadata metadata;

    private NodeMetadata metadata() {
        if (metadata == null) metadata = NodeMetadata.CACHE.computeIfAbsent(getClass(), NodeMetadata::scan);
        return metadata;
    }

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        NodeTooltipHelper.apply(nodeModel, getNodeTooltip());
    }

    /** The hover tooltip; by default the {@code kg.node.<name>.tooltip} the description panel uses. */
    protected @Nullable Component getNodeTooltip() {
        return NodeTooltipHelper.defaultTooltip(this);
    }

    @Override
    @Nullable
    public UIElement createDescriptionUI() {
        return NodeDescriptions.build(this);
    }

    @Override
    public Component getDisplayName() {
        var attribute = getClass().getAnnotation(NodeAttribute.class);
        return attribute == null
                ? Component.literal(getClass().getSimpleName())
                : Component.translatable(attribute.name());
    }

    @Override
    public final void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);
        metadata().applyOptions(context, this);
        onDefineExtraOptions(context);
    }

    @Override
    public final void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        metadata().applyPorts(context, this);
        onDefineDynamicPorts(context);
    }

    /** Hook for options the annotation scan cannot express. Default: no-op. */
    protected void onDefineExtraOptions(IOptionDefinitionContext context) {}

    /** Hook for a port shape that depends on option values. Default: no-op. */
    protected void onDefineDynamicPorts(IPortDefinitionContext context) {}

    @Override
    public void evaluate(EvalContext ctx) {}
}
