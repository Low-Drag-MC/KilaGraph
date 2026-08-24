package com.lowdragmc.kilagraph.blueprint.nodes.mc.block;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How long a block takes to break, and whether the tool will get anything for it.
 *
 * <h2>The two questions are not the same question</h2>
 * "Can I harvest this" is about the tool and the block and nothing else, so {@code mc_can_harvest} needs no
 * world. "How long will this take" depends on where the block is, what is enchanted, and whether the miner
 * is standing in water, so {@code mc_destroy_speed} needs a position and ideally a player. Folding them into
 * one node would force the cheap question to carry the expensive one's inputs.
 *
 * <p>Neither of these has a command. A mining automaton that has to decide whether a block is worth the
 * time has no other way to ask.
 */
public final class MiningNodes {

    private static final String GROUP = "mc/block";

    /**
     * The divisors the game applies to mining progress: harvestable blocks take 30 ticks at speed 1,
     * unharvestable ones 100.
     *
     * <p>Copied rather than called because {@code getDestroyProgress} only exists on the player path. They
     * are the two numbers the whole no-player estimate rests on, which is why they are named.</p>
     */
    private static final int HARVESTABLE_TICKS = 30;
    private static final int UNHARVESTABLE_TICKS = 100;

    private MiningNodes() {
    }

    /**
     * How hard the block at a position is, and roughly how long it takes to break.
     *
     * <h2>Two paths, and the accurate one needs a player</h2>
     * With a {@code player} wired, this asks the game directly — {@code getDestroyProgress} — so the answer
     * includes the player's held tool, its Efficiency, Haste and Mining Fatigue, being underwater and being
     * off the ground. That is the real number, and the {@code tool} input is ignored because the player's
     * own held item is what the game uses.
     *
     * <p>Without a player it falls back to hardness against the {@code tool}'s speed, which is the same
     * formula minus every modifier above. Good enough to compare two blocks or to plan a machine; not the
     * number a particular player will experience. {@code exact} says which of the two you got, so a graph
     * never has to guess.
     *
     * <p>{@code ticks} is -1 whenever the block will not come out in any finite time. That covers two
     * different situations and {@code unbreakable} is what separates them: true means the block itself never
     * breaks (bedrock, a barrier), false with -1 means nothing is making progress on it right now — Mining
     * Fatigue, or a mod that zeroed the break-speed attribute. -1 rather than a huge number so that a graph
     * sorting by time does not put bedrock first.</p>
     */
    @NodeAttribute(name = "mc_destroy_speed", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DestroySpeed extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_destroy_speed.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public ItemStack tool = ItemStack.EMPTY;
        @InputPort public Player player;
        @OutputPort public float hardness;
        @OutputPort public float progress;
        @OutputPort public int ticks;
        @OutputPort public boolean unbreakable;
        @OutputPort public boolean exact;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos pos = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
            if (world == null || pos == null) {
                miss(ctx);
                return;
            }

            BlockState state = world.getBlockState(pos);
            float hardness = state.getDestroySpeed(world, pos);
            Player player = ctx.getInput("player", Player.class, null);

            // -1 is the game's own "never breaks" and nothing else produces it, so this is the one place
            // unbreakable is decided. It is a fact about the block, not about the miner.
            boolean unbreakable = hardness < 0f;
            float progress = unbreakable ? 0f : progressOf(ctx, state, world, pos, player, hardness);

            ctx.setOutput("hardness", hardness);
            ctx.setOutput("progress", progress);
            ctx.setOutput("ticks", progress <= 0f ? -1 : (int) Math.ceil(1f / progress));
            ctx.setOutput("unbreakable", unbreakable);
            ctx.setOutput("exact", player != null);
        }

        /**
         * Per-tick progress: the game's own answer when there is a player, an estimate from the tool when
         * there is not.
         *
         * <h2>Zero hardness is short-circuited before either path</h2>
         * The game's formula divides by hardness, so a torch or a flower makes it {@code Infinity}. That is
         * a correct "instantly" and a terrible port value — it would travel down the wire into whatever
         * arithmetic the graph does next. One tick is the truthful answer instead: nothing in the game
         * breaks in less than a tick. Handling it here rather than only in the estimate is what keeps the
         * two paths from disagreeing about the same torch, which they did.
         *
         * <p>The estimate is {@code toolSpeed / hardness / divisor}, the shape of
         * {@code BlockBehaviour.getDestroyProgress} with every player-specific multiplier left out.</p>
         */
        private static float progressOf(EvalContext ctx, BlockState state, Level world, BlockPos pos,
                                        @Nullable Player player, float hardness) {
            if (hardness == 0f) return 1f;
            if (player != null) return state.getDestroyProgress(player, world, pos);

            ItemStack held = ctx.getInput("tool", ItemStack.class, ItemStack.EMPTY);
            if (held == null) held = ItemStack.EMPTY;
            int divisor = harvestable(state, held) ? HARVESTABLE_TICKS : UNHARVESTABLE_TICKS;
            return held.getDestroySpeed(state) / hardness / divisor;
        }

        private static void miss(EvalContext ctx) {
            ctx.setOutput("hardness", 0f);
            ctx.setOutput("progress", 0f);
            ctx.setOutput("ticks", -1);
            ctx.setOutput("unbreakable", false);
            ctx.setOutput("exact", false);
        }
    }

    /**
     * Whether breaking a block with a tool will actually drop anything.
     *
     * <p>Stone with a hand still breaks, it just gives nothing back — that gap is what this answers, and it
     * is the check a mining graph needs before it spends the time.
     *
     * <p>{@code requiresTool} is the block's own rule, separate from the verdict, because "yes, and anything
     * works" and "yes, because you happen to be holding the right thing" lead to different decisions when
     * the tool might change.
     *
     * <p>No world input: this is a property of the state and the item, not of a position. Enchantments do
     * not enter into it either — Silk Touch changes what drops, not whether anything does.</p>
     */
    @NodeAttribute(name = "mc_can_harvest", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class CanHarvest extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_can_harvest.tooltip");
        }

        @InputPort public BlockState state;
        @InputPort public ItemStack tool = ItemStack.EMPTY;
        @OutputPort public boolean out;
        @OutputPort public boolean requiresTool;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockState state = ctx.getInput("state", BlockState.class, null);
            if (state == null) {
                ctx.setOutput("out", false);
                ctx.setOutput("requiresTool", false);
                return;
            }
            ItemStack tool = ctx.getInput("tool", ItemStack.class, ItemStack.EMPTY);
            ctx.setOutput("out", harvestable(state, tool == null ? ItemStack.EMPTY : tool));
            ctx.setOutput("requiresTool", state.requiresCorrectToolForDrops());
        }
    }

    /**
     * The game's harvest rule: a block that needs no particular tool always drops, otherwise the tool has to
     * be the right one. Same test {@code Player.hasCorrectToolForDrops} makes.
     */
    private static boolean harvestable(BlockState state, ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }
}
