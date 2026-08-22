package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Entity state a graph can change that is not position, health or effects — fire, naming, riding and
 * equipment. See {@link McActions} for the rules every action shares.
 */
public final class EntityInteractionNodes {

    private static final String GROUP = "mc_action";

    private EntityInteractionNodes() {
    }

    /**
     * Sets an entity on fire, or puts it out.
     *
     * <p>Seconds, not ticks, because that is the unit the game's own {@code setSecondsOnFire} takes and
     * the unit a flame duration is thought about in. Zero puts the fire out.
     *
     * <p>Fire-immune entities report {@code ok = false}: setting a blaze alight is not something that
     * happens, and a graph that wanted to know can ask.</p>
     */
    @NodeAttribute(name = "mc_set_entity_fire", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetFire extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_entity_fire.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public int seconds = 5;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            int seconds = ctx.getInt("seconds", 5);
            if (e == null || e.level().isClientSide || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            if (seconds <= 0) {
                e.clearFire();
                McActions.done(ctx, true);
                return;
            }
            if (e.fireImmune()) {
                McActions.done(ctx, false);
                return;
            }
            e.setRemainingFireTicks(seconds * 20);
            McActions.done(ctx, true);
        }
    }

    /**
     * Names an entity, and decides whether the name floats above it.
     *
     * <p>The same thing a name tag does. An <b>empty</b> name clears the custom name and the entity goes
     * back to showing its type's name.
     *
     * <p>Empty rather than null is the test on purpose: the {@code Text} handle carries a default of
     * {@code Component.empty()}, so an unconnected {@code name} port never arrives as null — it arrives as
     * an empty component. A node that checked for null would leave the entity named "" and
     * {@code hasCustomName()} would stay true, which is a name that renders as nothing and cannot be
     * cleared. Checking the string is what makes "leave it unset to clear" actually work.
     */
    @NodeAttribute(name = "mc_set_entity_name", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetName extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_entity_name.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public Component name;
        @InputPort public boolean alwaysVisible = false;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            if (e == null || e.level().isClientSide || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            Component name = ctx.getInput("name", Component.class, null);
            boolean clear = name == null || name.getString().isEmpty();
            e.setCustomName(clear ? null : name);
            e.setCustomNameVisible(!clear && ctx.getBool("alwaysVisible", false));
            McActions.done(ctx, true);
        }
    }

    /**
     * Makes one entity ride another.
     *
     * <p>{@code ok} is false when the vehicle refuses the passenger — it is full, or the pair would form
     * a loop. Riding is the mechanism behind boats, minecarts and leashed stacks of mobs, and the game
     * enforces its own rules about who may carry whom.</p>
     */
    @NodeAttribute(name = "mc_mount_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Mount extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_mount_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public Entity vehicle;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity rider = ctx.getInput("entity", Entity.class, null);
            Entity vehicle = ctx.getInput("vehicle", Entity.class, null);
            if (rider == null || vehicle == null || rider == vehicle
                    || rider.level().isClientSide || rider.isRemoved() || vehicle.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, rider.startRiding(vehicle, true));
        }
    }

    /** Gets an entity off whatever it is riding. False when it was not riding anything. */
    @NodeAttribute(name = "mc_dismount_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Dismount extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_dismount_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            if (e == null || e.level().isClientSide || e.isRemoved() || !e.isPassenger()) {
                McActions.done(ctx, false);
                return;
            }
            e.stopRiding();
            McActions.done(ctx, true);
        }
    }

    /**
     * Replaces an entity's stored data with the given NBT.
     *
     * <p>The entity counterpart of {@code mc_set_block_entity_nbt}, and the same escape hatch with the
     * same warning: it writes whatever the tag says with no validation. Read the current data with
     * {@code mc_nbt_entity}, change the one key you mean to change, and write it back — a tag built from
     * nothing will drop every field you did not include.
     *
     * <p>Position and identity are deliberately not restored from the tag. {@code saveWithoutId} omits
     * the id, but it does include the coordinates, and loading those would silently teleport the entity
     * as a side effect of setting an unrelated field. The position is captured before the load and put
     * back afterwards, so this node changes state and nothing else. Moving an entity is
     * {@code mc_teleport_entity}, which says so in its name.
     */
    @NodeAttribute(name = "mc_set_entity_nbt", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetNbt extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_entity_nbt.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public CompoundTag nbt;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            var tag = ctx.getInput("nbt", CompoundTag.class, null);
            if (e == null || tag == null || e.level().isClientSide || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            double x = e.getX();
            double y = e.getY();
            double z = e.getZ();
            float yRot = e.getYRot();
            float xRot = e.getXRot();
            e.load(tag);
            // Put the entity back where it was: the tag carries Pos/Rotation, and a graph setting a
            // custom field has not asked to move anything.
            e.moveTo(x, y, z, yRot, xRot);
            McActions.done(ctx, true);
        }
    }

    /**
     * Puts an item in one of an entity's equipment slots.
     *
     * <p>The write counterpart of {@code mc_entity_held_item}. Equipment is a living-entity idea, so
     * anything else reports {@code ok = false} — a dropped item has no hands.</p>
     */
    @NodeAttribute(name = "mc_set_equipment", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetEquipment extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_equipment.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public EquipmentSlot slot = EquipmentSlot.MAINHAND;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            EquipmentSlot slot = ctx.getInput("slot", EquipmentSlot.class, EquipmentSlot.MAINHAND);
            ItemStack put = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide || e.isRemoved() || put == null) {
                McActions.done(ctx, false);
                return;
            }
            // Copy: the entity takes ownership of the stack it is given.
            living.setItemSlot(slot == null ? EquipmentSlot.MAINHAND : slot, put.copy());
            McActions.done(ctx, true);
        }
    }
}
