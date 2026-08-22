package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Entity queries that take an argument, and so cannot be a property block.
 *
 * <h2>What is not here</h2>
 * Plain properties — position, look direction, hitbox, health, identity, state — are blocks inside
 * {@link EntityInfoNode}, in {@code EntityInfoBlocks}. There used to be a standalone node for each of
 * those as well, seven pairs differing only by an {@code mc_} prefix, and they were deleted: two nodes
 * with the same meaning and near-identical names is worse than either one alone, and the context form is
 * the mechanism this graph settled on.
 *
 * <p>What survives is the queries that are not properties at all. Each takes a second input — a type to
 * compare against, a slot, an effect id, an attribute id — so there is nothing for a zero-input block to
 * read, and they compose directly in an expression.
 *
 * <h2>Living-entity data on an Entity pin</h2>
 * Effects, attributes and equipment only exist on a {@link LivingEntity}. Rather than introduce a
 * second pin type for it, these nodes take an {@code Entity} and report the neutral answer for anything
 * that is not living — an item frame wears nothing, and asking is not an error.
 */
public final class EntityDataNodes {

    private static final String GROUP = "mc_entity";

    private EntityDataNodes() {
    }

    // ---- identity ----------------------------------------------------------------------------

    @NodeAttribute(name = "mc_entity_is_type", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class IsType extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_is_type.tooltip");
        }

        @InputPort public Entity entity;
        @InputPort public EntityType<?> type;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            EntityType<?> t = ctx.getInput("type", EntityType.class, null);
            ctx.setOutput("out", e != null && t != null && e.getType() == t);
        }
    }

    // ---- living-entity data ------------------------------------------------------------------

    /** What the entity holds or wears in one slot. Empty for anything that is not a living entity. */
    @NodeAttribute(name = "mc_entity_held_item", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HeldItem extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_held_item.tooltip");
        }

        @InputPort public Entity entity;
        // An input port rather than an option: the type is already a pin type, so a port gets the same
        // inline dropdown AND can be driven by a wire. An option is a no-connector port — it can
        // never be computed, so it is only right when the type is not one the graph carries.
        @InputPort public EquipmentSlot slot = EquipmentSlot.MAINHAND;
        @OutputPort public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            EquipmentSlot slot = ctx.getInput("slot", EquipmentSlot.class, EquipmentSlot.MAINHAND);
            // getItemBySlot is declared on LivingEntity, not Entity — equipment is a living-entity idea,
            // so a dropped item or a minecart simply has nothing in any slot.
            ctx.setOutput("out", entity(ctx) instanceof LivingEntity living
                    ? living.getItemBySlot(slot == null ? EquipmentSlot.MAINHAND : slot)
                    : ItemStack.EMPTY);
        }
    }

    /**
     * Whether a status effect is active, and how strong.
     *
     * <p>{@code amplifier} is zero-based the way the game counts it: Strength II is amplifier 1. Both
     * numbers are zero when the effect is absent, which {@code has} disambiguates from a
     * genuinely-level-I effect.</p>
     */
    @NodeAttribute(name = "mc_entity_has_effect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HasEffect extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_has_effect.tooltip");
        }

        @InputPort public Entity entity;
        @InputPort public ResourceLocation effect;
        @OutputPort public boolean has;
        @OutputPort public int amplifier;
        @OutputPort public int duration;

        @Override
        public void evaluate(EvalContext ctx) {
            var holder = holder(ctx, "effect", BuiltInRegistries.MOB_EFFECT, Registries.MOB_EFFECT);
            var instance = entity(ctx) instanceof LivingEntity living && holder != null
                    ? living.getEffect(holder)
                    : null;
            ctx.setOutput("has", instance != null);
            ctx.setOutput("amplifier", instance == null ? 0 : instance.getAmplifier());
            ctx.setOutput("duration", instance == null ? 0 : instance.getDuration());
        }
    }

    /**
     * An attribute's current value, after every modifier.
     *
     * <p>{@code found} is false both for an unknown attribute id and for an entity that does not have
     * that attribute — a zombie has {@code movement_speed}, an arrow has nothing.</p>
     */
    @NodeAttribute(name = "mc_entity_attribute", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Attribute extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_attribute.tooltip");
        }

        @InputPort public Entity entity;
        @InputPort public ResourceLocation attribute;
        @OutputPort public double value;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            var holder = holder(ctx, "attribute", BuiltInRegistries.ATTRIBUTE, Registries.ATTRIBUTE);
            var instance = entity(ctx) instanceof LivingEntity living && holder != null
                    ? living.getAttribute(holder)
                    : null;
            ctx.setOutput("value", instance == null ? 0d : instance.getValue());
            ctx.setOutput("found", instance != null);
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    private static Entity entity(EvalContext ctx) {
        return ctx.getInput("entity", Entity.class, null);
    }

    /**
     * The registry holder for the id on {@code portId}, or null.
     *
     * <p>A {@code Holder} rather than the bare value because that is what 1.21's living-entity APIs
     * take — {@code getEffect}/{@code getAttribute} were re-signed against holders when effects and
     * attributes became registry-driven.</p>
     */
    private static <T> Holder<T> holder(EvalContext ctx, String portId, Registry<T> registry,
                                        ResourceKey<Registry<T>> registryKey) {
        ResourceLocation rl = ctx.getInput(portId, ResourceLocation.class, null);
        if (rl == null) return null;
        return registry.getHolder(ResourceKey.create(registryKey, rl)).orElse(null);
    }
}
