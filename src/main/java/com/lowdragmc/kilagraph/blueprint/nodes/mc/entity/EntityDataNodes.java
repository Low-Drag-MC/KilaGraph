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
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    // ---- tags and trades ---------------------------------------------------------------------

    /**
     * The scoreboard tags on an entity — the ones {@code /tag} adds.
     *
     * <p>The read half of a pair that was previously half-open: the game has {@code /tag add} and
     * {@code /tag list}, but the second answers in chat, so a graph could write tags and never see them.
     * These are the closest thing an entity has to a free-form flag, which makes them how a blueprint marks
     * an entity as its own.
     *
     * <p>Nothing to do with the entity-type tags {@code mc_entity_type_in_tag} asks about: those are
     * datapack groupings of kinds of entity, these are labels on one individual.
     *
     * <p>Sorted, because the game keeps them in a hash set and a graph that reads {@code out[0]} would
     * otherwise get a different one on a different launch.</p>
     */
    @NodeAttribute(name = "mc_entity_tags", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Tags extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_tags.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public List<?> out;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            List<String> tags = e == null ? new ArrayList<>() : new ArrayList<>(e.getTags());
            tags.sort(Comparator.naturalOrder());
            ctx.setOutput("out", tags);
            ctx.setOutput("count", tags.size());
        }
    }

    /**
     * What a villager or wandering trader is offering.
     *
     * <p>No command reads trades. {@code /data get entity} produces the raw {@code Offers} NBT, which a
     * graph would then have to walk item by item; this hands back the stacks.
     *
     * <p>Four parallel lists, same shape as {@code mc_enchantments}: {@code costA} and {@code costB} are
     * what the trade takes — {@code costB} is empty for a one-item trade — {@code result} is what it gives,
     * and {@code outOfStock} says whether it can still be used today.
     *
     * <p>{@code costA} is the current price, not the listed one, so it already includes demand, the
     * Hero of the Village discount and any reputation adjustment — the number a player would actually pay
     * right now rather than the number the trade was created with.
     *
     * <p>Reading a villager's offers is what makes it generate them if it has not yet, so this is not
     * quite free the first time and does change the entity. That is the game's own design, not a choice
     * made here.</p>
     */
    @NodeAttribute(name = "mc_villager_trades", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Trades extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_villager_trades.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public List<?> costA;
        @OutputPort public List<?> costB;
        @OutputPort public List<?> result;
        @OutputPort public List<?> outOfStock;
        @OutputPort public int count;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            List<ItemStack> costA = new ArrayList<>();
            List<ItemStack> costB = new ArrayList<>();
            List<ItemStack> result = new ArrayList<>();
            List<Boolean> outOfStock = new ArrayList<>();
            Merchant merchant = entity(ctx) instanceof Merchant m ? m : null;
            if (merchant != null) {
                for (MerchantOffer offer : merchant.getOffers()) {
                    costA.add(offer.getCostA());
                    costB.add(offer.getCostB());
                    result.add(offer.getResult());
                    outOfStock.add(offer.isOutOfStock());
                }
            }
            ctx.setOutput("costA", costA);
            ctx.setOutput("costB", costB);
            ctx.setOutput("result", result);
            ctx.setOutput("outOfStock", outOfStock);
            ctx.setOutput("count", result.size());
            ctx.setOutput("found", merchant != null);
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
