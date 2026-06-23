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
        ctx.useMinecraftUniform("Globals", "minecraft:globals.glsl");
        // CameraBlockPos is an ivec3 in the UBO; cast to vec3 for the float-vector port.
        ctx.output("CameraBlockPos", new ShaderExpr("vec3(CameraBlockPos)", GlslType.VEC3));
        ctx.output("CameraOffset", new ShaderExpr("CameraOffset", GlslType.VEC3));
        ctx.output("ScreenSize", new ShaderExpr("ScreenSize", GlslType.VEC2));
        ctx.output("GlintAlpha", new ShaderExpr("GlintAlpha", GlslType.FLOAT));
        ctx.output("GameTime", new ShaderExpr("GameTime", GlslType.FLOAT));
        ctx.output("MenuBlurRadius", new ShaderExpr("MenuBlurRadius", GlslType.INT));
        ctx.output("UseRgss", new ShaderExpr("UseRgss", GlslType.INT));
    }
}
