package com.lowdragmc.kilagraph.rendertype.nodes.fog;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

@NodeAttribute(name = "rt_linear_fog_value", group = "rendertype_scene", graphTypes = {RenderTypeGraph.class, ShaderFunctionGraph.class})
public class LinearFogValueNode extends ShaderNode {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_linear_fog_value.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("vertexDistance", TypeHandles.FLOAT);
        context.addInputPort("fogStart", TypeHandles.FLOAT);
        context.addInputPort("fogEnd", TypeHandles.FLOAT);
        context.addOutputPort("out", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext ctx) {
        // Inline mirror of fog.glsl's function (FogGlsl) instead of the #moj_import — pure math, and an
        // include in the fragment path would reject the whole graph from Iris injection.
        ctx.function("linear_fog_value", com.lowdragmc.kilagraph.rendertype.compiler.FogGlsl.FN_LINEAR_FOG_VALUE);
        String code = "linear_fog_value(" + ctx.input("vertexDistance").code() + ", "
                + ctx.input("fogStart").code() + ", " + ctx.input("fogEnd").code() + ")";
        ctx.output("out", new ShaderExpr(code, GlslType.FLOAT));
    }
}
