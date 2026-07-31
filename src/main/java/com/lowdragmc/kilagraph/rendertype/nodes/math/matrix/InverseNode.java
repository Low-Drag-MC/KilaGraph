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

/** {@code inverse(m)}: the inverse of a {@code mat4}. */
@NodeAttribute(name = "rt_mat4_inverse", group = "rendertype_math/matrix", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class InverseNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("m", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("out", RenderTypeGraphTypes.MAT4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("inverse(" + ctx.input("m").code() + ")", GlslType.MAT4));
    }

    @Override
    public String glslExample() {
        return """
                out = inverse(m);""";
    }
}
