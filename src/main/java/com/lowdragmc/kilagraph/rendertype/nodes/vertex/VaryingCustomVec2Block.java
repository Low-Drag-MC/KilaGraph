package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.IVaryingBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@UseWithContext(VaryingStageNode.class)
@NodeAttribute(name = "rt_vertex_custom_vec2", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingCustomVec2Block extends ShaderBlockNode implements IVaryingBlock {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("value", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("value", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public String varyingName() {
        return "vc_" + Integer.toHexString(getNodeModel().getUid().hashCode());
    }

    @Override
    public GlslType varyingType() {
        return GlslType.VEC2;
    }

    @Override
    public ShaderExpr compileVarying(ShaderCompileContext ctx) {
        return ctx.input("value");
    }
}
