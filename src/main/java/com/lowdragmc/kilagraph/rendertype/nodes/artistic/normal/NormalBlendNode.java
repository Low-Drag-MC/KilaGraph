package com.lowdragmc.kilagraph.rendertype.nodes.artistic.normal;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.nodes.artistic.ArtisticNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Normal Blend (Default/whiteout mode): combines two tangent-space normals {@code a} and
 * {@code b} into one — {@code normalize(vec3(a.xy + b.xy, a.z * b.z))}.
 */
@NodeAttribute(name = "rt_normal_blend", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalBlendNode extends ArtisticNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.VEC3);
        context.addInputPort("b", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr a = ctx.temp(GlslType.VEC3, ctx.input("a").code());
        ShaderExpr b = ctx.temp(GlslType.VEC3, ctx.input("b").code());
        ctx.output("out", new ShaderExpr("normalize(vec3(" + a.code() + ".xy + " + b.code() + ".xy, "
                + a.code() + ".z * " + b.code() + ".z))", GlslType.VEC3));
    }
}
