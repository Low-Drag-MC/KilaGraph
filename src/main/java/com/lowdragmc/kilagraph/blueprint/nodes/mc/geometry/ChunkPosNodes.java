package com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunk coordinates: construction, decomposition, and the conversions to and from block space where
 * the 16-block shift lives — the one part a user should not be doing by hand.
 */
public final class ChunkPosNodes {

    private static final String GROUP = "mc_geometry";

    private ChunkPosNodes() {
    }

    /**
     * A chunk's coordinates and the block range it covers.
     *
     * <p>The block bounds are here rather than in a node of their own because they are what a chunk
     * coordinate is usually wanted <em>for</em> — iterating the blocks in a chunk needs all four, and
     * computing them by hand is the {@code x << 4} / {@code +15} arithmetic this file exists to keep out
     * of graphs. {@code mc_chunk_pos_origin} still covers the common "just the corner, as a
     * {@code BlockPos}" case.</p>
     */
    @NodeAttribute(name = "mc_chunk_pos_unpack", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Unpack extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_chunk_pos_unpack.tooltip");
        }

        @InputPort public ChunkPos in;
        @OutputPort public int x;
        @OutputPort public int z;
        @OutputPort public int minBlockX;
        @OutputPort public int minBlockZ;
        @OutputPort public int maxBlockX;
        @OutputPort public int maxBlockZ;

        @Override
        public void evaluate(EvalContext ctx) {
            ChunkPos c = ctx.getInput("in", ChunkPos.class, null);
            if (c == null) c = new ChunkPos(0, 0);
            ctx.setOutput("x", c.x);
            ctx.setOutput("z", c.z);
            ctx.setOutput("minBlockX", c.getMinBlockX());
            ctx.setOutput("minBlockZ", c.getMinBlockZ());
            ctx.setOutput("maxBlockX", c.getMaxBlockX());
            ctx.setOutput("maxBlockZ", c.getMaxBlockZ());
        }
    }

    @NodeAttribute(name = "mc_chunk_pos_create", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Create extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_chunk_pos_create.tooltip");
        }

        @InputPort public int x = 0;
        @InputPort public int z = 0;
        @OutputPort public ChunkPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            ctx.setOutput("out", new ChunkPos(ctx.getInt("x", 0), ctx.getInt("z", 0)));
        }
    }

    /** Which chunk a block position falls in. */
    @NodeAttribute(name = "mc_chunk_pos_from_block_pos", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FromBlockPos extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_chunk_pos_from_block_pos.tooltip");
        }

        @InputPort public BlockPos pos = BlockPos.ZERO;
        @OutputPort public ChunkPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
            ctx.setOutput("out", new ChunkPos(p == null ? BlockPos.ZERO : p));
        }
    }

    /**
     * The chunk's north-west corner block, at y = 0.
     *
     * <p>{@code ChunkPos.getWorldPosition()} — the corner rather than the centre, because that is the
     * origin every chunk-relative calculation counts from.</p>
     */
    @NodeAttribute(name = "mc_chunk_pos_origin", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Origin extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_chunk_pos_origin.tooltip");
        }

        @InputPort public ChunkPos in;
        @OutputPort public BlockPos out;

        @Override
        public void evaluate(EvalContext ctx) {
            ChunkPos c = ctx.getInput("in", ChunkPos.class, null);
            ctx.setOutput("out", c == null ? BlockPos.ZERO : c.getWorldPosition());
        }
    }
}
