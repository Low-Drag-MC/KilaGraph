package com.lowdragmc.kilagraph.rendertype.nodes.uv;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity's Flipbook: remaps {@code uv} into one cell of a {@code width}×{@code height} sprite sheet selected
 * by {@code tile} (drive it from a Time/counter for animation). The {@code invertX}/{@code invertY} toggles
 * mirror the tile traversal. {@code uv} defaults to the mesh uv. Ports Unity's {@code Unity_Flipbook}.
 */
@NodeAttribute(name = "rt_flipbook", group = "rendertype_uv", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class FlipbookNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_flipbook.tooltip");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        context.addOption("invertX", TypeHandles.BOOL).withDefaultValue(false).build();
        context.addOption("invertY", TypeHandles.BOOL).withDefaultValue(false).build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.UV);
        context.addInputPort("width", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addInputPort("height", TypeHandles.FLOAT).withDefaultValue(1f);
        context.addInputPort("tile", TypeHandles.FLOAT);
        context.addOutputPort("out", RenderTypeGraphTypes.VEC2);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        String uv = ctx.input("uv").code();
        String w = ctx.input("width").code();
        String h = ctx.input("height").code();
        String ix = flag("invertX") ? "1.0" : "0.0";
        String iy = flag("invertY") ? "1.0" : "0.0";
        ShaderExpr tile = ctx.temp(GlslType.FLOAT, "mod(" + ctx.input("tile").code() + ", " + w + " * " + h + ")");
        ShaderExpr tc = ctx.temp(GlslType.VEC2, "(vec2(1.0) / vec2(" + w + ", " + h + "))");
        ShaderExpr ftile = ctx.temp(GlslType.FLOAT, "floor(" + tile.code() + " * " + tc.code() + ".x)");
        String tileY = "abs(" + iy + " * " + h + " - (" + ftile.code() + " + " + iy + "))";
        String tileX = "abs(" + ix + " * " + w + " - ((" + tile.code() + " - " + w + " * " + ftile.code() + ") + " + ix + "))";
        String code = "((" + uv + " + vec2(" + tileX + ", " + tileY + ")) * " + tc.code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.VEC2));
    }

    @Override
    protected String previewOutputPortId() {
        return "out";
    }

    private boolean flag(String id) {
        INodeOption opt = getNodeOptionById(id);
        Object raw = opt == null ? null : opt.tryGetValue(Object.class).result().orElse(null);
        return raw instanceof Boolean b && b;
    }
}
