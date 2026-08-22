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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sound, particles and dropped items — the actions a player sees and hears rather than walks on.
 * See {@link McActions} for the rules every action shares.
 */
public final class WorldEffectNodes {

    private static final String GROUP = "mc_action";

    private WorldEffectNodes() {
    }

    /**
     * Plays a sound at a position, for everyone nearby.
     *
     * <p>The sound is named by id ({@code minecraft:block.anvil.land}), so a graph can build one at
     * runtime. An unknown id reports {@code ok = false} rather than playing nothing silently.
     *
     * <p>{@code pitch} doubles the frequency at 2 and halves it at 0.5; the game clamps it to that range.
     * Volume above 1 does not get louder, it gets <em>further</em> — that is how the game models a loud
     * sound, and it is worth knowing before turning it up and hearing no difference up close.
     */
    @NodeAttribute(name = "mc_play_sound", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class PlaySound extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_play_sound.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public ResourceLocation sound;
        @InputPort public float volume = 1f;
        @InputPort public float pitch = 1f;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            ResourceLocation id = ctx.getInput("sound", ResourceLocation.class, null);
            if (world == null || at == null || id == null) {
                McActions.done(ctx, false);
                return;
            }
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
            if (event == null) {
                McActions.done(ctx, false);
                return;
            }
            // A null player means "send to every client in range", which is what a world action wants;
            // passing a player would exclude them, since that overload assumes they predicted it locally.
            world.playSound(null, at, event, SoundSource.BLOCKS,
                    ctx.getFloat("volume", 1f), ctx.getFloat("pitch", 1f));
            McActions.done(ctx, true);
        }
    }

    /**
     * Spawns particles at a position.
     *
     * <h2>Only simple particle types</h2>
     * Most particles ({@code flame}, {@code smoke}, {@code heart}) carry no data and can be named by id
     * alone. A few — {@code block}, {@code dust}, {@code item} — need parameters that have no
     * representation in this graph, so they are refused with {@code ok = false} rather than guessed at.
     *
     * <p>{@code spread} is the standard deviation of the offset from the position, in blocks, so 0 puts
     * every particle exactly on the point.
     */
    @NodeAttribute(name = "mc_spawn_particle", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SpawnParticle extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_spawn_particle.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public ResourceLocation particle;
        @InputPort public int count = 1;
        @InputPort public float spread = 0f;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            ResourceLocation id = ctx.getInput("particle", ResourceLocation.class, null);
            if (world == null || at == null || id == null) {
                McActions.done(ctx, false);
                return;
            }
            var type = BuiltInRegistries.PARTICLE_TYPE.get(id);
            if (!(type instanceof ParticleOptions options)) {
                // A parameterised type (block/dust/item) is a ParticleType but not a ParticleOptions.
                McActions.done(ctx, false);
                return;
            }
            double s = Math.max(0, ctx.getFloat("spread", 0f));
            world.sendParticles(options, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    Math.max(0, ctx.getInt("count", 1)), s, s, s, 0.0);
            McActions.done(ctx, true);
        }
    }

    /**
     * Drops an item stack into the world as a pickup.
     *
     * <p>The dropped entity comes out on {@code entity} so a graph can act on it — give it velocity, or
     * remember it to remove later. It is dropped at the centre of the block with no motion, which is what
     * a deliberate placement wants; the game's own random scatter is for breaking blocks.</p>
     */
    @NodeAttribute(name = "mc_drop_item", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DropItem extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_drop_item.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public Entity entity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            ItemStack drop = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (world == null || at == null || drop == null || drop.isEmpty()) {
                ctx.setOutput("entity", null);
                McActions.done(ctx, false);
                return;
            }
            // Copy: an ItemEntity owns the stack it is given, and the input may be read again downstream.
            ItemEntity item = new ItemEntity(world,
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, drop.copy());
            item.setDeltaMovement(0, 0, 0);
            boolean added = world.addFreshEntity(item);
            ctx.setOutput("entity", added ? item : null);
            McActions.done(ctx, added);
        }
    }

    /**
     * Sends a chat message to one player.
     *
     * <p>Takes {@code Text} rather than a string so a graph can build a styled message with the
     * {@code mc_text_*} nodes. A plain string works too — anything coerces to text on a String pin, and
     * {@code mc_text_literal} is the explicit form.</p>
     */
    @NodeAttribute(name = "mc_send_message", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SendMessage extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_send_message.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Player player;
        @InputPort public Component message;
        @InputPort public boolean actionBar = false;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var p = ctx.getInput("player", Player.class, null);
            Component text = ctx.getInput("message", Component.class, null);
            if (p == null || text == null || p.level().isClientSide) {
                McActions.done(ctx, false);
                return;
            }
            p.displayClientMessage(text, ctx.getBool("actionBar", false));
            McActions.done(ctx, true);
        }
    }
}
