package com.lowdragmc.kilagraph.rendertype.nodes.uv;

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
import org.joml.Vector2f;

/**
 * Unity's Polar Coordinates: converts the uv to polar form relative to {@code center} — {@code out.x} is
 * the radius ({@code radialScale}-scaled), {@code out.y} the angle ({@code lengthScale}-scaled). {@code uv}
 * defaults to the mesh uv.
 */
@NodeAttribute(name = "rt_polar_coordinates", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class PolarCoordinatesNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("center", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(0.5f, 0.5f));
        context.addInputPort("radialScale", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addInputPort("lengthScale", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String radialScale = ctx.input("radialScale").code();
        String lengthScale = ctx.input("lengthScale").code();
        ShaderExpr d = ctx.temp(GlslType.VEC2, "(" + ctx.input("uv").code() + " - " + ctx.input("center").code() + ")");
        String radius = "(length(" + d.code() + ") * 2.0 * " + radialScale + ")";
        String angle = "(atan(" + d.code() + ".x, " + d.code() + ".y) * 0.15915494 * " + lengthScale + ")";
        ctx.output("out", new ShaderExpr("vec2(" + radius + ", " + angle + ")", GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
