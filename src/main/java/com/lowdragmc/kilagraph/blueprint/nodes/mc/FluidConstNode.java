package com.lowdragmc.kilagraph.blueprint.nodes.mc;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.Option;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Emits a constant {@link Fluid} chosen from the inspector picker (registry accessor). */
@NodeAttribute(name = "mc_fluid_const", group = "mc", graphTypes = BlueprintGraph.class)
public class FluidConstNode extends AnnotatedNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.mc_fluid_const.tooltip");
    }


    @Option public Fluid value = Fluids.WATER;
    @OutputPort public Fluid out;

    @Override
    public void evaluate(EvalContext ctx) {
        ctx.setOutput("out", ctx.getOption("value", Fluid.class, Fluids.WATER));
    }
}
