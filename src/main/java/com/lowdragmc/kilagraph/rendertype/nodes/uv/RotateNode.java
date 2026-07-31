package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;
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
 * Unity's Rotate: rotates {@code uv} around {@code center} by {@code rotation} radians. {@code uv}
 * defaults to the mesh uv (UV-typed port).
 */
@NodeAttribute(name = "rt_rotate", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class RotateNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_rotate.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("unit", AngleUnit.class).withDefaultValue(AngleUnit.RADIANS);
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("center", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(0.5f, 0.5f));
        context.addInputPort("rotation", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String center = ctx.input("center").code();
        String rot = ctx.input("rotation").code();
        if (ctx.option("unit", AngleUnit.class, AngleUnit.RADIANS) == AngleUnit.DEGREES) {
            rot = "radians(" + rot + ")";
        }
        ShaderExpr p = ctx.temp(GlslType.VEC2, "(" + ctx.input("uv").code() + " - " + center + ")");
        ShaderExpr c = ctx.temp(GlslType.FLOAT, "cos(" + rot + ")");
        ShaderExpr s = ctx.temp(GlslType.FLOAT, "sin(" + rot + ")");
        String code = "(" + center + " + vec2("
                + c.code() + " * " + p.code() + ".x - " + s.code() + " * " + p.code() + ".y, "
                + s.code() + " * " + p.code() + ".x + " + c.code() + " * " + p.code() + ".y))";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    @Override
    public String glslExample() {
        return """
                vec2 p = uv - center;
                float c = cos(rotation), s = sin(rotation);
                out = center + vec2(c * p.x - s * p.y,
                                    s * p.x + c * p.y);""";
    }
}
