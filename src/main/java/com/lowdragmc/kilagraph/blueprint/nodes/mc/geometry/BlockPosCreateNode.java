package com.lowdragmc.kilagraph.blueprint.nodes.mc.geometry;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.BlockPos;

/** Builds a {@link BlockPos} from x/y/z. */
@NodeAttribute(name = "mc_block_pos_create", group = "mc/geometry", graphTypes = BlueprintGraph.class)
public class BlockPosCreateNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_block_pos_create.tooltip");
    }


    @InputPort public int x;
    @InputPort public int y;
    @InputPort public int z;
    @OutputPort public BlockPos out;

    @Override
    public void evaluate(EvalContext ctx) {
        int px = ctx.getInt("x", 0);
        int py = ctx.getInt("y", 0);
        int pz = ctx.getInt("z", 0);
        ctx.setOutput("out", new BlockPos(px, py, pz));
    }
}
