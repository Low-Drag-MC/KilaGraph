package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code level.getBlockState(pos)}. {@code level} is a wire-only input (no embedded constant) —
 * feed it from a graph variable or a host-provided source node. Null level → null output.
 */
@NodeAttribute(name = "mc_get_block_state", group = "mc/world", graphTypes = BlueprintGraph.class)
public class GetBlockStateNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_get_block_state.tooltip");
    }


    @InputPort public Level level;
    @InputPort public BlockPos pos = BlockPos.ZERO;
    @OutputPort public BlockState out;

    @Override
    public void evaluate(EvalContext ctx) {
        Level l = ctx.getInput("level", Level.class, null);
        BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
        ctx.setOutput("out", l == null ? null : l.getBlockState(p));
    }
}
