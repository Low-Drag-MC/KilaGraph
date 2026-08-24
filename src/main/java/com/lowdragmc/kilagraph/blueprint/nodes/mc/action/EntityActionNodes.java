package com.lowdragmc.kilagraph.blueprint.nodes.mc.action;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.kilagraph.graph.mc.McConvert;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Creating, moving and affecting entities. See {@link McActions} for the rules every action shares.
 *
 * <h2>Living-entity actions on an Entity pin</h2>
 * Damage, healing and effects only apply to a {@link LivingEntity}. As everywhere else in this mod, the
 * pin type stays {@code Entity} and the node reports {@code ok = false} for anything that is not living,
 * rather than introducing a second entity pin type that every graph would have to convert between.
 */
public final class EntityActionNodes {

    private static final String GROUP = "mc/action";

    private EntityActionNodes() {
    }

    /**
     * Spawns an entity of a given type.
     *
     * <p>The spawned entity comes out on {@code entity} so the rest of the graph can act on it — set its
     * velocity, give it an effect, remember its UUID. That is the whole reason this is worth a node rather
     * than a command: a spawn you cannot refer to afterwards is much less useful.
     *
     * <p>{@code MobSpawnType.COMMAND} is the reason given to the game, which is what stops mob-spawning
     * rules and spawn-egg-specific behaviour from applying — this is a deliberate placement, not natural
     * spawning.
     */
    @NodeAttribute(name = "mc_spawn_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SpawnEntity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_spawn_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public EntityType<?> type;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public Entity entity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            EntityType<?> t = ctx.getInput("type", EntityType.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || t == null || at == null) {
                ctx.setOutput("entity", null);
                McActions.done(ctx, false);
                return;
            }
            Entity spawned = t.spawn(world, at, MobSpawnType.COMMAND);
            ctx.setOutput("entity", spawned);
            McActions.done(ctx, spawned != null);
        }
    }

    /**
     * Removes an entity from the world without killing it.
     *
     * <p>Discard, not kill: no death animation, no drops, no death message. Killing something is
     * {@code mc_damage_entity} with enough damage. Removing a player is refused — a player is removed by
     * disconnecting, and discarding one leaves the server in a state it does not expect.</p>
     */
    @NodeAttribute(name = "mc_remove_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveEntity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_remove_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            if (e == null || e.level().isClientSide || e instanceof Player || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            e.discard();
            McActions.done(ctx, true);
        }
    }

    /** Moves an entity to a position instantly. */
    @NodeAttribute(name = "mc_teleport_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class TeleportEntity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_teleport_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public Vector3f pos;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            Vector3f to = ctx.getInput("pos", Vector3f.class, null);
            if (e == null || to == null || e.level().isClientSide || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            e.teleportTo(to.x, to.y, to.z);
            McActions.done(ctx, true);
        }
    }

    /**
     * Sets an entity's movement for this tick.
     *
     * <p>Replaces the velocity rather than adding to it; to nudge something, read its velocity with the
     * Velocity block, add, and set the result back. Gravity and drag still apply afterwards, so this is an
     * impulse and not a sustained speed.
     *
     * <p>{@code hasImpulse} is set so the change is sent to clients this tick — without it the server
     * knows the entity is moving and nothing on screen does.</p>
     */
    @NodeAttribute(name = "mc_set_entity_velocity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetVelocity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_entity_velocity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public Vector3f velocity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            Vector3f v = ctx.getInput("velocity", Vector3f.class, null);
            if (e == null || v == null || e.level().isClientSide || e.isRemoved()) {
                McActions.done(ctx, false);
                return;
            }
            e.setDeltaMovement(McConvert.toVec3(v));
            e.hasImpulse = true;
            McActions.done(ctx, true);
        }
    }

    /**
     * Damages an entity.
     *
     * <p>Generic damage, which armour and most protections do not reduce — the damage type a graph means
     * when it says "take 5 hearts off this". {@code ok} is false when the entity was invulnerable, already
     * dead, or is not a living entity at all.</p>
     */
    @NodeAttribute(name = "mc_damage_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DamageEntity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_damage_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public float amount = 1f;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            float amount = ctx.getFloat("amount", 1f);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide || e.isRemoved() || amount <= 0) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, living.hurt(e.level().damageSources().generic(), amount));
        }
    }

    /** Restores health, up to the entity's maximum. */
    @NodeAttribute(name = "mc_heal_entity", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HealEntity extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_heal_entity.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public float amount = 1f;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            float amount = ctx.getFloat("amount", 1f);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide || !living.isAlive() || amount <= 0) {
                McActions.done(ctx, false);
                return;
            }
            living.heal(amount);
            McActions.done(ctx, true);
        }
    }

    /**
     * Applies a status effect.
     *
     * <p>{@code amplifier} is zero-based the way the game counts it, so Strength II is amplifier 1 —
     * matching {@code mc_entity_has_effect}, which reads it back the same way. Duration is in ticks.</p>
     */
    @NodeAttribute(name = "mc_add_effect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AddEffect extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_add_effect.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public ResourceLocation effect;
        @InputPort public int duration = 200;
        @InputPort public int amplifier = 0;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            ResourceLocation id = ctx.getInput("effect", ResourceLocation.class, null);
            int duration = ctx.getInt("duration", 200);
            int amplifier = ctx.getInt("amplifier", 0);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide || id == null || duration <= 0) {
                McActions.done(ctx, false);
                return;
            }
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                    ResourceKey.create(
                            Registries.MOB_EFFECT, id)).orElse(null);
            if (holder == null) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, living.addEffect(new MobEffectInstance(holder, duration, Math.max(0, amplifier))));
        }
    }

    /**
     * Takes one status effect away.
     *
     * <p>{@code ok = false} when the entity did not have the effect, so this reports what it changed
     * rather than what it attempted — a graph that clears a buff before reapplying it can tell whether the
     * buff was there. An unknown effect id reads the same way, since neither case changed anything.</p>
     */
    @NodeAttribute(name = "mc_remove_effect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveEffect extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_remove_effect.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @InputPort public ResourceLocation effect;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            ResourceLocation id = ctx.getInput("effect", ResourceLocation.class, null);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide || id == null) {
                McActions.done(ctx, false);
                return;
            }
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                    ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
            McActions.done(ctx, holder != null && living.removeEffect(holder));
        }
    }

    /**
     * Takes every status effect away, good and bad alike.
     *
     * <p>{@code removed} is counted before the call because the game only answers yes/no, and "how many
     * buffs did I just wipe" is the question a graph doing this actually has.
     *
     * <p>This is milk-bucket behaviour, not a cure: nothing is filtered by whether it is beneficial, so a
     * graph that means "remove the debuffs" wants {@code mc_remove_effect} in a loop instead.</p>
     */
    @NodeAttribute(name = "mc_clear_effects", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ClearEffects extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_clear_effects.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Entity entity;
        @OutputPort public int removed;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Entity e = ctx.getInput("entity", Entity.class, null);
            if (!(e instanceof LivingEntity living) || e.level().isClientSide) {
                ctx.setOutput("removed", 0);
                McActions.done(ctx, false);
                return;
            }
            int had = living.getActiveEffects().size();
            boolean changed = living.removeAllEffects();
            ctx.setOutput("removed", changed ? had : 0);
            McActions.done(ctx, changed);
        }
    }

    /**
     * Puts an item into a player's inventory.
     *
     * <p>{@code ok} is false when the inventory had no room, and {@code remainder} is what would not fit —
     * a graph that cares can drop it with {@code mc_drop_item}. Silently losing items is the failure mode
     * this shape exists to avoid.</p>
     */
    @NodeAttribute(name = "mc_give_item", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GiveItem extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_give_item.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Player player;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public ItemStack remainder = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            Player p = ctx.getInput("player", Player.class, null);
            ItemStack give = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (p == null || give == null || give.isEmpty() || p.level().isClientSide) {
                ctx.setOutput("remainder", give == null ? ItemStack.EMPTY : give);
                McActions.done(ctx, false);
                return;
            }
            // Copy first: the inventory mutates what it is handed, and the input stack may already have
            // been read by another branch of this run.
            ItemStack copy = give.copy();
            boolean added = p.getInventory().add(copy);
            ctx.setOutput("remainder", copy.isEmpty() ? ItemStack.EMPTY : copy);
            McActions.done(ctx, added && copy.isEmpty());
        }
    }
}
