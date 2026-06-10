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

/** {@code clamp(value, min, max)}: constrains {@code value} into the {@code [min, max]} range. */
@NodeAttribute(name = "rt_clamp", group = "rendertype_math", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class ClampNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("value", TypeHandles.FLOAT);
        context.addInputPort("min", TypeHandles.FLOAT);
        context.addInputPort("max", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String code = "clamp(" + ctx.input("value").code() + ", "
                + ctx.input("min").code() + ", " + ctx.input("max").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
