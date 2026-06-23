package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.Direction;

/** Emits a constant {@link Direction} chosen from the inspector dropdown (enum accessor). */
@NodeAttribute(name = "mc_direction_const", group = "mc", graphTypes = BlueprintGraph.class)
public class DirectionConstNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_direction_const.tooltip");
    }


    @Option public Direction value = Direction.NORTH;
    @OutputPort public Direction out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption("value", Direction.class, Direction.NORTH));
    }
}
