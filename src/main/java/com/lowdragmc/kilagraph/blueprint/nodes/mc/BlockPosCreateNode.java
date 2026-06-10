package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;

/** Builds a {@link BlockPos} from x/y/z. */
@NodeAttribute(name = "mc_block_pos_create", group = "mc", graphTypes = BlueprintGraph.class)
public class BlockPosCreateNode extends AnnotatedNode {

    @InputPort public int x;
    @InputPort public int y;
    @InputPort public int z;
    @OutputPort public BlockPos out;

    @Override
    public void evaluate(EvalContext ctx) {
        int px = ctx.getInput("x", Integer.class, 0);
        int py = ctx.getInput("y", Integer.class, 0);
        int pz = ctx.getInput("z", Integer.class, 0);
        ctx.setOutput("out", new BlockPos(px, py, pz));
    }
}
