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
 * Fragment output: parallax height 0..1 (LabPBR {@code _n.a}). Shaderpack-only (see
 * {@link FragmentNormalBlock}). Unconnected/absent ⇒ {@code 1}.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_height", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentHeightBlock extends ShaderBlockNode implements IFragmentOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_height.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("height", TypeHandles.FLOAT);
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        out.height = ctx.input("height");
    }
}
