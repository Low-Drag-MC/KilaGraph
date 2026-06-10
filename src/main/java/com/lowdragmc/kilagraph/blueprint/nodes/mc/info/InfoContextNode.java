package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.BlockNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A "context" node that exposes a single {@code target} of type {@code T}. Reading a property of
 * the target is done by adding {@link InfoFieldBlock} blocks to this context — each block selects
 * one member and produces a typed output. This is the container half of the InfoNode design:
 * the context owns the shared {@code target} input; the blocks are the per-property readers.
 *
 * <p>Concrete subclasses (e.g. {@code ItemStackInfoNode}) only supply {@link #targetClass()} and a
 * {@code @NodeAttribute}. The accepted block type is fixed to {@link InfoFieldBlock} via
 * {@link #getSupportBlocks()}.</p>
 *
 * @param <T> the type whose properties the contained blocks read
 */
public abstract class InfoContextNode<T> extends ContextNode {

    /** The class whose properties this context's blocks read. Drives the {@code target} port. */
    protected abstract Class<T> targetClass();

    /** Public view of {@link #targetClass()} for the contained blocks. */
    public Class<?> targetType() {
        return targetClass();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(targetClass().getSimpleName() + " Info");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        var builder = context.addInputPort("target", KGTypeHandles.handleFor(targetClass()));
        // Wire-only context types (Level/Entity/…) have no accessor — drop the editor row + constant
        // (mirrors NodeMetadata's handling for annotated ports). Value-ish types (ItemStack/BlockPos)
        // keep their picker so a literal can be set on the context.
        if (!hasAccessor(targetClass())) builder.withoutConfigurator();
    }

    @Override
    public List<Class<? extends BlockNode>> getSupportBlocks() {
        return List.of(InfoFieldBlock.class);
    }

    private static boolean hasAccessor(Class<?> type) {
        try {
            AccessorRegistries.findByType(type);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
