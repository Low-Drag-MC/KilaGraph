package com.lowdragmc.kilagraph.blueprint.nodes.mc.entity;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Entity data as standalone nodes, for graphs that want one property without a context.
 *
 * <h2>How these relate to {@code info_entity}</h2>
 * Every property here also exists as a block inside {@link EntityInfoNode} ({@code EntityInfoBlocks}),
 * and the block form is the better one when a graph reads several properties of the same entity: the
 * entity is wired once into the context instead of once per node.
 *
 * <p>These stay because the opposite case is just as common — one property, read once, in the middle of
 * an expression, where dropping a context plus a block to get a position is three actions instead of one.
 * The two forms compute identically; {@code InfoNodeBenchGameTest} measures the standalone one at roughly
 * half the cost per read, which is tens of nanoseconds and not the reason to choose either.
 *
 * <h2>Living-entity data on an Entity pin</h2>
 * Effects, attributes and equipment only exist on a {@link LivingEntity}. Rather than introduce a
 * second pin type for it, these nodes take an {@code Entity} and report the neutral answer for anything
 * that is not living — an item frame has no health, and asking is not an error.
 */
public final class EntityDataNodes {

    private static final String GROUP = "mc_entity";

    private EntityDataNodes() {
    }

    // ---- positions and geometry ---------------------------------------------------------------

    @NodeAttribute(name = "mc_entity_position", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Position extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_position.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", (Object) (e == null ? new Vector3f() : McConvert.toJoml(e.position())));
        }
    }

    /** Where the entity looks from — its feet position plus its eye height. */
    @NodeAttribute(name = "mc_entity_eye_position", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EyePosition extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_eye_position.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", (Object) (e == null ? new Vector3f() : McConvert.toJoml(e.getEyePosition())));
        }
    }

    /** A unit vector along the entity's line of sight. */
    @NodeAttribute(name = "mc_entity_look_direction", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class LookDirection extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_look_direction.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", (Object) (e == null ? new Vector3f() : McConvert.toJoml(e.getLookAngle())));
        }
    }

    /** Movement this tick, in blocks. Not a speed — take its length for that. */
    @NodeAttribute(name = "mc_entity_velocity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Velocity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_velocity.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public Vector3f out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", (Object) (e == null ? new Vector3f() : McConvert.toJoml(e.getDeltaMovement())));
        }
    }

    /** The entity's collision box, in world coordinates. */
    @NodeAttribute(name = "mc_entity_bounding_box", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BoundingBox extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_bounding_box.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public AABB out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", e == null ? null : e.getBoundingBox());
        }
    }

    /** The block the entity is standing in. */
    @NodeAttribute(name = "mc_entity_block_pos", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class EntityBlockPos extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_block_pos.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public BlockPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            Entity e = entity(ctx);
            ctx.setOutput("out", e == null ? BlockPos.ZERO : e.blockPosition());
        }
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

    /**
     * Health and maximum health.
     *
     * <p>Health is declared on {@code LivingEntity}, not on {@code Entity}, so a node taking an
     * {@code Entity} has to check: an arrow or a boat has none. Both numbers come from one node because
     * the useful quantity is almost always the ratio, and fetching the halves separately would mean two
     * instanceof checks for one question.
     *
     * <p>{@code entity_health} is the block form, and reports the same numbers plus a {@code living} flag
     * saying whether they mean anything.</p>
     */
    @NodeAttribute(name = "mc_entity_health", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Health extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_entity_health.tooltip");
        }

        @InputPort public Entity entity;
        @OutputPort public float out;
        @OutputPort public float max;
        @OutputPort public boolean living;

        @Override
        public void evaluate(EvalContext ctx) {
            boolean isLiving = entity(ctx) instanceof LivingEntity;
            LivingEntity living = isLiving ? (LivingEntity) entity(ctx) : null;
            ctx.setOutput("out", living == null ? 0f : living.getHealth());
            ctx.setOutput("max", living == null ? 0f : living.getMaxHealth());
            // Reported rather than left to be inferred from a zero: a dead mob and a boat both read
            // zero health, and only one of them is a question worth asking.
            ctx.setOutput("living", isLiving);
        }
    }

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
