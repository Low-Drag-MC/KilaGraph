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
 * Unity's Normal Reconstruct Z: rebuilds a unit normal's Z from its {@code xy} (as stored in a 2-channel
 * normal map) — {@code z = sqrt(1 - saturate(dot(xy, xy)))}, then normalizes.
 */
@NodeAttribute(name = "rt_normal_reconstruct_z", group = "rendertype_artistic/normal", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class NormalReconstructZNode extends ArtisticNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr xy = ctx.temp(GlslType.VEC2, ctx.input("in").code());
        String z = "sqrt(1.0 - clamp(dot(" + xy.code() + ", " + xy.code() + "), 0.0, 1.0))";
        ctx.output("out", new ShaderExpr("normalize(vec3(" + xy.code() + ", " + z + "))", GlslType.VEC3));
    }
}
