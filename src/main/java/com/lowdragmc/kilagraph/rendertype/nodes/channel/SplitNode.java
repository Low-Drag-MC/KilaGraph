package com.lowdragmc.kilagraph.rendertype.nodes.channel;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Split: splits the input vector into its four channels as floats. The input is
 * {@linkplain RenderTypeGraphTypes#DYNAMIC dynamic}, so a vector narrower than 4 reads the missing
 * channels as {@code 0} (the input is padded to vec4 with zeros, matching Unity).
 */
@NodeAttribute(name = "rt_split", group = "rendertype_channel", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SplitNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.DYNAMIC);
        context.addOutputPort("r", TypeHandles.FLOAT);
        context.addOutputPort("g", TypeHandles.FLOAT);
        context.addOutputPort("b", TypeHandles.FLOAT);
        context.addOutputPort("a", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr in = ctx.inputDynamic("in");
        ShaderExpr v = ctx.temp(GlslType.VEC4, ChannelGlsl.toVec4(in));
        ctx.output("r", new ShaderExpr(v.code() + ".x", GlslType.FLOAT));
        ctx.output("g", new ShaderExpr(v.code() + ".y", GlslType.FLOAT));
        ctx.output("b", new ShaderExpr(v.code() + ".z", GlslType.FLOAT));
        ctx.output("a", new ShaderExpr(v.code() + ".w", GlslType.FLOAT));
    }

    @Override
    public String glslExample() {
        return """
                vec4 v = vec4(in, 0.0, 0.0, 0.0);
                r = v.x; g = v.y; b = v.z; a = v.w;""";
    }
}
