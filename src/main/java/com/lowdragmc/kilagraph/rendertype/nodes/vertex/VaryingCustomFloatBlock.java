package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.IVaryingBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@UseWithContext(VaryingStageNode.class)
@NodeAttribute(name = "rt_vertex_custom_float", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingCustomFloatBlock extends ShaderBlockNode implements IVaryingBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_custom_float.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("value", TypeHandles.FLOAT);
        context.addOutputPort("value", TypeHandles.FLOAT);
    }

    @Override
    public String varyingName() {
        return "vc_" + Integer.toHexString(getNodeModel().getUid().hashCode());
    }

    @Override
    public GlslType varyingType() {
        return GlslType.FLOAT;
    }

    @Override
    public ShaderExpr compileVarying(ShaderCompileContext ctx) {
        return ctx.input("value");
    }

    @Override
    public String glslExample() {
        return IVaryingBlock.varyingGlslExample("float");
    }
}
