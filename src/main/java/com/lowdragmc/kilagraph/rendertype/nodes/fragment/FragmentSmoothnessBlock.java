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
 * Fragment output: perceptual smoothness 0..1 (LabPBR {@code _s.r}). Shaderpack-only (see
 * {@link FragmentNormalBlock}). Unconnected/absent ⇒ {@code 0}.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_smoothness", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentSmoothnessBlock extends ShaderBlockNode implements IFragmentOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_smoothness.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("smoothness", TypeHandles.FLOAT);
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        out.smoothness = ctx.input("smoothness");
    }
}
