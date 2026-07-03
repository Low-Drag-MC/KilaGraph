package com.lowdragmc.kilagraph.rendertype.nodes.transform;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_dynamic_transforms_ubo", group = "rendertype_transform", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class DynamicTransformsUboNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_dynamic_transforms_ubo.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addOutputPort("ModelViewMat", RenderTypeGraphTypes.MAT4);
        context.addOutputPort("ColorModulator", RenderTypeGraphTypes.VEC4);
        context.addOutputPort("ModelOffset", RenderTypeGraphTypes.VEC3);
        context.addOutputPort("TextureMat", RenderTypeGraphTypes.MAT4);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // 1.21.1: these are individual uniforms (no dynamictransforms.glsl UBO). ModelViewMat/ColorModulator/
        // TextureMat are auto-set by ShaderInstance.setDefaultUniforms; ModelOffset by KGBuiltinUniforms (0).
        ctx.output("ModelViewMat", new ShaderExpr(ctx.useBuiltinUniform("ModelViewMat", GlslType.MAT4), GlslType.MAT4));
        ctx.output("ColorModulator", new ShaderExpr(ctx.useBuiltinUniform("ColorModulator", GlslType.VEC4), GlslType.VEC4));
        ctx.output("ModelOffset", new ShaderExpr(ctx.useBuiltinUniform("ModelOffset", GlslType.VEC3), GlslType.VEC3));
        ctx.output("TextureMat", new ShaderExpr(ctx.useBuiltinUniform("TextureMat", GlslType.MAT4), GlslType.MAT4));
    }
}
