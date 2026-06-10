package com.lowdragmc.kilagraph.blueprint.nodes.mc.info;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.neoforged.neoforge.fluids.FluidStack;

/** Reads properties off a {@link FluidStack} (amount, fluid, isEmpty, …). */
@NodeAttribute(name = "info_fluid_stack", group = "mc_info", graphTypes = BlueprintGraph.class)
public class FluidStackInfoNode extends InfoContextNode<FluidStack> {
    @Override
    protected Class<FluidStack> targetClass() {
        return FluidStack.class;
    }
}
