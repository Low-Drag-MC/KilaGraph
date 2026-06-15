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

/** Builds a {@code mat4} from four {@code vec4} columns ({@code mat4(c0, c1, c2, c3)}, column-major). */
@NodeAttribute(name = "rt_mat4_construct", group = "rendertype_math/matrix", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class Mat4ConstructNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("c0", RenderTypeGraphTypes.VEC4);
        context.addInputPort("c1", RenderTypeGraphTypes.VEC4);
        context.addInputPort("c2", RenderTypeGraphTypes.VEC4);
        context.addInputPort("c3", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("out", RenderTypeGraphTypes.MAT4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String code = "mat4(" + ctx.input("c0").code() + ", " + ctx.input("c1").code() + ", "
                + ctx.input("c2").code() + ", " + ctx.input("c3").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.MAT4));
    }
}
