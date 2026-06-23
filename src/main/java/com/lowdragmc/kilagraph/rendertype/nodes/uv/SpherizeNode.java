package com.lowdragmc.kilagraph.rendertype.nodes.uv;

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
 * Unity's Spherize: a fisheye-like distortion that pushes the uv outward by the 4th power of its distance
 * from {@code center} (scaled by {@code strength}), plus an {@code offset}. {@code uv} defaults to mesh uv.
 */
@NodeAttribute(name = "rt_spherize", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class SpherizeNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_spherize.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("center", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(0.5f, 0.5f));
        context.addInputPort("strength", TypeHandles.FLOAT).withDefaultValue(10f);
        context.addInputPort("offset", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String uv = ctx.input("uv").code();
        String offset = ctx.input("offset").code();
        String strength = ctx.input("strength").code();
        ShaderExpr d = ctx.temp(GlslType.VEC2, "(" + uv + " - " + ctx.input("center").code() + ")");
        ShaderExpr d2 = ctx.temp(GlslType.FLOAT, "dot(" + d.code() + ", " + d.code() + ")");
        ShaderExpr off = ctx.temp(GlslType.FLOAT, "(" + d2.code() + " * " + d2.code() + " * " + strength + ")");
        String code = "(" + uv + " + " + d.code() + " * " + off.code() + " + " + offset + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
