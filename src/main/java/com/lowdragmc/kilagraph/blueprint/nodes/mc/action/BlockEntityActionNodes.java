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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Writing to a block entity. See {@link McActions} for the rules every action shares.
 *
 * <h2>Three steps, and skipping any of them is a bug</h2>
 * Loading NBT into a block entity is the easy part. What makes the change actually take effect is what
 * comes after:
 * <ol>
 *   <li>{@code loadCustomOnly} applies the data;</li>
 *   <li>{@code setChanged} marks the chunk dirty, without which the change is lost on world save;</li>
 *   <li>{@code sendBlockUpdated} tells clients, without which the server and the screen disagree until
 *       something else happens to resend the block.</li>
 * </ol>
 * A node that did only the first would look correct in a single-player test and be wrong in every real
 * use. {@code McActionGameTest} asserts the value survives a re-read for exactly this reason.
 */
public final class BlockEntityActionNodes {

    private static final String GROUP = "mc/action";

    private BlockEntityActionNodes() {
    }

    /**
     * Replaces a block entity's stored data with the given NBT.
     *
     * <h2>This is the escape hatch, not the front door</h2>
     * It writes whatever the tag says, with no validation: a malformed tag produces a broken block entity
     * rather than an error, because the game's own loaders are lenient by design. Read the current data
     * with {@code mc_nbt_block_entity} first, change the one key you mean to change, and write it back —
     * writing a tag built from nothing will drop every field you did not include.
     *
     * <p>For inventories specifically, prefer the {@code mc_container_*} nodes: they go through the
     * capability, work on modded blocks, and cannot corrupt anything.
     */
    @NodeAttribute(name = "mc_set_block_entity_nbt", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetNbt extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_set_block_entity_nbt.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public CompoundTag nbt;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            var world = McActions.writableLevel(ctx);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            CompoundTag tag = ctx.getInput("nbt", CompoundTag.class, null);
            if (world == null || at == null || tag == null) {
                McActions.done(ctx, false);
                return;
            }
            BlockEntity be = world.getBlockEntity(at);
            if (be == null) {
                McActions.done(ctx, false);
                return;
            }
            // 26.1 reads block-entity NBT through ValueInput; TagValueInput is the CompoundTag-backed
            // one, and DISCARDING drops the parse problems this node has nowhere to report anyway.
            be.loadCustomOnly(TagValueInput.create(
                    ProblemReporter.DISCARDING, world.registryAccess(), tag));
            // Without this the change is lost when the chunk saves.
            be.setChanged();
            // And without this the client keeps showing the old contents until something else resends
            // the block — the same state and flags on both sides is a "nothing structural changed,
            // just resend me" update.
            BlockState state = world.getBlockState(at);
            world.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
            McActions.done(ctx, true);
        }
    }
}
