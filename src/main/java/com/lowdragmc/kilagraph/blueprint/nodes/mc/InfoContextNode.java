package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.graph.type.KGTypeHandles;
import com.lowdragmc.kilagraph.graph.util.INodeDescription;
import com.lowdragmc.kilagraph.graph.util.NodeDescriptions;
import com.lowdragmc.kilagraph.graph.util.NodeTooltipHelper;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.ContextNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A context node holding one {@code target} of type {@code T}, whose properties are read by
 * {@link InfoPropertyBlock} blocks placed inside it.
 *
 * <h2>The shape, and why it is this shape</h2>
 * The context owns the {@code target} input; each block reads one property of it. So a graph drops
 * <em>one</em> Entity Info node, wires the entity once, and stacks as many property blocks inside it as
 * it needs — rather than wiring the same entity into eight separate nodes.
 *
 * <p>Every block is a <b>dedicated class with typed output ports</b>, scoped to its context by
 * {@code @UseWithContext}. There is no generic "pick a property by name" block, and no reflection
 * anywhere in the mechanism. An earlier version had exactly that, and it was wrong on three counts:
 * <ul>
 *   <li><b>It offered the wrong properties.</b> Reflection cannot tell data from plumbing, so the
 *       property searcher listed {@code declaringClass}, {@code describeConstable}, {@code popTime} and
 *       {@code componentsPatch} beside the handful anyone wanted.</li>
 *   <li><b>It serialised a member name.</b> The selected property was stored as a string, so a
 *       Minecraft version renaming a getter turned a saved graph's read into a silent null. A block
 *       class is bound at compile time and serialises its registry name instead, so the same rename is
 *       a build failure. (Runtime obfuscation is <em>not</em> the concern here — NeoForge has shipped
 *       Mojang mappings at runtime since 1.20.2, so member names match development exactly.)</li>
 *   <li><b>A getter returning an unsupported type produced a dead pin.</b> {@code Entity.position()}
 *       returns a {@code Vec3}, which the graph deliberately does not carry, so it rendered as a pin
 *       that connected to nothing. That needed a whole curation registry to paper over; a dedicated
 *       block just declares a {@code Vector3f} output and converts.</li>
 * </ul>
 *
 * <h2>What gets a context at all</h2>
 * Only live game objects: {@code Entity}, {@code Player}, {@code Level}, {@code BlockEntity}. Value
 * types — {@code BlockPos}, {@code AABB}, {@code ItemStack}, {@code Direction} and the rest — get plain
 * multi-output nodes instead, because there is no shared target worth hoisting: a node that takes a
 * {@code BlockPos} and emits x, y and z is already the whole story.
 *
 * @param <T> the type whose properties the contained blocks read
 */
public abstract class InfoContextNode<T> extends ContextNode implements INodeDescription {

    /** The class whose properties this context's blocks read. Drives the {@code target} port. */
    protected abstract Class<T> targetClass();

    @Override
    public void setImplementation(NodeModel nodeModel) {
        super.setImplementation(nodeModel);
        NodeTooltipHelper.apply(nodeModel, getNodeTooltip());
    }

    /** The hover tooltip; by default the same {@code kg.node.<name>.tooltip} the panel uses. */
    protected @Nullable Component getNodeTooltip() {
        return NodeTooltipHelper.defaultTooltip(this);
    }

    @Override
    @Nullable
    public UIElement createDescriptionUI() {
        return NodeDescriptions.build(this);
    }

    /** Public view of {@link #targetClass()} for the contained blocks. */
    public Class<?> targetType() {
        return targetClass();
    }

    @Override
    public Component getDisplayName() {
        var attribute = getClass().getAnnotation(NodeAttribute.class);
        return attribute == null
                ? Component.literal(targetClass().getSimpleName() + " Info")
                : Component.translatable(attribute.name());
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        var builder = context.addInputPort("target", KGTypeHandles.handleFor(targetClass()));
        // Every context type is a live game object, so none of them has an accessor: drop the editor row
        // and the embedded constant, mirroring NodeMetadata's handling of annotated ports. The check
        // stays rather than being assumed, so a future context on a serialisable type keeps its picker.
        if (!hasAccessor(targetClass())) builder.withoutConfigurator();
    }

    // getSupportBlocks() is deliberately not overridden. The default scans the graph's registered nodes
    // for blocks whose @UseWithContext names this context, which means adding a property is adding one
    // class and nothing else — no list here to forget to update.

    private static boolean hasAccessor(Class<?> type) {
        try {
            AccessorRegistries.findByType(type);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
