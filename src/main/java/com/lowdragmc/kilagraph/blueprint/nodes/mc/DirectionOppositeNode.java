package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;

/** The opposite of a {@link Direction}. */
@NodeAttribute(name = "mc_direction_opposite", group = "mc", graphTypes = BlueprintGraph.class)
public class DirectionOppositeNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_direction_opposite.tooltip");
    }


    @InputPort public Direction in = Direction.NORTH;
    @OutputPort public Direction out;

    @Override
    public void evaluate(EvalContext ctx) {
        Direction d = ctx.getInput("in", Direction.class, Direction.NORTH);
        ctx.setOutput("out", d.getOpposite());
    }
}
