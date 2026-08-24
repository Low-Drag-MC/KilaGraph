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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Interacting with the world the way a player or a tool would — fire, bone meal, lightning, explosions.
 * See {@link McActions} for the rules every action shares.
 *
 * <p>These go through the game's own mechanics rather than reimplementing them, which is why bone meal
 * respects what is actually bonemealable and fire respects what can actually burn. A graph that wanted to
 * bypass those rules would use {@code mc_set_block} instead.
 */
public final class WorldInteractionNodes {

    private static final String GROUP = "mc/action";

    private WorldInteractionNodes() {
    }

    /**
     * Lights a fire at a position.
     *
     * <p>Places the correct fire for the dimension — ordinary fire in the Overworld, soul fire on soul
     * soil — by asking the game what fire belongs there, which is what flint and steel does.
     *
     * <p>{@code ok} is false when the position is occupied, since fire needs an empty block to live in.
     * Lighting the block <em>above</em> something flammable is the usual intent.</p>
     */
    @NodeAttribute(name = "mc_ignite", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Ignite extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_ignite.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null || !world.isInWorldBounds(at)) {
                McActions.done(ctx, false);
                return;
            }
            if (!world.getBlockState(at).isAir()) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, world.setBlock(at, BaseFireBlock.getState(world, at), Block.UPDATE_ALL));
        }
    }

    /** Puts out a fire. False when there was no fire there. */
    @NodeAttribute(name = "mc_extinguish", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Extinguish extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_extinguish.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null || !(world.getBlockState(at).getBlock() instanceof BaseFireBlock)) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, world.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        }
    }

    /**
     * Applies bone meal, as a player would.
     *
     * <p>Goes through the item's own logic, so it grows what bone meal grows and does nothing to what it
     * does not — including modded crops, which a hand-written "increment the age property" would miss.
     * {@code ok} is false when the block was not bonemealable or was already fully grown.</p>
     */
    @NodeAttribute(name = "mc_bonemeal", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BoneMeal extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_bonemeal.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                McActions.done(ctx, false);
                return;
            }
            // A throwaway stack: growCrop consumes from it, and nothing here owns a real one.
            McActions.done(ctx, BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), world, at));
        }
    }

    /**
     * Strikes lightning at a position.
     *
     * <p>{@code visualOnly} gives the flash and the sound without the fire, the damage or the
     * pig-to-zombified-piglin conversions — for weather effects that are not meant to be dangerous.</p>
     */
    @NodeAttribute(name = "mc_strike_lightning", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class StrikeLightning extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_strike_lightning.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public boolean visualOnly = false;
        @OutputPort public Entity entity;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("entity", null);
                McActions.done(ctx, false);
                return;
            }
            var bolt = EntityType.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED);
            if (bolt == null) {
                ctx.setOutput("entity", null);
                McActions.done(ctx, false);
                return;
            }
            bolt.snapTo(Vec3.atBottomCenterOf(at));
            bolt.setVisualOnly(ctx.getBool("visualOnly", false));
            boolean added = world.addFreshEntity(bolt);
            ctx.setOutput("entity", added ? bolt : null);
            McActions.done(ctx, added);
        }
    }

    /**
     * Creates an explosion.
     *
     * <h2>Both destructive switches are off by default</h2>
     * A graph asking for an explosion usually means the effect — the sound, the particles, the knockback,
     * the damage. Cratering the terrain and setting the area alight are separate decisions, and a node
     * that did them unless told otherwise would surprise someone exactly once, irreversibly. So
     * {@code destroyBlocks} and {@code fire} both default to off and have to be asked for.
     *
     * <p>Radius is in blocks and is the same scale the game uses: TNT is 4, a creeper 3, a charged
     * creeper 6. It is clamped to a sane maximum so a graph cannot ask for a radius that would take the
     * server down.
     */
    @NodeAttribute(name = "mc_explode", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Explode extends AnnotatedNode {
        /** Well past a charged creeper, well short of anything that stalls a tick. */
        public static final float MAX_RADIUS = 16f;

        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_explode.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public float radius = 3f;
        @InputPort public boolean destroyBlocks = false;
        @InputPort public boolean fire = false;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            float radius = ctx.getFloat("radius", 3f);
            if (world == null || at == null || radius <= 0) {
                McActions.done(ctx, false);
                return;
            }
            boolean destroy = ctx.getBool("destroyBlocks", false);
            world.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    Math.min(radius, MAX_RADIUS),
                    ctx.getBool("fire", false),
                    destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            McActions.done(ctx, true);
        }
    }
}
