package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import net.minecraft.network.chat.Component;
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
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_position.tooltip");
    }

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
        ctx.useMinecraftUniform("Projection", "minecraft:projection.glsl");
        // Match vanilla block.vsh: transform Position + ModelOffset (modelPosition adds the dynamictransforms import).
        return new ShaderExpr("ProjMat * ModelViewMat * vec4(" + ctx.modelPosition().code() + ", 1.0)", GlslType.VEC4);
    }

    @Override
    public String glslExample() {
        return """
                // connected
                gl_Position = position;
                // unconnected (the vanilla transform)
                gl_Position = ProjMat * ModelViewMat
                            * vec4(Position, 1.0);""";
    }
}
