package com.lowdragmc.kilagraph.rendertype.nodes.scene;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_globals_ubo", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class GlobalsUboNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_globals_ubo.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("CameraBlockPos", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("CameraOffset", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("ScreenSize", RenderTypeGraphTypes.VEC2);
        context.addOutputPort("GlintAlpha", TypeHandles.FLOAT);
        context.addOutputPort("GameTime", TypeHandles.FLOAT);
        context.addOutputPort("MenuBlurRadius", TypeHandles.INT);
        context.addOutputPort("UseRgss", TypeHandles.INT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // 1.21.1 has no globals.glsl. ScreenSize/GlintAlpha/GameTime are individual vanilla uniforms
        // (setDefaultUniforms); camera world position is our KG-managed kg_CameraPos (camera-relative render),
        // split into the block/offset pair the modern Globals UBO exposed. MenuBlurRadius/UseRgss: no 1.21.1 source.
        String camPos = ctx.useBuiltinUniform("kg_CameraPos", GlslType.VEC3);
        ctx.output("CameraBlockPos", new ShaderExpr("floor(" + camPos + ")", GlslType.VEC3));
        ctx.output("CameraOffset", new ShaderExpr("(floor(" + camPos + ") - " + camPos + ")", GlslType.VEC3));
        ctx.output("ScreenSize", new ShaderExpr(ctx.useBuiltinUniform("ScreenSize", GlslType.VEC2), GlslType.VEC2));
        ctx.output("GlintAlpha", new ShaderExpr(ctx.useBuiltinUniform("GlintAlpha", GlslType.FLOAT), GlslType.FLOAT));
        ctx.output("GameTime", new ShaderExpr(ctx.useBuiltinUniform("GameTime", GlslType.FLOAT), GlslType.FLOAT));
        ctx.output("MenuBlurRadius", new ShaderExpr("0", GlslType.INT));
        ctx.output("UseRgss", new ShaderExpr("0", GlslType.INT));
    }
}
