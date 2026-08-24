package com.lowdragmc.kilagraph.blueprint.nodes.mc.redstone;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Reading redstone.
 *
 * <h2>Read-only, deliberately</h2>
 * There is no "set the power here" node, because the game has no such operation: redstone power is not
 * stored at a position, it is <em>computed</em> from what the surrounding blocks emit. A graph that wants
 * to emit a signal places something that emits one — a redstone block, a lever, a repeater — with
 * {@code mc_set_block}, which is exactly what a player would do. A setter would have to lie.
 *
 * <h2>The four questions, which are genuinely different</h2>
 * Redstone has more than one notion of "powered" and picking the wrong one is the classic mistake:
 * <ul>
 *   <li><b>Best neighbour signal</b> — the strongest signal reaching this block from any side. This is
 *       what a lamp uses, and what most graphs mean by "is it on".</li>
 *   <li><b>Signal from a side</b> — what one particular neighbour is providing, which is how a comparator
 *       reads its sides differently from its back.</li>
 *   <li><b>Direct signal</b> — strong power only, ignoring the weak power a block passes on after being
 *       energised. The difference is why a redstone dust next to a powered block lights up but a repeater
 *       facing it may not.</li>
 *   <li><b>Analog output</b> — a container's fullness as 0-15, which is what a comparator reads. Nothing
 *       to do with the signal reaching the block.</li>
 * </ul>
 */
public final class RedstoneNodes {

    private static final String GROUP = "mc_redstone";

    private RedstoneNodes() {
    }

    /**
     * The strongest signal reaching a position, and whether there is any.
     *
     * <p>What most graphs mean by "is this powered". Both outputs come from one node because the boolean
     * is just {@code power > 0} and asking for them separately would evaluate the same neighbour scan
     * twice.</p>
     */
    @NodeAttribute(name = "mc_redstone_power", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Power extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_redstone_power.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public int power;
        @OutputPort public boolean powered;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("power", 0);
                ctx.setOutput("powered", false);
                return;
            }
            int power = world.getBestNeighborSignal(at);
            ctx.setOutput("power", power);
            ctx.setOutput("powered", power > 0);
        }
    }

    /**
     * The signal reaching a position from one particular side.
     *
     * <p>The per-side version of {@code mc_redstone_power}: {@code side = UP} asks what the block above is
     * providing. Weak and strong power are both included; for strong power alone use
     * {@code mc_redstone_direct_signal}.
     *
     * <h2>The direction is normalised, and vanilla's is not</h2>
     * Minecraft's own {@code getSignal(pos, direction)} does <em>not</em> mean "the signal reaching pos
     * from direction" — it asks the block <b>at</b> pos what it emits, with the direction reversed. The
     * game's source carries a note saying "directions in redstone signal related methods are backwards",
     * and getting it wrong reads as a constant zero rather than as an error. This node queries
     * {@code pos.relative(side)} so that {@code side} means what it says, which is also exactly what
     * {@code getBestNeighborSignal} does internally.
     */
    @NodeAttribute(name = "mc_redstone_signal", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Signal extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_redstone_signal.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Direction side = Direction.UP;
        @OutputPort public int power;
        @OutputPort public boolean powered;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("power", 0);
                ctx.setOutput("powered", false);
                return;
            }
            Direction from = ctx.getInput("side", Direction.class, Direction.UP);
            Direction side = from == null ? Direction.UP : from;
            // pos.relative(side), not pos: see the class note on vanilla's reversed convention.
            int power = world.getSignal(at.relative(side), side);
            ctx.setOutput("power", power);
            ctx.setOutput("powered", power > 0);
        }
    }

    /**
     * Strong power only, from one side.
     *
     * <p>Strong power is what a redstone block or a repeater's output puts <em>into</em> a solid block;
     * weak power is what that energised block then passes to dust beside it. A repeater will not accept
     * weak power, which is why the two are separate questions and why a graph emulating repeater
     * behaviour needs this one.</p>
     */
    @NodeAttribute(name = "mc_redstone_direct_signal", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class DirectSignal extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_redstone_direct_signal.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Direction side = Direction.UP;
        @OutputPort public int power;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("power", 0);
                return;
            }
            Direction from = ctx.getInput("side", Direction.class, Direction.UP);
            Direction side = from == null ? Direction.UP : from;
            ctx.setOutput("power", world.getDirectSignal(at.relative(side), side));
        }
    }

    /**
     * What a comparator would read from a block.
     *
     * <p>Container fullness as 0-15 for a chest or a hopper, but also the specific values other blocks
     * define: a cake's remaining slices, a cauldron's level, a jukebox's disc. Blocks with no comparator
     * behaviour report zero and {@code hasOutput = false}, which distinguishes "empty container" from
     * "not a container at all" — the two look the same on the number alone.</p>
     */
    @NodeAttribute(name = "mc_comparator_output", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ComparatorOutput extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_comparator_output.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        /**
         * The face the reading is taken from — 26.1 added it to
         * {@code BlockState.getAnalogOutputSignal}, the way a comparator passes the face it touches.
         * Vanilla blocks ignore it, but a modded block is free not to, so it is a port rather than a
         * hidden constant.
         */
        @InputPort public Direction side = Direction.UP;
        @OutputPort public int signal;
        @OutputPort public boolean hasOutput;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("signal", 0);
                ctx.setOutput("hasOutput", false);
                return;
            }
            Direction from = ctx.getInput("side", Direction.class, Direction.UP);
            var state = world.getBlockState(at);
            boolean has = state.hasAnalogOutputSignal();
            ctx.setOutput("hasOutput", has);
            ctx.setOutput("signal", has ? state.getAnalogOutputSignal(world, at, from) : 0);
        }
    }
}
