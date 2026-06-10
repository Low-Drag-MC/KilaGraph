package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Emits a constant {@link Block} chosen from the inspector picker (registry accessor). */
@NodeAttribute(name = "mc_block_const", group = "mc", graphTypes = BlueprintGraph.class)
public class BlockConstNode extends AnnotatedNode {

    @Option public Block value = Blocks.STONE;
    @OutputPort public Block out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption("value", Block.class, Blocks.STONE));
    }
}
