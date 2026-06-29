package com.lowdragmc.kilagraph.rendertype.nodes.fragment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.FragmentOutputs;
import com.lowdragmc.kilagraph.rendertype.compiler.IFragmentOutputBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Fragment output: porosity 0..1 (LabPBR {@code _s.b} low range). Shaderpack-only (see
 * {@link FragmentNormalBlock}). Unconnected/absent ⇒ {@code 0}. Mutually exclusive with subsurface
 * (SSS wins when both are set), per LabPBR's shared {@code _s.b} channel.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_porosity", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentPorosityBlock extends ShaderBlockNode implements IFragmentOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_porosity.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("porosity", TypeHandles.FLOAT);
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        out.porosity = ctx.input("porosity");
    }
}
