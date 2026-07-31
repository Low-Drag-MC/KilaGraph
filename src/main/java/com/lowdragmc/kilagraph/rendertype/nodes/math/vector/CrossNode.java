package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** {@code cross(a, b)}: the vec3 cross product of two vec3s. */
@NodeAttribute(name = "rt_cross", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class CrossNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("a", RenderTypeGraphTypes.VEC3);
        context.addInputPort("b", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ctx.output("out", new ShaderExpr("cross(" + ctx.input("a").code() + ", " + ctx.input("b").code() + ")", GlslType.VEC3));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                out = cross(a, b);""";
    }
}
