package com.lowdragmc.kilagraph.rendertype.nodes.math;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/** {@code smoothstep(edge0, edge1, x)}: smooth Hermite interpolation between two edges. */
@NodeAttribute(name = "rt_smoothstep", group = "rendertype_math", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SmoothstepNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("edge0", TypeHandles.FLOAT);
        context.addInputPort("edge1", TypeHandles.FLOAT);
        context.addInputPort("x", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String code = "smoothstep(" + ctx.input("edge0").code() + ", "
                + ctx.input("edge1").code() + ", " + ctx.input("x").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
