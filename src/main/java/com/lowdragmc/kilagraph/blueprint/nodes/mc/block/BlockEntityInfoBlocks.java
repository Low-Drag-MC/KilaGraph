package com.lowdragmc.kilagraph.blueprint.nodes.mc.block;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.InfoPropertyBlock;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The properties of a {@link BlockEntity}, one block each, usable only inside
 * {@link BlockEntityInfoNode}.
 *
 * <p>A block entity is the least interesting of the four contexts on purpose: what makes one useful is
 * its <em>contents</em>, and those are specific to its kind — a chest's items, a furnace's progress, a
 * sign's text. None of that is reachable through the {@code BlockEntity} base class, so it goes through
 * NBT instead ({@code mc_nbt_block_entity}, then the {@code mc_nbt_*} nodes). What is here is the
 * identity and placement every block entity shares.
 */
public final class BlockEntityInfoBlocks {

    private static final String GROUP = "mc_block";

    private BlockEntityInfoBlocks() {
    }

    /** Base so each concrete block is only its ports and its read. */
    private abstract static class BeBlock extends InfoPropertyBlock<BlockEntity> {
        @Override
        protected final Class<BlockEntity> targetClass() {
            return BlockEntity.class;
        }
    }

    /** Where it is. */
    @NodeAttribute(name = "block_entity_position", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(BlockEntityInfoNode.class)
    public static class Position extends BeBlock {
        @OutputPort public BlockPos value;

        @Override
        protected void read(BlockEntity be, EvalContext ctx) {
            ctx.setOutput("value", be.getBlockPos());
        }
    }

    /** The state of the block it belongs to — its properties, not just its type. */
    @NodeAttribute(name = "block_entity_state", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(BlockEntityInfoNode.class)
    public static class State extends BeBlock {
        @OutputPort public BlockState value;

        @Override
        protected void read(BlockEntity be, EvalContext ctx) {
            ctx.setOutput("value", be.getBlockState());
        }
    }

    /**
     * The level it lives in.
     *
     * <p>Worth having because it closes a loop: a block entity found by {@code mc_get_block_entity} can
     * hand its own level back, which then feeds a Level Info context or another world query — without the
     * graph having to carry the level along beside it.</p>
     */
    @NodeAttribute(name = "block_entity_level", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(BlockEntityInfoNode.class)
    public static class ContainingLevel extends BeBlock {
        @OutputPort public Level value;
        @OutputPort public boolean present;

        @Override
        protected void read(BlockEntity be, EvalContext ctx) {
            ctx.setOutput("value", be.getLevel());
            // A block entity can exist without a level: one deserialized from an item, or mid-placement.
            ctx.setOutput("present", be.hasLevel());
        }
    }

    /** Which kind of block entity this is, as a registry id, and whether it has been removed. */
    @NodeAttribute(name = "block_entity_identity", group = GROUP, graphTypes = BlueprintGraph.class)
    @UseWithContext(BlockEntityInfoNode.class)
    public static class Identity extends BeBlock {
        @OutputPort public ResourceLocation type;
        @OutputPort public boolean removed;

        @Override
        protected void read(BlockEntity be, EvalContext ctx) {
            ctx.setOutput("type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
            // Removed means the block is gone but this object is still referenced somewhere. Reading its
            // contents afterwards gives stale data, so a graph holding one across ticks should check.
            ctx.setOutput("removed", be.isRemoved());
        }
    }
}
