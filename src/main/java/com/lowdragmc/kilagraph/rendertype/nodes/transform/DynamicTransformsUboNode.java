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
        // Under an Iris shaderpack we run inside the pack's gbuffers program, which doesn't declare Mojang's
        // DynamicTransforms UBO (and we can't #moj_import it there). Degrade to neutral values so the graph
        // stays injection-compatible: identity matrices, no colour modulation, no model offset. (Per-draw
        // ColorModulator/ModelOffset are lost under shaderpacks for now — base surface shading is preserved.)
        if (ctx.isInjection()) {
            ctx.output("ModelViewMat", new ShaderExpr("mat4(1.0)", GlslType.MAT4));
            ctx.output("ColorModulator", new ShaderExpr("vec4(1.0)", GlslType.VEC4));
            ctx.output("ModelOffset", new ShaderExpr("vec3(0.0)", GlslType.VEC3));
            ctx.output("TextureMat", new ShaderExpr("mat4(1.0)", GlslType.MAT4));
            return;
        }
        ctx.useMinecraftUniform("DynamicTransforms", "minecraft:dynamictransforms.glsl");
        ctx.output("ModelViewMat", new ShaderExpr("ModelViewMat", GlslType.MAT4));
        ctx.output("ColorModulator", new ShaderExpr("ColorModulator", GlslType.VEC4));
        ctx.output("ModelOffset", new ShaderExpr("ModelOffset", GlslType.VEC3));
        ctx.output("TextureMat", new ShaderExpr("TextureMat", GlslType.MAT4));
    }
}
