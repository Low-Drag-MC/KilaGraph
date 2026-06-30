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
 * Fragment output: emissiveness 0..1 (LabPBR {@code _s.a}) — the glow intensity; the glow <em>colour</em> is
 * the albedo (LabPBR carries no separate emission colour). This is the correct float control for emission
 * under a shaderpack, preferred over the vec3 {@code Emission} block (which only does additive emission in
 * our own non-shader pipeline). Shaderpack-only (see {@link FragmentNormalBlock}). Unconnected/absent ⇒ {@code 0}.
 */
@UseWithContext(FragmentStageNode.class)
@NodeAttribute(name = "rt_fragment_emissiveness", group = "rendertype_fragment", graphTypes = RenderTypeGraph.class)
public class FragmentEmissivenessBlock extends ShaderBlockNode implements IFragmentOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_fragment_emissiveness.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("emissiveness", TypeHandles.FLOAT);
    }

    @Override
    public void emitFragment(ShaderCompileContext ctx, FragmentOutputs out) {
        out.emissiveness = ctx.input("emissiveness");
    }
}
