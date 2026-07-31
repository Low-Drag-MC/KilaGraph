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

/** {@code m * v}: transforms a {@code vec4} by a {@code mat4}, yielding a {@code vec4}. */
@NodeAttribute(name = "rt_mat4_transform", group = "rendertype_math/matrix", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class Mat4TransformNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("m", RenderTypeGraphTypes.MAT4);
        context.addInputPort("v", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("(" + ctx.input("m").code() + " * " + ctx.input("v").code() + ")", GlslType.VEC4));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = m * v;""";
    }
}
