package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Offsets a {@link BlockPos} by {@code amount} blocks in {@code direction}. */
@NodeAttribute(name = "mc_block_pos_offset", group = "mc", graphTypes = BlueprintGraph.class)
public class BlockPosOffsetNode extends AnnotatedNode {

    @InputPort public BlockPos pos = BlockPos.ZERO;
    @InputPort public Direction direction = Direction.NORTH;
    @InputPort public int amount = 1;
    @OutputPort public BlockPos out;

    @Override
    public void evaluate(EvalContext ctx) {
        BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
        Direction d = ctx.getInput("direction", Direction.class, Direction.NORTH);
        int n = ctx.getInput("amount", Integer.class, 1);
        ctx.setOutput("out", p.relative(d, n));
    }
}
