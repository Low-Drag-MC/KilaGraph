package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;

/** The axis of a {@link Direction} as a string ("x"/"y"/"z"). */
@NodeAttribute(name = "mc_direction_axis", group = "mc", graphTypes = BlueprintGraph.class)
public class DirectionAxisNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_direction_axis.tooltip");
    }


    @InputPort public Direction in = Direction.NORTH;
    @OutputPort public String out;

    @Override
    public void evaluate(EvalContext ctx) {
        Direction d = ctx.getInput("in", Direction.class, Direction.NORTH);
        ctx.setOutput("out", d.getAxis().getName());
    }
}
