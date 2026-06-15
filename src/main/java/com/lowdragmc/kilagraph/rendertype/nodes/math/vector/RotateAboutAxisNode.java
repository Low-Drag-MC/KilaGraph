package com.lowdragmc.kilagraph.rendertype.nodes.math.vector;

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
 * Rotates {@code in} (vec3) about {@code axis} by {@code angle} radians (Rodrigues' rotation formula):
 * {@code in*cos + cross(axis,in)*sin + axis*dot(axis,in)*(1-cos)}, with {@code axis} normalized.
 */
@NodeAttribute(name = "rt_rotate_about_axis", group = "rendertype_math/vector", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RotateAboutAxisNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("in", RenderTypeGraphTypes.VEC3);
        context.addInputPort("axis", RenderTypeGraphTypes.VEC3);
        context.addInputPort("angle", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC3);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String v = ctx.input("in").code();
        ShaderExpr axis = ctx.temp(GlslType.VEC3, "normalize(" + ctx.input("axis").code() + ")");
        String angle = ctx.input("angle").code();
        ShaderExpr s = ctx.temp(GlslType.FLOAT, "sin(" + angle + ")");
        ShaderExpr c = ctx.temp(GlslType.FLOAT, "cos(" + angle + ")");
        String ax = axis.code();
        String code = "(" + v + " * " + c.code() + " + cross(" + ax + ", " + v + ") * " + s.code()
                + " + " + ax + " * dot(" + ax + ", " + v + ") * (1.0 - " + c.code() + "))";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC3));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
