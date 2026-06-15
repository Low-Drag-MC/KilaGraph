package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import org.joml.Vector2f;

/**
 * Unity-style Tiling And Offset: {@code uv * tiling + offset}. An unconnected {@code uv} defaults to
 * the mesh uv and an unconnected {@code tiling} defaults to (1,1), so the node is useful with nothing
 * wired in.
 */
@NodeAttribute(name = "rt_tiling_offset", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class TilingAndOffsetNode extends ShaderNode {
    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("tiling", RenderTypeGraphTypes.VEC2).withDefaultValue(new Vector2f(1, 1));
        context.addInputPort("offset", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        ShaderExpr uv = ctx.input("uv");
        ShaderExpr tiling = ctx.input("tiling");
        ShaderExpr offset = ctx.input("offset");
        ctx.output("out", new ShaderExpr("(" + uv.code() + " * " + tiling.code() + " + " + offset.code() + ")", GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }
}
