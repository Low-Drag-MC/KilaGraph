package com.lowdragmc.kilagraph.rendertype.nodes.math.matrix;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** Splits a {@code mat4} into its four {@code vec4} columns ({@code m[0]..m[3]}). */
@NodeAttribute(name = "rt_mat4_split", group = "rendertype_math/matrix", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class Mat4SplitNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("c0", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("c1", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("c2", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("c3", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String in = ctx.input("in").code();
        ctx.output("c0", new ShaderExpr("(" + in + ")[0]", GlslType.VEC4));
        ctx.output("c1", new ShaderExpr("(" + in + ")[1]", GlslType.VEC4));
        ctx.output("c2", new ShaderExpr("(" + in + ")[2]", GlslType.VEC4));
        ctx.output("c3", new ShaderExpr("(" + in + ")[3]", GlslType.VEC4));
    }

    @Override
    public String glslExample() {
        return """
                c0 = in[0]; c1 = in[1];
                c2 = in[2]; c3 = in[3];""";
    }
}
