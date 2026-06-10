package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.IVertexPositionBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@UseWithContext(VaryingStageNode.class)
@NodeAttribute(name = "rt_vertex_position", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VertexPositionBlock extends ShaderBlockNode implements IVertexPositionBlock {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("position", RenderTypeGraphTypes.VEC4).withoutConfigurator();
    }

    @Override
    public ShaderExpr compilePosition(ShaderCompileContext ctx) {
        if (ctx.isConnected("position")) {
            return ctx.input("position");
        }
        ctx.useMinecraftUniform("DynamicTransforms", "minecraft:dynamictransforms.glsl");
        ctx.useMinecraftUniform("Projection", "minecraft:projection.glsl");
        return new ShaderExpr("ProjMat * ModelViewMat * vec4(Position, 1.0)", GlslType.VEC4);
    }
}
