package com.lowdragmc.kilagraph.rendertype.nodes.fragment;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.FragmentOutputs;
import com.lowdragmc.kilagraph.rendertype.compiler.IFragmentOutputBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Fragment output: the tangent-space surface normal (LabPBR {@code _n.rg}). Only affects rendering under an
 * Iris shaderpack (the injection path encodes it into the {@code normals} sampler); our own pipeline /
 * editor preview ignore it. Unconnected/absent ⇒ flat {@code (0,0,1)}.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_normal", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentNormalBlock extends ShaderBlockNode implements IFragmentOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_normal.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        out.normalTS = ctx.input("normal");
    }
}
