package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

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
@NodeAttribute(name = "rt_vertex_cylindrical_distance", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingCylindricalVertexDistanceBlock extends ShaderBlockNode implements IVaryingBlock {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("distance", TypeHandles.FLOAT).withoutConfigurator();
        context.addOutputPort("distance", TypeHandles.FLOAT);
    }

    @Override
    public String varyingName() {
        return "cylindricalVertexDistance";
    }

    @Override
    public GlslType varyingType() {
        return GlslType.FLOAT;
    }

    @Override
    public ShaderExpr compileVarying(ShaderCompileContext ctx) {
        if (ctx.isConnected("distance")) {
            return ctx.input("distance");
        }
        ctx.include("minecraft:fog.glsl");
        return new ShaderExpr("fog_cylindrical_distance(Position)", GlslType.FLOAT);
    }
}
