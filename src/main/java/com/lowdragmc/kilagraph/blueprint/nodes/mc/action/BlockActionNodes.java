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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Changing blocks in the world. See {@link McActions} for the rules every action shares.
 */
public final class BlockActionNodes {

    private static final String GROUP = "mc/action";

    private BlockActionNodes() {
    }

    /**
     * Places a block state at a position.
     *
     * <h2>Why the update flags are an input and not a constant</h2>
     * {@code notifyNeighbours} is what makes redstone re-evaluate, water start flowing and a torch pop off
     * the block you just replaced. It is on by default because that is what "place a block" normally
     * means. Turning it off is how a graph builds a structure without every intermediate state triggering
     * physics — the same reason the game's own structure placement does it.
     */
    @NodeAttribute(name = "mc_set_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_block.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public BlockState state;
        @InputPort public boolean notifyNeighbours = true;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            BlockState placed = ctx.getInput("state", BlockState.class, null);
            if (world == null || at == null || placed == null) {
                McActions.done(ctx, false);
                return;
            }
            // isInWorldBounds is the guard setBlock itself uses; without it a y outside the build range
            // silently does nothing and reports success.
            if (!world.isInWorldBounds(at)) {
                McActions.done(ctx, false);
                return;
            }
            int flags = ctx.getBool("notifyNeighbours", true) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS;
            McActions.done(ctx, world.setBlock(at, placed, flags));
        }
    }

    /**
     * Breaks the block at a position.
     *
     * <p>{@code drop} decides whether it leaves an item behind, exactly as breaking it by hand versus in
     * creative mode. Breaking air reports false — there was nothing to break.</p>
     */
    @NodeAttribute(name = "mc_break_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BreakBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_break_block.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public boolean drop = true;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, world.destroyBlock(at, ctx.getBool("drop", true)));
        }
    }

    /**
     * Fills a box with one block state.
     *
     * <h2>Capped, and it says so</h2>
     * A fill is the one action whose cost is set by user input rather than by the graph's shape: the box
     * between two positions a player typed can be millions of blocks, and placing them one per tick is how
     * a server stops responding. So the fill stops at {@link #LIMIT} and {@code truncated} says whether it
     * did — the same treatment, and the same reasoning, as {@code mc_block_pos_between}.
     *
     * <p>Neighbour updates are suppressed for every block and applied once at the end, which is what makes
     * a fill one physics pass instead of N.
     */
    @NodeAttribute(name = "mc_fill_blocks", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FillBlocks extends AnnotatedNode {
        /** Roughly a 32-block cube, matching {@code mc_block_pos_between}. */
        public static final int LIMIT = 32768;

        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fill_blocks.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos min = BlockPos.ZERO;
        @InputPort public BlockPos max = BlockPos.ZERO;
        @InputPort public BlockState state;
        @OutputPort public boolean ok;
        @OutputPort public int placed;
        @OutputPort public boolean truncated;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos a = ctx.getInput("min", BlockPos.class, null);
            BlockPos b = ctx.getInput("max", BlockPos.class, null);
            BlockState fill = ctx.getInput("state", BlockState.class, null);
            if (world == null || a == null || b == null || fill == null) {
                ctx.setOutput("placed", 0);
                ctx.setOutput("truncated", false);
                McActions.done(ctx, false);
                return;
            }

            int count = 0;
            boolean cut = false;
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                if (count >= LIMIT) {
                    cut = true;
                    break;
                }
                if (!world.isInWorldBounds(p)) continue;
                // UPDATE_CLIENTS only: neighbours are notified once below rather than per block.
                if (world.setBlock(p, fill, Block.UPDATE_CLIENTS)) count++;
            }
            if (count > 0) {
                for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                    if (!world.isInWorldBounds(p)) continue;
                    world.updateNeighborsAt(p, fill.getBlock(), null);
                }
            }
            ctx.setOutput("placed", count);
            ctx.setOutput("truncated", cut);
            McActions.done(ctx, count > 0);
        }
    }

    /**
     * Replaces the block at a position only if it currently holds an expected block.
     *
     * <p>A compare-and-set, and the reason it exists rather than being a Branch plus a Set Block: between
     * reading a block and writing it, another graph or another player can change it. Doing both in one
     * action closes that window. {@code ok} is false when the position did not hold {@code expected}.</p>
     */
    @NodeAttribute(name = "mc_replace_block", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ReplaceBlock extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_replace_block.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Block expected = Blocks.AIR;
        @InputPort public BlockState state;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            Block want = ctx.getInput("expected", Block.class, null);
            BlockState placed = ctx.getInput("state", BlockState.class, null);
            if (world == null || at == null || want == null || placed == null || !world.isInWorldBounds(at)) {
                McActions.done(ctx, false);
                return;
            }
            if (!world.getBlockState(at).is(want)) {
                McActions.done(ctx, false);
                return;
            }
            McActions.done(ctx, world.setBlock(at, placed, Block.UPDATE_ALL));
        }
    }
}
