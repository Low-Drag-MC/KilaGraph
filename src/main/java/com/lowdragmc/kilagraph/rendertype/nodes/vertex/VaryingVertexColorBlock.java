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
@NodeAttribute(name = "rt_vertex_color", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VaryingVertexColorBlock extends ShaderBlockNode implements IVaryingBlock {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("color", RenderTypeGraphTypes.VEC4).withoutConfigurator();
        context.addOutputPort("color", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public String varyingName() {
        return "vertexColor";
    }

    @Override
    public GlslType varyingType() {
        return GlslType.VEC4;
    }

    @Override
    public ShaderExpr compileVarying(ShaderCompileContext ctx) {
        if (ctx.isConnected("color")) {
            return ctx.input("color");
        }
        // Unconnected → vanilla per-vertex diffuse lighting (minecraft_mix_light), mirroring how the
        // distance blocks default to fog_*_distance. The MixLight node stays available to wire an
        // explicit colour/normal; the default shader no longer needs to wire it in. Normal/Color route
        // through ctx.attribute so removing those elements from the format degrades to safe constants
        // (up / white) instead of emitting undefined-variable GLSL.
        ctx.useMinecraftUniform("Lighting", "minecraft:light.glsl");
        ShaderExpr normal = ctx.attribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElements.NORMAL,
                GlslType.VEC3, new ShaderExpr("vec3(0.0, 1.0, 0.0)", GlslType.VEC3));
        ShaderExpr color = ctx.attribute(com.lowdragmc.kilagraph.rendertype.format.KGVertexElements.COLOR,
                GlslType.VEC4, new ShaderExpr("vec4(1.0)", GlslType.VEC4));
        return new ShaderExpr("minecraft_mix_light(Light0_Direction, Light1_Direction, "
                + normal.code() + ", " + color.code() + ")", GlslType.VEC4);
    }
}
