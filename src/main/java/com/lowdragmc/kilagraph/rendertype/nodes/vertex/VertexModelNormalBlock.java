package com.lowdragmc.kilagraph.rendertype.nodes.vertex;

import net.minecraft.network.chat.Component;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.IVertexOutputBlock;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderBlockNode;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.VertexOutputs;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.UseWithContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;

/**
 * Unity-like vertex <b>Normal</b> block: the connected value replaces the model-space normal for every
 * consumer — the default lit vertex colour ({@code minecraft_mix_light}), the world-normal varying
 * behind Fresnel-style defaults, and the Normal input node. Unconnected it contributes nothing (the
 * mesh's own {@code Normal} attribute flows through, byte-identical GLSL).
 */
@UseWithContext(VaryingStageNode.class)
@NodeAttribute(name = "rt_vertex_model_normal", group = "rendertype_vertex", graphTypes = RenderTypeGraph.class)
public class VertexModelNormalBlock extends ShaderBlockNode implements IVertexOutputBlock {
    @Override
    protected Component getNodeTooltip() {
        return Component.translatable("kg.node.rt_vertex_model_normal.tooltip");
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        context.addInputPort("normal", RenderTypeGraphTypes.VEC3).withoutConfigurator();
    }

    @Override
    public void emitVertex(ShaderCompileContext ctx, VertexOutputs out) {
        if (ctx.isConnected("normal")) {
            out.normal = ctx.input("normal");
        }
        // Unconnected: contribute nothing — the mesh normal flows through untouched (identity).
    }

    @Override
    public String glslExample() {
        return "vec3 kg_vertexNormal = normal;\n// every consumer reads this instead of Normal";
    }
}
