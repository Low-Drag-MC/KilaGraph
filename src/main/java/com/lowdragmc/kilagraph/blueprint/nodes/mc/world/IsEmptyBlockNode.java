package com.lowdragmc.kilagraph.blueprint.nodes.mc.world;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** {@code level.isEmptyBlock(pos)} (air). Null level → false. */
@NodeAttribute(name = "mc_is_empty_block", group = "mc_world", graphTypes = BlueprintGraph.class)
public class IsEmptyBlockNode extends AnnotatedNode {

    @InputPort public Level level;
    @InputPort public BlockPos pos = BlockPos.ZERO;
    @OutputPort public boolean out;

    @Override
    public void evaluate(EvalContext ctx) {
        Level l = ctx.getInput("level", Level.class, null);
        BlockPos p = ctx.getInput("pos", BlockPos.class, BlockPos.ZERO);
        ctx.setOutput("out", l != null && l.isEmptyBlock(p));
    }
}
